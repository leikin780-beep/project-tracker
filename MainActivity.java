package com.projects.tracker;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.CalendarContract;
import android.speech.RecognizerIntent;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.TimeZone;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static final int PICK_FILE = 42;
    private static final int VOICE = 43;
    private static final int CAL_PERMISSION = 2;
    private static final String OUTLOOK = "com.microsoft.office.outlook";
    private static final String PAGE = "file:///android_asset/index.html";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0a1230"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0a1230"));
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "בחירת קובץ גיבוי"), PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(PAGE + hashFor(getIntent()));
        }

        CheckWorker.createChannel(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        scheduleChecks();
    }

    private static String hashFor(Intent i) {
        String a = i != null ? i.getAction() : null;
        if (TaskWidget.ACTION_NEW_TASK.equals(a)) return "#newtask";
        if (TaskWidget.ACTION_MEETING.equals(a)) return "#meeting-voice";
        return "";
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String h = hashFor(intent);
        if (web == null) return;
        if (h.equals("#newtask")) web.evaluateJavascript("window.openNewTask&&window.openNewTask()", null);
        if (h.equals("#meeting-voice")) web.evaluateJavascript("window.openMeeting&&window.openMeeting(true)", null);
    }

    private void js(String code) {
        web.post(() -> web.evaluateJavascript(code, null));
    }

    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAL_PERMISSION) js("window.onCalendarPermission&&window.onCalendarPermission(" + hasCalendarPermission() + ")");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VOICE) {
            String text = null;
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) text = r.get(0);
            }
            js("window.onVoiceResult&&window.onVoiceResult(" + (text == null ? "null" : JSONObject.quote(text)) + ")");
            return;
        }
        if (requestCode == PICK_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** Hourly check; the worker itself decides when the daily notification and email are due. */
    private void scheduleChecks() {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(CheckWorker.class, 1, TimeUnit.HOURS).build();
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork("hourly-check", ExistingPeriodicWorkPolicy.KEEP, req);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) js("window.syncFromNative&&window.syncFromNative()");
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

    private void share(String content, String fileName, String mime, String title) {
        try {
            File dir = new File(getCacheDir(), "exports");
            dir.mkdirs();
            File f = new File(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(() -> startActivity(Intent.createChooser(send, title)));
        } catch (Exception ignored) {
        }
    }

    /** Methods the page calls as window.Android.* */
    private class Bridge {
        @JavascriptInterface
        public void saveData(String json) {
            synchronized (Store.class) {
                try (FileOutputStream out = openFileOutput("projects.json", MODE_PRIVATE)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                }
            }
            TaskWidget.refreshAll(MainActivity.this);
        }

        @JavascriptInterface
        public String takePending() {
            return Store.takePending(MainActivity.this);
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
        public void startVoice() {
            runOnUiThread(() -> {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL");
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, "אמור את פרטי הפגישה");
                try {
                    startActivityForResult(i, VOICE);
                } catch (ActivityNotFoundException e) {
                    js("window.onVoiceResult&&window.onVoiceResult(null)");
                }
            });
        }

        @JavascriptInterface
        public void requestCalendarPermission() {
            runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, CAL_PERMISSION));
        }

        @JavascriptInterface
        public String listCalendars() {
            if (!hasCalendarPermission()) return "{\"permission\":false}";
            JSONArray out = new JSONArray();
            String[] proj = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.IS_PRIMARY};
            String sel = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=" + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
            try (Cursor c = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, proj, sel, null, null)) {
                while (c != null && c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.getLong(0));
                    o.put("name", c.getString(1) == null ? "" : c.getString(1));
                    o.put("account", c.getString(2) == null ? "" : c.getString(2));
                    o.put("type", c.getString(3) == null ? "" : c.getString(3));
                    o.put("primary", c.getInt(4) == 1);
                    out.put(o);
                }
            } catch (Exception e) {
                return "[]";
            }
            return out.toString();
        }

        @JavascriptInterface
        public String addEvent(String json) {
            try {
                if (!hasCalendarPermission()) return "{\"ok\":false,\"error\":\"אין לאפליקציה גישה ליומן\"}";
                JSONObject e = new JSONObject(json);
                ContentValues v = new ContentValues();
                v.put(CalendarContract.Events.CALENDAR_ID, Long.parseLong(e.getString("calendarId")));
                v.put(CalendarContract.Events.TITLE, e.optString("title"));
                v.put(CalendarContract.Events.EVENT_LOCATION, e.optString("location"));
                v.put(CalendarContract.Events.DESCRIPTION, e.optString("notes"));
                v.put(CalendarContract.Events.DTSTART, e.getLong("start"));
                v.put(CalendarContract.Events.DTEND, e.getLong("end"));
                v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
                int rem = e.optInt("reminder", 0);
                v.put(CalendarContract.Events.HAS_ALARM, rem > 0 ? 1 : 0);
                Uri uri = getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v);
                if (uri == null) return "{\"ok\":false,\"error\":\"היומן לא קיבל את הפגישה\"}";
                long id = ContentUris.parseId(uri);
                if (rem > 0) {
                    ContentValues r = new ContentValues();
                    r.put(CalendarContract.Reminders.EVENT_ID, id);
                    r.put(CalendarContract.Reminders.MINUTES, rem);
                    r.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                    getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI, r);
                }
                return "{\"ok\":true,\"id\":" + id + "}";
            } catch (SecurityException se) {
                return "{\"ok\":false,\"error\":\"אין לאפליקציה גישה ליומן\"}";
            } catch (Exception ex) {
                return "{\"ok\":false,\"error\":\"הקביעה נכשלה. נסה פתיחה ב-Outlook\"}";
            }
        }

        @JavascriptInterface
        public void openCalendarInsert(String json) {
            runOnUiThread(() -> {
                try {
                    JSONObject e = new JSONObject(json);
                    Intent i = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, e.optString("title"))
                            .putExtra(CalendarContract.Events.EVENT_LOCATION, e.optString("location"))
                            .putExtra(CalendarContract.Events.DESCRIPTION, e.optString("notes"))
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.getLong("start"))
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.getLong("end"));
                    try {
                        startActivity(new Intent(i).setPackage(OUTLOOK));
                    } catch (ActivityNotFoundException nf) {
                        startActivity(Intent.createChooser(i, "פתיחה ביומן"));
                    }
                } catch (Exception ignored) {
                }
            });
        }

        @JavascriptInterface
        public void shareCsv(String csv, String fileName) {
            share(csv, fileName, "text/csv", "ייצוא לאקסל");
        }

        @JavascriptInterface
        public void shareFile(String content, String fileName, String mime) {
            share(content, fileName, mime, "שמירת גיבוי");
        }
    }
}
