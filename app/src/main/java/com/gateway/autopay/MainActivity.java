package com.gateway.autopay;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1001;
    private EditText etServerUrl, etApiKey, etBkashNumber;
    private SwitchCompat switchBkash, switchNagad, switchBanglaQr;
    private Button btnConnect, btnToggleService, btnNotifAccess, btnUploadQr, btnClearQr;
    private TextView tvStatus, tvQrStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("GatewayPrefs", Context.MODE_PRIVATE);

        etServerUrl = findViewById(R.id.etServerUrl);
        etApiKey = findViewById(R.id.etApiKey);
        etBkashNumber = findViewById(R.id.etBkashNumber);
        switchBkash = findViewById(R.id.switchBkash);
        switchNagad = findViewById(R.id.switchNagad);
        switchBanglaQr = findViewById(R.id.switchBanglaQr);
        btnConnect = findViewById(R.id.btnConnect);
        btnToggleService = findViewById(R.id.btnToggleService);
        btnNotifAccess = findViewById(R.id.btnNotifAccess);
        btnUploadQr = findViewById(R.id.btnUploadQr);
        btnClearQr = findViewById(R.id.btnClearQr);
        tvStatus = findViewById(R.id.tvStatus);
        tvQrStatus = findViewById(R.id.tvQrStatus);

        etServerUrl.setText(prefs.getString("server_url", "https://allgatewaypayment.bd-voter-cloud.workers.dev"));
        etApiKey.setText(prefs.getString("api_key", ""));
        etBkashNumber.setText(prefs.getString("bkash_number", ""));
        switchBkash.setChecked(prefs.getBoolean("bkash_enabled", true));
        switchNagad.setChecked(prefs.getBoolean("nagad_enabled", true));
        switchBanglaQr.setChecked(prefs.getBoolean("bangla_qr_enabled", false));

        updateQrStatusUI();
        requestPermissions();

        btnConnect.setOnClickListener(v -> syncMerchantWithServer());
        btnToggleService.setOnClickListener(v -> toggleService());
        btnNotifAccess.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));

        btnUploadQr.setOnClickListener(v -> {
            String apiKey = prefs.getString("api_key", "").trim();
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "⚠️ আগে API Key বসিয়ে 'সেভ ও কানেক্ট' করুন!", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "কিউআর ছবি নির্বাচন করুন"), PICK_IMAGE_REQUEST);
        });

        btnClearQr.setOnClickListener(v -> clearQrImage());

        switchBkash.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("bkash_enabled", isChecked).apply());
        switchNagad.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("nagad_enabled", isChecked).apply());
        switchBanglaQr.setOnCheckedChangeListener((btn, isChecked) -> prefs.edit().putBoolean("bangla_qr_enabled", isChecked).apply());
    }

    private void updateQrStatusUI() {
        boolean hasImage = prefs.getBoolean("has_qr_image", false);
        if (hasImage) {
            tvQrStatus.setText("ছবি স্ট্যাটাস: ✅ আসল স্পষ্ট QR সার্ভারে সেট করা আছে");
            tvQrStatus.setTextColor(0xFF27AE60);
        } else {
            tvQrStatus.setText("ছবি স্ট্যাটাস: ⚠️ কোনো ছবি সেট করা নেই");
            tvQrStatus.setTextColor(0xFF7F8C8D);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            uploadImageToServer(imageUri);
        }
    }

    private void uploadImageToServer(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) {
                Toast.makeText(this, "❌ ছবি লোড করা যায়নি!", Toast.LENGTH_SHORT).show();
                return;
            }

            // কিউআর কোডের পিক্সেল অক্ষত রেখে ৬৫০x৬৫০ পারফেক্ট সাইজ (Lossless PNG)
            int maxDim = 650;
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w > maxDim || h > maxDim) {
                float ratio = Math.min((float) maxDim / w, (float) maxDim / h);
                w = Math.max(1, Math.round(w * ratio));
                h = Math.max(1, Math.round(h * ratio));
                bitmap = Bitmap.createScaledBitmap(bitmap, w, h, false); // false = sharp pixelated, কোনো ঘোলা হবে না
            }

            // শতভাগ আসল ও স্পষ্ট রাখতে PNG ফরম্যাট (যাতে প্রতিটা ডট ১০০% পরিষ্কার থাকে)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

            Toast.makeText(this, "⏳ ছবি সার্ভারে আপলোড হচ্ছে...", Toast.LENGTH_SHORT).show();
            new UploadImageTask().execute(base64Image);
        } catch (Exception e) {
            Toast.makeText(this, "ত্রুটি: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private class UploadImageTask extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... params) {
            try {
                String base64Image = params[0];
                String server = prefs.getString("server_url", "https://allgatewaypayment.bd-voter-cloud.workers.dev").replaceAll("/+$", "");
                String apiKey = prefs.getString("api_key", "");

                URL url = new URL(server + "/api/v1/app/upload-qr");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(25000);
                conn.setReadTimeout(25000);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("api_key", apiKey);
                json.put("image_base64", base64Image);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                return conn.getResponseCode();
            } catch (Exception e) {
                return -1;
            }
        }

        @Override
        protected void onPostExecute(Integer code) {
            if (code == 200) {
                prefs.edit().putBoolean("has_qr_image", true).putBoolean("bangla_qr_enabled", true).apply();
                switchBanglaQr.setChecked(true);
                updateQrStatusUI();
                Toast.makeText(MainActivity.this, "🎉 আসল স্পষ্ট QR ছবি সফলভাবে সেট হয়েছে!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "❌ আপলোড ব্যর্থ! কোড: " + code, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void clearQrImage() {
        new Thread(() -> {
            try {
                String server = prefs.getString("server_url", "https://allgatewaypayment.bd-voter-cloud.workers.dev").replaceAll("/+$", "");
                String apiKey = prefs.getString("api_key", "");

                URL url = new URL(server + "/api/v1/app/upload-qr");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("api_key", apiKey);
                json.put("action", "clear");

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                if (conn.getResponseCode() == 200) {
                    runOnUiThread(() -> {
                        prefs.edit().putBoolean("has_qr_image", false).putBoolean("bangla_qr_enabled", false).apply();
                        switchBanglaQr.setChecked(false);
                        updateQrStatusUI();
                        Toast.makeText(MainActivity.this, "🗑️ ছবি মুছে ফেলা হয়েছে ও বাংলা কিউআর বন্ধ করা হয়েছে।", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean isVerified = prefs.getBoolean("is_key_verified", false);
        boolean shouldRun = prefs.getBoolean("service_enabled", false);

        if (isVerified && shouldRun && !ForegroundService.isRunning) {
            Intent intent = new Intent(this, ForegroundService.class);
            ContextCompat.startForegroundService(this, intent);
        }
        updateServiceStatusUI();
    }

    private void requestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.READ_MEDIA_IMAGES
            };
        } else {
            permissions = new String[]{
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 101);
                break;
            }
        }
    }

    private void syncMerchantWithServer() {
        String server = etServerUrl.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        String number = etBkashNumber.getText().toString().trim();

        if (apiKey.isEmpty() || number.isEmpty()) {
            Toast.makeText(this, "⚠️ API Key এবং মোবাইল নম্বর লিখুন!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (number.length() != 11 || !number.startsWith("01")) {
            Toast.makeText(this, "⚠️ সঠিক ১১ ডিজিটের মোবাইল নম্বর দিন!", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "⏳ সার্ভারের সাথে যাচাই করা হচ্ছে...", Toast.LENGTH_SHORT).show();
        new SyncTask().execute(
            server,
            apiKey,
            number,
            String.valueOf(switchBkash.isChecked()),
            String.valueOf(switchNagad.isChecked()),
            String.valueOf(switchBanglaQr.isChecked())
        );
    }

    private class SyncTask extends AsyncTask<String, Void, Integer> {
        private String server, apiKey, number;
        private boolean bkashOn, nagadOn, banglaQrOn;
        private boolean serverHasQr = false;

        @Override
        protected Integer doInBackground(String... params) {
            server = params[0];
            apiKey = params[1];
            number = params[2];
            bkashOn = Boolean.parseBoolean(params[3]);
            nagadOn = Boolean.parseBoolean(params[4]);
            banglaQrOn = Boolean.parseBoolean(params[5]);

            try {
                String cleanUrl = server.replaceAll("/+$", "");
                URL url = new URL(cleanUrl + "/api/v1/app/connect");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("api_key", apiKey);
                json.put("bkash_number", number);
                json.put("bkash_enabled", bkashOn);
                json.put("nagad_enabled", nagadOn);
                json.put("bangla_qr_enabled", banglaQrOn);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                if (code == 200) {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        JSONObject res = new JSONObject(sb.toString());
                        serverHasQr = !res.optString("banglaQrImage", "").isEmpty();
                    } catch (Exception ignored) {}
                }
                return code;
            } catch (Exception e) {
                return -1;
            }
        }

        @Override
        protected void onPostExecute(Integer statusCode) {
            if (statusCode == 200) {
                prefs.edit()
                    .putString("server_url", server)
                    .putString("api_key", apiKey)
                    .putString("bkash_number", number)
                    .putBoolean("bkash_enabled", bkashOn)
                    .putBoolean("nagad_enabled", nagadOn)
                    .putBoolean("bangla_qr_enabled", banglaQrOn)
                    .putBoolean("has_qr_image", serverHasQr)
                    .putBoolean("is_key_verified", true)
                    .apply();

                updateQrStatusUI();
                Toast.makeText(MainActivity.this, "✅ সেটিংস সার্ভারে সংরক্ষিত ও সক্রিয় হয়েছে!", Toast.LENGTH_SHORT).show();
            } else if (statusCode == 401 || statusCode == 403 || statusCode == 404) {
                prefs.edit().putBoolean("is_key_verified", false).apply();
                Toast.makeText(MainActivity.this, "❌ ভুল API Key! সার্ভারে পাওয়া যায়নি।", Toast.LENGTH_LONG).show();
                stopServiceIfRunning();
            } else {
                Toast.makeText(MainActivity.this, "⚠️ সার্ভারে সংযোগ করা যায়নি! URL চেক করুন।", Toast.LENGTH_SHORT).show();
            }
            updateServiceStatusUI();
        }
    }

    private void toggleService() {
        String key = prefs.getString("api_key", "").trim();
        String num = prefs.getString("bkash_number", "").trim();
        boolean isVerified = prefs.getBoolean("is_key_verified", false);

        if (key.isEmpty() || num.isEmpty() || !isVerified) {
            Toast.makeText(this, "❌ আগে সঠিক API Key দিয়ে 'সেভ ও কানেক্ট' চাপুন!", Toast.LENGTH_LONG).show();
            return;
        }

        boolean willStart = !ForegroundService.isRunning;
        new UpdateServerStatusTask(willStart).execute();
    }

    private class UpdateServerStatusTask extends AsyncTask<Void, Void, Boolean> {
        private boolean targetState;

        public UpdateServerStatusTask(boolean state) {
            this.targetState = state;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                String server = prefs.getString("server_url", "https://allgatewaypayment.bd-voter-cloud.workers.dev");
                String apiKey = prefs.getString("api_key", "");
                String cleanUrl = server.replaceAll("/+$", "");

                URL url = new URL(cleanUrl + "/api/v1/app/status");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(8000);
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("api_key", apiKey);
                json.put("is_running", targetState);

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                return conn.getResponseCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Intent intent = new Intent(MainActivity.this, ForegroundService.class);
            if (targetState) {
                ContextCompat.startForegroundService(MainActivity.this, intent);
                ForegroundService.isRunning = true;
                prefs.edit().putBoolean("service_enabled", true).apply();
                Toast.makeText(MainActivity.this, "🟢 সার্ভিস চালু হয়েছে (পেমেন্ট গ্রহণ সক্রিয়)", Toast.LENGTH_SHORT).show();
            } else {
                stopService(intent);
                ForegroundService.isRunning = false;
                prefs.edit().putBoolean("service_enabled", false).apply();
                Toast.makeText(MainActivity.this, "🔴 সার্ভিস বন্ধ হয়েছে (কী সাময়িক নিষ্ক্রিয়)", Toast.LENGTH_SHORT).show();
            }
            updateServiceStatusUI();
        }
    }

    private void stopServiceIfRunning() {
        if (ForegroundService.isRunning) {
            Intent intent = new Intent(this, ForegroundService.class);
            stopService(intent);
            ForegroundService.isRunning = false;
            prefs.edit().putBoolean("service_enabled", false).apply();
        }
    }

    private void updateServiceStatusUI() {
        boolean active = ForegroundService.isRunning;
        if (active) {
            tvStatus.setText("সার্ভিস স্ট্যাটাস: চালু আছে (Active)");
            tvStatus.setTextColor(0xFF27AE60);
            btnToggleService.setText("সার্ভিস বন্ধ করুন");
            btnToggleService.setBackgroundColor(0xFFE74C3C);
        } else {
            tvStatus.setText("সার্ভিস স্ট্যাটাস: বন্ধ আছে");
            tvStatus.setTextColor(0xFFE74C3C);
            btnToggleService.setText("সার্ভিস চালু করুন");
            btnToggleService.setBackgroundColor(0xFF27AE60);
        }
    }
}
