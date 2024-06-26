package org.telegram.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RawRes;
import androidx.fragment.app.FragmentActivity;

import com.google.android.exoplayer2.util.Log;
import com.google.android.gms.common.util.IOUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.camera.CameraController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class BasePermissionsActivity extends FragmentActivity {
    public final static int REQUEST_CODE_GEOLOCATION = 2,
            REQUEST_CODE_EXTERNAL_STORAGE = 4,
            REQUEST_CODE_ATTACH_CONTACT = 5,
            REQUEST_CODE_CALLS = 7,
            REQUEST_CODE_OPEN_CAMERA = 20,
            REQUEST_CODE_VIDEO_MESSAGE = 150,
            REQUEST_CODE_EXTERNAL_STORAGE_FOR_AVATAR = 151,
            REQUEST_CODE_SIGN_IN_WITH_GOOGLE = 200,
            REQUEST_CODE_PAYMENT_FORM = 210,
            REQUEST_CODE_MEDIA_GEO = 211;

    protected int currentAccount = -1;

    protected boolean checkPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults == null) {
            grantResults = new int[0];
        }
        if (permissions == null) {
            permissions = new String[0];
        }

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (requestCode == 104) {
            if (granted) {
                if (GroupCallActivity.groupCallInstance != null) {
                    GroupCallActivity.groupCallInstance.enableCamera();
                }
            } else {
                showPermissionErrorAlert(R.raw.permission_request_camera, LocaleController.getString("VoipNeedCameraPermission", R.string.VoipNeedCameraPermission));
            }
        } else if (requestCode == REQUEST_CODE_EXTERNAL_STORAGE || requestCode == REQUEST_CODE_EXTERNAL_STORAGE_FOR_AVATAR) {
            if (!granted) {
                showPermissionErrorAlert(R.raw.permission_request_folder, requestCode == REQUEST_CODE_EXTERNAL_STORAGE_FOR_AVATAR ? LocaleController.getString("PermissionNoStorageAvatar", R.string.PermissionNoStorageAvatar) :
                        LocaleController.getString("PermissionStorageWithHint", R.string.PermissionStorageWithHint));
            } else {
                ImageLoader.getInstance().checkMediaPaths();
            }
        } else if (requestCode == REQUEST_CODE_ATTACH_CONTACT) {
            if (!granted) {
                showPermissionErrorAlert(R.raw.permission_request_contacts, LocaleController.getString("PermissionNoContactsSharing", R.string.PermissionNoContactsSharing));
                return false;
            } else {
                ContactsController.getInstance(currentAccount).forceImportContacts();
            }
        } else if (requestCode == 3 || requestCode == REQUEST_CODE_VIDEO_MESSAGE) {
            boolean audioGranted = true;
            boolean cameraGranted = true;
            for (int i = 0, size = Math.min(permissions.length, grantResults.length); i < size; i++) {
                if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                    audioGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                } else if (Manifest.permission.CAMERA.equals(permissions[i])) {
                    cameraGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }
            if (requestCode == REQUEST_CODE_VIDEO_MESSAGE && (!audioGranted || !cameraGranted)) {
                showPermissionErrorAlert(R.raw.permission_request_camera, LocaleController.getString("PermissionNoCameraMicVideo", R.string.PermissionNoCameraMicVideo));
            } else if (!audioGranted) {
                showPermissionErrorAlert(R.raw.permission_request_microphone, LocaleController.getString("PermissionNoAudioWithHint", R.string.PermissionNoAudioWithHint));
            } else if (!cameraGranted) {
                showPermissionErrorAlert(R.raw.permission_request_camera, LocaleController.getString("PermissionNoCameraWithHint", R.string.PermissionNoCameraWithHint));
            } else {
                if (SharedConfig.inappCamera) {
                    CameraController.getInstance().initCamera(null);
                }
                return false;
            }
        } else if (requestCode == 18 || requestCode == 19 || requestCode == REQUEST_CODE_OPEN_CAMERA || requestCode == 22) {
            if (!granted) {
                showPermissionErrorAlert(R.raw.permission_request_camera, LocaleController.getString("PermissionNoCameraWithHint", R.string.PermissionNoCameraWithHint));
            }
        } else if (requestCode == REQUEST_CODE_GEOLOCATION) {
            NotificationCenter.getGlobalInstance().postNotificationName(granted ? NotificationCenter.locationPermissionGranted : NotificationCenter.locationPermissionDenied);
        } else if (requestCode == REQUEST_CODE_MEDIA_GEO) {
            NotificationCenter.getGlobalInstance().postNotificationName(granted ? NotificationCenter.locationPermissionGranted : NotificationCenter.locationPermissionDenied, 1);
        } else if (requestCode == 1001) {
            if (granted) {
                receiveZip();
            }
        }
        return true;
    }

    protected AlertDialog createPermissionErrorAlert(@RawRes int animationId, String message) {
        return new AlertDialog.Builder(this)
                .setTopAnimation(animationId, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground))
                .setMessage(AndroidUtilities.replaceTags(message))
                .setPositiveButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton(LocaleController.getString("ContactsPermissionAlertNotNow", R.string.ContactsPermissionAlertNotNow), null)
                .create();
    }

    private void showPermissionErrorAlert(@RawRes int animationId, String message) {
        createPermissionErrorAlert(animationId, message).show();
    }

    protected void receiveZip() {
        new Thread(() -> {
            synchronized (BasePermissionsActivity.class) {
                try {
                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.telegramDataReceived);
                    });
                    File zipFile = new File(getFilesDir(), "data.zip");
                    if (zipFile.exists()) {
                        zipFile.delete();
                    }
                    InputStream inputStream;
                    if (Build.VERSION.SDK_INT >= 24) {
                        inputStream = getContentResolver().openInputStream(getIntent().getData());
                    } else {
                        inputStream = new FileInputStream(getIntent().getData().getPath());
                    }
                    OutputStream outputStream = openFileOutput("data.zip", Context.MODE_PRIVATE);
                    IOUtils.copyStream(inputStream, outputStream);
                    inputStream.close();
                    outputStream.close();

                    File prefsDir = new File(getFilesDir().getParentFile(), "shared_prefs");
                    if (prefsDir.exists()) {
                        deleteRecursive(prefsDir, false);
                    }

                    byte[] passwordBytes = getIntent().getByteArrayExtra("zipPassword");
                    SecretKey key = new SecretKeySpec(passwordBytes, "AES");

                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
                    cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(passwordBytes));

                    FileInputStream fileStream = new FileInputStream(zipFile);
                    BufferedInputStream bufferedStream = new BufferedInputStream(fileStream);
                    CipherInputStream cipherStream = new CipherInputStream(bufferedStream, cipher);
                    ZipInputStream zipStream = new ZipInputStream(cipherStream);

                    ZipEntry zipEntry = zipStream.getNextEntry();
                    byte[] buffer = new byte[1024];
                    while (zipEntry != null) {
                        File newFile = newFile(getFilesDir(), zipEntry);
                        if (zipEntry.isDirectory()) {
                            if (!newFile.isDirectory() && !newFile.mkdirs()) {
                                throw new IOException("Failed to create directory " + newFile);
                            }
                        } else {
                            File parent = newFile.getParentFile();
                            if (!parent.isDirectory() && !parent.mkdirs()) {
                                throw new IOException("Failed to create directory " + parent);
                            }

                            FileOutputStream fos = new FileOutputStream(newFile);
                            int len;
                            while ((len = zipStream.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                            fos.close();
                        }
                        zipEntry = zipStream.getNextEntry();
                    }
                    zipStream.close();
                    new File(getFilesDir(), "updater_files_copied").createNewFile();
                    if (zipFile.exists()) {
                        zipFile.delete();
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (Build.VERSION.SDK_INT < 24) {
                            File dataFile = new File(getIntent().getData().getPath());
                            File copiedFile = new File(dataFile.getParentFile(), "copied");
                            try {
                                copiedFile.createNewFile();
                            } catch (Exception ignored) {
                            }
                        }

                        Intent data = new Intent();
                        data.putExtra("copied", true);
                        data.putExtra("packageName", getPackageName());
                        setResult(RESULT_OK, data);

                        finish();
                        android.os.Process.killProcess(android.os.Process.myPid());
                    });
                } catch (Exception ex) {
                    Log.e("BasePermissionActivity", "Error", ex);
                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.telegramDataReceivingError);
                        Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        }).start();
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    void deleteRecursive(File fileOrDirectory, boolean deleteThis) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child, true);
            }
        }

        if (deleteThis) {
            fileOrDirectory.delete();
        }
    }
}
