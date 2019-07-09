package com.game.test.activity;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.game.test.R;
import com.game.test.camera.CameraInterface;
import com.game.test.camera.CameraTextureView;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CameraActivity";

    private CameraTextureView vameraTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        vameraTv = findViewById(R.id.camera_tv);
        findViewById(R.id.take_pic_iv).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.take_pic_iv) {
            //获取屏幕方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Log.e(TAG, "onClick: " + rotation);
            CameraInterface.getInstance().takePicture(rotation);
        }
    }
}
