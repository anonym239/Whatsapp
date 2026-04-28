package com.alexander.autoreplybot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Restarts the foreground service after device reboot if the bot was enabled.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            AppPreferences prefs = new AppPreferences(context);
            if (prefs.isBotEnabled()) {
                Log.d(TAG, "Boot completed – restarting foreground service");
                BotForegroundService.start(context);
            }
        }
    }
}
