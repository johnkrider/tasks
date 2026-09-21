package io.johnkrider12.tasks;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home-screen widget: the task happening now, plus the next three.
 *
 * The countdown is a Chronometer, so Android ticks it every second by itself.
 * The rest of the widget is redrawn by an exact alarm at the moment a task starts
 * or ends (and after boot, clock changes, and whenever the app saves the timetable).
 */
public class TimetableWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "StudyWidget";

    static final String ACTION_REFRESH = "io.johnkrider12.tasks.WIDGET_REFRESH";

    private static final int REFRESH_REQUEST_CODE = 7001;
    private static final long MAX_REFRESH_GAP_MS = 60L * 60L * 1000L;

    private static final String[] DAYS = {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    };

    private static final int[] ROW = {R.id.w_row1, R.id.w_row2, R.id.w_row3};
    private static final int[] ROW_TIME = {R.id.w_row1_time, R.id.w_row2_time, R.id.w_row3_time};
    private static final int[] ROW_NAME = {R.id.w_row1_name, R.id.w_row2_name, R.id.w_row3_name};

    private static final class Task {
        final String name;
        final long start;
        final long end;

        Task(String name, long start, long end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }

    // ------------------------------------------------------------------
    // Provider callbacks
    // ------------------------------------------------------------------

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            refreshAll(context);
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        refreshAll(context);
    }

    @Override
    public void onEnabled(Context context) {
        refreshAll(context);
    }

    @Override
    public void onDisabled(Context context) {
        cancelRefresh(context);
    }

    // ------------------------------------------------------------------
    // Refresh
    // ------------------------------------------------------------------

    static void refreshAll(Context context) {
        Context app = context.getApplicationContext();

        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(app);
            int[] ids = manager.getAppWidgetIds(new ComponentName(app, TimetableWidgetProvider.class));

            if (ids == null || ids.length == 0) {
                cancelRefresh(app);
                return;
            }

            long now = System.currentTimeMillis();
            List<Task> all = loadTasks(app, now);

            Task current = null;
            List<Task> next = new ArrayList<>();

            for (Task t : all) {
                if (t.start <= now && now < t.end) {
                    if (current == null) current = t;
                } else if (t.start > now && next.size() < 3) {
                    next.add(t);
                }
            }

            manager.updateAppWidget(ids, render(app, all.isEmpty(), current, next, now));

            long boundary = -1;
            if (current != null) boundary = current.end;
            if (!next.isEmpty() && (boundary < 0 || next.get(0).start < boundary)) {
                boundary = next.get(0).start;
            }
            scheduleRefresh(app, boundary);
        } catch (Throwable e) {
            // A widget bug must never crash the app or block adding the widget.
            Log.e(TAG, "Widget refresh failed", e);
        }
    }

    private static RemoteViews render(Context c, boolean noData, Task current, List<Task> next, long now) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_timetable);

        // Tap anywhere on the widget to open the app.
        Intent launch = new Intent(c, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(
                c, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // ----- current section -----
        if (noData) {
            rv.setTextViewText(R.id.w_now_label, "STUDY TIMETABLE");
            rv.setTextViewText(R.id.w_now_title, "Open the app once to load your timetable");
            rv.setTextViewText(R.id.w_now_time, "");
            hideCountdown(rv);
        } else if (current != null) {
            rv.setTextViewText(R.id.w_now_label, "NOW");
            rv.setTextViewText(R.id.w_now_title, current.name);
            rv.setTextViewText(R.id.w_now_time, clock(c, current.start) + " - " + clock(c, current.end));
            showCountdown(rv, "ends in", current.end - now);
        } else if (!next.isEmpty()) {
            rv.setTextViewText(R.id.w_now_label, "FREE");
            rv.setTextViewText(R.id.w_now_title, "No task right now");
            rv.setTextViewText(R.id.w_now_time, "");
            showCountdown(rv, "next starts in", next.get(0).start - now);
        } else {
            rv.setTextViewText(R.id.w_now_label, "FREE");
            rv.setTextViewText(R.id.w_now_title, "Nothing scheduled");
            rv.setTextViewText(R.id.w_now_time, "");
            hideCountdown(rv);
        }

        // ----- next three -----
        rv.setViewVisibility(R.id.w_next_section, next.isEmpty() ? View.GONE : View.VISIBLE);

        for (int i = 0; i < 3; i++) {
            if (i < next.size()) {
                Task t = next.get(i);
                rv.setViewVisibility(ROW[i], View.VISIBLE);
                rv.setTextViewText(ROW_TIME[i], rowTime(c, t.start, now));
                rv.setTextViewText(ROW_NAME[i], t.name);
            } else {
                rv.setViewVisibility(ROW[i], View.GONE);
            }
        }

        return rv;
    }

    private static void showCountdown(RemoteViews rv, String label, long millisLeft) {
        rv.setViewVisibility(R.id.w_countdown_group, View.VISIBLE);
        rv.setTextViewText(R.id.w_countdown_label, label);
        rv.setChronometerCountDown(R.id.w_chrono, true);
        rv.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() + Math.max(0, millisLeft), null, true);
    }

    private static void hideCountdown(RemoteViews rv) {
        rv.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime(), null, false);
        rv.setViewVisibility(R.id.w_countdown_group, View.GONE);
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    /** Every task from today through the same weekday next week, sorted by start time. */
    private static List<Task> loadTasks(Context c, long now) {
        List<Task> out = new ArrayList<>();
        JSONObject timetable = AlarmScheduler.getTimetable(c);

        for (int offset = 0; offset <= 7; offset++) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(now);
            day.set(Calendar.HOUR_OF_DAY, 0);
            day.set(Calendar.MINUTE, 0);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            day.add(Calendar.DAY_OF_YEAR, offset);

            JSONArray tasks = timetable.optJSONArray(DAYS[day.get(Calendar.DAY_OF_WEEK) - 1]);
            if (tasks == null) continue;

            for (int i = 0; i < tasks.length(); i++) {
                JSONObject o = tasks.optJSONObject(i);
                if (o == null) continue;

                long start = timeOnDay(day, o.optString("start"));
                long end = timeOnDay(day, o.optString("end"));
                if (start < 0 || end < 0) continue;

                out.add(new Task(o.optString("name", "Task"), start, end));
            }
        }

        Collections.sort(out, (a, b) -> Long.compare(a.start, b.start));
        return out;
    }

    private static long timeOnDay(Calendar day, String hhmm) {
        try {
            String[] parts = hhmm.split(":");
            Calendar c = (Calendar) day.clone();
            c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            c.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            return c.getTimeInMillis();
        } catch (Exception e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    private static String clock(Context c, long millis) {
        return android.text.format.DateFormat.getTimeFormat(c).format(new Date(millis));
    }

    private static String rowTime(Context c, long start, long now) {
        String time = clock(c, start);

        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(start);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(now);

        boolean sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);

        if (sameDay) return time;
        return new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(start)) + " " + time;
    }

    // ------------------------------------------------------------------
    // Refresh alarm
    // ------------------------------------------------------------------

    private static PendingIntent refreshIntent(Context c) {
        Intent i = new Intent(c, TimetableWidgetProvider.class);
        i.setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(
                c, REFRESH_REQUEST_CODE, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleRefresh(Context c, long boundary) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        long now = System.currentTimeMillis();
        long at = boundary > 0 ? boundary + 1000L : now + MAX_REFRESH_GAP_MS;
        at = Math.min(at, now + MAX_REFRESH_GAP_MS);
        at = Math.max(at, now + 1000L);

        PendingIntent pi = refreshIntent(c);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi);
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC, at, pi);
        }
    }

    private static void cancelRefresh(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(refreshIntent(c));
    }
}
