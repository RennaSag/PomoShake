package com.example.pomoshake;

import android.app.*;
import android.content.pm.ActivityInfo;
import android.hardware.*;
import android.media.*;
import android.os.*;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = "PomoShake";

    private TextView timerTextView, statusText;
    private CountDownTimer countDownTimer, breakTimer;
    private boolean timerRunning = false, breakRunning = false, waitingForShake = false;

    private long timeLeft = 60_000, TIMER_DURATION = 60_000, BREAK_DURATION = 10_000;

    private SensorManager sensorManager;
    private float acelVal, acelLast, shake;

    private MediaPlayer completionSound, breakEndSound;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timerTextView = findViewById(R.id.timerTextView);
        statusText = findViewById(R.id.statusText);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        acelVal = acelLast = SensorManager.GRAVITY_EARTH;
        shake = 0.0f;

        registerSensors();
        initializeSounds();
        updateTimerText();

        statusText.setText("Chacoalhe para começar!");
        setupSoundTestListener();
    }

    private void registerSensors() {
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void setupSoundTestListener() {
        final long[] clicks = new long[5];
        timerTextView.setOnClickListener(v -> {
            System.arraycopy(clicks, 1, clicks, 0, 4);
            clicks[4] = System.currentTimeMillis();
            if (clicks[0] > 0 && (clicks[4] - clicks[0]) < 2000) {
                testSounds();
                for (int i = 0; i < 5; i++) clicks[i] = 0;
            }
        });
    }

    private void testSounds() {
        new Thread(() -> {
            try {
                runOnUiThread(() -> statusText.setText("Testando conclusão..."));
                playCompletionAlarm();
                Thread.sleep(3000);
                runOnUiThread(() -> statusText.setText("Testando fim da pausa..."));
                playBreakEndAlarm();
                Thread.sleep(3000);
                runOnUiThread(() -> statusText.setText("Teste concluído!"));
            } catch (Exception ignored) {}
        }).start();
    }

    private void startTimer() {
        waitingForShake = false;
        countDownTimer = new CountDownTimer(timeLeft, 1000) {
            public void onTick(long millis) {
                timeLeft = millis;
                updateTimerText();
            }
            public void onFinish() {
                timerRunning = false;
                playCompletionAlarm();
                showDialog("Parabéns!", "Você completou uma sessão.");
                startBreakTimer();
            }
        }.start();
        timerRunning = true;
        statusText.setText("Cronômetro rodando...");
    }

    private void startBreakTimer() {
        breakRunning = true;
        breakTimer = new CountDownTimer(BREAK_DURATION, 1000) {
            public void onTick(long millis) {
                timerTextView.setText("00:0" + ((millis / 1000) + 1));
            }
            public void onFinish() {
                breakRunning = false;
                playBreakEndAlarm();
                waitingForShake = true;
                resetTimer();
                statusText.setText("Pausa encerrada. Chacoalhe para reiniciar.");
            }
        }.start();
        statusText.setText("Pausa de 10s...");
    }

    private void pauseTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        statusText.setText("Pausado");
    }

    private void pauseBreak() {
        if (breakTimer != null) breakTimer.cancel();
        breakRunning = false;
    }

    private void resetTimer() {
        timeLeft = TIMER_DURATION;
        updateTimerText();
    }

    private void updateTimerText() {
        int m = (int) (timeLeft / 1000) / 60;
        int s = (int) (timeLeft / 1000) % 60;
        timerTextView.setText(String.format("%02d:%02d", m, s));
    }

    private void initializeSounds() {
        releaseSounds();
        completionSound = MediaPlayer.create(this, R.raw.completion_sound);
        breakEndSound = MediaPlayer.create(this, R.raw.break_end_sound);
    }

    private void releaseSounds() {
        if (completionSound != null) {
            completionSound.release();
            completionSound = null;
        }
        if (breakEndSound != null) {
            breakEndSound.release();
            breakEndSound = null;
        }
    }

    private void playCompletionAlarm() {
        if (completionSound != null) {
            try {
                if (completionSound.isPlaying()) {
                    completionSound.stop();
                    completionSound.prepare();
                }
                completionSound.start();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao tocar som de conclusão", e);
            }
        }

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    private void playBreakEndAlarm() {
        if (breakEndSound != null) {
            try {
                if (breakEndSound.isPlaying()) {
                    breakEndSound.stop();
                    breakEndSound.prepare();
                }
                breakEndSound.start();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao tocar som de fim de pausa", e);
            }
        }

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    private void showDialog(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0], y = event.values[1], z = event.values[2];
            acelLast = acelVal;
            acelVal = (float) Math.sqrt(x * x + y * y + z * z);
            float delta = acelVal - acelLast;
            shake = shake * 0.9f + delta;

            if (shake > 12) {
                if (waitingForShake) {
                    startTimer();
                    return;
                }
                if (breakRunning) {
                    pauseBreak();
                    resetTimer();
                    startTimer();
                    return;
                }
                if (timerRunning) pauseTimer();
                resetTimer();
                startTimer();
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    @Override protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override protected void onResume() {
        super.onResume();
        registerSensors();
        initializeSounds();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        releaseSounds();
    }
}
