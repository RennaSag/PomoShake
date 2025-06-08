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
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = "PomoShake";

    private TextView timerTextView, statusText;
    private CountDownTimer countDownTimer;
    private CountDownTimer breakCountDownTimer;
    private boolean timerRunning = false;
    private boolean breakRunning = false;
    private boolean waitingForShakeAfterBreak = false;
    private long timeLeftInMillis = 1 * 60 * 1000;
    private final long TIMER_DURATION = 1 * 60 * 1000;
    private final long BREAK_DURATION = 10 * 1000;

    private SensorManager sensorManager;
    private float acelVal;
    private float acelLast;
    private float shake;

    // Geradores de som para alarmes
    private ToneGenerator toneGenerator;
    private MediaPlayer completionSound;
    private MediaPlayer breakEndSound;
    private AudioManager audioManager;
    private Vibrator vibrator;

    // Controle de orientação
    private int currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    private long lastOrientationChange = 0;
    private final long ORIENTATION_DELAY = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timerTextView = findViewById(R.id.timerTextView);
        statusText = findViewById(R.id.statusText);

        // Inicializa AudioManager e Vibrator
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Registra sensores
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
        statusText.setText("Chacoalhe para começar!");

        // Listener para testar sons - toque 5 vezes rápido no timer para testar
        setupSoundTestListener();
    }

    private void setupSoundTestListener() {
        final long[] clickTimes = new long[5];

        timerTextView.setOnClickListener(v -> {
            // Shift dos clicks para a esquerda
            System.arraycopy(clickTimes, 1, clickTimes, 0, clickTimes.length - 1);
            clickTimes[clickTimes.length - 1] = System.currentTimeMillis();

            // Se 5 cliques em menos de 2 segundos
            if (clickTimes[0] > 0 && (clickTimes[4] - clickTimes[0]) < 2000) {
                Log.d(TAG, "🧪 TESTE DE SONS ATIVADO!");
                testSounds();
                // Reset
                for (int i = 0; i < clickTimes.length; i++) {
                    clickTimes[i] = 0;
                }
            }
        });
    }

    private void testSounds() {
        Log.d(TAG, "=== INICIANDO TESTE DE SONS ===");
        statusText.setText("🧪 Testando sons...");

        new Thread(() -> {
            try {
                // Teste 1: Completion sound
                Log.d(TAG, "🧪 Testando completion_sound...");
                runOnUiThread(() -> statusText.setText("🧪 Testando som de conclusão..."));
                playCompletionAlarm();
                Thread.sleep(3000);

                // Teste 2: Break end sound
                Log.d(TAG, "🧪 Testando break_end_sound...");
                runOnUiThread(() -> statusText.setText("🧪 Testando som de fim de pausa..."));
                playBreakEndAlarm();
                Thread.sleep(3000);

                // Volta ao normal
                runOnUiThread(() -> statusText.setText("🧪 Teste concluído! Chacoalhe para começar."));

            } catch (Exception e) {
                Log.e(TAG, "Erro no teste de sons", e);
            }
        }).start();
    }

    private void startTimer() {
        waitingForShakeAfterBreak = false;
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerText();
            }

            public void onFinish() {
                timerRunning = false;
                playCompletionAlarm();
                showCelebrationDialog();
                startBreakTimer();
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
                playBreakEndAlarm();

                // Nova lógica: espera chacoalhar para reiniciar
                waitingForShakeAfterBreak = true;
                resetTimer();
                statusText.setText("⏰ Pausa encerrada! Chacoalhe para reiniciar o timer");
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
        if (!breakRunning && !waitingForShakeAfterBreak) {
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
            Log.d(TAG, "Inicializando sons...");

            // Libera recursos anteriores se existirem
            releaseSounds();

            // Configura volume do alarme para máximo
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
            Log.d(TAG, "Volume do alarme definido para máximo: " + maxVolume);

            // Inicializa o gerador de tons
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            Log.d(TAG, "ToneGenerator inicializado");

            // Carrega arquivos de áudio
            loadAudioFiles();

        } catch (Exception e) {
            Log.e(TAG, "Erro na inicialização de sons", e);
        }
    }

    private void loadAudioFiles() {
        try {
            Log.d(TAG, "=== DIAGNÓSTICO DE CARREGAMENTO DE ÁUDIO ===");

            // Testa se os recursos existem usando getIdentifier
            int completionResId = getResources().getIdentifier("completion_sound", "raw", getPackageName());
            int breakEndResId = getResources().getIdentifier("break_end_sound", "raw", getPackageName());

            Log.d(TAG, "completion_sound resource ID: " + completionResId);
            Log.d(TAG, "break_end_sound resource ID: " + breakEndResId);

            if (completionResId == 0) {
                Log.e(TAG, "❌ completion_sound NÃO ENCONTRADO em res/raw/");
            }
            if (breakEndResId == 0) {
                Log.e(TAG, "❌ break_end_sound NÃO ENCONTRADO em res/raw/");
            }

            // Carrega completion_sound.mp3
            try {
                completionSound = MediaPlayer.create(this, R.raw.completion_sound);
                if (completionSound != null) {
                    completionSound.setAudioStreamType(AudioManager.STREAM_MUSIC); // Mudança aqui
                    completionSound.setVolume(1.0f, 1.0f);
                    completionSound.setLooping(false);
                    Log.d(TAG, "✅ completion_sound.mp3 carregado com sucesso");

                    // Teste de duração
                    int duration = completionSound.getDuration();
                    Log.d(TAG, "Duração do completion_sound: " + duration + "ms");
                } else {
                    Log.e(TAG, "❌ MediaPlayer.create retornou null para completion_sound");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Exceção ao carregar completion_sound", e);
            }

            // Carrega break_end_sound.mp3
            try {
                breakEndSound = MediaPlayer.create(this, R.raw.break_end_sound);
                if (breakEndSound != null) {
                    breakEndSound.setAudioStreamType(AudioManager.STREAM_MUSIC); // Mudança aqui
                    breakEndSound.setVolume(1.0f, 1.0f);
                    breakEndSound.setLooping(false);
                    Log.d(TAG, "✅ break_end_sound.mp3 carregado com sucesso");

                    // Teste de duração
                    int duration = breakEndSound.getDuration();
                    Log.d(TAG, "Duração do break_end_sound: " + duration + "ms");
                } else {
                    Log.e(TAG, "❌ MediaPlayer.create retornou null para break_end_sound");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Exceção ao carregar break_end_sound", e);
            }

            // Log de status geral
            Log.d(TAG, "Status final - completionSound: " + (completionSound != null ? "OK" : "ERRO"));
            Log.d(TAG, "Status final - breakEndSound: " + (breakEndSound != null ? "OK" : "ERRO"));

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro geral ao carregar arquivos de áudio", e);
        }
    }

    // Método para testar todos os streams de áudio
    private void testAllAudioStreams() {
        Log.d(TAG, "=== TESTANDO VOLUMES DE TODOS OS STREAMS ===");

        int[] streams = {
                AudioManager.STREAM_ALARM,
                AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_RING,
                AudioManager.STREAM_SYSTEM
        };

        String[] streamNames = {
                "ALARM", "MUSIC", "NOTIFICATION", "RING", "SYSTEM"
        };

        for (int i = 0; i < streams.length; i++) {
            try {
                int maxVol = audioManager.getStreamMaxVolume(streams[i]);
                int currentVol = audioManager.getStreamVolume(streams[i]);
                audioManager.setStreamVolume(streams[i], maxVol, 0);
                Log.d(TAG, streamNames[i] + " - max: " + maxVol + ", era: " + currentVol + ", agora: " + maxVol);
            } catch (Exception e) {
                Log.e(TAG, "Erro no stream " + streamNames[i], e);
            }
        }
    }

    private void releaseMediaPlayers() {
        if (completionSound != null) {
            try {
                if (completionSound.isPlaying()) {
                    completionSound.stop();
                }
                completionSound.release();
                Log.d(TAG, "completionSound liberado");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao liberar completionSound", e);
            }
            completionSound = null;
        }

        if (breakEndSound != null) {
            try {
                if (breakEndSound.isPlaying()) {
                    breakEndSound.stop();
                }
                breakEndSound.release();
                Log.d(TAG, "breakEndSound liberado");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao liberar breakEndSound", e);
            }
            breakEndSound = null;
        }
    }

    private void playCompletionAlarm() {
        Log.d(TAG, "=== TOCANDO ALARME DE CONCLUSÃO ===");

        // Testa diferentes streams de áudio
        testAllAudioStreams();

        // Vibra
        if (vibrator != null) {
            try {
                vibrator.vibrate(new long[]{0, 500, 200, 500, 200, 500}, -1);
            } catch (Exception e) {
                Log.e(TAG, "Erro na vibração", e);
            }
        }

        boolean audioPlayed = false;

        // Método 1: Tenta tocar o arquivo completion_sound.mp3
        if (completionSound != null) {
            try {
                Log.d(TAG, "🔊 Tentando tocar completion_sound...");

                if (completionSound.isPlaying()) {
                    completionSound.stop();
                    completionSound.prepare();
                }

                // Força volume antes de tocar
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);

                completionSound.start();
                audioPlayed = true;
                Log.d(TAG, "✅ completion_sound.mp3 INICIADO");

                // Listener para debug
                completionSound.setOnCompletionListener(mp -> {
                    Log.d(TAG, "✅ completion_sound.mp3 TERMINOU de tocar");
                });

                completionSound.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "❌ Erro no completion_sound - what: " + what + ", extra: " + extra);
                    return false;
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ EXCEÇÃO ao tocar completion_sound", e);
                audioPlayed = false;
            }
        } else {
            Log.e(TAG, "❌ completionSound é NULL");
        }

        // Método 2: Sempre toca tons do sistema também (para garantir)
        Log.d(TAG, "🔊 Tocando tons do sistema como backup...");
        playSystemTone(true);

        // Método 3: Tons adicionais em thread separada
        new Thread(() -> {
            try {
                Thread.sleep(500);
                Log.d(TAG, "🔊 Tocando tons adicionais...");
                if (toneGenerator != null) {
                    // Tom de alta prioridade
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000);
                    Thread.sleep(1200);
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro nos tons adicionais", e);
            }
        }).start();

        Log.d(TAG, "Status reprodução: audioPlayed=" + audioPlayed);
    }

    private void playBreakEndAlarm() {
        Log.d(TAG, "=== TOCANDO ALARME DE FIM DE PAUSA ===");

        // Testa diferentes streams de áudio
        testAllAudioStreams();

        // Vibra
        if (vibrator != null) {
            try {
                vibrator.vibrate(new long[]{0, 200, 100, 200}, -1);
            } catch (Exception e) {
                Log.e(TAG, "Erro na vibração", e);
            }
        }

        boolean audioPlayed = false;

        // Método 1: Tenta tocar o arquivo break_end_sound.mp3
        if (breakEndSound != null) {
            try {
                Log.d(TAG, "🔊 Tentando tocar break_end_sound...");

                if (breakEndSound.isPlaying()) {
                    breakEndSound.stop();
                    breakEndSound.prepare();
                }

                // Força volume antes de tocar
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);

                breakEndSound.start();
                audioPlayed = true;
                Log.d(TAG, "✅ break_end_sound.mp3 INICIADO");

                // Listener para debug
                breakEndSound.setOnCompletionListener(mp -> {
                    Log.d(TAG, "✅ break_end_sound.mp3 TERMINOU de tocar");
                });

                breakEndSound.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "❌ Erro no break_end_sound - what: " + what + ", extra: " + extra);
                    return false;
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ EXCEÇÃO ao tocar break_end_sound", e);
                audioPlayed = false;
            }
        } else {
            Log.e(TAG, "❌ breakEndSound é NULL");
        }

        // Método 2: Sempre toca tons do sistema também
        Log.d(TAG, "🔊 Tocando tons do sistema como backup...");
        playSystemTone(false);

        // Método 3: Tons adicionais
        new Thread(() -> {
            try {
                Thread.sleep(300);
                Log.d(TAG, "🔊 Tocando tons adicionais para pausa...");
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ANSWER, 600);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro nos tons adicionais de pausa", e);
            }
        }).start();

        Log.d(TAG, "Status reprodução pausa: audioPlayed=" + audioPlayed);
    }

    private void playSystemTone(boolean isCompletion) {
        if (toneGenerator == null) {
            Log.w(TAG, "toneGenerator é null");
            return;
        }

        new Thread(() -> {
            try {
                if (isCompletion) {
                    // 3 bips longos para conclusão
                    Log.d(TAG, "Tocando 3 bips longos para conclusão");
                    for (int i = 0; i < 3; i++) {
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500);
                        Thread.sleep(700);
                    }
                } else {
                    // 2 bips rápidos + 1 longo para fim de pausa
                    Log.d(TAG, "Tocando 2 bips rápidos + 1 longo para fim de pausa");
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 200);
                    Thread.sleep(300);
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 200);
                    Thread.sleep(300);
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 800);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Tom do sistema interrompido", e);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao tocar tom do sistema", e);
            }
        }).start();
    }

    private void releaseSounds() {
        Log.d(TAG, "Liberando recursos de áudio...");
        try {
            if (toneGenerator != null) {
                toneGenerator.release();
                toneGenerator = null;
            }
            releaseMediaPlayers();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao liberar sons", e);
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

        if (shake > 12) {
            Log.d(TAG, "Shake detectado! Estado atual - timerRunning: " + timerRunning +
                    ", breakRunning: " + breakRunning + ", waitingForShakeAfterBreak: " + waitingForShakeAfterBreak);

            // Se estiver esperando shake após a pausa
            if (waitingForShakeAfterBreak) {
                waitingForShakeAfterBreak = false;
                startTimer();
                return;
            }

            // Se estiver na pausa, interrompe a pausa e reinicia
            if (breakRunning) {
                pauseBreakTimer();
                resetTimer();
                startTimer();
                return;
            }

            // Comportamento normal: reinicia o timer
            if (timerRunning) {
                pauseTimer();
            }
            resetTimer();
            startTimer();
        }
    }

    private void handleOrientationEvent(SensorEvent event) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastOrientationChange < ORIENTATION_DELAY) {
            return;
        }

        // Não processar orientação durante a pausa ou esperando shake
        if (breakRunning || waitingForShakeAfterBreak) {
            return;
        }

        float pitch = event.values[1];

        if (Math.abs(pitch) < 30) { // Horizontal
            if (currentOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                currentOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                lastOrientationChange = currentTime;

                if (!timerRunning && timeLeftInMillis < TIMER_DURATION && timeLeftInMillis > 0) {
                    startTimer();
                    statusText.setText("Despausado (telefone na horizontal)");
                } else if (timeLeftInMillis == TIMER_DURATION) {
                    statusText.setText("Pronto para começar! Chacoalhe o telefone.");
                }
            }
        } else if (Math.abs(pitch) > 60) { // Vertical
            if (currentOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                currentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                lastOrientationChange = currentTime;

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
        // Não implementado
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

        // Recarrega sons ao voltar para o app
        initializeSounds();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseSounds();
    }
}