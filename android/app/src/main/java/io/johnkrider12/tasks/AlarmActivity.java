package io.johnkrider12.tasks;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.core.content.ContextCompat;

import java.util.Random;

/**
 * The alarm screen. It does not make any sound itself (AlarmService does), so
 * closing or leaving this screen never silences the alarm.
 *
 * Stop challenge (configured in the app's Settings page):
 *   1. optionally solve N maths problems,
 *   2. then hold the big button for N seconds without lifting your finger.
 */
public class AlarmActivity extends Activity {

    private static final int BG = Color.parseColor("#101828");
    private static final int ORANGE = Color.parseColor("#F5A623");
    private static final int MUTED = Color.parseColor("#98A2B3");
    private static final int FIELD = Color.parseColor("#1D2939");

    private final Random random = new Random();

    private TextView taskView;
    private TextView startView;

    private LinearLayout mathBox;
    private TextView mathTitle;
    private TextView mathQuestion;
    private TextView mathFeedback;
    private EditText mathInput;

    private LinearLayout holdBox;
    private TextView holdStatus;
    private ProgressBar holdProgress;
    private Button stopButton;

    private CountDownTimer holdTimer;
    private int holdSeconds;
    private int mathRemaining;
    private int mathAnswer;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver stoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finishAndRemoveTask();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        // Opened from a stale notification after the alarm already ended.
        if (!AlarmService.ringing) {
            finish();
            return;
        }

        AlarmScheduler.Config config = AlarmScheduler.getConfig(this);
        holdSeconds = config.holdSeconds;
        mathRemaining = config.mathCount;

        buildScreen();
        updateTexts();

        if (mathRemaining > 0) {
            holdBox.setVisibility(View.GONE);
            nextQuestion();
        } else {
            mathBox.setVisibility(View.GONE);
        }

