package io.johnkrider12.tasks;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "StudyAlarm")
public class StudyAlarmPlugin extends Plugin {

    /**
     * schedule({ alarms: [{id, task, day, start}], config: {...}, timetable: {...} })
     * Stores everything natively, (re)creates the alarms and refreshes the widget.
     */
    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray alarms = call.getArray("alarms");
        if (alarms == null) {
            call.reject("alarms is required");
            return;
        }

        Context context = getContext();

        AlarmScheduler.save(context, call.getObject("config"), alarms, call.getObject("timetable"));
        long next = AlarmScheduler.rescheduleAll(context);
        TimetableWidgetProvider.refreshAll(context);

        JSObject result = new JSObject();
        result.put("scheduled", alarms.length());
        result.put("nextAlarmAt", next);
        call.resolve(result);
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        AlarmScheduler.cancelAll(getContext());
        call.resolve();
    }

    /** Rings once in a few seconds so the whole chain can be tested with the app closed. */
    @PluginMethod
    public void testAlarm(PluginCall call) {
        Integer seconds = call.getInt("seconds", 10);
        AlarmScheduler.scheduleTest(getContext(), seconds == null ? 10 : seconds);
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        Context context = getContext();

        boolean notifications = NotificationManagerCompat.from(context).areNotificationsEnabled();

        boolean fullScreen = true;
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            fullScreen = nm != null && nm.canUseFullScreenIntent();
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());

        JSObject result = new JSObject();
        result.put("notifications", notifications);
        result.put("fullScreen", fullScreen);
        result.put("batteryUnrestricted", battery);
        call.resolve(result);
    }

    /** openSettings({ type: "notifications" | "fullScreen" | "battery" }) */
    @PluginMethod
    public void openSettings(PluginCall call) {
        Context context = getContext();
        String type = call.getString("type", "notifications");
        String pkg = context.getPackageName();

        Intent intent;
        if ("fullScreen".equals(type) && Build.VERSION.SDK_INT >= 34) {
            intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + pkg));
        } else if ("battery".equals(type)) {
            intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        } else if ("notifications".equals(type)) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (RuntimeException e) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg));
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fallback);
        }

        call.resolve();
    }
}
