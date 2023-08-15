package com.example.atlasnaviaidetection;

import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.atlasnaviaidetection.env.ImageUtils;
import com.example.atlasnaviaidetection.env.Logger;
import com.example.atlasnaviaidetection.usb.CameraDialog;
import com.example.atlasnaviaidetection.usb.Size;
import com.example.atlasnaviaidetection.usb.USBMonitor;
import com.example.atlasnaviaidetection.usb.common.AbstractUVCCameraHandler;
import com.example.atlasnaviaidetection.usb.encoder.RecordParams;
import com.example.atlasnaviaidetection.usb.widget.CameraViewInterface;
import com.example.atlasnaviaidetection.usbcamera.UVCCameraHelper;
import com.example.atlasnaviaidetection.usbcamera.utils.FileUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;
import java.util.List;

public class USBCameraActivity extends BaseDetectorActivity implements
        CameraDialog.CameraDialogParent,
        CameraViewInterface.Callback {

    private static final String TAG = "Debug";
    private static final Logger LOGGER = new Logger();

    public View mTextureView;
    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;
    private AlertDialog mDialog;
    private boolean isRequest;
    private boolean isPreview;
    private final UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        @Override
        public void onAttachDev(UsbDevice device) {
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                showShortMsg(device.getDeviceName() + " is out");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params");
                isPreview = false;
            } else {
                isPreview = true;
                showShortMsg("connecting");
                // initialize seekbar
                // need to wait UVCCamera initialize over

                new Thread(() -> {
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Looper.prepare();

                    Looper.loop();
                }).start();
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {
            showShortMsg("disconnecting");
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);

        mTextureView = findViewById(R.id.camera_view);
        // step.1 initialize UVCCameraHelper
        mUVCCameraView = (CameraViewInterface) mTextureView;
        mUVCCameraView.setCallback(this);


        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);
        mCameraHelper.setOnPreviewFrameListener(nv21Yuv -> {
            Log.d(TAG, "onPreviewResult: " + nv21Yuv.length);
            onExternalImageAvailable(nv21Yuv);
        });


        threadsTextView = findViewById(R.id.threads);
        plusImageView = findViewById(R.id.plus);
        minusImageView = findViewById(R.id.minus);
        bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
        gestureLayout = findViewById(R.id.gesture_layout);
        bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);
        frameValueTextView = findViewById(R.id.frame_info);
        cropValueTextView = findViewById(R.id.crop_info);
        inferenceTimeTextView = findViewById(R.id.inference_info);
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
        plusImageView.setOnClickListener(this);
        minusImageView.setOnClickListener(this);

        gestureLayout.setVisibility(View.GONE);
        ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                //                int width = bottomSheetLayout.getMeasuredWidth();
                int height = gestureLayout.getMeasuredHeight();

                sheetBehavior.setPeekHeight(height);
            }
        });
        sheetBehavior.setHideable(false);

        sheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED: {
                        bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
                    }
                    break;
                    case BottomSheetBehavior.STATE_COLLAPSED: {
                        bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                    }
                    break;
                    case BottomSheetBehavior.STATE_DRAGGING:
                        break;
                    case BottomSheetBehavior.STATE_SETTLING:
                        bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // step.2 register USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // step.3 unregister USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
        }
    }

    private void menu_takepic() {
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            showShortMsg("sorry,camera open failed");
        }
        String picPath = UVCCameraHelper.ROOT_PATH + "AtlasNavi" + "/images/" + System.currentTimeMillis() + UVCCameraHelper.SUFFIX_JPEG;

        mCameraHelper.capturePicture(picPath, path -> {
            if (TextUtils.isEmpty(path)) {
                return;
            }
            new Handler(getMainLooper()).post(() -> Toast.makeText(USBCameraActivity.this, "save path:" + path, Toast.LENGTH_SHORT).show());
        });
    }

    private void menu_recording() {
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            showShortMsg("sorry,camera open failed");
        }
        if (!mCameraHelper.isPushing()) {
            String videoPath = UVCCameraHelper.ROOT_PATH + "AtlasNavi" + "/videos/" + System.currentTimeMillis() + UVCCameraHelper.SUFFIX_MP4;

//                    FileUtils.createfile(FileUtils.ROOT_PATH + "test666.h264");
            // if you want to record,please create RecordParams like this
            RecordParams params = new RecordParams();
            params.setRecordPath(videoPath);
            params.setRecordDuration(0);                        // auto divide saved,default 0 means not divided
            params.setVoiceClose(false);    // is close voice

            params.setSupportOverlay(true); // overlay only support armeabi-v7a & arm64-v8a
            mCameraHelper.startPusher(params, new AbstractUVCCameraHandler.OnEncodeResultListener() {
                @Override
                public void onEncodeResult(byte[] data, int offset, int length, long timestamp, int type) {
                    // type = 1,h264 video stream
                    if (type == 1) {
                        FileUtils.putFileStream(data, offset, length);
                    }
                    // type = 0,aac audio stream
                    if (type == 0) {

                    }
                }

                @Override
                public void onRecordResult(String videoPath) {
                    if (TextUtils.isEmpty(videoPath)) {
                        return;
                    }
                    new Handler(getMainLooper()).post(() -> Toast.makeText(USBCameraActivity.this, "save videoPath:" + videoPath, Toast.LENGTH_SHORT).show());
                }
            });
            // if you only want to push stream,please call like this
            // mCameraHelper.startPusher(listener);
            showShortMsg("start record...");
        } else {
            FileUtils.releaseFile();
            mCameraHelper.stopPusher();
            showShortMsg("stop record...");
        }
    }

    private void menu_resolution() {
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            showShortMsg("sorry,camera open failed");
        }
        showResolutionListDialog();
    }

    private void menu_focus() {
        if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
            showShortMsg("sorry,camera open failed");
        }
        mCameraHelper.startCameraFoucs();
    }

    private void showResolutionListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(USBCameraActivity.this);
        View rootView = LayoutInflater.from(USBCameraActivity.this).inflate(R.layout.layout_dialog_list, null);
        ListView listView = rootView.findViewById(R.id.listview_dialog);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(USBCameraActivity.this, android.R.layout.simple_list_item_1, getResolutionList());
        if (adapter != null) {
            listView.setAdapter(adapter);
        }
        listView.setOnItemClickListener((adapterView, view, position, id) -> {
            if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) return;
            final String resolution = (String) adapterView.getItemAtPosition(position);
            String[] tmp = resolution.split("x");
            if (tmp != null && tmp.length >= 2) {
                int widht = Integer.valueOf(tmp[0]);
                int height = Integer.valueOf(tmp[1]);
                mCameraHelper.updateResolution(widht, height);
            }
            mDialog.dismiss();
        });

        builder.setView(rootView);
        mDialog = builder.create();
        mDialog.show();
    }

    // example: {640x480,320x240,etc}
    private List<String> getResolutionList() {
        List<Size> list = mCameraHelper.getSupportedPreviewSizes();
        List<String> resolutions = null;
        if (list != null && list.size() != 0) {
            resolutions = new ArrayList<>();
            for (Size size : list) {
                if (size != null) {
                    resolutions.add(size.width + "x" + size.height);
                }
            }
        }
        return resolutions;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FileUtils.releaseFile();
        // step.4 release uvc camera resources
        if (mCameraHelper != null) {
            mCameraHelper.release();
        }
    }

    private void showShortMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            showShortMsg("取消操作");
        }
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        if (!isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }
    }

    public void onExternalImageAvailable(final byte[] nv21Yuv) {
        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!");
            return;
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                previewHeight = mCameraHelper.getPreviewHeight();
                previewWidth = mCameraHelper.getPreviewWidth();
                rgbBytes = new int[previewWidth * previewHeight];
                onPreviewSizeChosen(new android.util.Size(mCameraHelper.getPreviewWidth(), mCameraHelper.getPreviewHeight()), getScreenOrientation());
            }
        } catch (final Exception e) {
            isProcessingFrame = false;
            LOGGER.e(e, "Exception!");
            return;
        }

        isProcessingFrame = true;
        yuvBytes[0] = nv21Yuv;
        yRowStride = previewWidth;

        imageConverter = () -> ImageUtils.convertYUV420SPToARGB8888(nv21Yuv, previewWidth, previewHeight, rgbBytes);

        postInferenceCallback = () -> {
      /* todo: implement a custom callback buffer for mCameraHelper
              camera.addCallbackBuffer(bytes);*/
            isProcessingFrame = false;
        };
        processImage();
    }
}
