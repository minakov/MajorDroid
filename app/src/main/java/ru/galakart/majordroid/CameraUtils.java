package ru.galakart.majordroid;

import android.hardware.Camera;
import android.support.annotation.Nullable;
import android.util.Log;

public final class CameraUtils {
    private static final String TAG = CameraUtils.class.getSimpleName();

    private CameraUtils() {
    }

    @Nullable
    static Integer getBackFacingCameraId() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
            try {
                final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                final int count = Camera.getNumberOfCameras();
                for (int i = 0; i < count; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        return i;
                    }
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
            }
        }
        return null;
    }

    @Nullable
    static Integer getFrontFacingCameraId() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
            try {
                final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        return i;
                    }
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
            }
        }
        return null;
    }
}
