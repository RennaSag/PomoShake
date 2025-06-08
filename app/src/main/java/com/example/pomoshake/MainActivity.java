package com.example.pomoshake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {

    private TextView timerTextView, statusText;
    private CountDownTimer countDownTimer;
    private CountDownTimer breakCountDownTimer; // Timer para a pausa
    private boolean timerRunning = false;
    private boolean breakRunning = false; // Controla se está na pausa
    private long timeLeftInMillis = 1 * 60 * 1000; // tempo de duração = 1 minuto
    private final long TIMER_DURATION = 1 * 60 * 1000; // duração original para reset
    private final long BREAK_DURATION = 10 * 1000; // 10 segundos de pausa

    private SensorManager sensorManager;
    private float acelVal;  // aceleração atual
    private float acelLast; // última aceleração
    private float shake;    // valor de agitação

    // Geradores de som para alarmes
    private ToneGenerator toneGenerator;
    private MediaPlayer completionSound;
    private MediaPlayer breakEndSound;

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

        // Inicializa os geradores de som
        initializeSounds();

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
                playCompletionAlarm(); // Toca alarme de conclusão
                showCelebrationDialog();
                startBreakTimer(); // Inicia a pausa automática
            }
        }.start();

        timerRunning = true;
        statusText.setText("Cronômetro rodando...");
    }

    private void startBreakTimer() {
        breakRunning = true;
        statusText.setText("🎉 Parabéns! Pausa de 10 segundos...");

        breakCountDownTimer = new CountDownTimer(BREAK_DURATION, 1000) {
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000) + 1;
                timerTextView.setText("00:0" + secondsLeft);
                statusText.setText("🎉 Parabéns! Pausa: " + secondsLeft + "s");
            }

            public void onFinish() {
                breakRunning = false;
                playBreakEndAlarm(); // Toca alarme de fim de pausa
                resetTimer();
                startTimer(); // Reinicia automaticamente o timer principal
            }
        }.start();
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        timerRunning = false;
        statusText.setText("Pausado");
    }

    private void pauseBreakTimer() {
        if (breakCountDownTimer != null) {
            breakCountDownTimer.cancel();
        }
        breakRunning = false;
    }

    private void resetTimer() {
        timeLeftInMillis = TIMER_DURATION;
        updateTimerText();
        if (!breakRunning) {
            statusText.setText("Timer reiniciado! Chacoalhe para começar novamente.");
        }
    }

    private void showCelebrationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎉 PARABÉNS! 🎉");
        builder.setMessage("Você completou uma sessão de Pomodoro!\n\nPausa automática de 10 segundos iniciando...");
        builder.setPositiveButton("OK", (dialog, which) -> {
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

    private void initializeSounds() {
        try {
            // Inicializa o gerador de tons (backup)
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 80);

            // Carrega arquivos de áudio personalizados
            try {
                completionSound = MediaPlayer.create(this, R.raw.completion_sound);
                breakEndSound = MediaPlayer.create(this, R.raw.break_end_sound);

                // Configura o volume para stream de alarme
                if (completionSound != null) {
                    completionSound.setAudioStreamType(AudioManager.STREAM_ALARM);
                }
                if (breakEndSound != null) {
                    breakEndSound.setAudioStreamType(AudioManager.STREAM_ALARM);
                }
            } catch (Exception e) {
                // Se não conseguir carregar os arquivos, usa tons do sistema
                System.out.println("Arquivos de áudio não encontrados, usando tons do sistema");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playCompletionAlarm() {
        try {
            // Prioriza arquivo MP3 personalizado
            if (completionSound != null) {
                completionSound.start();
            } else {
                // Fallback para tons do sistema
                if (toneGenerator != null) {
                    // Sequência: 3 bips longos
                    new Thread(() -> {
                        try {
                            for (int i = 0; i < 3; i++) {
                                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500);
                                Thread.sleep(700);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playBreakEndAlarm() {
        try {
            // Prioriza arquivo MP3 personalizado
            if (breakEndSound != null) {
                breakEndSound.start();
            } else {
                // Fallback para tons do sistema
                if (toneGenerator != null) {
                    // Sequência: 2 bips rápidos + 1 longo
                    new Thread(() -> {
                        try {
                            // 2 bips rápidos
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 200);
                            Thread.sleep(300);
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 200);
                            Thread.sleep(300);
                            // 1 bip longo
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 800);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void releaseSounds() {
        try {
            if (toneGenerator != null) {
                toneGenerator.release();
                toneGenerator = null;
            }
            if (completionSound != null) {
                completionSound.release();
                completionSound = null;
            }
            if (breakEndSound != null) {
                breakEndSound.release();
                breakEndSound = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            // Se estiver na pausa, interrompe a pausa e reinicia
            if (breakRunning) {
                pauseBreakTimer();
                resetTimer();
                startTimer();
                return;
            }

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

        // Não processar orientação durante a pausa automática
        if (breakRunning) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseSounds(); // Libera recursos de áudio
    }
}