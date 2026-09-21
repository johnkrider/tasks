package io.johnkrider12.tasks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Owns the actual ringing, so the alarm keeps going even if the alarm screen is
 * closed, swiped away or covered by another app. Only AlarmActivity's stop
 * challenge (or the 15 minute safety timeout) ends it.
 */
public class AlarmService extends Service {

    static final String ACTION_START = "io.johnkrider12.tasks.START_ALARM";
    static final String ACTION_STOP = "io.johnkrider12.tasks.STOP_ALARM";
    static final String ACTION_STOPPED = "io.johnkrider12.tasks.ALARM_STOPPED";

    static final String CHANNEL_RINGING = "study_alarm_v2";
    static final String CHANNEL_FALLBACK = "study_alarm_fallback";
    private static final String CHANNEL_LEGACY = "study_alarm";

    private static final String TAG = "StudyAlarm";
    private static final int NOTIFICATION_ID = 4242;

    private static final int MAX_RING_SECONDS = 15 * 60;   // safety timeout
    private static final int REPOST_EVERY_SECONDS = 15;    // pop the notification up again
    private static final int RAMP_SECONDS = 20;            // volume climbs from 30% to 100%

    /** Read by AlarmActivity. Same process, so plain statics are fine. */
    static volatile boolean ringing = false;
    static volatile String currentTask = "Your next task";
    static volatile String currentStart = "";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private AlarmScheduler.Config config = new AlarmScheduler.Config();
    private MediaPlayer player;
    private Vibrator vibrator;
    private AudioManager audio;
    private PowerManager.WakeLock wakeLock;
    private int savedAlarmVolume = -1;
    private int elapsedSeconds = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRinging();
            return START_NOT_STICKY;
        }

        String task = intent == null ? null : intent.getStringExtra("task_name");
        String start = intent == null ? null : intent.getStringExtra("start_label");

        currentTask = (task == null || task.isEmpty()) ? "Your next task" : task;
        currentStart = start == null ? "" : start;
        ringing = true;
        elapsedSeconds = 0;
        config = AlarmScheduler.getConfig(this);

        ensureChannels(this);
        startInForeground(buildNotification());
        startSoundAndVibration();

        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000);

        // Works when Android allows it; otherwise the full-screen notification does the job.
        try {
            startActivity(new Intent(this, AlarmActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (RuntimeException ignored) {
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        cleanup();
        ringing = false;
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Ringing
    // ------------------------------------------------------------------

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!ringing) return;

            elapsedSeconds++;

            if (config.maxVolume) {
                forceMaxVolume();
                rampVolume();
            }

            if (elapsedSeconds % REPOST_EVERY_SECONDS == 0) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
            }

            if (elapsedSeconds >= MAX_RING_SECONDS) {
                stopRinging();
                return;
            }

            handler.postDelayed(this, 1000);
        }
    };

    private void startSoundAndVibration() {
        stopPlayback();

        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tasks:alarm");
            }
            if (!wakeLock.isHeld()) {
                wakeLock.acquire((MAX_RING_SECONDS + 30) * 1000L);
            }
        }

        if (audio != null && savedAlarmVolume < 0) {
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM);
        }
        if (config.maxVolume) forceMaxVolume();

        player = createPlayer();
        if (player != null) {
            float volume = config.maxVolume ? 0.3f : 1f;
            player.setVolume(volume, volume);
            player.start();
        }

        startVibration();
    }

    private MediaPlayer createPlayer() {
        Uri[] candidates = {
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        };

        for (Uri uri : candidates) {
            if (uri == null) continue;

            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setAudioAttributes(alarmAttributes());
                mp.setDataSource(this, uri);
                mp.setLooping(true);
                mp.prepare();
                return mp;
            } catch (Exception e) {
                Log.w(TAG, "Could not play " + uri, e);
                mp.release();
            }
        }
        return null;
    }

    private void startVibration() {
        if (!config.vibrate) return;

        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = vm == null ? null : vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }

        if (vibrator == null || !vibrator.hasVibrator()) return;

        long[] pattern = {0, 800, 300, 800, 300, 1500, 600};

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAttributes());
            } else {
                vibrator.vibrate(pattern, 0, alarmAttributes());
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Vibration failed", e);
        }
    }

    private void forceMaxVolume() {
        if (audio == null) return;
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            if (audio.getStreamVolume(AudioManager.STREAM_ALARM) != max) {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void rampVolume() {
        if (player == null) return;
        float volume = Math.min(1f, 0.3f + 0.7f * elapsedSeconds / RAMP_SECONDS);
        try {
            player.setVolume(volume, volume);
        } catch (IllegalStateException ignored) {
        }
    }

    private static AudioAttributes alarmAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    // ------------------------------------------------------------------
    // Stopping
    // ------------------------------------------------------------------

    private void stopPlayback() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    private void cleanup() {
        handler.removeCallbacks(tick);
        stopPlayback();

        if (audio != null && savedAlarmVolume >= 0) {
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0);
            } catch (RuntimeException ignored) {
            }
        }
        savedAlarmVolume = -1;

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void stopRinging() {
        cleanup();
        ringing = false;

        stopForeground(Service.STOP_FOREGROUND_REMOVE);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);

        sendBroadcast(new Intent(ACTION_STOPPED).setPackage(getPackageName()));
        stopSelf();
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    private void startInForeground(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "startForeground failed", e);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent openPending = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = currentStart.isEmpty()
                ? "Tap to open the alarm and stop it"
                : "Starts at " + currentStart + " - tap to stop the alarm";

        return new NotificationCompat.Builder(this, CHANNEL_RINGING)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(currentTask)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(openPending)
                .setFullScreenIntent(openPending, true)
                .build();
    }

    static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        // The first version made this channel WITH a sound; channels can't be edited afterwards.
        if (nm.getNotificationChannel(CHANNEL_LEGACY) != null) {
            nm.deleteNotificationChannel(CHANNEL_LEGACY);
        }

        // The service plays the sound itself, so this channel is silent.
        NotificationChannel ringingChannel = new NotificationChannel(
                CHANNEL_RINGING, "Study alarm (ringing)", NotificationManager.IMPORTANCE_HIGH);
        ringingChannel.setDescription("Shown while a study alarm is ringing");
        ringingChannel.setSound(null, null);
        ringingChannel.enableVibration(false);
        ringingChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ringingChannel);

        // Only used if Android refuses to start the ringing service.
        NotificationChannel fallback = new NotificationChannel(
                CHANNEL_FALLBACK, "Study alarm (backup)", NotificationManager.IMPORTANCE_HIGH);
        fallback.setDescription("Backup alert if the alarm screen cannot start");
        fallback.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, alarmAttributes());
        fallback.enableVibration(true);
        fallback.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(fallback);
    }

    static void postFallbackNotification(Context context, String task, String start) {
        ensureChannels(context);

        PendingIntent open = PendingIntent.getActivity(
                context, 2, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(context, CHANNEL_FALLBACK)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(task == null ? "Study alarm" : task)
                .setContentText(start == null || start.isEmpty() ? "Your next task is starting soon"
                        : "Starts at " + start)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID + 1, n);
    }
}
