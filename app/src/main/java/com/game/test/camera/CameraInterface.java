package com.game.test.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraInterface {

    private static final String TAG = "CameraInterface";

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS_");;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    // 摄像头管理者
    private CameraManager cameraManager;

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    // 摄像头id
    private String mCameraId;
    private Size mPreviewSize = new Size(-1, -1);

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mPreviewSession;
    private CaptureRequest.Builder mPreviewBuilder;
    private CaptureRequest mPreviewRequest;
    private ImageReader mImageReader;

    private PreviewCallback mPreviewCallback;

    private Activity mActivity;

    private AutoFitTextureView mTextureView;

    /**
     * 相机状态：显示预览
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * 相机状态：等待焦点锁定
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * 相机状态：等待曝光处于不稳定状态
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * 相机状态：等待曝光状态为非危险状态
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * 相机状态：拍摄了照片
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * 最大预览宽度
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * 最大预览高度
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * 拍摄照片的相机状态的当前状态
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * 防止应用程序在关闭相机前退出
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * 当前相机设备是否支持闪光灯
     */
    private boolean mFlashSupported;

    /**
     * 摄像头传感器的方向
     */
    private int mSensorOrientation;

    // 操作类型
    private Type mType = Type.PREVIEW;

    private Size mVideoSize;

    private MediaRecorder mMediaRecorder;

    // 录屏状态
    private boolean mIsRecordingVideo;

    // 保存的文件路径
    private String mPath;
    // 文件名
    private String mFilename;
    // 文件完整路径
    private String mFilePath;

    private SaveListener mSaveListener;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    private CameraInterface() {
    }

    private static class Holder {
        private static final CameraInterface INSTANCE = new CameraInterface();
    }

    /**
     * 初始化
     *
     * @param activity
     */
    public void init(Activity activity, AutoFitTextureView textureView) {
        this.mActivity = activity;
        this.mTextureView = textureView;
        // 获取摄像头管理者，它主要用来查询和打开可用的摄像头
        if (cameraManager == null) {
            cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        }
    }

    public static CameraInterface getInstance() {
        return Holder.INSTANCE;
    }

    public void setSaveListener(SaveListener saveListener) {
        this.mSaveListener = saveListener;
    }

    public void setType(Type type) {
        this.mType = type;
    }

    public void setPath(String path) {
        this.mPath = path;
    }

    public void setFilename(String filename) {
        this.mFilename = filename;
    }

    public boolean isIsRecordingVideo() {
        return mIsRecordingVideo;
    }
    /**
     * 设置回调
     *
     * @param previewCallback
     */
    public void setPreviewCallback(PreviewCallback previewCallback) {
        this.mPreviewCallback = previewCallback;
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mPreviewSession.capture(mPreviewBuilder.build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            if (null == mActivity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mPreviewSession.stopRepeating();
            mPreviewSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewBuilder);
            mPreviewSession.capture(mPreviewBuilder.build(), mCaptureCallback, mCameraHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mPreviewSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                if (mType == Type.PREVIEW || mType == Type.CAPTURE) {
                    // For still image captures, we use the largest available size.
                    Size largest = Collections.max(
                            Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                            new CompareSizesByArea());
                    /**
                     * @width 默认的像素宽度
                     * @height 默认的像素高度
                     * @format 此处还有很多格式，比如我所用到YUV等
                     * @maxImages 最大的图片数，mImageReader里能获取到图片数，但是实际中是2+1张图片，就是多一张
                     */
                    if (mType == Type.PREVIEW) {
                        mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.YUV_420_888, 2);
                        mImageReader.setOnImageAvailableListener(mOnByteAvailableListener, mCameraHandler);
                    } else {
                        mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/2);
                        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraHandler);
                    }

                    // Find out if we need to swap dimension to get the preview size relative to sensor
                    // coordinate.
                    int displayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                    //noinspection ConstantConditions
                    mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    boolean swappedDimensions = false;
                    switch (displayRotation) {
                        case Surface.ROTATION_0:
                        case Surface.ROTATION_180:
                            if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                                swappedDimensions = true;
                            }
                            break;
                        case Surface.ROTATION_90:
                        case Surface.ROTATION_270:
                            if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                                swappedDimensions = true;
                            }
                            break;
                        default:
                            Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                    }

                    Point displaySize = new Point();
                    mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                    int rotatedPreviewWidth = width;
                    int rotatedPreviewHeight = height;
                    int maxPreviewWidth = displaySize.x;
                    int maxPreviewHeight = displaySize.y;

                    if (swappedDimensions) {
                        rotatedPreviewWidth = height;
                        rotatedPreviewHeight = width;
                        maxPreviewWidth = displaySize.y;
                        maxPreviewHeight = displaySize.x;
                    }

                    if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                        maxPreviewWidth = MAX_PREVIEW_WIDTH;
                    }

                    if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                        maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                    }

                    // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                    // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                    // garbage capture data.
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                            rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                            maxPreviewHeight, largest);
//                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class),
//                        rotatedPreviewWidth, rotatedPreviewHeight);
                } else if (mType == Type.RECORD) {
                    //noinspection ConstantConditions
                    mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                            width, height, mVideoSize);
                }
                Log.i(TAG, "width->" + mPreviewSize.getWidth() + "    height->" + mPreviewSize.getHeight());

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = mActivity.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace();
        }
    }

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * 在这个示例中，我们选择一个具有3x4纵横比的视频大小。此外，我们不使用大于1080p的尺寸，因为MediaRecorder无法处理如此高分辨率的视频。
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     * 给定（相机支持的@code size），选择最小的一个，该最小的一个至少与相应纹理视图大小一样大，
     * 并且最多与相应最大大小一样大，并且其纵横比与指定值匹配。
     * 如果不存在这样的尺寸，请选择最大的尺寸，其最大尺寸最多与相应的最大尺寸相同，并且其纵横比与指定值匹配。
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
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
        assert (!(bestWidth == 0 || bestHeight == 0));
        if (mPreviewSize.getWidth() == bestWidth && mPreviewSize.getHeight() == bestHeight)
            return mPreviewSize;
        else {
            return new Size(bestWidth, bestHeight);
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     * <p>
     * 固定“mTextureView”的大小
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    public void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize || null == mActivity) {
            return;
        }
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * 打开相机
     */
    @SuppressLint("MissingPermission")
    public void openCamera(int width, int height) {
        startBackgroundThread();
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        if (mType == Type.RECORD) {
            mMediaRecorder = new MediaRecorder();
        }
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            cameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * 关闭相机
     */
    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mMediaRecorder != null) {
                if (mIsRecordingVideo) {
                    mIsRecordingVideo = false;
                    // Stop recording
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    if (mSaveListener != null) {
                        mSaveListener.onSave(mFilePath);
                    }
                }
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
            stopBackgroundThread();
            mPreviewBuilder = null;
            mPreviewRequest = null;
            mPreviewSize = new Size(-1, -1);
            mPreviewCallback = null;
            mFlashSupported = false;
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
            mType = Type.PREVIEW;
        }
    }

    /**
     * 释放内存
     */
    public void release() {
        mActivity = null;
        mTextureView = null;
        cameraManager = null;
    }

    /**
     * 启动后台线程
     */
    private void startBackgroundThread() {
        // 创建并启动Camera子线程，后面Camera开启、预览、拍照都放在这个子线程中
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    /**
     * 停止后台线程
     */
    private void stopBackgroundThread() {
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            try {
                mCameraThread.join();
                mCameraThread = null;
                mCameraHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
     * 当相机成功打开后会回调onOpened方法，这里可以拿到CameraDevice对象，也就是具体的摄像头设备
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            // 创建CameraPreviewSession
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }
    };

    /**
     * 创建CameraPreviewSession
     * 开始预览
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            // 设置预览尺寸
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            // 开始预览的输出页面
            Surface surface = new Surface(texture);
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 添加输出的surface
            // 这里一定分别add两个surface，一个Textureview的，一个ImageReader的，如果没add，会造成没摄像头预览，或者没有ImageReader的那个回调！！
            mPreviewBuilder.addTarget(surface);
//            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            if (mType == Type.RECORD) {
                // 创建一个CameraCaptureSession来进行相机预览。
                mCameraDevice.createCaptureSession(Arrays.asList(surface), mSessionStateCallback, mCameraHandler);
            } else if (mType == Type.PREVIEW || mType == Type.CAPTURE){
                // 创建一个CameraCaptureSession来进行相机预览。
                mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionStateCallback, mCameraHandler);
            }
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
            mPreviewSession = session;
            try {
                // 就是在这里，通过这个set(key,value)方法，设置曝光啊，自动聚焦等参数！！ 如下举例：
                // 自动曝光模式
//                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                // 自动对焦模式
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // 闪光灯
                setAutoFlash(mPreviewBuilder);
                // 开启相机预览并添加事件
                // 发送请求，开启相机预览并添加事件
                mPreviewRequest = mPreviewBuilder.build();
                mPreviewSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            session.close();
            mPreviewSession = null;
        }
    };

    private ImageReader.OnImageAvailableListener mOnByteAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();// 最后一帧
            if (image == null) {
                return;
            }
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] data = new byte[byteBuffer.remaining()];
            byteBuffer.get(data);
            try {
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

    /**
     * 拍照
     */
    public void takePicture() {
        if (mType == Type.CAPTURE)
            lockFocus();
    }

    /**
     * 录屏
     */
    public void startRecordingVideo() {
        if (mType != Type.RECORD) return;
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIsRecordingVideo = true;
                            // Start recording
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession.close();
                    mPreviewSession = null;
                }
            }, mCameraHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
            if (mSaveListener != null) {
                mSaveListener.onError(e);
            }
        }

    }

    /**
     * 停止录屏
     */
    public void stopRecordingVideo() {
        if (mType != Type.RECORD) return;
        mIsRecordingVideo = false;
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        if (mSaveListener != null) {
            mSaveListener.onSave(mFilePath);
        }
        startPreview();
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            mPreviewSession.close();
                            mPreviewSession = null;
                        }
                    }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpMediaRecorder() throws IOException {
        if (null == mActivity) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mFilePath = getPath();
        mMediaRecorder.setOutputFile(mFilePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case 90:
                mMediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));
                break;
            case 270:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
    }

    /**
     * 关闭预览会话
     */
    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            mFilePath = getPath();
            mCameraHandler.post(new ImageSaver(image, mFilePath, mSaveListener));
        }
    };

    private String getPath() {
        String path = mPath;
        if (mPath == null || mPath.isEmpty()) {
            if (mType == Type.CAPTURE) {
                path = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Pictures/";
            } else if (mType == Type.RECORD) {
                path = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Videos/";
            }
        }
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }
        String filename = mFilename;
        if (mFilename == null || mFilename.isEmpty()) {
            int random = (int)((Math.random() * 9 + 1) * 100000);
            if (mType == Type.CAPTURE) {
                filename = sdf.format(new Date()) + random + ".jpeg";
            } else if (mType == Type.RECORD) {
                filename = sdf.format(new Date()) + random + ".mp4";
            }
        }
        String filePath = path + "/" + filename;
        file = new File(filePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file.getPath();
    }

    /**
     * 将焦点锁定为静态图像捕获的第一步。（对焦）
     */
    private void lockFocus() {
        try {
            // 相机对焦
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            // 修改状态
            mState = STATE_WAITING_LOCK;
            mPreviewSession.capture(mPreviewBuilder.build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public interface PreviewCallback {
        void onPreviewFrame(byte[] data, Size size);
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            // 强制转换以确保乘法不会溢出
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    public enum Type {
        // 拍照
        CAPTURE,
        // 预览
        PREVIEW,
        // 录屏
        RECORD
    }

}

