package com.game.test.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AudioRecorder {

    //指定音频源 这个和MediaRecorder是相同的 MediaRecorder.AudioSource.MIC指的是麦克风
    private static final int mAudioSource = MediaRecorder.AudioSource.MIC;
    //指定采样率 （MediaRecoder 的采样率通常是8000Hz AAC的通常是44100Hz。 设置采样率为44100，目前为常用的采样率，官方文档表示这个值可以兼容所有的设置）
    private static final int mSampleRateInHz = 44100;
    //指定捕获音频的声道数目。在AudioFormat类中指定用于此的常量
    private static final int mChannelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO; //单声道
    //指定音频量化位数 ,在AudioFormaat类中指定了以下各种可能的常量。通常我们选择ENCODING_PCM_16BIT和ENCODING_PCM_8BIT PCM代表的是脉冲编码调制，它实际上是原始音频样本。
    //因此可以设置每个样本的分辨率为16位或者8位，16位将占用更多的空间和处理能力,表示的音频也更加接近真实。
    private static final int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    //指定缓冲区大小。调用AudioRecord类的getMinBufferSize方法可以获得。
    private int mBufferSizeInBytes;

    //标记为开始采集状态
    private boolean isRecording = false;

    private AudioRecord mAudioRecord;

    private RecordThread mThread;

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS_");
    private String mPath;
    private String mFileName;

    private AudioRecorder() {
    }

    private static final class Holder {
        private static final AudioRecorder INSTANCE = new AudioRecorder();
    }

    public static AudioRecorder getInstance() {
        return Holder.INSTANCE;
    }

    public boolean isRecording() {
        return isRecording;
    }

    /**
     * 初始化
     */
    public void init() {
        // 指定缓冲区大小。调用AudioRecord类的getMinBufferSize方法可以获得。
        mBufferSizeInBytes = AudioRecord.getMinBufferSize(mSampleRateInHz, mChannelConfig, mAudioFormat);
        //创建AudioRecord。AudioRecord类实际上不会保存捕获的音频，因此需要手动创建文件并保存下载。
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRateInHz, mChannelConfig, mAudioFormat, mBufferSizeInBytes);//创建AudioRecorder对象
    }

    /**
     * 开始录音
     */
    public void startRecord() {
        //判断AudioRecord的状态是否初始化完毕
        //在AudioRecord对象构造完毕之后，就处于AudioRecord.STATE_INITIALIZED状态了。
        if (mAudioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
            throw new RuntimeException("The AudioRecord is not uninitialized");
        } else {
            destroyThread();
            isRecording = true;
            if(mThread == null){
                mThread = new RecordThread();
                mThread.start();//开启线程
            }
        }
    }

    /**
     * 销毁线程方法
     */
    private void destroyThread() {
        try {
            isRecording = false;
            if (null != mThread && Thread.State.RUNNABLE == mThread.getState()) {
                try {
                    Thread.sleep(500);
                    mThread.interrupt();
                } catch (Exception e) {
                    mThread = null;
                }
            }
            mThread = null;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mThread = null;
        }
    }

    /**
     * 停止录音
     */
    public void stopRecord() {
        isRecording = false;
        // 停止录音，回收AudioRecord对象，释放内存
        if (mAudioRecord != null) {
            if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {// 初始化成功
                mAudioRecord.stop();
            }
            mAudioRecord.release();
        }
    }

    /**
     * 释放内存
     */
    public void release () {
        mBufferSizeInBytes = 0;
        mAudioRecord = null;
        mThread = null;
        mPath = null;
        mFileName = null;
    }

    private class RecordThread extends Thread {
        private static final String TAG = "RecordThread";
        private File mRecordingFile;

        @Override
        public void run() {
            //标记为开始采集状态
            isRecording = true;
            //创建一个流，存放从AudioRecord读取的数据
            mRecordingFile = getFile();
            try {
                //获取到文件的数据流
                DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(mRecordingFile)));
                byte[] buffer = new byte[mBufferSizeInBytes];


                //判断AudioRecord未初始化，停止录音的时候释放了，状态就为STATE_UNINITIALIZED
                if (mAudioRecord.getState() == mAudioRecord.STATE_UNINITIALIZED) {
                    init();
                }

                mAudioRecord.startRecording();//开始录音
                //getRecordingState获取当前AudioReroding是否正在采集数据的状态
                while (isRecording && mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    int bufferReadResult = mAudioRecord.read(buffer, 0, mBufferSizeInBytes);
                    for (int i = 0; i < bufferReadResult; i++) {
                        dataOutputStream.write(buffer[i]);
                    }
                }
                dataOutputStream.close();
            } catch (Throwable t) {
                Log.e(TAG, "Recording Failed");
                stopRecord();
            }
        }
    }

    private File getFile() {
        String path = mPath;
        if (mPath == null || mPath.isEmpty()) {
            path = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Audios/";
        }
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }
        String filename = mFileName;
        if (mFileName == null || mFileName.isEmpty()) {
            int random = (int) ((Math.random() * 9 + 1) * 100000);
            filename = sdf.format(new Date()) + random + ".pcm";
        }
        file = new File(path, filename);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                file = null;
            }
        }
        return file;
    }
}
