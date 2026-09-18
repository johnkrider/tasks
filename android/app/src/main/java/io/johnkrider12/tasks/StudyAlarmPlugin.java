package io.johnkrider12.tasks;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

@CapacitorPlugin(name = "StudyAlarm")
public class StudyAlarmPlugin extends Plugin {

    private static final String PREFS = "study_alarm_prefs";
    private static final String IDS = "scheduled_ids";

    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray alarms = call.getArray("alarms");
        if (alarms == null) {
            call.reject("alarms is required");
            return;
        }

        AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        cancelStoredAlarms(alarmManager);

        Set<String> ids = new HashSet<>();

        try {
            for (int i = 0; i < alarms.length(); i++) {
                JSONObject alarm = alarms.getJSONObject(i);
                int id = alarm.getInt("id");
                long triggerAt = alarm.getLong("triggerAt");
                String taskName = alarm.optString("task", "Study task");

                Intent intent = new Intent(getContext(), AlarmReceiver.class);
                intent.putExtra("alarm_id", id);
                intent.putExtra("task_name", taskName);
                intent.putExtra("trigger_at", triggerAt);

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        getContext(),
                        id,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    call.reject("Exact alarms are not allowed for this app. Enable Alarms & reminders in Android settings.");
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            pendingIntent
                    );
                } else {
                    alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            pendingIntent
                    );
                }

                ids.add(String.valueOf(id));
            }
        } catch (JSONException e) {
            call.reject("Invalid alarm data", e);
            return;
        }

        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(IDS, ids)
                .apply();

        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        cancelStoredAlarms(alarmManager);
        call.resolve();
    }

    private void cancelStoredAlarms(AlarmManager alarmManager) {
        Set<String> ids = getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(IDS, new HashSet<>());

        for (String value : ids) {
            try {
                int id = Integer.parseInt(value);
                Intent intent = new Intent(getContext(), AlarmReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        getContext(),
                        id,
                        intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                );
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent);
                    pendingIntent.cancel();
                }
            } catch (NumberFormatException ignored) {
            }
        }

        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(IDS)
                .apply();
    }
}
