package com.game.test.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class CameraInterface {

    private static final String TAG = "CameraInterface";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    // 摄像头管理者
    private CameraManager cameraManager;

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private CameraCharacteristics mCharacteristics;
    // 摄像头id
    private String mCameraId;
    private Size mPreviewSize = new Size(-1, -1);

    private CameraDevice mCameraDevice;

    private SurfaceTexture mSurfaceTexture;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private ImageReader mImageReader;

    private boolean isClosed = false;
    private boolean isPreviewing = false;
    private boolean mFlashSupported = false;

    private PreviewCallback mPreviewCallback;

    private CameraInterface() {
    }

    private static class Holder {
        private static final CameraInterface INSTANCE = new CameraInterface();
    }

    /**
     * 初始化
     * @param context
     */
    public void init(Context context) {
        // 获取摄像头管理者，它主要用来查询和打开可用的摄像头
        if (cameraManager == null) {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        }
    }

    public static CameraInterface getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 设置回调
     * @param previewCallback
     */
    public void setPreviewCallback(PreviewCallback previewCallback) {
        this.mPreviewCallback = previewCallback;
    }

    /**
     * 设置相机参数
     *
     * @param width
     * @param height
     * @return
     */
    public void initCamera(int width, int height) {
        // 创建并启动Camera子线程，后面Camera开启、预览、拍照都放在这个子线程中
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        try {
            String[] cameraIdList = cameraManager.getCameraIdList();
            //遍历所有摄像头
            for (String id : cameraIdList) {
                // 获取此ID对应摄像头的参数
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                // 默认打开后置摄像头
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    mCharacteristics = characteristics;
                    mCameraId = id;
                    Log.i(TAG, "openCamera: " + mCameraId);
                    break;
                }
            }
            // 获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
            StreamConfigurationMap map = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            // 根据屏幕尺寸（通过参数传进来）匹配最合适的预览尺寸
            // 获取预览尺寸
            Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
            mPreviewSize = getOptimalSize(previewSizes, width, height);
            Log.i(TAG, "width->" + mPreviewSize.getWidth() + "    height->" + mPreviewSize.getWidth());

            // 设置是否支持闪光灯
            Boolean available = mCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            mFlashSupported = available == null ? false : available;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置自动闪光灯
     *
     * @param requestBuilder
     */
    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    /**
     * 选择sizeMap中大于并且最接近width和height的size
     *
     * @param sizeMap
     * @param width
     * @param height
     * @return
     */
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        int bestWidth = 0, bestHeight = 0;
        float aspect = (float) width / height;
        bestWidth = sizeMap[0].getWidth();
        bestHeight = sizeMap[0].getHeight();
        for (Size sz : sizeMap) {
            int w = sz.getWidth(), h = sz.getHeight();
            if (width >= w && height >= h && bestWidth <= w && bestHeight <= h && Math.abs(aspect - (float) w / h) < 0.2) {
                bestWidth = w;
                bestHeight = h;
            }
        }
        assert(!(bestWidth == 0 || bestHeight == 0));
        if (mPreviewSize.getWidth() == bestWidth && mPreviewSize.getHeight() == bestHeight)
            return mPreviewSize;
        else {
            return new Size(bestWidth, bestHeight);
        }
    }

    /**
     * 开始预览
     */
    public void startPreview() {
        if (mCaptureSession == null || isPreviewing) {
            return;
        }
        try {
            mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mCameraHandler);
            isPreviewing = true;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止预览
     */
    public void stopPreview() {
        if (mCaptureSession == null || !isPreviewing) {
            return;
        }
        try {
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            isPreviewing = false;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public boolean openCamera(SurfaceTexture surfaceTexture) {
        return openCamera(surfaceTexture, 600, 840);
    }

    /**
     * 打开相机
     *
     * @return
     */
    @SuppressLint("MissingPermission")
    public boolean openCamera(SurfaceTexture surfaceTexture, int width, int height) {
        try {
            this.mSurfaceTexture = surfaceTexture;
            initCamera(width, height);
            if (mCameraId == null || mCameraHandler == null || mStateCallback == null) {
                Log.e(TAG, "openCamera: mCameraId is NULL");
                return false;
            }
            isClosed = false;
            // 开启相机，第一个参数指示打开哪个摄像头，第二个参数mStateCallback为相机的状态回调接口，第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
            cameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 关闭相机
     */
    public void stopCamera() {
        isClosed = true;
        if (mCaptureSession != null) {
            if (isPreviewing) {
                try {
                    mCaptureSession.stopRepeating();
                    isPreviewing = false;
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        mCaptureRequestBuilder = null;
        mPreviewSize = new Size(-1, -1);
        mPreviewCallback = null;
        try {
            if (mCameraThread != null) {
                mCameraThread.quitSafely();
                mCameraThread.join();
                mCameraThread = null;
            }
            mCameraHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mCameraId = null;
        mCharacteristics = null;
        mSurfaceTexture = null;
        isPreviewing = false;
        mFlashSupported = false;
    }

    /**
     * 当相机成功打开后会回调onOpened方法，这里可以拿到CameraDevice对象，也就是具体的摄像头设备
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            // 创建CameraPreviewSession
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    /**
     * 创建CameraPreviewSession
     * 开始预览
     */
    private void createCameraPreviewSession() {
        if (mCameraDevice == null || mSurfaceTexture == null || mPreviewSize == null) {
            return;
        }
        // 设置预览尺寸
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        try {
            /**
             * 设置请求类型
             * TEMPLATE_RECORD 创建适合录像的请求
             * TEMPLATE_PREVIEW 创建一个适合于相机预览窗口的请求
             * TEMPLATE_STILL_CAPTURE 创建适用于静态图像捕获的请求
             * TEMPLATE_VIDEO_SNAPSHOT 在录制视频时创建适合静态图像捕获的请求
             */
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // 就是在这里，通过这个set(key,value)方法，设置曝光啊，自动聚焦等参数！！ 如下举例：
            // 自动曝光模式
//        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 自动对焦模式
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            Surface surface = new Surface(mSurfaceTexture);
            /**
             * @width 默认的像素宽度
             * @height 默认的像素高度
             * @format 此处还有很多格式，比如我所用到YUV等
             * @maxImages 最大的图片数，mImageReader里能获取到图片数，但是实际中是2+1张图片，就是多一张
             */
//            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 1);
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(mOnByteAvailableListener, mCameraHandler);
            // 添加输出的surface
            // 这里一定分别add两个surface，一个Textureview的，一个ImageReader的，如果没add，会造成没摄像头预览，或者没有ImageReader的那个回调！！
            mCaptureRequestBuilder.addTarget(surface);
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            // 创建一个CameraCaptureSession来进行相机预览。
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            // 相机已经关闭
            if (null == mCameraDevice) {
                return;
            }
            // 会话准备好后，我们开始显示预览
            mCaptureSession = session;
            try {
                // 闪光灯
                setAutoFlash(mCaptureRequestBuilder);
                // 开启相机预览并添加事件
                // 发送请求，开启相机预览并添加事件
                mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            session.close();
            mCaptureSession = null;
        }
    };

    private ImageReader.OnImageAvailableListener mOnByteAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();// 最后一帧
            if (image == null || isClosed) {
                return;
            }
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] data = new byte[byteBuffer.remaining()];
            byteBuffer.get(data);
            try{
                if (mPreviewCallback != null) {
                    mPreviewCallback.onPreviewFrame(data, mPreviewSize);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                byteBuffer.clear();
                // 一定要关闭
                image.close();
                image = null;
            }
        }
    };

    private void resetPreview() {
        try {
            mImageReader.setOnImageAvailableListener(mOnByteAvailableListener, mCameraHandler);
            mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 拍照
     * @param rotation 手机方向
     */
    public void takePicture(int rotation) {
        if (mCameraDevice == null) return;
        try {
            // 创建拍照需要的CaptureRequest.Builder
            CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraHandler);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 根据设备方向计算设置照片的方向
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            // 这个回调接口用于拍照结束时重启预览，因为拍照会导致预览停止
            CameraCaptureSession.CaptureCallback mImageSavedCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    // 重启预览
                    resetPreview();
                }
            };
            // 停止预览
            mCaptureSession.stopRepeating();
            // 拍照
            mCaptureSession.capture(captureRequestBuilder.build(), mImageSavedCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image == null || isClosed) {
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String name = sdf.format(new Date()) + ".jpeg";
            String path = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Pictures/" + name;
            mCameraHandler.post(new ImageSaver(image, path, null));
        }
    };

    /**
     * 将焦点锁定为静态图像捕获的第一步。（对焦）
     */
    private void lockFocus() {
        try {
            // 相机对焦
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            // 修改状态
//            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mCaptureRequestBuilder.build(), null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public interface PreviewCallback {
        void onPreviewFrame(byte[] data, Size size);
    }

}

