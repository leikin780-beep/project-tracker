package com.projects.tracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.Toast;

/** Home-screen widget: open tasks by project with a quick "done" check, plus new task / voice meeting buttons. */
public class TaskWidget extends AppWidgetProvider {

    static final String ACTION_NEW_TASK = "com.projects.tracker.NEW_TASK";
    static final String ACTION_MEETING = "com.projects.tracker.MEETING_VOICE";
    static final String ACTION_DONE = "com.projects.tracker.TASK_DONE";
    static final String ACTION_REMIND = "com.projects.tracker.REMIND_VOICE";

    private static PendingIntent open(Context context, String action, int code) {
        Intent open = new Intent(context, MainActivity.class);
        open.setAction(action);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, code, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        int n = TaskWidgetService.openCount(context);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_task);
            views.setTextViewText(R.id.widget_title, n > 0 ? "משימות פתוחות (" + n + ")" : "משימות פתוחות");
            views.setOnClickPendingIntent(R.id.widget_title, open(context, "android.intent.action.MAIN", 6));
            views.setOnClickPendingIntent(R.id.widget_logo, open(context, "android.intent.action.MAIN", 6));
            views.setOnClickPendingIntent(R.id.btn_task, open(context, ACTION_NEW_TASK, 7));
            views.setOnClickPendingIntent(R.id.btn_meet, open(context, ACTION_MEETING, 8));
            views.setOnClickPendingIntent(R.id.btn_remind, open(context, ACTION_REMIND, 10));

            Intent svc = new Intent(context, TaskWidgetService.class);
            svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
            views.setRemoteAdapter(R.id.widget_list, svc);
            views.setEmptyView(R.id.widget_list, R.id.widget_empty);

            Intent done = new Intent(context, TaskWidget.class);
            done.setAction(ACTION_DONE);
            PendingIntent template = PendingIntent.getBroadcast(context, 9, done,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            views.setPendingIntentTemplate(R.id.widget_list, template);
            manager.updateAppWidget(id, views);
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_DONE.equals(intent.getAction())) {
            String pid = intent.getStringExtra("pid"), tid = intent.getStringExtra("tid");
            if (pid != null && tid != null && Store.markDone(context, pid, tid)) {
                Toast.makeText(context, "המשימה סומנה כבוצעה ✓", Toast.LENGTH_SHORT).show();
            }
            refreshAll(context);
        }
    }

    /** Redraws every widget of this app (after any data change). */
    static void refreshAll(Context context) {
        AppWidgetManager m = AppWidgetManager.getInstance(context);
        int[] ids = m.getAppWidgetIds(new ComponentName(context, TaskWidget.class));
        if (ids == null || ids.length == 0) return;
        new TaskWidget().onUpdate(context, m, ids);
    }
}
