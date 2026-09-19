package com.gateway.autopay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class GatewayNotificationService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        String title = extras.getString("android.title", "");
        CharSequence textChar = extras.getCharSequence("android.text");
        String text = textChar != null ? textChar.toString() : "";

        SharedPreferences prefs = getApplicationContext().getSharedPreferences("GatewayPrefs", Context.MODE_PRIVATE);
        boolean bkashOn = prefs.getBoolean("bkash_enabled", true);
        boolean nagadOn = prefs.getBoolean("nagad_enabled", true);
        boolean qrOn = prefs.getBoolean("bangla_qr_enabled", false);
        boolean isVerified = prefs.getBoolean("is_key_verified", false);

        if (isVerified) {
            if (title != null && title.equalsIgnoreCase("bKash") && bkashOn) {
                SmsReceiver.forwardToServer(getApplicationContext(), title + " " + text, "bkash");
            } else if (title != null && title.equalsIgnoreCase("NAGAD") && nagadOn) {
                SmsReceiver.forwardToServer(getApplicationContext(), title + " " + text, "nagad");
            } else if (qrOn && (
                    (title != null && (title.equalsIgnoreCase("TallyPay") || title.toLowerCase().contains("tallypay"))) ||
                    text.toLowerCase().contains("tallypay")
            )) {
                SmsReceiver.forwardToServer(getApplicationContext(), title + " " + text, "bangla_qr");
            }
        }
    }
}
