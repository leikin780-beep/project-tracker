package com.projects.tracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Same rules as the page: tasks are open from the day they are added; a step is late after 7 working days (Sun–Thu). */
final class Summary {
    static final int LATE_DAYS = 7;

    final List<String> pushLines = new ArrayList<>();
    String emailText = "";
    int openTasks, activeSteps, lateSteps;

    static int workingDays(LocalDate from, LocalDate to) {
        int n = 0;
        LocalDate d = from;
        while (d.isBefore(to)) {
            d = d.plusDays(1);
            DayOfWeek w = d.getDayOfWeek();
            if (w != DayOfWeek.FRIDAY && w != DayOfWeek.SATURDAY) n++;
        }
        return n;
    }

    static String openLabel(long n) {
        if (n <= 0) return "נפתחה היום";
        if (n == 1) return "פתוחה יום";
        return "פתוחה " + n + " ימים";
    }

    static Summary build(JSONObject root) {
        Summary s = new Summary();
        if (root == null) return s;
        JSONArray names = root.optJSONArray("steps");
        JSONObject projects = root.optJSONObject("projects");
        if (projects == null) return s;

        List<JSONObject> list = new ArrayList<>();
        Iterator<String> keys = projects.keys();
        while (keys.hasNext()) {
            JSONObject p = projects.optJSONObject(keys.next());
            if (p != null) list.add(p);
        }
        list.sort((a, b) -> Double.compare(a.optDouble("order", 0), b.optDouble("order", 0)));

        LocalDate today = LocalDate.now();
        List<String> blocks = new ArrayList<>();
        for (JSONObject p : list) {
            String pname = p.optString("name");
            List<String> lines = new ArrayList<>();

            JSONArray tasks = p.optJSONArray("tasks");
            if (tasks != null) for (int i = 0; i < tasks.length(); i++) {
                JSONObject t = tasks.optJSONObject(i);
                if (t == null || t.optString("text").isEmpty() || !t.optString("done").isEmpty()) continue;
                long d = 0;
                try { d = ChronoUnit.DAYS.between(LocalDate.parse(t.optString("opened")), today); } catch (Exception ignored) {}
                s.openTasks++;
                lines.add("   • משימה: " + t.optString("text") + " – " + openLabel(d));
                s.pushLines.add(pname + " · משימה: " + t.optString("text") + " (" + openLabel(d) + ")");
            }

            JSONArray steps = p.optJSONArray("steps");
            if (steps != null) for (int i = 0; i < steps.length(); i++) {
                JSONObject st = steps.optJSONObject(i);
                if (st == null) continue;
                String plan = st.optString("plan"), done = st.optString("done");
                if (plan.isEmpty() || !done.isEmpty()) continue;
                int d;
                try { d = workingDays(LocalDate.parse(plan), today); } catch (Exception e) { continue; }
                String name = (names != null && i < names.length()) ? names.optString(i) : ("שלב " + (i + 1));
                boolean late = d >= LATE_DAYS;
                s.activeSteps++;
                if (late) {
                    s.lateSteps++;
                    s.pushLines.add(pname + " · " + name + " (" + d + " ימי עבודה)");
                }
                lines.add("   • שלב: " + name + " – אושר לפני " + d + " ימי עבודה" + (late ? " ⚠ באיחור" : ""));
            }

            if (!lines.isEmpty()) blocks.add("■ " + pname + " (" + p.optString("status") + ")\n" + String.join("\n", lines));
        }

        String head = "סיכום נושאים פתוחים – " + today.getDayOfMonth() + "/" + today.getMonthValue() + "/" + today.getYear() + "\n"
                + s.openTasks + " משימות פתוחות · " + s.activeSteps + " שלבים בביצוע"
                + (s.lateSteps > 0 ? " (מתוכם " + s.lateSteps + " באיחור)" : "") + "\n";
        s.emailText = blocks.isEmpty() ? head + "\nאין נושאים פתוחים. כל הכבוד!" : head + "\n" + String.join("\n\n", blocks);
        return s;
    }
}
