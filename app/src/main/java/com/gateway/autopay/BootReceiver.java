package com.gateway.autopay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("GatewayPrefs", Context.MODE_PRIVATE);
                if (prefs.getBoolean("service_enabled", false) && prefs.getBoolean("is_key_verified", false)) {
                    Intent serviceIntent = new Intent(context, ForegroundService.class);
                    ContextCompat.startForegroundService(context, serviceIntent);
                }
            } catch (Exception ignored) {
                // ব্যাকগ্রাউন্ড বুট রেস্ট্রিকশনের কারণে ক্র্যাশ এড়ানো, GatewayNotificationService অটো-চালু থাকবে
            }
        }
    }
}