        ContextCompat.registerReceiver(
                this, stoppedReceiver,
                new IntentFilter(AlarmService.ACTION_STOPPED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;

        // Android 13+ (and 16 by default) route Back through this callback.
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    });
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (taskView != null) updateTexts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AlarmService.ringing) finishAndRemoveTask();
    }

    /** Back does nothing while the alarm is ringing. */
    @Override
    public void onBackPressed() {
    }

    @Override
    protected void onDestroy() {
        if (holdTimer != null) holdTimer.cancel();
        if (receiverRegistered) {
            unregisterReceiver(stoppedReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(48), dp(28), dp(48));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView badge = text("Study alarm", 16, ORANGE, true);
        root.addView(badge);

        taskView = text("", 32, Color.WHITE, true);
        taskView.setPadding(0, dp(20), 0, dp(6));
        root.addView(taskView);

        startView = text("", 18, MUTED, false);
        startView.setPadding(0, 0, 0, dp(36));
        root.addView(startView);

        // ----- maths challenge -----
        mathBox = new LinearLayout(this);
        mathBox.setOrientation(LinearLayout.VERTICAL);
        mathBox.setGravity(Gravity.CENTER);
        root.addView(mathBox, matchWidth());

        mathTitle = text("", 16, MUTED, false);
        mathBox.addView(mathTitle);

        mathQuestion = text("", 38, Color.WHITE, true);
        mathQuestion.setPadding(0, dp(14), 0, dp(14));
        mathBox.addView(mathQuestion);

        mathInput = new EditText(this);
        mathInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        mathInput.setTextSize(28);
        mathInput.setTextColor(Color.WHITE);
        mathInput.setGravity(Gravity.CENTER);
        mathInput.setBackground(rounded(FIELD, 14));
        mathInput.setPadding(dp(16), dp(14), dp(16), dp(14));
        mathBox.addView(mathInput, matchWidth());

        mathFeedback = text("", 15, Color.parseColor("#F97066"), false);
        mathFeedback.setPadding(0, dp(10), 0, dp(10));
        mathBox.addView(mathFeedback);

        Button check = button("Check answer", Color.WHITE, FIELD);
        check.setOnClickListener(v -> checkAnswer());
        mathBox.addView(check, matchWidth());

        // ----- hold to stop -----
        holdBox = new LinearLayout(this);
        holdBox.setOrientation(LinearLayout.VERTICAL);
        holdBox.setGravity(Gravity.CENTER);
        root.addView(holdBox, matchWidth());

        holdStatus = text(holdHint(), 18, MUTED, false);
        holdStatus.setPadding(0, 0, 0, dp(14));
        holdBox.addView(holdStatus);

        holdProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        holdProgress.setMax(100);
        holdProgress.setProgress(0);
        holdBox.addView(holdProgress, matchWidth());

        stopButton = button("Hold to stop", Color.parseColor("#101828"), ORANGE);
        stopButton.setTextSize(22);
        stopButton.setMinimumHeight(dp(120));
        stopButton.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    beginHold();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getX() < 0 || event.getY() < 0
                            || event.getX() > v.getWidth() || event.getY() > v.getHeight()) {
                        cancelHold();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelHold();
                    return true;
                default:
                    return true;
            }
        });
        LinearLayout.LayoutParams stopParams = matchWidth();
        stopParams.topMargin = dp(18);
        holdBox.addView(stopButton, stopParams);

        setContentView(scroll);
    }

    private void updateTexts() {
        taskView.setText(AlarmService.currentTask);
        startView.setText(AlarmService.currentStart.isEmpty()
                ? "" : "Starts at " + AlarmService.currentStart);
    }

    private String holdHint() {
        return "Hold the button for " + holdSeconds + " seconds to stop";
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String label, int textColor, int bgColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setBackground(rounded(bgColor, 18));
        b.setPadding(dp(16), dp(16), dp(16), dp(16));
        return b;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------
    // Maths challenge
    // ------------------------------------------------------------------

    private void nextQuestion() {
        if (random.nextBoolean()) {
            int a = 12 + random.nextInt(28);
            int b = 3 + random.nextInt(7);
            mathAnswer = a * b;
            mathQuestion.setText(a + " x " + b + " = ?");
        } else {
            int a = 100 + random.nextInt(900);
            int b = 100 + random.nextInt(900);
            mathAnswer = a + b;
            mathQuestion.setText(a + " + " + b + " = ?");
        }

        mathTitle.setText(mathRemaining == 1
                ? "Solve 1 more problem to unlock the stop button"
                : "Solve " + mathRemaining + " more problems to unlock the stop button");
        mathInput.setText("");
    }

    private void checkAnswer() {
        String typed = mathInput.getText().toString().trim();

        try {
            if (Integer.parseInt(typed) == mathAnswer) {
                mathRemaining--;
                mathFeedback.setText("");

                if (mathRemaining <= 0) {
                    hideKeyboard();
                    mathBox.setVisibility(View.GONE);
                    holdBox.setVisibility(View.VISIBLE);
                } else {
                    nextQuestion();
                }
                return;
            }
        } catch (NumberFormatException ignored) {
        }

        mathFeedback.setText("Wrong answer - here is a new problem");
        nextQuestion();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mathInput.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------
    // Hold to stop
    // ------------------------------------------------------------------

    private void beginHold() {
        cancelHold();

        final long total = holdSeconds * 1000L;

        holdTimer = new CountDownTimer(total, 50) {
            @Override
            public void onTick(long millisUntilFinished) {
                holdProgress.setProgress((int) (100 - (millisUntilFinished * 100 / total)));
                holdStatus.setText("Keep holding... " + ((millisUntilFinished + 999) / 1000) + "s");
            }

            @Override
            public void onFinish() {
                holdProgress.setProgress(100);
                stopAlarm();
            }
        }.start();
    }

    private void cancelHold() {
        if (holdTimer != null) {
            holdTimer.cancel();
            holdTimer = null;
        }
        if (holdProgress != null) holdProgress.setProgress(0);
        if (holdStatus != null) holdStatus.setText(holdHint());
    }

    private void stopAlarm() {
        Intent stop = new Intent(this, AlarmService.class);
        stop.setAction(AlarmService.ACTION_STOP);
        startService(stop);
        finishAndRemoveTask();
    }
}
