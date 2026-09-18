package io.johnkrider12.tasks;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationManagerCompat;

public class AlarmActivity extends Activity {

    private MediaPlayer player;
    private Vibrator vibrator;
    private CountDownTimer holdTimer;
    private Button stopButton;
    private TextView status;
    private int notificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        );

        notificationId = getIntent().getIntExtra("alarm_id", 0);
        String taskName = getIntent().getStringExtra("task_name");

        buildScreen(taskName == null ? "Your next task" : taskName);
        startAlarm();
    }

    private void buildScreen(String taskName) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("STUDY ALARM");
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);

        TextView task = new TextView(this);
        task.setText(taskName);
        task.setTextSize(24);
        task.setGravity(Gravity.CENTER);
        task.setPadding(0, 30, 0, 30);

        status = new TextView(this);
        status.setText("HOLD the button for 5 seconds to stop");
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);

        stopButton = new Button(this);
        stopButton.setText("HOLD TO STOP");
        stopButton.setTextSize(20);
        stopButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    beginHold();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    cancelHold();
                    return true;
                default:
                    return true;
            }
        });

        root.addView(title);
        root.addView(task);
        root.addView(status);
        root.addView(stopButton);
        setContentView(root);
    }

    private void startAlarm() {
        try {
            Uri sound = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, sound);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (android.os.Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            vibrator = vm.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 700, 400};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        }
    }

    private void beginHold() {
        status.setText("KEEP HOLDING... 5 seconds");
        holdTimer = new CountDownTimer(5000, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                status.setText("KEEP HOLDING... " + ((millisUntilFinished + 999) / 1000) + " seconds");
            }

            @Override
            public void onFinish() {
                stopAlarm();
            }
        }.start();
    }

    private void cancelHold() {
        if (holdTimer != null) {
            holdTimer.cancel();
            holdTimer = null;
            status.setText("HOLD the button for 5 seconds to stop");
        }
    }

    private void stopAlarm() {
        if (holdTimer != null) holdTimer.cancel();
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        if (vibrator != null) vibrator.cancel();
        NotificationManagerCompat.from(this).cancel(notificationId);
        finishAndRemoveTask();
    }

    @Override
    protected void onDestroy() {
        if (holdTimer != null) holdTimer.cancel();
        super.onDestroy();
    }
}
