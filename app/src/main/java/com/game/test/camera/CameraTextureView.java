package com.game.test.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.util.Size;
import android.view.TextureView;

public class CameraTextureView extends TextureView implements TextureView.SurfaceTextureListener, CameraInterface.PreviewCallback {

    private SurfaceViewListener mSurfaceViewListener;

    public CameraTextureView(Context context) {
        this(context, null);
    }

    public CameraTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        CameraInterface.getInstance().init(context);
        setSurfaceTextureListener(this);
    }

    /**
     * 设置回调
     * @param surfaceViewListener
     */
    public void setSurfaceViewListener(SurfaceViewListener surfaceViewListener) {
        this.mSurfaceViewListener = surfaceViewListener;
    }

    /**
     * 在SurfaceTexture准备使用时调用
     * @param surface
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        CameraInterface.getInstance().setPreviewCallback(this);
        CameraInterface.getInstance().openCamera(surface);
    }

    /**
     * 当SurfaceTexture缓冲区大小更改时调用。
     * @param surface
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    /**
     * 当指定SurfaceTexture即将被销毁时调用。如果返回true，则调用此方法后，表面纹理中不会发生渲染。如果返回false，则客户端需要调用release()。大多数应用程序应该返回true
     * @param surface
     * @return
     */
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        CameraInterface.getInstance().stopCamera();
        return true;
    }

    /**
     * 当指定SurfaceTexture的更新时调用updateTexImage()。
     * @param surface
     */
    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onPreviewFrame(byte[] data, Size size) {
        if (mSurfaceViewListener != null) {
            mSurfaceViewListener.onPreviewFrame(data, size);
        }
    }

    public interface SurfaceViewListener {
        void onPreviewFrame(byte[] data, Size size);
    }

}
