package com.projects.tracker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0a1230"));

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl("file:///android_asset/index.html");
        }

        CheckWorker.createChannel(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        scheduleChecks();
    }

    /** Hourly check; the worker itself decides when the daily notification and email are due. */
    private void scheduleChecks() {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(CheckWorker.class, 1, TimeUnit.HOURS).build();
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork("hourly-check", ExistingPeriodicWorkPolicy.KEEP, req);
        WorkManager.getInstance(this).cancelUniqueWork("daily-reminder");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** Methods the page calls as window.Android.* */
    private class Bridge {
        @JavascriptInterface
        public void saveData(String json) {
            try (FileOutputStream out = openFileOutput("projects.json", MODE_PRIVATE)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void savePassword(String pass) {
            Store.savePassword(MainActivity.this, pass);
        }

        @JavascriptInterface
        public boolean hasPassword() {
            return !Store.password(MainActivity.this).isEmpty();
        }

        @JavascriptInterface
        public void sendTestEmail() {
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(CheckWorker.class)
                    .setInputData(new Data.Builder().putBoolean("test", true).build())
                    .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build();
            WorkManager.getInstance(MainActivity.this).enqueue(req);
        }

        @JavascriptInterface
        public void shareCsv(String csv, String fileName) {
            try {
                File dir = new File(getCacheDir(), "exports");
                dir.mkdirs();
                File f = new File(dir, fileName);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(csv.getBytes(StandardCharsets.UTF_8));
                }
                Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/csv");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(send, "ייצוא לאקסל")));
            } catch (Exception ignored) {
            }
        }
    }
}
