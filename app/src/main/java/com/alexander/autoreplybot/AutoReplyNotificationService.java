package com.alexander.autoreplybot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import android.app.RemoteInput;

import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

/**
 * Core service that listens to incoming notifications and triggers auto-replies.
 *
 * Flow:
 *  1. onNotificationPosted() fires when a new WhatsApp (or other messaging) notification arrives.
 *  2. We record the sender + the notification's RemoteInput action (the "quick-reply" action).
 *  3. We schedule a delayed Handler callback for the configured delay.
 *  4. If the user replies manually before the delay expires we cancel the pending callback.
 *  5. When the delay fires we send the auto-reply via the RemoteInput action.
 */
public class AutoReplyNotificationService extends NotificationListenerService {

    private static final String TAG = "AutoReplyService";

    // Notification channel for bot-sent confirmations
    public static final String CHANNEL_BOT = "bot_channel";
    public static final String CHANNEL_FG  = "fg_channel";

    // Packages we monitor
    private static final String PKG_WHATSAPP         = "com.whatsapp";
    private static final String PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b";
    private static final String PKG_TELEGRAM         = "org.telegram.messenger";
    private static final String PKG_SIGNAL           = "org.thoughtcrime.securesms";
    private static final String PKG_INSTAGRAM        = "com.instagram.android";
    private static final String PKG_MESSENGER        = "com.facebook.orca";

    // Broadcast action so MainActivity can refresh the log
    public static final String ACTION_LOG_UPDATED = "com.alexander.autoreplybot.LOG_UPDATED";
    // Broadcast to cancel a pending reply (user replied manually)
    public static final String ACTION_CANCEL_REPLY = "com.alexander.autoreplybot.CANCEL_REPLY";

