package com.gateway.autopay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.content.ContextCompat;

public class GatewayNotificationService extends NotificationListenerService {

    private void ensureForegroundRunning() {
        try {
            SharedPreferences prefs = getApplicationContext().getSharedPreferences("GatewayPrefs", Context.MODE_PRIVATE);
            boolean shouldRun = prefs.getBoolean("service_enabled", false);
            boolean isVerified = prefs.getBoolean("is_key_verified", false);
            if (shouldRun && isVerified && !ForegroundService.isRunning) {
                Intent serviceIntent = new Intent(getApplicationContext(), ForegroundService.class);
                ContextCompat.startForegroundService(getApplicationContext(), serviceIntent);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        ensureForegroundRunning();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null) return;
            Bundle extras = sbn.getNotification().extras;
            if (extras == null) return;

            String title = extras.getString("android.title", "");
            CharSequence textChar = extras.getCharSequence("android.text");
            String text = textChar != null ? textChar.toString() : "";

            SharedPreferences prefs = getApplicationContext().getSharedPreferences("GatewayPrefs", Context.MODE_PRIVATE);
            boolean bkashOn = prefs.getBoolean("bkash_enabled", true);
            boolean nagadOn = prefs.getBoolean("nagad_enabled", true);
            boolean rocketOn = prefs.getBoolean("rocket_enabled", false);
            boolean upayOn = prefs.getBoolean("upay_enabled", false);
            boolean qrOn = prefs.getBoolean("bangla_qr_enabled", false);
            boolean isVerified = prefs.getBoolean("is_key_verified", false);
            boolean serviceEnabled = prefs.getBoolean("service_enabled", false);

            if (isVerified && serviceEnabled) {
                ensureForegroundRunning();

                if (title != null && title.equalsIgnoreCase("bKash") && bkashOn) {
                    SmsReceiver.forwardToServer(getApplicationContext(), title + " " + text, "bkash");
                } else if (title != null && title.equalsIgnoreCase("NAGAD") && nagadOn) {
                    SmsReceiver.forwardToServer(getApplicationContext(), title + " " + text, "nagad");
                } else if (rocketOn && title != null && (title.equalsIgnoreCase("16216") || title.toLowerCase().contains("rocket"))) {
                    SmsReceiver.forwardToServer(getApplicationContext(), title + " " + text, "rocket");
                } else if (upayOn && title != null && (title.equalsIgnoreCase("upay") || title.toLowerCase().contains("upay"))) {
                    SmsReceiver.forwardToServer(getApplicationContext(), title + " " + text, "upay");
                } else if (qrOn && (
                        (title != null && (title.equalsIgnoreCase("TallyPay") || title.toLowerCase().contains("tallypay"))) ||
                        text.toLowerCase().contains("tallypay")
                )) {
                    SmsReceiver.forwardToServer(getApplicationContext(), title + " " + text, "bangla_qr");
                }
            }
        } catch (Exception ignored) {}
    }
}