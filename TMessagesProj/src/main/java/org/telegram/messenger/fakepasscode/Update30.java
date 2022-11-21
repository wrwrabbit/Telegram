package org.telegram.messenger.fakepasscode;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Update30 {
    public interface MakeZipDelegate {
        void makeZipCompleted(File zipFile, byte[] passwordBytes);
        void makeZipFailed(MakeZipFailReason reason);
    }

    public enum MakeZipFailReason {
        UNKNOWN,
        NO_TELEGRAM_APK
    }

    private static class MakeZipException extends Exception {
        public MakeZipFailReason reason;

        public MakeZipException(MakeZipFailReason reason) {
            this.reason = reason;
        }

        public MakeZipException() {
            this.reason = MakeZipFailReason.UNKNOWN;
        }
    }

    public static void makeZip(Activity activity, MakeZipDelegate delegate) {
        try {
            byte[] passwordBytes = new byte[16];
            Utilities.random.nextBytes(passwordBytes);
            File zipFile = makeDataZip(activity, passwordBytes);
            if (zipFile == null) {
                return;
            }
            delegate.makeZipCompleted(zipFile, passwordBytes);
        } catch (MakeZipException e) {
            delegate.makeZipFailed(e.reason);
        } catch (Exception e) {
            delegate.makeZipFailed(MakeZipFailReason.UNKNOWN);
            Log.e("Update30", "Error", e);
        }
    }

    private static Uri fileToUri(File file, Activity activity) {
        if (Build.VERSION.SDK_INT >= 24) {
            return FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
        } else {
            return Uri.fromFile(file);
        }
    }

    private static String buildPath(String path, String file) {
        if (path == null || path.isEmpty()) {
            return file;
        } else {
            return path + "/" + file;
        }
    }

    private static void addDirToZip(ZipOutputStream zos, String path, File dir) throws IOException {
        if (!dir.canRead()) {
            return;
        }

        File[] files = dir.listFiles();
        path = buildPath(path, dir.getName());

        if (files != null) {
            for (File source : files) {
                if (source.isDirectory()) {
                    addDirToZip(zos, path, source);
                } else {
                    addFileToZip(zos, path, source);
                }
            }
        }
    }

    private static void addFileToZip(ZipOutputStream zos, String path, File file) throws IOException {
        if (!file.canRead()) {
            return;
        }

        zos.putNextEntry(new ZipEntry(buildPath(path, file.getName())));

        FileInputStream fis = new FileInputStream(file);

        byte[] buffer = new byte[4092];
        int byteCount;
        while ((byteCount = fis.read(buffer)) != -1) {
            zos.write(buffer, 0, byteCount);
        }

        fis.close();
        zos.closeEntry();
    }

    private static File makeDataZip(Activity activity, byte[] passwordBytes) throws Exception {
        File externalFilesDir = getExternalFilesDir();
        if (externalFilesDir == null) {
            return null;
        }

        File zipFile;
        if (Build.VERSION.SDK_INT >= 24) {
            zipFile = new File(externalFilesDir, "data.zip");
        } else {
            zipFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), "data.zip");
        }
        if (zipFile.exists()) {
            if (!zipFile.delete()) {
                throw new MakeZipException();
            }
        }
        if (!zipFile.createNewFile()) {
            throw new MakeZipException();
        }

        SecretKey key = new SecretKeySpec(passwordBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(passwordBytes));

        FileOutputStream fileStream = new FileOutputStream(zipFile);
        BufferedOutputStream bufferedStream = new BufferedOutputStream(fileStream);
        CipherOutputStream cipherStream = new CipherOutputStream(bufferedStream, cipher);
        ZipOutputStream zipStream = new ZipOutputStream(cipherStream);

        File filesDir = activity.getFilesDir();
        addDirToZip(zipStream, "", filesDir);
        addDirToZip(zipStream, "", new File(filesDir.getParentFile(), "shared_prefs"));
        zipStream.close();
        return zipFile;
    }

    public static void deleteDataZip() {
        File zipFile;
        if (Build.VERSION.SDK_INT >= 24) {
            File externalFilesDir = getExternalFilesDir();
            if (externalFilesDir == null) {
                return;
            }
            zipFile = new File(externalFilesDir, "data.zip");
        } else {
            zipFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), "data.zip");
        }
        if (zipFile.exists()) {
            zipFile.delete();
        }
    }

    private static File getExternalFilesDir() {
        File externalFilesDir = ApplicationLoader.applicationContext.getExternalFilesDir(null);
        if (!externalFilesDir.exists() && !externalFilesDir.mkdirs()) {
            return null;
        }
        return externalFilesDir;
    }

    public static void startNewTelegram(Activity activity, File zipFile, byte[] passwordBytes) {
        Intent searchIntent = new Intent(Intent.ACTION_MAIN);
        searchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infoList = activity.getPackageManager().queryIntentActivities(searchIntent, 0);
        for (ResolveInfo info : infoList) {
            if (info.activityInfo.packageName.equals("org.telegram.messenger.web")) {
                disableConnection();

                Intent intent = new Intent(Intent.ACTION_MAIN);
                try {
                    intent.setClassName(info.activityInfo.applicationInfo.packageName, info.activityInfo.name);
                    intent.setDataAndType(fileToUri(zipFile, activity), "application/zip");
                    intent.putExtra("zipPassword", passwordBytes);
                    intent.putExtra("packageName", activity.getPackageName());
                    intent.putExtra("language", LocaleController.getInstance().getLanguageOverride());
                    intent.putExtra("fromOldTelegram", true);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    activity.startActivityForResult(intent, 20202020);
                } catch (Exception e) {
                    Log.e("Update30", "copyUpdaterFileFromAssets error ", e);
                }
            }
        }
    }

    public static void disableConnection() {
        SharedConfig.ProxyInfo proxyInfo = new SharedConfig.ProxyInfo("127.0.0.1", 1080, "", "", "");
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        SharedConfig.addProxy(proxyInfo);
        SharedConfig.currentProxy = proxyInfo;
        editor.putBoolean("proxy_enabled", true);

        editor.putString("proxy_ip", proxyInfo.address);
        editor.putString("proxy_pass", proxyInfo.password);
        editor.putString("proxy_user", proxyInfo.username);
        editor.putInt("proxy_port", proxyInfo.port);
        editor.putString("proxy_secret", proxyInfo.secret);
        ConnectionsManager.setProxySettings(true, proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret);
        editor.commit();

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    public static void waitForTelegramInstallation(Activity activity, Runnable onInstalled) {
        Utilities.globalQueue.postRunnable(new NewStandaloneTelegramInstallationWaiter(activity, onInstalled), 100);
    }

    private static class NewStandaloneTelegramInstallationWaiter implements Runnable {
        private int iteration;
        private final Activity activity;
        private final Runnable onInstalled;

        public NewStandaloneTelegramInstallationWaiter(Activity activity, Runnable onInstalled) {
            super();
            this.activity = activity;
            this.onInstalled = onInstalled;
        }

        @Override
        public void run() {
            iteration++;
            if (iteration >= 100) {
                Toast.makeText(activity, "Telegram was not installed", Toast.LENGTH_LONG).show();
            } else if (isNewStandaloneTelegramInstalled(activity)) {
                onInstalled.run();
            } else {
                Utilities.globalQueue.postRunnable(this, 100);
            }
        }
    }

    public static boolean isOldStandaloneTelegramInstalled(Activity activity) {
        PackageInfo packageInfo = getStandaloneTelegramPackageInfo(activity);
        return packageInfo != null && !isStandalone30(packageInfo);
    }

    public static boolean isNewStandaloneTelegramInstalled(Activity activity) {
        PackageInfo packageInfo = getStandaloneTelegramPackageInfo(activity);
        return isStandalone30(packageInfo);
    }

    private static PackageInfo getStandaloneTelegramPackageInfo(Activity activity) {
        try {
            PackageManager pm = activity.getPackageManager();
            if (Build.VERSION.SDK_INT >= 28) {
                return pm.getPackageInfo("org.telegram.messenger.web", PackageManager.GET_SIGNING_CERTIFICATES);
            } else {
                return pm.getPackageInfo("org.telegram.messenger.web", PackageManager.GET_SIGNATURES);
            }
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static boolean isStandalone30(PackageInfo packageInfo) {
        if (packageInfo != null) {
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                signatures = packageInfo.signingInfo.getApkContentsSigners();
            } else {
                signatures = packageInfo.signatures;
            }
            if (signatures != null) {
                for (final Signature sig : signatures) {
                    try {
                        MessageDigest hash = MessageDigest.getInstance("SHA-1");
                        String thumbprint = Utilities.bytesToHex(hash.digest(sig.toByteArray()));
                        return thumbprint.equalsIgnoreCase("06480D1C49ADA4A50D7BC57B097271D68AE7707E");
                    } catch (NoSuchAlgorithmException ignored) {
                    }
                }
            }
        }
        return false;
    }

    public static void installStandaloneTelegram(Activity activity, File standaloneTelegramApk) {
        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", standaloneTelegramApk);
        } else {
            uri = Uri.fromFile(standaloneTelegramApk);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
    }
}
