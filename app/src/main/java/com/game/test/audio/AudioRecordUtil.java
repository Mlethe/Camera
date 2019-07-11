package com.game.test.audio;

import android.media.MediaRecorder;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AudioRecordUtil {
    private MediaRecorder mMediaRecorder;

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS_");
    private String mPath;
    private String mFileName;
    // 标记为开始采集状态
    private boolean isRecording = false;

    private AudioRecordUtil() {
    }

    private static final class Holder {
        private static final AudioRecordUtil INSTANCE = new AudioRecordUtil();
    }

    public static AudioRecordUtil getInstance() {
        return Holder.INSTANCE;
    }

    public boolean isRecording() {
        return isRecording;
    }

    /**
     * 开始录制
     */
    public void startRecord() {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);  //音频输入源
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);   //设置输出格式
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);   //设置编码格式
        String filepath = getPath();
        mMediaRecorder.setOutputFile(filepath);
        try {
            mMediaRecorder.prepare();
            isRecording = true;
            mMediaRecorder.start();  // 开始录制
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止录制，资源释放
     */
    public void stopRecord() {
        if (mMediaRecorder != null) {
            isRecording = false;
            mMediaRecorder.stop();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    private String getPath() {
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
            filename = sdf.format(new Date()) + random + ".amr";
        }
        file = new File(path, filename);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return file.getPath();
    }
}
