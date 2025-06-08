package com.example.pomoshake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {

    private TextView timerTextView, statusText;
    private CountDownTimer countDownTimer;
    private boolean timerRunning = false;
    private long timeLeftInMillis = 1 * 60 * 1000; // tempo de duração = 1 minuto
    private final long TIMER_DURATION = 1 * 60 * 1000; // duração original para reset

    private SensorManager sensorManager;
    private float acelVal;  // aceleração atual
    private float acelLast; // última aceleração
    private float shake;    // valor de agitação

    // Controle de orientação
    private int currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    private long lastOrientationChange = 0;
    private final long ORIENTATION_DELAY = 1000; // 1 segundo de delay para evitar mudanças múltiplas sem necessidade

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timerTextView = findViewById(R.id.timerTextView);
        statusText = findViewById(R.id.statusText);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Registra o acelerômetro e a orientação
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_NORMAL);

        acelVal = SensorManager.GRAVITY_EARTH;
        acelLast = SensorManager.GRAVITY_EARTH;
        shake = 0.00f;

        updateTimerText();
    }

    private void startTimer() {
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerText();
            }

            public void onFinish() {
                timerRunning = false;
                showCelebrationDialog();
                resetTimer();
            }
        }.start();

        timerRunning = true;
        statusText.setText("Cronômetro rodando...");
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        timerRunning = false;
        statusText.setText("Pausado");
    }

    private void resetTimer() {
        timeLeftInMillis = TIMER_DURATION;
        updateTimerText();
        statusText.setText("Timer reiniciado! Chacoalhe para começar novamente.");
    }

    private void showCelebrationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎉 PARABÉNS! 🎉");
        builder.setMessage("Você completou uma sessão de Pomodoro!\n\nTempo para uma pausa bem merecida!");
        builder.setPositiveButton("Continuar", (dialog, which) -> {
            dialog.dismiss();
        });
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateTimerText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        String timeFormatted = String.format("%02d:%02d", minutes, seconds);
        timerTextView.setText(timeFormatted);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            handleAccelerometerEvent(event);
        } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            handleOrientationEvent(event);
        }
    }

    private void handleAccelerometerEvent(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        acelLast = acelVal;
        acelVal = (float) Math.sqrt((x * x + y * y + z * z));
        float delta = acelVal - acelLast;
        shake = shake * 0.9f + delta;

        if (shake > 12) { // Sensibilidade do shake
            // chacoalhar sempre vai reiniciar o timer
            if (timerRunning) {
                pauseTimer(); // Para o timer atual
            }
            resetTimer(); // reinicia para o tempo original
            startTimer(); // recomeça desde o inicio
        }
    }

    private void handleOrientationEvent(SensorEvent event) {
        long currentTime = System.currentTimeMillis();

        // evitar múltiplas mudanças muito rápidas
        if (currentTime - lastOrientationChange < ORIENTATION_DELAY) {
            return;
        }

        float azimuth = event.values[0]; // rotação em torno do eixo Z
        float pitch = event.values[1];   // rotação em torno do eixo X
        float roll = event.values[2];    // rotação em torno do eixo Y

        // detectar orientação baseada no pitch
        if (Math.abs(pitch) < 30) { // telefone na horizontal (landscape)
            if (currentOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                currentOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                lastOrientationChange = currentTime;

                // despausa na horizontal
                if (!timerRunning && timeLeftInMillis < TIMER_DURATION && timeLeftInMillis > 0) {
                    startTimer();
                    statusText.setText("Despausado (telefone na horizontal)");
                } else if (timeLeftInMillis == TIMER_DURATION) {
                    statusText.setText("Pronto para começar! Chacoalhe o telefone.");
                }
            }
        } else if (Math.abs(pitch) > 60) { // Telefone na vertical (portrait)
            if (currentOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                lastOrientationChange = currentTime;

                // pausa na vertical
                if (timerRunning) {
                    pauseTimer();
                    statusText.setText("Pausado (telefone na vertical)");
                } else if (!timerRunning && timeLeftInMillis < TIMER_DURATION) {
                    statusText.setText("Timer pausado - vire na horizontal para despausar");
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_NORMAL);
    }
}