package com.example.atlasnaviaidetection;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.location.Location;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import timber.log.Timber;


public class VideoRecorderFragment extends Fragment implements View.OnClickListener, TextureView.SurfaceTextureListener {

    public static final String TAG = VideoRecorderFragment.class.getSimpleName();
    private static final String DIRECTORY_ATLAS_IMAGES = "/AtlasNavi/images/";
    private static final int VIDEO_INTERVAL = 180000;
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1888;
    public static SurfaceTexture surface;
    public MediaRecorder recorder;
    public String fileName;
    public Camera camera;
    public OnSurfaceCameraClicked surfaceListener;
    private View view;
    private Context context;

    public VideoRecorderFragment() {
    }

    public VideoRecorderFragment(Context context) {
        this.context = context;
    }

    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, Camera camera) {
        Camera.CameraInfo info =
                new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    public void setOnSurfaceCameraClicked(OnSurfaceCameraClicked surfaceListener) {
        this.surfaceListener = surfaceListener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_video_recorder, container, false);
        myTexture = view.findViewById(R.id.camera_view);
        myTexture.setSurfaceTextureListener(this);
        myTexture.setOnClickListener(this);

        return view;
    }

    private TextureView myTexture;

    @Override
    public void onResume() {
        super.onResume();
    }

    public void onClick(View v) {
        if (surfaceListener != null)
            surfaceListener.onSurfaceCameraClicked();
    }

    public void startRecording() {
        startRecorderService();
    }

    public void stopRecording() {
        stopRecorderService();
    }

    private void startRecorderService() {
        try {
            Intent intent = new Intent(this.context, RecorderService.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("activeTripId", "100");
            this.context.startService(intent);
        } catch (IllegalStateException ex) {
            Log.w(TAG, "Can't start account fetcher service.", ex);
        }
    }

    private void stopRecorderService() {
        this.context.stopService(new Intent(this.context, RecorderService.class));
    }

    public void setCameraPreviewOrientation(Activity activity) {
        try {
            camera = Camera.open();
        } catch (RuntimeException e) {
            System.err.println(e);
            return;
        }
        Camera.Parameters param;
        param = camera.getParameters();
        if (this.context.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            param.set("orientation", "portrait");
            setCameraDisplayOrientation(activity, 1, camera);
        }
        camera.setParameters(param);
        try {
            camera.setPreviewTexture(surface);
            startPreview();
        } catch (Exception e) {
            Log.e(TAG, "Exception during setting preview holder", e);
        }
    }

    public void startPreview() {
        try {
            camera.startPreview();
        } catch (Exception e) {
            Log.e(TAG, "Exception during preview start", e);
        }
    }

    public void stopPreview() {
        try {
            camera.stopPreview();
        } catch (Exception e) {
            Log.e(TAG, "Exception during preview stop", e);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.d("yyyy", "onDestroy VideoRecorderFr");
        super.onDestroy();
    }

    public void savePicture(boolean isRecording) {
        if (!isRecording) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent,
                        CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
            } else {

                boolean cameraWasOpen = camera != null;
                if (!cameraWasOpen) {
                    setCameraPreviewOrientation(getActivity());
                }

                try {
                    camera.takePicture(
                            null, null, null, (data, unused) -> {
                                try {
                                    String filepath = Environment.getExternalStorageDirectory().getPath();
                                    File imgDirectory = new File(filepath + DIRECTORY_ATLAS_IMAGES);

                                    if (!imgDirectory.exists()) {
                                        imgDirectory.mkdirs();
                                    }

//                                    File picPath = new File(
//                                            imgDirectory.getPath() + "/"
//                                                    + System.currentTimeMillis()
//                                                    + UVCCameraHelper.SUFFIX_JPEG
//                                    );

//                                    if (!picPath.exists()) {
//                                        try {
//                                            picPath.createNewFile();
//                                        } catch (IOException e) {
//                                            Timber.tag(TAG).e(e, "Failed to save picture");
//                                            Snackbar.make(
//                                                    view,
//                                                    R.string.failed_to_save_photo,
//                                                    Snackbar.LENGTH_LONG
//                                            ).show();
//                                        }
//                                    }

//                                    try (FileOutputStream outputStream = new FileOutputStream(picPath)) {
//                                        outputStream.write(data);
//                                        Snackbar.make(
//                                                view,
//                                                getString(R.string.successfully_saved_photo),
//                                                Snackbar.LENGTH_LONG
//                                        ).show();
//                                    } catch (IOException e) {
//                                        Timber.tag(TAG).e(e, "Failed to save picture");
//                                        Snackbar.make(
//                                                view,
//                                                R.string.failed_to_save_photo,
//                                                Snackbar.LENGTH_LONG
//                                        ).show();
//                                    }
                                } finally {
                                    if (!cameraWasOpen) {
                                        this.camera.stopPreview();
                                        this.camera.release();
                                        this.camera = null;
                                    }
                                }
                            }
                    );
                } catch (Exception e) {
                    Timber.tag(TAG).e(e, "Failed to save picture");
                    Snackbar.make(
                            view,
                            R.string.photo_action_not_supported,
                            Snackbar.LENGTH_LONG
                    ).show();
                }
            }
        } else {
            camera.lock();
            Camera.Parameters params = camera.getParameters();
            Camera.Size previewSize = params.getPreviewSize();
            Bitmap bitmap = myTexture.getBitmap(previewSize.height, previewSize.width);
            String fileName = System.currentTimeMillis() + ".png";

            try {
                saveImages(getContext(), bitmap, fileName);
                Snackbar.make(
                        view,
                        R.string.successfully_saved_photo,
                        Snackbar.LENGTH_LONG
                ).show();
            } catch (IOException ex) {
                Timber.tag(TAG).e(ex, "Failed to save picture");
                Snackbar.make(
                        view,
                        "Failed to save photo",
                        Snackbar.LENGTH_LONG
                ).show();
            }
        }
    }

    private static Uri saveImages(Context mContext, Bitmap bitmap, @NonNull String name) throws IOException {

        OutputStream fos;
        File image;
        Uri imageUri = null;
        String imagesDir;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = mContext.getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM" + DIRECTORY_ATLAS_IMAGES);
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            fos = resolver.openOutputStream(imageUri);
        } else {
            imagesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM).toString() + File.separator + DIRECTORY_ATLAS_IMAGES;

            File file = new File(imagesDir);

            if (!file.exists()) {
                file.mkdir();
            }

            image = new File(imagesDir, name + ".png");
            fos = new FileOutputStream(image);
        }

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.flush();
        fos.close();
        return imageUri;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Bitmap bmp = (Bitmap) data.getExtras().get("data");
                    getImageUri(getActivity(), bmp);
                    /** suspended */
//                    uploadDetectionPicture(
//                            bmp,
//                            getContext(),
//                            getTripsActivity().getHomeManager().getCurrentLocation(),
//                            "traffic"
//                    );
                }
            }
        } catch (Exception e) {
            Toast.makeText(this.getActivity(), e + "Something went wrong", Toast.LENGTH_LONG).show();
        }
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }

    private void uploadDetectionPicture(Bitmap bitmap, Context context, Location location, String detection) {
//        getProxy()
//                .uploadFile(bitmap, context, location, detection, 0.0, "", true)
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                        response -> Log.d("yyyy", "uploaded image from internal camera"),
//                        error -> {
//                            Log.d("yyyy", "err upload file: " + error.getMessage());
//                            Timber.e(error, "error on uploading file");
//                        }
//                );
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        this.surface = surface;
        setCameraPreviewOrientation(getActivity());
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        try {
            if (!getActivity().getSupportFragmentManager().isStateSaved()) {
                getActivity().getSupportFragmentManager().beginTransaction().remove(this).commit();
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }

    public interface OnSurfaceCameraClicked {
        void onSurfaceCameraClicked();

        void onVideoCameraStopClicked();
    }
}
