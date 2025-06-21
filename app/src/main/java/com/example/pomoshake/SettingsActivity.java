package com.example.pomoshake;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

public class SettingsActivity extends Activity {

    private EditText pomodoroInput, breakInput;
    private Button saveBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        pomodoroInput = findViewById(R.id.pomodoroInput);
        breakInput = findViewById(R.id.breakInput);
        saveBtn = findViewById(R.id.saveBtn);

        // Preenche com valores antigos, se existirem
        long pomo = getIntent().getLongExtra("pomodoro", 60_000);
        long pause = getIntent().getLongExtra("break", 10_000);
        pomodoroInput.setText(String.valueOf(pomo / 1000));
        breakInput.setText(String.valueOf(pause / 1000));

        saveBtn.setOnClickListener(v -> {
            long newPomodoro = Long.parseLong(pomodoroInput.getText().toString()) * 1000;
            long newBreak = Long.parseLong(breakInput.getText().toString()) * 1000;

            Intent result = new Intent();
            result.putExtra("newPomodoro", newPomodoro);
            result.putExtra("newBreak", newBreak);
            setResult(Activity.RESULT_OK, result);
            finish();
        });
    }
}
