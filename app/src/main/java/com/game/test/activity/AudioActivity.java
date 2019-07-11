package com.game.test.activity;

import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;

import com.game.test.R;
import com.game.test.audio.AudioRecordUtil;
import com.game.test.audio.AudioRecorder;

public class AudioActivity extends AppCompatActivity implements View.OnClickListener {

    private Chronometer recordChronometer;
    private Button recordBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        recordChronometer = findViewById(R.id.record_chronometer);
        recordBtn = findViewById(R.id.audio_record_btn);
        recordBtn.setOnClickListener(this);
        AudioRecorder.getInstance().init();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.audio_record_btn) {  // 开始录制
            if (AudioRecordUtil.getInstance().isRecording()) {
                recordBtn.setText("开始录制");
                recordChronometer.stop();
                AudioRecordUtil.getInstance().stopRecord();
            } else {
                recordBtn.setText("停止录制");
                //设置开始计时时间
                recordChronometer.setBase(SystemClock.elapsedRealtime());
                //启动计时器
                recordChronometer.start();
                AudioRecordUtil.getInstance().startRecord();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AudioRecordUtil.getInstance().stopRecord();
    }
}
