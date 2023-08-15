package com.example.atlasnaviaidetection;

import android.app.Service;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class RecorderService extends Service {

    private static final String DIRECTORY_ATLAS_VIDEOS = "/AtlasNavi/videos/";
    private static final String TAG = "RecorderService";
    private static Camera mServiceCamera;
    private SurfaceTexture mSurfaceHolder;
    private boolean mRecordingStatus;
    private MediaRecorder mMediaRecorder;

    private String fileName;
    private String outputFile;

    private int activeTripId = 0;

    @Override
    public void onCreate() {
        mRecordingStatus = false;
        mServiceCamera = Camera.open(0);
        mSurfaceHolder = VideoRecorderFragment.surface;

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        activeTripId = intent.getIntExtra("activeTripId", 0);

        if (mRecordingStatus == false) {
            startRecording();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onDestroy() {
        stopRecording(fileName, outputFile);
        mRecordingStatus = false;

        activeTripId = 0;
        fileName = null;
        outputFile = null;
        super.onDestroy();
    }

    public void startRecording() {
        Log.d(TAG, "Recording Service Started");

        Camera.Parameters params = mServiceCamera.getParameters();
        mServiceCamera.setParameters(params);
        Camera.Parameters p = mServiceCamera.getParameters();

        final List<Size> listSize = p.getSupportedPreviewSizes();
        Size mPreviewSize = listSize.get(2);
        p.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        p.setPreviewFormat(PixelFormat.YCbCr_420_SP);
        mServiceCamera.setParameters(p);

        CamcorderProfile profile = CamcorderProfile
                .get(CamcorderProfile.QUALITY_1080P);
        profile.videoFrameRate = 30;

        //this.fileName = System.currentTimeMillis() + "_" + activeTripId + UVCCameraHelper.SUFFIX_MP4;
        this.outputFile = getFilePath() + "/" + fileName;

        try {
            mServiceCamera.setPreviewTexture(mSurfaceHolder);
            mServiceCamera.startPreview();
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        }

        mServiceCamera.setDisplayOrientation(90);
        mServiceCamera.unlock();

        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setCamera(mServiceCamera);
        mMediaRecorder.setOrientationHint(90);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        mMediaRecorder.setOutputFormat(profile.fileFormat);
        mMediaRecorder.setVideoEncoder(profile.videoCodec);
        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
        mMediaRecorder.setOutputFile(outputFile);
        mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        }

        mMediaRecorder.start();
        mRecordingStatus = true;
    }

    public void stopRecording(String fileName, String outputFile) {
        Log.d("yyyy", "Recording Service Stopped");

        if (mMediaRecorder == null)
            return;
        try {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            mMediaRecorder.release();
        } catch (Exception e) {
            Log.d("yyyy", "Error stop media recorder:" + e.getMessage());
        }

        try {
            mServiceCamera.reconnect();
            mServiceCamera.stopPreview();
            mServiceCamera.release();
            mServiceCamera = null;
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        }
    }


    private String getFilePath() {
        File videoFilePath;

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            ContextWrapper cw = new ContextWrapper(getBaseContext());
            File directory = cw.getExternalFilesDir(DIRECTORY_ATLAS_VIDEOS);
            videoFilePath = new File(directory, "");
        } else {
            String filepath = Environment.getExternalStorageDirectory().getPath();
            videoFilePath = new File(filepath + DIRECTORY_ATLAS_VIDEOS);
        }

        if (!videoFilePath.exists()) {
            videoFilePath.mkdirs();
        }
        return (videoFilePath.getAbsolutePath());
    }
}