package com.projects.tracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Same rules as the page: tasks are open from the day they are added; a step is late after 7 working days (Sun–Thu); N/A steps are ignored. */
final class Summary {
    static final int LATE_DAYS = 7;

    static final class Item {
        final boolean task, late; final String text, when; String note = "";
        Item(boolean task, String text, String when, boolean late) { this.task = task; this.text = text; this.when = when; this.late = late; }
    }
    static final class Group {
        final String name, status; final List<Item> items = new ArrayList<>();
        Group(String name, String status) { this.name = name; this.status = status; }
    }

    final List<String> pushLines = new ArrayList<>();
    final List<Group> groups = new ArrayList<>();
    String emailText = "", emailHtml = "", dateLabel = "";
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
        LocalDate today = LocalDate.now();
        s.dateLabel = today.getDayOfMonth() + "/" + today.getMonthValue() + "/" + today.getYear();
        JSONArray names = root != null ? root.optJSONArray("steps") : null;
        JSONObject projects = root != null ? root.optJSONObject("projects") : null;
        List<JSONObject> list = new ArrayList<>();
        if (projects != null) {
            Iterator<String> keys = projects.keys();
            while (keys.hasNext()) {
                JSONObject p = projects.optJSONObject(keys.next());
                if (p != null) list.add(p);
            }
        }
        list.sort((a, b) -> Double.compare(a.optDouble("order", 0), b.optDouble("order", 0)));

