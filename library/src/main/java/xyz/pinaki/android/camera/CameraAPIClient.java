package xyz.pinaki.android.camera;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;

import xyz.pinaki.android.camera.dimension.AspectRatio;

/**
 * Created by pinaki on 10/8/17.
 */

public final class CameraAPIClient {
    private static String TAG = CameraAPIClient.class.getSimpleName();
    private final AppCompatActivity activity;
    private final CameraAPI.PreviewType previewType;
    private final AspectRatio aspectRatio;
    private final CameraAPI.LensFacing lensFacing;
    private final boolean useDeprecatedCamera;
    private final CameraAPI.FlashStatus flashStatus;
    private CameraFragment cameraView;
    private boolean shouldFixOrientation = true;

    private CameraAPIClient(Builder builder) {
        activity = builder.activity;
        previewType = builder.previewType;
        aspectRatio = builder.aspectRatio;
        lensFacing = builder.lensFacing;
        useDeprecatedCamera = builder.useDeprecatedCamera;
        flashStatus = builder.flashStatus;
    }

    public interface Callback {
        void onCameraOpened();
        void onAspectRatioAvailable();
        void onCameraClosed();
        void onPhotoTaken(byte[] data);
        void onBitmapProcessed(Bitmap bitmap);
    }

    // interface that might be useful when we have an API to trigger image capture.
    public interface PhotoTakenCallback {
        void onPhotoTaken(byte[] data);
        void onBitmapProcessed(Bitmap bitmap);
    }

    public void start(int containerID, Callback callback) {
        if (shouldFixOrientation) {
            fixOrientation(activity);
        }
        cameraView = new CameraFragment();
        if (useDeprecatedCamera || !isCamera2Supported(activity)) {
            // use dep camera api
            cameraView.setPresenter(new Camera1Presenter(activity));
        } else {
            // use camera 2 api
            cameraView.setPresenter(new Camera2Presenter(activity));
        }
        cameraView.setPreviewType(previewType);
        cameraView.setCallback(callback);
        activity.getSupportFragmentManager().beginTransaction().
                replace(containerID, cameraView, "CameraFragment").commit();
    }

    public void stop() {
        cameraView.setCallback(null);
        cameraView = null;
    }

    private static void fixOrientation(AppCompatActivity activity) {
        int orientation = activity.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private static boolean isCamera2Supported(Context context) {
        if ( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            return false;
        }
        CameraManager cameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager.getCameraIdList().length == 0) {
                return false;
            }
            for (String cameraIdStr : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraIdStr);
                Integer hardwareSupport = cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if ( hardwareSupport == null ) {
                    return false;
                }
                if (hardwareSupport == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY ) {
                    return false;
                }
                if (hardwareSupport == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
                    return false;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static class Builder {
        private final AppCompatActivity activity;
        private CameraAPI.PreviewType previewType = CameraAPI.PreviewType.SURFACE_VIEW;
        private AspectRatio aspectRatio;
        private CameraAPI.LensFacing lensFacing = CameraAPI.LensFacing.BACK;
        private boolean useDeprecatedCamera = false;
        private CameraAPI.FlashStatus flashStatus = CameraAPI.FlashStatus.OFF;
        // private int maxWidth; // max width of smaller dimension
        public Builder(AppCompatActivity a) {
            activity = a;
        }
        public Builder previewType(CameraAPI.PreviewType p) {
            previewType = p;
            return this;
        }
        public Builder lensFacing(CameraAPI.LensFacing f) {
            lensFacing = f;
            return this;
        }
        public Builder aspectRatio(AspectRatio a) {
            aspectRatio = a;
            return this;
        }
        public Builder useDeprecatedCamera(boolean b) {
            useDeprecatedCamera = b;
            return this;
        }
        public Builder flashStatus(CameraAPI.FlashStatus f) {
            flashStatus = f;
            return this;
        }
        public CameraAPIClient build() {
            return new CameraAPIClient(this);
        }
    }

}
