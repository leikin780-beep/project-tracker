package com.projects.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import org.json.JSONObject;

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
