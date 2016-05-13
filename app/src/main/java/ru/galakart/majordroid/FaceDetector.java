package ru.galakart.majordroid;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;

public class FaceDetector {
    private static final String TAG = FaceDetector.class.getSimpleName();
    @NonNull
    private final FaceDetectorImpl impl;

    public FaceDetector(@NonNull final Context context, @NonNull final Listener listener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            impl = new FaceDetectorImplIceCreamSandwich(context, listener);
        } else {
            impl = new FaceDetectorImplBase();
        }
    }

    void start() {
        impl.start();
    }

    void stop() {
        impl.stop();
    }

    void destroy() {
        impl.destroy();
    }

    interface Listener {
        void onFacesDetected(int numberOfFacesDetected);
    }

    interface FaceDetectorImpl {
        void start();

        void stop();

        void destroy();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static final class FaceDetectorImplIceCreamSandwich implements FaceDetectorImpl {
        @NonNull
        private final Context context;
        @NonNull
        private final Listener listener;
        @Nullable
        private Camera camera;
        private boolean working = false;
        private int numberOfFacesDetected = 0;

        private FaceDetectorImplIceCreamSandwich(@NonNull final Context context, @NonNull final Listener listener) {
            this.context = context;
            this.listener = listener;
        }

        @Override
        public void start() {
            if (!Prefs.isFaceDetectionOn(context)) {
                Log.d(TAG, "Face detection disabled");
                stop();
                return;
            }
            if (!working) {
                final Integer cameraId = Prefs.getFaceDetectionCameraId(context);
                Log.d(TAG, "Initiating camera: " + cameraId);
                if (cameraId == null) {
                    return;
                }
                try {
                    camera = Camera.open(cameraId);
                } catch (RuntimeException e) {
                    Log.e(TAG, e.getMessage(), e);
                    return;
                }
                if (camera != null && camera.getParameters().getMaxNumDetectedFaces() > 0) {
                    try {
                        final SurfaceView cameraSurface = new SurfaceView(context);
                        camera.setPreviewDisplay(cameraSurface.getHolder());
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                    camera.setFaceDetectionListener(new Camera.FaceDetectionListener() {
                        @Override
                        public void onFaceDetection(Camera.Face[] faces, Camera camera) {
                            if (faces.length != numberOfFacesDetected) {
                                Log.d(TAG, "Number of faces: " + faces.length);
                                numberOfFacesDetected = faces.length;
                                listener.onFacesDetected(numberOfFacesDetected);
                            }
                        }
                    });
                    camera.startPreview();
                    camera.startFaceDetection();
                    Log.d(TAG, "Face detection started for camera " + Integer.toString(cameraId));
                    working = true;
                } else {
                    Toast.makeText(context, "Face detection is not supported for camera " + Integer.toString(cameraId), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Face detection is not supported for camera " + Integer.toString(cameraId));
                    working = false;
                    if (camera != null) {
                        camera.release();
                    }
                }
            }
        }

        @Override
        public void stop() {
            if (working && camera != null) {
                camera.setFaceDetectionListener(null);
                camera.setErrorCallback(null);
                camera.stopPreview();
                camera.release();
                working = false;
                Log.d(TAG, "Face detection stopped");
            }
        }

        @Override
        public void destroy() {
            stop();
            if (camera != null) {
                camera = null;
            }
        }
    }

    private static final class FaceDetectorImplBase implements FaceDetectorImpl {

        private FaceDetectorImplBase() {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void destroy() {
        }
    }
}
