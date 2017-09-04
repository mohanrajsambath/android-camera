package xyz.pinaki.android.camera;

import xyz.pinaki.android.camera.dimension.AspectRatio;

/**
 * Created by pinaki on 8/11/17.
 * Presenter in the MVP pattern
 */
interface CameraPresenter {
    void onCreate();
    void onDestroy();
    boolean onResume();
    void onPause();
    void setPreview(ViewFinderPreview v);
    boolean isCameraOpened();
    void setFacing(CameraAPI.LensFacing l);
    int getFacing();
    void takePicture();
    AspectRatio getAspectRatio();
}
