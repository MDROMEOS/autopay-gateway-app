package com.gateway.autopay;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

public class ForegroundService extends Service {
    public static boolean isRunning = false;
    private static final String CHANNEL_ID = "GatewayAutoPayChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            SharedPreferences prefs = getSharedPreferences("GatewayPrefs", Context.MODE_PRIVATE);
            if (!prefs.getBoolean("is_key_verified", false) || !prefs.getBoolean("service_enabled", false)) {
                isRunning = false;
                stopSelf();
                return START_NOT_STICKY;
            }

            isRunning = true;
            Intent notifIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, notifIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
            );

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Payment Gateway Active")
                    .setContentText("বিকাশ, নগদ, রকেট, উপায় ও বাংলা QR সার্ভিস ২৪/৭ চালু আছে...")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(1, notification);
            }
        } catch (Exception ignored) {}
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            SharedPreferences prefs = getSharedPreferences("GatewayPrefs", Context.MODE_PRIVATE);
            if (prefs.getBoolean("service_enabled", false) && prefs.getBoolean("is_key_verified", false)) {
                Intent restartServiceIntent = new Intent(getApplicationContext(), ForegroundService.class);
                restartServiceIntent.setPackage(getPackageName());

                PendingIntent restartPendingIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    restartPendingIntent = PendingIntent.getForegroundService(
                            getApplicationContext(), 1, restartServiceIntent,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
                    );
                } else {
                    restartPendingIntent = PendingIntent.getService(
                            getApplicationContext(), 1, restartServiceIntent,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
                    );
                }

                AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                if (alarmService != null) {
                    alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1500, restartPendingIntent);
                }
            }
        } catch (Exception ignored) {
            // Android 12+ ব্যাকগ্রাউন্ড রেস্ট্রিকশনে কোনোভাবেই ক্র্যাশ করবে না
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "AutoPay Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}