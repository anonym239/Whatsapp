package com.alexander.autoreplybot;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alexander.autoreplybot.databinding.ActivityMainBinding;

/**
 * Main screen of AutoReply Bot – Alexander.
 *
 * Features shown here:
 *  • Big ON/OFF toggle
 *  • Delay slider (1–60 min)
 *  • Reply message text field
 *  • Quick-access buttons for Blacklist and Log
 *  • Permission status indicators
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICATION_PERMISSION = 100;
    private static final int REQ_CONTACTS_PERMISSION     = 101;

    private ActivityMainBinding binding;
    private AppPreferences prefs;

    // Receives broadcast when bot sends a reply → refresh status
    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateLastReplyStatus();
        }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = new AppPreferences(this);

        setupToggle();
        setupDelaySlider();
        setupMessageField();
        setupButtons();
        requestPermissionsIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
        updatePermissionStatus();

        // Register broadcast receiver
        IntentFilter filter = new IntentFilter(AutoReplyNotificationService.ACTION_LOG_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(logReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save message text
        String msg = binding.editReplyMessage.getText().toString().trim();
        if (!msg.isEmpty()) {
            prefs.setReplyMessage(msg);
        }
        try {
            unregisterReceiver(logReceiver);
        } catch (IllegalArgumentException ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Setup
    // ─────────────────────────────────────────────────────────────────────────

    private void setupToggle() {
        binding.switchBotEnabled.setChecked(prefs.isBotEnabled());
        updateToggleAppearance(prefs.isBotEnabled());

        binding.switchBotEnabled.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.setBotEnabled(isChecked);
            updateToggleAppearance(isChecked);

            if (isChecked) {
                if (!isNotificationListenerEnabled()) {
                    showNotificationListenerDialog();
                    binding.switchBotEnabled.setChecked(false);
                    prefs.setBotEnabled(false);
                    return;
                }
                BotForegroundService.start(this);
                Toast.makeText(this, R.string.bot_started, Toast.LENGTH_SHORT).show();
            } else {
                BotForegroundService.stop(this);
                Toast.makeText(this, R.string.bot_stopped, Toast.LENGTH_SHORT).show();
            }
            updatePermissionStatus();
        });
    }

    private void setupDelaySlider() {
        int delay = prefs.getDelayMinutes();
        // SeekBar: 0 = 1 min, 59 = 60 min
        binding.seekBarDelay.setMax(59);
        binding.seekBarDelay.setProgress(delay - 1);
        updateDelayLabel(delay);

        binding.seekBarDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int minutes = progress + 1;
                prefs.setDelayMinutes(minutes);
                updateDelayLabel(minutes);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupMessageField() {
        binding.editReplyMessage.setText(prefs.getReplyMessage());
        binding.editReplyMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (!text.isEmpty()) {
                    prefs.setReplyMessage(text);
                }
            }
        });

        binding.btnResetMessage.setOnClickListener(v -> {
            binding.editReplyMessage.setText(AppPreferences.DEFAULT_MESSAGE);
            prefs.setReplyMessage(AppPreferences.DEFAULT_MESSAGE);
        });
    }

    private void setupButtons() {
        binding.btnBlacklist.setOnClickListener(v ->
                startActivity(new Intent(this, BlacklistActivity.class)));

        binding.btnLog.setOnClickListener(v ->
                startActivity(new Intent(this, LogActivity.class)));

        binding.btnGrantPermission.setOnClickListener(v ->
                showNotificationListenerDialog());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Updates
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshUI() {
        binding.switchBotEnabled.setChecked(prefs.isBotEnabled());
        updateToggleAppearance(prefs.isBotEnabled());
        updateDelayLabel(prefs.getDelayMinutes());
        binding.seekBarDelay.setProgress(prefs.getDelayMinutes() - 1);
        binding.editReplyMessage.setText(prefs.getReplyMessage());
        updateLastReplyStatus();
    }

    private void updateToggleAppearance(boolean enabled) {
        if (enabled) {
            binding.cardToggle.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.green_active));
            binding.tvBotStatus.setText(R.string.status_active);
            binding.tvBotStatus.setTextColor(ContextCompat.getColor(this, R.color.white));
            binding.ivBotIcon.setImageResource(R.drawable.ic_bot_on);
        } else {
            binding.cardToggle.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.card_background));
            binding.tvBotStatus.setText(R.string.status_inactive);
            binding.tvBotStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            binding.ivBotIcon.setImageResource(R.drawable.ic_bot_off);
        }
    }

    private void updateDelayLabel(int minutes) {
        binding.tvDelayValue.setText(getString(R.string.delay_value, minutes));
    }

    private void updateLastReplyStatus() {
        java.util.List<LogEntry> entries = prefs.getLogEntries();
        if (entries.isEmpty()) {
            binding.tvLastReply.setText(R.string.no_replies_yet);
        } else {
            LogEntry last = entries.get(0);
            binding.tvLastReply.setText(getString(R.string.last_reply_info,
                    last.getContactName(), last.getFormattedTime()));
        }
    }

    private void updatePermissionStatus() {
        boolean hasListener = isNotificationListenerEnabled();
        if (hasListener) {
            binding.tvPermissionStatus.setText(R.string.permission_granted);
            binding.tvPermissionStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.green_active));
            binding.btnGrantPermission.setVisibility(View.GONE);
        } else {
            binding.tvPermissionStatus.setText(R.string.permission_missing);
            binding.tvPermissionStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.red_error));
            binding.btnGrantPermission.setVisibility(View.VISIBLE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isNotificationListenerEnabled() {
        String flat = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void showNotificationListenerDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_permission_title)
                .setMessage(R.string.dialog_permission_message)
                .setPositiveButton(R.string.dialog_open_settings, (d, w) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void requestPermissionsIfNeeded() {
        // POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIFICATION_PERMISSION);
            }
        }
        // READ_CONTACTS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    REQ_CONTACTS_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_notifications_granted, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
