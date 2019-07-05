package com.game.test.camera;

import android.media.Image;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageSaver implements Runnable {
    private Image mImage;
    private String mPathName;
    private CameraCallback mCameraCallback;

    public ImageSaver(Image image, String path, CameraCallback callback) {
        this.mImage = image;
        this.mPathName = path;
        this.mCameraCallback = callback;
    }

    @Override
    public void run() {
        if (mImage == null) {
            return;
        }
        ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
        byte[] data = new byte[byteBuffer.remaining()];
        byteBuffer.get(data);
        File file = new File(mPathName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(data, 0 ,data.length);
            if (mCameraCallback != null) {
                mCameraCallback.onCapture(mPathName);
            }
            Log.e("TAG", "run: " + mPathName);
        } catch (IOException e) {
            e.printStackTrace();
            if (mCameraCallback != null) {
                mCameraCallback.onError(e);
            }
        } finally {
            file = null;
            if (fos != null) {
                try {
                    fos.close();
                    fos = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            byteBuffer.clear();
            // 一定要关闭
            mImage.close();
        }
    }
}
