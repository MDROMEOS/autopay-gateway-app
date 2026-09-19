package com.gateway.autopay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                String format = bundle.getString("format");
                if (pdus != null) {
                    StringBuilder fullBody = new StringBuilder();
                    String sender = "";

                    for (Object pdu : pdus) {
                        try {
                            SmsMessage msg;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                msg = SmsMessage.createFromPdu((byte[]) pdu, format);
                            } else {
                                msg = SmsMessage.createFromPdu((byte[]) pdu);
                            }
                            if (msg != null) {
                                sender = msg.getDisplayOriginatingAddress();
                                if (msg.getMessageBody() != null) {
                                    fullBody.append(msg.getMessageBody());
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    String smsText = fullBody.toString();
                    SharedPreferences prefs = context.getSharedPreferences("GatewayPrefs", Context.MODE_PRIVATE);
                    boolean bkashOn = prefs.getBoolean("bkash_enabled", true);
                    boolean nagadOn = prefs.getBoolean("nagad_enabled", true);
                    boolean qrOn = prefs.getBoolean("bangla_qr_enabled", false);
                    boolean isVerified = prefs.getBoolean("is_key_verified", false);

                    if (isVerified) {
                        if (sender != null && sender.equalsIgnoreCase("bKash") && bkashOn) {
                            forwardToServer(context, smsText, "bkash");
                        } else if (sender != null && sender.equalsIgnoreCase("NAGAD") && nagadOn) {
                            forwardToServer(context, smsText, "nagad");
                        } else if (qrOn && (
                                (sender != null && (sender.equalsIgnoreCase("TallyPay") || sender.toLowerCase().contains("tallypay"))) ||
                                smsText.contains("TallyPay QR payment") ||
                                smsText.toLowerCase().contains("tallypay")
                        )) {
                            forwardToServer(context, smsText, "bangla_qr");
                        }
                    }
                }
            }
        }
    }

    public static void forwardToServer(Context context, String smsText, String method) {
        SharedPreferences prefs = context.getSharedPreferences("GatewayPrefs", Context.MODE_PRIVATE);
        String serverUrl = prefs.getString("server_url", "https://allgatewaypayment.bd-voter-cloud.workers.dev");
        String apiKey = prefs.getString("api_key", "");

        new Thread(() -> {
            try {
                String cleanUrl = serverUrl.replaceAll("/+$", "");
                URL url = new URL(cleanUrl + "/api/sms-webhook");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("api_key", apiKey);
                json.put("message", smsText);
                json.put("method", method);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                conn.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
    }
}
