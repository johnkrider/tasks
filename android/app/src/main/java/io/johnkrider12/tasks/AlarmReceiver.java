package io.johnkrider12.tasks;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "study_alarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        int alarmId = intent.getIntExtra("alarm_id", 0);
        String taskName = intent.getStringExtra("task_name");
        long triggerAt = intent.getLongExtra("trigger_at", System.currentTimeMillis());

        createChannel(context);

        Intent alarmIntent = new Intent(context, AlarmActivity.class);
        alarmIntent.putExtra("task_name", taskName);
        alarmIntent.putExtra("alarm_id", alarmId);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                context,
                alarmId,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri alarmSound = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Study Timetable Alarm")
                .setContentText(taskName == null ? "Your next task is starting soon" : taskName)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(alarmSound)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenIntent, true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(alarmId, builder.build());

        scheduleNextWeek(context, alarmId, taskName, triggerAt);
    }

    private void scheduleNextWeek(Context context, int id, String taskName, long triggerAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long next = triggerAt + 7L * 24L * 60L * 60L * 1000L;

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("alarm_id", id);
        intent.putExtra("task_name", taskName);
        intent.putExtra("trigger_at", next);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, next, pendingIntent);
        }
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Study alarms",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Alarms for upcoming study timetable tasks");
        channel.setSound(
                android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
        );
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }
}
