package com.projects.tracker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs about once an hour. Once a day, after the chosen hour, it posts the open-items notification
 * and (if enabled) sends the summary email. With input "test" it sends one email right away.
 */
public class CheckWorker extends Worker {

    static final String CHANNEL = "open-items";

    public CheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void createChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "נושאים פתוחים", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("תזכורת יומית על משימות פתוחות ושלבים באיחור");
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        createChannel(c);
        JSONObject root = Store.load(c);
        JSONObject settings = root != null ? root.optJSONObject("settings") : null;
        if (settings == null) settings = new JSONObject();

        if (getInputData().getBoolean("test", false)) {
            String err = sendEmail(c, root, settings, true);
            notify(c, 2, err == null ? "מייל הבדיקה נשלח" : "שליחת המייל נכשלה",
                    err == null ? "בדוק את תיבת הדואר של הנמענים." : err, null);
            return Result.success();
        }

        SharedPreferences st = Store.state(c);
        String today = LocalDate.now().toString();
        int hour = LocalTime.now().getHour();

        if (hour >= settings.optInt("pushHour", 9) && !today.equals(st.getString("lastPush", ""))) {
            Summary s = Summary.build(root);
            if (!s.pushLines.isEmpty()) {
                notify(c, 1, "יש עדיין נושאים פתוחים",
                        s.pushLines.size() + " נושאים פתוחים: " + s.openTasks + " משימות, " + s.lateSteps + " שלבים באיחור",
                        String.join("\n", s.pushLines));
            }
            st.edit().putString("lastPush", today).apply();
        }

        if (settings.optBoolean("emailEnabled", false)
                && hour >= settings.optInt("emailHour", 8)
                && !today.equals(st.getString("lastEmail", ""))) {
            String err = sendEmail(c, root, settings, false);
            if (err == null) st.edit().putString("lastEmail", today).apply();
            // on failure it simply tries again on the next hourly run
        }
        return Result.success();
    }

    /** Returns null on success, otherwise a message in Hebrew. */
    private static String sendEmail(Context c, JSONObject root, JSONObject settings, boolean test) {
        String from = settings.optString("from", "").trim();
        String pass = Store.password(c);
        List<String> to = new ArrayList<>();
        JSONArray r = settings.optJSONArray("recipients");
        if (r != null) for (int i = 0; i < r.length(); i++) if (!r.optString(i).isEmpty()) to.add(r.optString(i));

        if (to.isEmpty()) return "לא הוגדרו נמענים בהגדרות.";
        if (from.isEmpty()) return "לא הוגדרה כתובת Gmail לשליחה.";
        if (pass.isEmpty()) return "לא נשמרה סיסמת אפליקציה של Gmail.";

        Summary s = Summary.build(root);
        String subject = (test ? "[בדיקה] " : "") + "סיכום נושאים פתוחים – " + s.openTasks + " משימות, " + s.lateSteps + " שלבים באיחור";
        try {
            Mailer.send(from, pass, to, subject, s.emailText);
            return null;
        } catch (javax.mail.AuthenticationFailedException e) {
            return "Gmail דחה את ההתחברות. בדוק את כתובת השולח ואת סיסמת האפליקציה.";
        } catch (Exception e) {
            return "אין חיבור לשרת המייל, או שהשליחה נכשלה. נסה שוב מאוחר יותר.";
        }
    }

    private static void notify(Context c, int id, String title, String text, String big) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(big != null ? big : text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        Notification n = b.build();
        try { NotificationManagerCompat.from(c).notify(id, n); } catch (SecurityException ignored) {}
    }
}
