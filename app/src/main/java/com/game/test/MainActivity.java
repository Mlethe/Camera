package com.game.test;

import android.Manifest;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.game.test.activity.AudioActivity;
import com.game.test.activity.CameraActivity;
import com.tbruyelle.rxpermissions2.RxPermissions;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private RxPermissions permissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.sound_recording_btn).setOnClickListener(this);
        findViewById(R.id.take_pic_btn).setOnClickListener(this);
        findViewById(R.id.record_btn).setOnClickListener(this);
        permissions = new RxPermissions(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.sound_recording_btn) {   // 录音
            permissions.request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO)
                    .subscribe(grant -> {
                        if (grant) {
                            Intent intent = new Intent(this, AudioActivity.class);
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "请到“应用管理->权限管理”开启读写、录音权限", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else if (id == R.id.take_pic_btn) {   // 拍照
            permissions.request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
                    .subscribe(grant -> {
                        if (grant) {
                            Intent intent = new Intent(this, CameraActivity.class);
                            intent.putExtra(CameraActivity.PARAM_NAME, CameraActivity.CAPTURE);
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "请到“应用管理->权限管理”开启读写、相机权限", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else if (id == R.id.record_btn) {     // 录屏
            permissions.request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    .subscribe(grant -> {
                        if (grant) {
                            Intent intent = new Intent(this, CameraActivity.class);
                            intent.putExtra(CameraActivity.PARAM_NAME, CameraActivity.RECORD);
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "请到“应用管理->权限管理”开启读写、相机、录音权限", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }
}
