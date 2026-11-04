package com.projects.tracker;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Feeds the widget list: open tasks grouped by project ("כללי" first). */
public class TaskWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static final class Row {
        final boolean header; final String text, sub, pid, tid;
        Row(boolean header, String text, String sub, String pid, String tid) {
            this.header = header; this.text = text; this.sub = sub; this.pid = pid; this.tid = tid;
        }
    }

    static List<Row> build(Context c) {
        List<Row> rows = new ArrayList<>();
        JSONObject root = Store.load(c);
        JSONObject projects = root != null ? root.optJSONObject("projects") : null;
        if (projects == null) return rows;
        List<String> ids = new ArrayList<>();
        Iterator<String> it = projects.keys();
        while (it.hasNext()) ids.add(it.next());
        ids.sort((a, b) -> Double.compare(projects.optJSONObject(a).optDouble("order", 0), projects.optJSONObject(b).optDouble("order", 0)));
        LocalDate today = LocalDate.now();
        for (String pid : ids) {
            JSONObject p = projects.optJSONObject(pid);
            JSONArray tasks = p != null ? p.optJSONArray("tasks") : null;
            if (tasks == null) continue;
            List<JSONObject> open = new ArrayList<>();
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject t = tasks.optJSONObject(i);
                if (t != null && !t.optString("text").isEmpty() && t.optString("done").isEmpty()) open.add(t);
            }
            if (open.isEmpty()) continue;
            open.sort((a, b) -> a.optString("opened").compareTo(b.optString("opened")));
            rows.add(new Row(true, p.optString("name") + "  ·  " + open.size(), "", pid, ""));
            for (JSONObject t : open) {
                long d = 0;
                try { d = ChronoUnit.DAYS.between(LocalDate.parse(t.optString("opened")), today); } catch (Exception ignored) {}
                String sub = Summary.openLabel(d);
                JSONArray ups = t.optJSONArray("updates");
                if (ups != null && ups.length() > 0) {
                    JSONObject last = ups.optJSONObject(ups.length() - 1);
                    if (last != null) sub += " · " + last.optString("text");
                }
                rows.add(new Row(false, t.optString("text"), sub, pid, t.optString("id")));
            }
        }
        return rows;
    }

    static int openCount(Context c) {
        int n = 0;
        for (Row r : build(c)) if (!r.header) n++;
        return n;
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context c;
        private List<Row> rows = new ArrayList<>();

        Factory(Context c) { this.c = c; }

        @Override public void onCreate() {}
        @Override public void onDataSetChanged() { rows = build(c); }
        @Override public void onDestroy() {}
        @Override public int getCount() { return rows.size(); }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return false; }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position >= rows.size()) return null;
            Row r = rows.get(position);
            if (r.header) {
                RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_row_header);
                v.setTextViewText(R.id.row_title, r.text);
                return v;
            }
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_row_task);
            v.setTextViewText(R.id.row_text, r.text);
            v.setTextViewText(R.id.row_sub, r.sub);
            Intent fill = new Intent();
            fill.putExtra("pid", r.pid);
            fill.putExtra("tid", r.tid);
            v.setOnClickFillInIntent(R.id.row_check, fill);
            return v;
        }
    }
}
