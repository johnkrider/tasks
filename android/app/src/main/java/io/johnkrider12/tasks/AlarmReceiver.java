package io.johnkrider12.tasks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/**
 * Fired by AlarmManager. Its only jobs:
 *   1. queue the same alarm for next week,
 *   2. start AlarmService, which owns the sound / vibration / full-screen screen.
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int alarmId = intent.getIntExtra("alarm_id", 0);
        String taskName = intent.getStringExtra("task_name");
        String startLabel = intent.getStringExtra("start_label");
        long triggerAt = intent.getLongExtra("trigger_at", System.currentTimeMillis());

        boolean isTest = alarmId == AlarmScheduler.TEST_ALARM_ID;

        // Do this first so next week's alarm exists even if ringing fails.
        if (!isTest) {
            AlarmScheduler.rescheduleAfterFire(context, alarmId, triggerAt);
        }

        if (!isTest && !AlarmScheduler.getConfig(context).enabled) {
            return;
        }

        Intent service = new Intent(context, AlarmService.class);
        service.setAction(AlarmService.ACTION_START);
        service.putExtra("task_name", taskName);
        service.putExtra("start_label", startLabel);

        try {
            ContextCompat.startForegroundService(context, service);
        } catch (RuntimeException e) {
            // Android refused to start the service: at least show a loud notification.
            AlarmService.postFallbackNotification(context, taskName, startLabel);
        }
    }
}
