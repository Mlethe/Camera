package com.game.test.activity;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.game.test.R;
import com.game.test.camera.CameraInterface;
import com.game.test.camera.AutoFitTextureView;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CameraActivity";

    private AutoFitTextureView mTextureView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        mTextureView = findViewById(R.id.camera_tv);
        findViewById(R.id.take_pic_iv).setOnClickListener(this);
        CameraInterface.getInstance().init(this, mTextureView);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.take_pic_iv) {
            CameraInterface.getInstance().takePicture();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mTextureView.isAvailable()) {
            CameraInterface.getInstance().openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        CameraInterface.getInstance().closeCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CameraInterface.getInstance().release();
    }
}
