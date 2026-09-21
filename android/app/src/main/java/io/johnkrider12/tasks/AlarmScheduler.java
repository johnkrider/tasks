package io.johnkrider12.tasks;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * Everything that decides WHEN alarms ring.
 *
 * The web app pushes three things down here (see StudyAlarmPlugin.schedule):
 *   - config    : the user's alarm settings
 *   - alarms    : [{id, task, day, start}]  one entry per task that wants an alarm
 *   - timetable : the whole timetable (used by the home-screen widget)
 *
 * We store them in SharedPreferences so alarms can be re-created after a reboot
 * without the web app running. Next-trigger times are always recalculated from
 * (weekday + start time - lead minutes) using Calendar, so daylight-saving
 * changes (Egypt uses DST) never shift an alarm by an hour.
 */
public final class AlarmScheduler {

    static final String PREFS = "study_alarm_prefs";

    // Same key the first version used, so old alarms still get cancelled.
    private static final String KEY_IDS = "scheduled_ids";
    private static final String KEY_ALARMS = "alarms_json";
    private static final String KEY_CONFIG = "config_json";
    private static final String KEY_TIMETABLE = "timetable_json";

    static final int TEST_ALARM_ID = 999;

    private AlarmScheduler() {
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    public static final class Config {
        public boolean enabled = true;
        public int leadMinutes = 10;
        public int holdSeconds = 5;
        public int mathCount = 0;
        public boolean maxVolume = true;
        public boolean vibrate = true;

        static Config from(JSONObject o) {
            Config c = new Config();
            if (o == null) return c;
            c.enabled = o.optBoolean("enabled", true);
            c.leadMinutes = clamp(o.optInt("leadMinutes", 10), 0, 240);
            c.holdSeconds = clamp(o.optInt("holdSeconds", 5), 1, 60);
            c.mathCount = clamp(o.optInt("mathCount", 0), 0, 10);
            c.maxVolume = o.optBoolean("maxVolume", true);
            c.vibrate = o.optBoolean("vibrate", true);
            return c;
        }

        private static int clamp(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void save(Context c, JSONObject config, JSONArray alarms, JSONObject timetable) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (config != null) e.putString(KEY_CONFIG, config.toString());
        if (alarms != null) e.putString(KEY_ALARMS, alarms.toString());
        if (timetable != null) e.putString(KEY_TIMETABLE, timetable.toString());
        e.apply();
    }

    static Config getConfig(Context c) {
        try {
            String s = prefs(c).getString(KEY_CONFIG, null);
            return Config.from(s == null ? null : new JSONObject(s));
        } catch (JSONException e) {
            return new Config();
        }
    }

    static JSONArray getAlarms(Context c) {
        try {
            return new JSONArray(prefs(c).getString(KEY_ALARMS, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static JSONObject getTimetable(Context c) {
        try {
            return new JSONObject(prefs(c).getString(KEY_TIMETABLE, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    // ------------------------------------------------------------------
    // Scheduling
    // ------------------------------------------------------------------

    /**
     * Cancels everything we scheduled before and schedules it again from the
     * stored alarms + config. Returns the time of the earliest alarm, or -1.
     */
    static long rescheduleAll(Context c) {
        cancelAll(c);

        Config cfg = getConfig(c);
        if (!cfg.enabled) return -1;

        JSONArray alarms = getAlarms(c);
        Set<String> ids = new HashSet<>();
        long earliest = -1;
        long now = System.currentTimeMillis();

        for (int i = 0; i < alarms.length(); i++) {
            JSONObject a = alarms.optJSONObject(i);
            if (a == null) continue;

            long trigger = scheduleOne(c, a, cfg, now);
            if (trigger > 0) {
                ids.add(String.valueOf(a.optInt("id")));
                if (earliest < 0 || trigger < earliest) earliest = trigger;
            }
        }

        prefs(c).edit().putStringSet(KEY_IDS, ids).apply();
        return earliest;
    }

    /** Called by AlarmReceiver after an alarm rang: queue the same alarm for next week. */
    static void rescheduleAfterFire(Context c, int id, long firedAt) {
        Config cfg = getConfig(c);
        if (!cfg.enabled) return;

        long after = Math.max(System.currentTimeMillis(), firedAt) + 1000L;
        JSONArray alarms = getAlarms(c);

        for (int i = 0; i < alarms.length(); i++) {
            JSONObject a = alarms.optJSONObject(i);
            if (a != null && a.optInt("id", -1) == id) {
                scheduleOne(c, a, cfg, after);
                return;
            }
        }
    }

    static void cancelAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Set<String> ids = prefs(c).getStringSet(KEY_IDS, new HashSet<String>());

        for (String value : ids) {
            try {
                cancelOne(c, am, Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
            }
        }

        prefs(c).edit().remove(KEY_IDS).apply();
    }

    /** Rings once, a few seconds from now, so you can test with the app closed. */
    static void scheduleTest(Context c, int seconds) {
        long at = System.currentTimeMillis() + Math.max(3, seconds) * 1000L;

        Intent intent = new Intent(c, AlarmReceiver.class);
        intent.putExtra("alarm_id", TEST_ALARM_ID);
        intent.putExtra("task_name", "Test alarm");
        intent.putExtra("start_label", "now");
        intent.putExtra("trigger_at", at);

        PendingIntent pi = PendingIntent.getBroadcast(
                c, TEST_ALARM_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        setAlarmClock(c, at, pi);
    }

    private static long scheduleOne(Context c, JSONObject a, Config cfg, long after) {
        try {
            int id = a.getInt("id");
            String start = a.getString("start");
            long trigger = nextTrigger(a.getString("day"), start, cfg.leadMinutes, after);

            Intent intent = new Intent(c, AlarmReceiver.class);
            intent.putExtra("alarm_id", id);
            intent.putExtra("task_name", a.optString("task", "Study task"));
            intent.putExtra("start_label", start);
            intent.putExtra("trigger_at", trigger);

            PendingIntent pi = PendingIntent.getBroadcast(
                    c, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            setAlarmClock(c, trigger, pi);
            return trigger;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * setAlarmClock is the most reliable API for a real alarm: it is exempt from
     * Doze, needs no "exact alarm" permission, and lets the alarm start a
     * foreground service from the background.
     */
    private static void setAlarmClock(Context c, long at, PendingIntent operation) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);

        PendingIntent show = PendingIntent.getActivity(
                c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), operation);
    }

    private static void cancelOne(Context c, AlarmManager am, int id) {
        PendingIntent pi = PendingIntent.getBroadcast(
                c, id, new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    /** First moment strictly after `after` that is (day, start) minus leadMinutes. */
    static long nextTrigger(String day, String start, int leadMinutes, long after) {
        String[] parts = start.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(after);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        int diff = (dayToCalendar(day) - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        cal.add(Calendar.DAY_OF_YEAR, diff);

        while (true) {
            Calendar alarm = (Calendar) cal.clone();
            alarm.add(Calendar.MINUTE, -leadMinutes);
            if (alarm.getTimeInMillis() > after) return alarm.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, 7);
        }
    }

    private static int dayToCalendar(String day) {
        switch (day) {
            case "Sunday":
                return Calendar.SUNDAY;
            case "Monday":
                return Calendar.MONDAY;
            case "Tuesday":
                return Calendar.TUESDAY;
            case "Wednesday":
                return Calendar.WEDNESDAY;
            case "Thursday":
                return Calendar.THURSDAY;
            case "Friday":
                return Calendar.FRIDAY;
            default:
                return Calendar.SATURDAY;
        }
    }
}
