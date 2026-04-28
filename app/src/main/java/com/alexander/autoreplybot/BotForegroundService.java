package com.alexander.autoreplybot;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Foreground service that keeps the app alive so Android doesn't kill it.
 * Shows a persistent notification in the status bar while the bot is active.
 */
public class BotForegroundService extends Service {

    private static final String TAG = "BotForegroundService";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "com.alexander.autoreplybot.START_FG";
    public static final String ACTION_STOP  = "com.alexander.autoreplybot.STOP_FG";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Log.d(TAG, "Stopping foreground service");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Starting foreground service");
        startForeground(NOTIFICATION_ID, buildForegroundNotification());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildForegroundNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Stop action
        Intent stopIntent = new Intent(this, BotForegroundService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, AutoReplyNotificationService.CHANNEL_FG)
                .setSmallIcon(R.drawable.ic_bot_notification)
                .setContentTitle(getString(R.string.fg_notification_title))
                .setContentText(getString(R.string.fg_notification_text))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi)
                .addAction(R.drawable.ic_stop, getString(R.string.stop_bot), stopPi)
                .setOngoing(true)
                .build();
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, BotForegroundService.class);
        intent.setAction(ACTION_START);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, BotForegroundService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }
}
