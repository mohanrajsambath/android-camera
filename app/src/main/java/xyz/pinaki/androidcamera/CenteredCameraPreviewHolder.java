package xyz.pinaki.androidcamera;

/**
 * Created by pinaki on 9/14/16.
 */

import android.app.Activity;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.util.List;

/**
 * A simple wrapper around a Camera and a SurfaceView that renders a centered preview of the Camera
 * to the surface. We need to center the SurfaceView because not all devices have cameras that
 * support preview sizes at the same aspect ratio as the device's display.
 */
@SuppressWarnings("deprecation")
public class CenteredCameraPreviewHolder extends ViewGroup implements SurfaceHolder.Callback {
    private final String TAG = CameraFragment.class.getSimpleName();
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    Size previewSize;
    Size pictureSize;
    Camera camera;
    Activity activity;
    int cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private int displayOrientation = 0;
    private int layoutOrientation = 0;
    // This flag is required to handle the case when the capture icon is tapped twice simultaneously.
    // Without the flag capture will be invoke again before the previous onPictureTaken call completed.
    // resulting in "RuntimeException takePicture failed" in android.hardware.Camera.takePicture(Camera.java:1436)
    // http://stackoverflow.com/questions/21723557/java-lang-runtimeexception-takepicture-failed
    private boolean safeToTakePicture = false;

    CenteredCameraPreviewHolder(Activity activity) {
        super(activity);
        this.activity = activity;
        addSurfaceView();
    }

    private void addSurfaceView() {
        if (surfaceView != null) {
            removeView(surfaceView);
            surfaceView = null;
        }
        surfaceView = new SurfaceView(activity);
        addView(surfaceView);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void unsetCamera() {
        Log.i(TAG, "unsetCamera");
        camera = null;
        cameraId = 0;
    }

    public void setCamera(Camera camera, int cameraId) {
        Log.i(TAG, "setCamera");
        this.camera = camera;
        this.cameraId = cameraId;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);
        Log.i(TAG, "onMeasure, width:" + width + ", height:" + height);
        if (camera != null) {
            try {
                Camera.Parameters parameters = camera.getParameters();
                List<Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
                List<Size> supportedPictureSize = parameters.getSupportedPictureSizes();
                previewSize = getOptimalPreviewSize(supportedPreviewSizes, width, height);
                pictureSize = getOptimalPreviewSize(supportedPictureSize, width, height);
                Log.i(TAG, "getOptimalPreviewSize: width:" + previewSize.width + ", height:" + previewSize.height);
                Log.i(TAG, "getOptimalPictureSize: width:" + pictureSize.width + ", height:" + pictureSize.height);
            } catch (RuntimeException exception) {
                Log.i(TAG, "RuntimeException caused by getParameters in onMeasure", exception);
            }

        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);
            final int availableWidth = r - l;
            final int availableHeight = b - t;
            int previewWidth = availableWidth;
            int previewHeight = availableHeight;
            if (previewSize != null) {
                if (isPortrait()) {
                    previewWidth = previewSize.height;
                    previewHeight = previewSize.width;
                } else {
                    previewWidth = previewSize.width;
                    previewHeight = previewSize.height;
                }
            }
            Log.i(TAG, "Available width = " + availableWidth + ", height = " + availableHeight);
            Log.i(TAG, "Preview Width = " + previewWidth + ", Height = " + previewHeight);
            float factH = (float) availableHeight  / previewHeight ;
            float factW = (float) availableWidth / previewWidth;
            float fact = factH < factW ? factH : factW;
            int layoutHeight = (int) (previewHeight * fact);
            int layoutWidth = (int) (previewWidth * fact);
            Log.i(TAG, "Final Preview Layout Width = " + layoutWidth + ", Height = " + layoutHeight);
            child.layout( (availableWidth - layoutWidth ) / 2 ,
                    (availableHeight - layoutHeight) / 2,
                    (availableWidth + layoutWidth ) / 2,
                    (availableHeight + layoutHeight) / 2); // left, top, right, bottom
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            if (camera != null) {
                camera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.i(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed");
        // Surface will be destroyed when we return, so stop the preview.
        if (camera != null) {
            stopCameraPreview();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surfaceChanged");
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        if (camera != null) {
            Log.i(TAG, "surfaceChanged " + previewSize.width + ", " + previewSize.height + "|| " +
                    pictureSize.width + ", " + pictureSize.height);
            try { // handle RuntimeException: getParameters failed (empty parameters)
                Camera.Parameters parameters = camera.getParameters();
                if (previewSize!= null) {
                    parameters.setPreviewSize(previewSize.width, previewSize.height);
                }
                if (pictureSize != null) {
                    parameters.setPictureSize(pictureSize.width, pictureSize.height);
                }
                camera.setParameters(parameters);
                configureOrientationParams();
                requestLayout();
                startCameraPreview();
            } catch (RuntimeException exception) {
                Log.i(TAG, "RuntimeException caused by getParameters in surfaceChanged", exception);
            }
        }

    }

    private Size getOptimalPreviewSize(List<Size> sizes, int targetWidth, int targetHeight) {
        final double ASPECT_TOLERANCE = 0.01;
        double targetRatio = (double) targetWidth / targetHeight;
        if (sizes == null) return null;
        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        boolean isPortrait = isPortrait();
        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            int previewWidth = size.width;
            int previewHeight = size.height;
            if (isPortrait) {
                previewWidth = size.height;
                previewHeight = size.width;
            }
            double ratio = (double) previewWidth / previewHeight;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(previewHeight - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(previewHeight - targetHeight);
            }
        }
        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                int previewWidth = size.width;
                int previewHeight = size.height;
                if (isPortrait) {
                    previewWidth = size.height;
                    previewHeight = size.width;
                }
                double curRatio = ((double) previewWidth) / previewHeight;
                double deltaRatio = Math.abs(targetRatio - curRatio);
                if (deltaRatio < minDiff) {
                    optimalSize = size;
                    minDiff = deltaRatio;
                }
            }
        }
        return optimalSize;
    }

    /* package */ synchronized void startCameraPreview() {
        Log.i(TAG, "startCameraPreview");
        if (camera != null ) {
            Log.i(TAG, "startCameraPreview in If Block");
            camera.startPreview();
            this.safeToTakePicture = true;
        }
    }

    /* package */ void stopCameraPreview() {
        if (camera != null) {
            camera.stopPreview();
        }
    }

    private boolean isPortrait() {
        return (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
    }

    // https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
    private void configureOrientationParams() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, cameraInfo);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees  = 0;
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
        int displayOrientation = 0;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (cameraInfo.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (cameraInfo.orientation - degrees + 360) % 360;
        }
        this.displayOrientation = displayOrientation;
        this.layoutOrientation  = degrees;
        Log.i(TAG, "cameraInfo.orientation: " + cameraInfo.orientation + ", getDefaultDisplay.degrees: " + degrees);
        Log.i(TAG, "configureOrientationParams, displayOrientation: " + displayOrientation);
        camera.setDisplayOrientation(displayOrientation);
    }

    /*package*/ final int getDisplayOrientation() {
        return this.displayOrientation;
    }

    /*package*/ final int getLayoutOrientation() {
        return this.layoutOrientation;
    }

    /*package*/ final boolean getSafeToTakePicture() {
        return safeToTakePicture;
    }

    /*package*/ void setSafeToTakePicture(boolean safeToTakePicture) {
        this.safeToTakePicture = safeToTakePicture;
    }
}