        for (JSONObject p : list) {
            Group g = new Group(p.optString("name"), p.optString("status"));
            JSONArray tasks = p.optJSONArray("tasks");
            if (tasks != null) for (int i = 0; i < tasks.length(); i++) {
                JSONObject t = tasks.optJSONObject(i);
                if (t == null || t.optString("text").isEmpty() || !t.optString("done").isEmpty()) continue;
                long d = 0;
                try { d = ChronoUnit.DAYS.between(LocalDate.parse(t.optString("opened")), today); } catch (Exception ignored) {}
                s.openTasks++;
                Item item = new Item(true, t.optString("text"), openLabel(d), false);
                JSONArray ups = t.optJSONArray("updates");
                if (ups != null && ups.length() > 0) {
                    JSONObject last = ups.optJSONObject(ups.length() - 1);
                    if (last != null) {
                        String dt = last.optString("date");
                        try { LocalDate ld = LocalDate.parse(dt); dt = ld.getDayOfMonth() + "/" + ld.getMonthValue(); } catch (Exception ignored) {}
                        item.note = "עדכון אחרון (" + dt + "): " + last.optString("text");
                    }
                }
                g.items.add(item);
                s.pushLines.add(g.name + " · משימה: " + t.optString("text") + " (" + openLabel(d) + ")");
            }
            JSONArray steps = p.optJSONArray("steps");
            if (steps != null) for (int i = 0; i < steps.length(); i++) {
                JSONObject st = steps.optJSONObject(i);
                if (st == null || st.optBoolean("na", false)) continue;
                String plan = st.optString("plan"), done = st.optString("done");
                if (plan.isEmpty() || !done.isEmpty()) continue;
                int d;
                try { d = workingDays(LocalDate.parse(plan), today); } catch (Exception e) { continue; }
                String name = (names != null && i < names.length()) ? names.optString(i) : ("שלב " + (i + 1));
                boolean late = d >= LATE_DAYS;
                s.activeSteps++;
                if (late) { s.lateSteps++; s.pushLines.add(g.name + " · " + name + " (" + d + " ימי עבודה)"); }
                g.items.add(new Item(false, name, "אושר לפני " + d + " ימי עבודה", late));
            }
            if (!g.items.isEmpty()) s.groups.add(g);
        }
        s.renderText();
        s.renderHtml();
        return s;
    }

    String statsLine() {
        return openTasks + " משימות פתוחות · " + activeSteps + " שלבים בביצוע" + (lateSteps > 0 ? " (" + lateSteps + " באיחור)" : "");
    }

    private void renderText() {
        StringBuilder b = new StringBuilder("סיכום נושאים פתוחים – " + dateLabel + "\n" + statsLine() + "\n");
        if (groups.isEmpty()) b.append("\nאין נושאים פתוחים. כל הכבוד!");
        for (Group g : groups) {
            b.append("\n").append(g.name).append(g.status.isEmpty() ? "" : " (" + g.status + ")").append("\n");
            for (Item it : g.items) {
                b.append("  - ").append(it.task ? "משימה: " : "שלב: ").append(it.text).append(" – ").append(it.when)
                 .append(it.late ? " – באיחור" : "").append("\n");
                if (!it.note.isEmpty()) b.append("      ").append(it.note).append("\n");
            }
        }
        emailText = b.toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Table layout with inline styles, so Outlook and Gmail both keep the lines and the right-to-left order. */
    private void renderHtml() {
        String font = "font-family:Arial,Helvetica,sans-serif;";
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html dir=\"rtl\" lang=\"he\"><head><meta charset=\"utf-8\"></head>")
         .append("<body dir=\"rtl\" style=\"margin:0;padding:0;background:#eef2f8;").append(font).append("\">")
         .append("<table role=\"presentation\" dir=\"rtl\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#eef2f8;\"><tr><td align=\"center\" style=\"padding:18px 10px;\">")
         .append("<table role=\"presentation\" dir=\"rtl\" width=\"640\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;max-width:640px;background:#ffffff;border:1px solid #dce3ee;\">");
        // header
        h.append("<tr><td dir=\"rtl\" align=\"right\" style=\"background:#1b2d6e;padding:18px 20px;text-align:right;").append(font).append("\">")
         .append("<div style=\"color:#ffffff;font-size:20px;font-weight:bold;\">סיכום נושאים פתוחים</div>")
         .append("<div style=\"color:#bcd0ff;font-size:13px;padding-top:4px;\">").append(esc(dateLabel)).append(" &nbsp;|&nbsp; ").append(esc(statsLine())).append("</div>")
         .append("</td></tr>");
        if (groups.isEmpty()) {
            h.append("<tr><td dir=\"rtl\" align=\"right\" style=\"padding:20px;font-size:15px;text-align:right;").append(font).append("\">אין נושאים פתוחים. כל הכבוד!</td></tr>");
        }
        for (Group g : groups) {
            h.append("<tr><td dir=\"rtl\" align=\"right\" style=\"padding:18px 20px 6px;text-align:right;").append(font).append("\">")
             .append("<span style=\"font-size:17px;font-weight:bold;color:#0e1630;\">").append(esc(g.name)).append("</span>")
             .append(g.status.isEmpty() ? "" : "<span style=\"font-size:13px;color:#5a6785;\"> &nbsp;(" + esc(g.status) + ")</span>")
             .append("<div style=\"height:2px;background:#2f6bff;margin-top:6px;font-size:0;line-height:0;\">&nbsp;</div></td></tr>");
            for (Item it : g.items) {
                String tagBg = it.task ? "#e4ecff" : (it.late ? "#fde8e8" : "#fff1dc");
                String tagFg = it.task ? "#2f6bff" : (it.late ? "#d63a3f" : "#b26b00");
                String tag = it.task ? "משימה" : (it.late ? "שלב באיחור" : "שלב");
                h.append("<tr><td dir=\"rtl\" align=\"right\" style=\"padding:8px 20px;border-bottom:1px solid #edf1f7;text-align:right;font-size:14px;color:#0e1630;")
                 .append(it.late ? "background:#fff7f7;" : "").append(font).append("\">")
                 .append("<span style=\"background:").append(tagBg).append(";color:").append(tagFg)
                 .append(";font-size:12px;font-weight:bold;padding:2px 8px;\">").append(tag).append("</span> &nbsp;")
                 .append("<b>").append(esc(it.text)).append("</b>")
                 .append("<span style=\"color:").append(it.late ? "#d63a3f" : "#5a6785").append(";\"> – ").append(esc(it.when)).append("</span>")
                 .append(it.note.isEmpty() ? "" : "<div style=\"font-size:12px;color:#5a6785;padding-top:3px;\">" + esc(it.note) + "</div>")
                 .append("</td></tr>");
            }
        }
        h.append("<tr><td dir=\"rtl\" align=\"right\" style=\"padding:14px 20px;font-size:12px;color:#8c9ab8;text-align:right;").append(font).append("\">נשלח אוטומטית מאפליקציית מעקב פרויקטים</td></tr>")
         .append("</table></td></tr></table></body></html>");
        emailHtml = h.toString();
    }
}
