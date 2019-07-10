package com.game.test.activity;

import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.game.test.R;
import com.game.test.camera.CameraInterface;
import com.game.test.camera.AutoFitTextureView;
import com.game.test.camera.SaveListener;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CameraActivity";
    public static final String PARAM_NAME = "type";
    public static final int PREVIEW = 0x011;
    public static final int CAPTURE = 0x012;
    public static final int RECORD = 0x013;

    private AutoFitTextureView mTextureView;

    private int mType;
    private LinearLayout recordStopLayout;
    private ImageView takePicIv;
    private Chronometer recordChronometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        mTextureView = findViewById(R.id.camera_tv);
        takePicIv = findViewById(R.id.take_pic_iv);
        recordStopLayout = findViewById(R.id.record_stop_layout);
        recordChronometer = findViewById(R.id.record_chronometer);
        takePicIv.setOnClickListener(this);
        recordStopLayout.setOnClickListener(this);
        CameraInterface.getInstance().init(this, mTextureView);
        mType = getIntent().getIntExtra(PARAM_NAME, PREVIEW);
        if (mType == PREVIEW) {
            CameraInterface.getInstance().setType(CameraInterface.Type.PREVIEW);
        } else if (mType == CAPTURE) {
            CameraInterface.getInstance().setType(CameraInterface.Type.CAPTURE);
            CameraInterface.getInstance().setSaveListener(new SaveListener() {
                @Override
                public void onSave(final String filepath) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(CameraActivity.this, "图片地址：" + filepath, Toast.LENGTH_LONG).show();
                        }
                    });
                    finish();
                }

                @Override
                public void onError(Exception e) {
                    e.printStackTrace();
                }
            });
        } else if (mType == RECORD) {
            CameraInterface.getInstance().setType(CameraInterface.Type.RECORD);
            CameraInterface.getInstance().setSaveListener(new SaveListener() {
                @Override
                public void onSave(final String filepath) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(CameraActivity.this, "视频地址：" + filepath, Toast.LENGTH_LONG).show();
                        }
                    });
                    finish();
                }

                @Override
                public void onError(Exception e) {
                    e.printStackTrace();
                }
            });
        } else {
            finish();
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.take_pic_iv) {
            if (mType == PREVIEW) {
            } else if (mType == CAPTURE) {
                CameraInterface.getInstance().takePicture();
            } else if (mType == RECORD) {
                recordStopLayout.setVisibility(View.VISIBLE);
                takePicIv.setVisibility(View.GONE);
                CameraInterface.getInstance().startRecordingVideo();
                //设置开始计时时间
                recordChronometer.setBase(SystemClock.elapsedRealtime());
                //启动计时器
                recordChronometer.start();
            }
        } else if (id == R.id.record_stop_layout) {
            if (mType == RECORD) {
                if (CameraInterface.getInstance().isIsRecordingVideo()) {
                    CameraInterface.getInstance().stopRecordingVideo();
                    takePicIv.setVisibility(View.VISIBLE);
                    recordStopLayout.setVisibility(View.GONE);
                    recordChronometer.stop();
                }
            }
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
