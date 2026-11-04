package com.projects.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.time.LocalDate;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Data written by the page, plus the Gmail app password (kept encrypted on the phone only). */
final class Store {
    private Store() {}

    static JSONObject load(Context c) {
        try {
            File f = new File(c.getFilesDir(), "projects.json");
            if (!f.exists()) return null;
            return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    static synchronized void save(Context c, JSONObject root) {
        try (FileOutputStream out = c.openFileOutput("projects.json", Context.MODE_PRIVATE)) {
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    /** Marks a task done from the widget; the page picks the change up next time it opens. */
    static synchronized boolean markDone(Context c, String pid, String tid) {
        try {
            JSONObject root = load(c);
            if (root == null) return false;
            JSONObject p = root.getJSONObject("projects").optJSONObject(pid);
            JSONArray tasks = p != null ? p.optJSONArray("tasks") : null;
            if (tasks == null) return false;
            String today = LocalDate.now().toString();
            boolean found = false;
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject t = tasks.optJSONObject(i);
                if (t != null && tid.equals(t.optString("id"))) { t.put("done", today); found = true; }
            }
            if (!found) return false;
            save(c, root);
            SharedPreferences st = state(c);
            JSONArray pending = new JSONArray(st.getString("pending", "[]"));
            pending.put(new JSONObject().put("pid", pid).put("tid", tid).put("done", today));
            st.edit().putString("pending", pending.toString()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static synchronized String takePending(Context c) {
        SharedPreferences st = state(c);
        String v = st.getString("pending", "[]");
        st.edit().remove("pending").apply();
        return v;
    }

    static SharedPreferences state(Context c) {
        return c.getSharedPreferences("state", Context.MODE_PRIVATE);
    }

    private static SharedPreferences secure(Context c) throws Exception {
        String key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
        return EncryptedSharedPreferences.create("secure", key, c,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    static void savePassword(Context c, String pass) {
        try { secure(c).edit().putString("gmailAppPassword", pass).apply(); } catch (Exception ignored) {}
    }

    static String password(Context c) {
        try { return secure(c).getString("gmailAppPassword", ""); } catch (Exception e) { return ""; }
    }
}
