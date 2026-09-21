package io.johnkrider12.tasks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Android forgets every alarm on reboot. This puts them back (and refreshes the
 * widget) after boot, app updates, and clock / time-zone changes.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmScheduler.rescheduleAll(context);
        TimetableWidgetProvider.refreshAll(context);
    }
}
