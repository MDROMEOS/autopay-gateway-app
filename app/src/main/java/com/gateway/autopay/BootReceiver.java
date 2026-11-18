package com.gateway.autopay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("GatewayPrefs", Context.MODE_PRIVATE);
            if (prefs.getBoolean("service_enabled", false) && prefs.getBoolean("is_key_verified", false)) {
                Intent serviceIntent = new Intent(context, ForegroundService.class);
                ContextCompat.startForegroundService(context, serviceIntent);
            }
        }
    }
}