    // key = "package|conversationKey", value = pending runnable
    private final Map<String, Runnable> pendingReplies = new HashMap<>();
    // key = "package|conversationKey", value = StatusBarNotification key for reply
    private final Map<String, ReplyInfo> replyInfoMap = new HashMap<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppPreferences prefs;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new AppPreferences(this);
        createNotificationChannels();
        Log.d(TAG, "AutoReplyNotificationService created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "AutoReplyNotificationService destroyed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification events
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String pkg = sbn.getPackageName();
        if (!isMonitoredPackage(pkg)) return;
        if (!prefs.isBotEnabled()) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        // Skip group summary notifications
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        // Extract sender name
        String senderName = extractSenderName(extras);
        if (senderName == null || senderName.isEmpty()) return;

        // Skip blacklisted contacts
        if (prefs.isBlacklisted(senderName)) {
            Log.d(TAG, "Skipping blacklisted contact: " + senderName);
            return;
        }

        // Find the RemoteInput (quick-reply) action
        Notification.Action replyAction = findReplyAction(notification);
        if (replyAction == null) {
            Log.d(TAG, "No reply action found for: " + senderName);
            return;
        }

        // Build a unique key for this conversation
        String conversationKey = buildConversationKey(sbn, senderName);
        String mapKey = pkg + "|" + conversationKey;

        // Store reply info
        replyInfoMap.put(mapKey, new ReplyInfo(senderName, replyAction, pkg));

        // Cancel any existing pending reply for this conversation (new message arrived)
        cancelPendingReply(mapKey);

        // Schedule auto-reply after configured delay
        long delayMs = prefs.getDelayMinutes() * 60_000L;
        Log.d(TAG, "Scheduling auto-reply for " + senderName + " in " + prefs.getDelayMinutes() + " min");

        Runnable replyRunnable = () -> sendAutoReply(mapKey);
        pendingReplies.put(mapKey, replyRunnable);
        handler.postDelayed(replyRunnable, delayMs);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (!isMonitoredPackage(pkg)) return;

        // When the user opens the chat and reads/replies, the notification is dismissed.
        // We treat notification removal as "user is active" → cancel pending auto-reply.
        Bundle extras = sbn.getNotification() != null ? sbn.getNotification().extras : null;
        if (extras == null) return;

        String senderName = extractSenderName(extras);
        if (senderName == null) return;

        String conversationKey = buildConversationKey(sbn, senderName);
        String mapKey = pkg + "|" + conversationKey;

        cancelPendingReply(mapKey);
        replyInfoMap.remove(mapKey);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auto-reply logic
    // ─────────────────────────────────────────────────────────────────────────

    private void sendAutoReply(String mapKey) {
        ReplyInfo info = replyInfoMap.get(mapKey);
        if (info == null) return;

        pendingReplies.remove(mapKey);

        // Build the reply text, replacing [Name] placeholder
        String replyText = prefs.getReplyMessage()
                .replace("[Name]", info.senderName);

        try {
            // Build the RemoteInput bundle
            Bundle inputBundle = new Bundle();
            RemoteInput[] remoteInputs = info.action.getRemoteInputs();
            if (remoteInputs == null || remoteInputs.length == 0) {
                Log.w(TAG, "No RemoteInputs on action for " + info.senderName);
                return;
            }

            String resultKey = remoteInputs[0].getResultKey();
            inputBundle.putCharSequence(resultKey, replyText);

            Intent fillInIntent = new Intent();
            RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, inputBundle);

            // Send via the action's PendingIntent
            info.action.actionIntent.send(this, 0, fillInIntent);

            Log.d(TAG, "Auto-reply sent to " + info.senderName + ": " + replyText);

            // Log the entry
            LogEntry entry = new LogEntry(info.senderName, replyText, System.currentTimeMillis());
            prefs.addLogEntry(entry);

            // Notify the user that the bot replied
            showBotConfirmationNotification(info.senderName, entry.getFormattedTime());

            // Broadcast so MainActivity can refresh
            sendBroadcast(new Intent(ACTION_LOG_UPDATED));

            // Clean up
            replyInfoMap.remove(mapKey);

        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "PendingIntent cancelled for " + info.senderName, e);
        } catch (Exception e) {
            Log.e(TAG, "Error sending auto-reply to " + info.senderName, e);
        }
    }

    private void cancelPendingReply(String mapKey) {
        Runnable existing = pendingReplies.remove(mapKey);
        if (existing != null) {
            handler.removeCallbacks(existing);
            Log.d(TAG, "Cancelled pending reply for key: " + mapKey);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isMonitoredPackage(String pkg) {
        return PKG_WHATSAPP.equals(pkg)
                || PKG_WHATSAPP_BUSINESS.equals(pkg)
                || PKG_TELEGRAM.equals(pkg)
                || PKG_SIGNAL.equals(pkg)
                || PKG_INSTAGRAM.equals(pkg)
                || PKG_MESSENGER.equals(pkg);
    }

    private String extractSenderName(Bundle extras) {
        // Try EXTRA_TITLE first (usually the contact name in messaging apps)
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title != null && !title.toString().isEmpty()) {
            return title.toString().trim();
        }
        // Fallback to EXTRA_SUB_TEXT
        CharSequence sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        if (sub != null && !sub.toString().isEmpty()) {
            return sub.toString().trim();
        }
        return null;
    }

    private Notification.Action findReplyAction(Notification notification) {
        if (notification.actions == null) return null;
        for (Notification.Action action : notification.actions) {
            if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                return action;
            }
        }
        return null;
    }

    private String buildConversationKey(StatusBarNotification sbn, String senderName) {
        // Use the notification tag if available, otherwise fall back to sender name
        String tag = sbn.getTag();
        if (tag != null && !tag.isEmpty()) return tag;
        return senderName;
    }

    private void showBotConfirmationNotification(String senderName, String time) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_BOT)
                .setSmallIcon(R.drawable.ic_bot_notification)
                .setContentTitle(getString(R.string.bot_replied_title))
                .setContentText(getString(R.string.bot_replied_text, senderName, time))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.bot_replied_text, senderName, time)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        nm.notify((int) System.currentTimeMillis(), notification);
    }

    private void createNotificationChannels() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Bot confirmation channel
        NotificationChannel botChannel = new NotificationChannel(
                CHANNEL_BOT,
                getString(R.string.channel_bot_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        botChannel.setDescription(getString(R.string.channel_bot_desc));
        nm.createNotificationChannel(botChannel);

        // Foreground service channel
        NotificationChannel fgChannel = new NotificationChannel(
                CHANNEL_FG,
                getString(R.string.channel_fg_name),
                NotificationManager.IMPORTANCE_LOW);
        fgChannel.setDescription(getString(R.string.channel_fg_desc));
        nm.createNotificationChannel(fgChannel);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner class to hold reply info
    // ─────────────────────────────────────────────────────────────────────────

    private static class ReplyInfo {
        final String senderName;
        final Notification.Action action;
        final String packageName;

        ReplyInfo(String senderName, Notification.Action action, String packageName) {
            this.senderName = senderName;
            this.action = action;
            this.packageName = packageName;
        }
    }
}
