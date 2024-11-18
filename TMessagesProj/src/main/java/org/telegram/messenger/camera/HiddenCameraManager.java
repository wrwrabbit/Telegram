package org.telegram.messenger.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.PartisanLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


public class HiddenCameraManager implements Camera.PictureCallback, Camera.PreviewCallback, Camera.AutoFocusCallback {
    private final Context context;
    private Camera camera;
    private SurfaceTexture surface;
    private Consumer<String> onPhotoTaken;
    private boolean soundWasMuted;

    public HiddenCameraManager(Context context) {
        this.context = context;
    }

    public void takePhoto(boolean front, Consumer<String> onPhotoTaken) {
        if (front && isCameraAvailable(Camera.CameraInfo.CAMERA_FACING_FRONT)) {
            this.onPhotoTaken = onPhotoTaken;
            initCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
        } else if (!front && isCameraAvailable(Camera.CameraInfo.CAMERA_FACING_BACK)) {
            this.onPhotoTaken = onPhotoTaken;
            initCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
        }
    }

    private boolean isCameraAvailable(int facing) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            boolean result = false;
            if (context != null && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                int numberOfCameras = Camera.getNumberOfCameras();
                for (int i = 0; i < numberOfCameras; i++) {
                    Camera.CameraInfo info = new Camera.CameraInfo();
                    Camera.getCameraInfo(i, info);
                    if (info.facing == facing) {
                        result = true;
                        break;
                    }
                }
            }
            return result;
        } catch (Exception e) {
            PartisanLog.handleException(e);
            return false;
        }
    }

    private void initCamera(int facing) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                camera = Camera.open(facing);
                handler.post(() -> {
                    try {
                        if (camera != null) {
                            surface = new SurfaceTexture(123);
                            Camera.CameraInfo info = new Camera.CameraInfo();
                            Camera.getCameraInfo(facing, info);
                            camera.setPreviewTexture(surface);
                            Camera.Parameters params = camera.getParameters();
                            params.setRotation((info.orientation + 360) % 360);
                            if (autoFocusSupported(camera)) {
                                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                            }
                            Camera.Size size = params.getSupportedPictureSizes().stream().max(
                                    Comparator.comparingLong(a -> (long) a.height * (long) a.width)
                            ).get();
                            params.setPictureSize(size.width, size.height);

                            camera.setParameters(params);
                            camera.setPreviewCallback(HiddenCameraManager.this);
                            camera.startPreview();
                            muteSound();
                        }
                    } catch (IOException e) {
                        PartisanLog.handleException(e);
                        releaseCamera();
                    }
                });
            } catch (Exception e) {
                PartisanLog.handleException(e);
            }
        });
    }

    private boolean autoFocusSupported(Camera camera) {
        if (camera != null) {
            Camera.Parameters params = camera.getParameters();
            List<String> focusModes = params.getSupportedFocusModes();
            return focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        return false;
    }

    private void muteSound() {
        if (context != null && SharedConfig.takePhotoMuteAudio) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                soundWasMuted = audioManager.isStreamMute(AudioManager.STREAM_SYSTEM);
                if (!soundWasMuted) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0);
                }
            } else {
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
            }
        }
    }

    private void unMuteSound() {
        if (context != null && SharedConfig.takePhotoMuteAudio) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!soundWasMuted) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0);
                }
            } else {
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
            }
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.release();
            surface.release();
            camera = null;
            surface = null;
        }
        unMuteSound();
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        try {
            camera.setPreviewCallback(null);
            camera.takePicture(null, null, this);
        } catch (Exception e) {
            PartisanLog.handleException(e);
            releaseCamera();
        }
    }

    @Override
    public void onAutoFocus(boolean b, Camera camera) {
        if (camera != null) {
            try {
                camera.takePicture(null, null, this);
                this.camera.autoFocus(null);
            } catch (Exception e) {
                PartisanLog.handleException(e);
                releaseCamera();
            }
        }
    }

    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        String filePath = savePicture(bytes);
        releaseCamera();
        onPhotoTaken.accept(filePath);
    }

    private String savePicture(byte[] bytes) {
        try {
            if (bytes == null) {
                return null;
            }
            File pictureFileDir = ApplicationLoader.getFilesDirFixed();
            if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {
                return null;
            }
            SimpleDateFormat dateFormat = new SimpleDateFormat("(hh:mm:ss)(dd.MM.yyyy)", Locale.US);
            String date = dateFormat.format(new Date());
            String fileName = "hidden photo " + date + ".jpg";

            File fullPath = new File(pictureFileDir, fileName);
            FileOutputStream fos = new FileOutputStream(fullPath);
            fos.write(bytes);
            fos.close();
            return fileName;
        } catch (Exception e) {
            PartisanLog.handleException(e);
            return null;
        }
    }
}
