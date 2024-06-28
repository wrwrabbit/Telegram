package org.telegram.messenger.partisan.appmigration;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.ConnectionsManager;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AppMigrator {
    private static final String PTG_30_SIGNATURE = "06480D1C49ADA4A50D7BC57B097271D68AE7707E";
    private static final String PTG_30_DEBUG_SIGNATURE = "B134DF916190F59F832BE4E1DE8354DC23444059";
    private static final List<String> PTG_PACKAGE_NAMES = Arrays.asList(
            "org.telegram.messenger.alpha",
            "org.telegram.messenger.beta",
            "org.telegram.messenger.web",
            "org.telegram.messenger"
    );
    private static Step step;
    private static Long maxCancelledInstallationDate;
    private static String migratedPackageName;
    private static Long migratedDate;
    private static File zipFile;
    private static byte[] passwordBytes;
    private static boolean receivingZip = false;

    public interface MakeZipDelegate {
        void makeZipCompleted();
        void makeZipFailed();
    }

    private static class MakeZipException extends Exception {
    }

    public static void makeZip(Activity activity, MakeZipDelegate delegate) {
        try {
            byte[] passwordBytes = new byte[16];
            Utilities.random.nextBytes(passwordBytes);
            File zipFile = makeDataZip(activity, passwordBytes);
            if (zipFile == null) {
                return;
            }
            AppMigrator.zipFile = zipFile;
            AppMigrator.passwordBytes = passwordBytes;
            delegate.makeZipCompleted();
        } catch (MakeZipException e) {
            delegate.makeZipFailed();
        } catch (Exception e) {
            delegate.makeZipFailed();
            PartisanLog.e("MoveDataToOtherPtg", e);
        }
    }

    private static Uri fileToUri(File file, Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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

    public static void deleteZipFile() {
        File zipFile;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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

    public static void startNewTelegram(Activity activity) {
        Intent searchIntent = new Intent(Intent.ACTION_MAIN);
        searchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infoList = activity.getPackageManager().queryIntentActivities(searchIntent, 0);
        PackageInfo newestPackage = AppMigrator.getNewestUncheckedPtgPackage(activity, false);
        for (ResolveInfo info : infoList) {
            if (info.activityInfo.packageName.equals(newestPackage.packageName)) {
                disableConnection();

                Intent intent = new Intent(Intent.ACTION_MAIN);
                try {
                    intent.setClassName(info.activityInfo.applicationInfo.packageName, info.activityInfo.name);
                    intent.setDataAndType(fileToUri(zipFile, activity), "application/zip");
                    intent.putExtra("zipPassword", passwordBytes);
                    intent.putExtra("packageName", activity.getPackageName());
                    intent.putExtra("language", LocaleController.getInstance().getLanguageOverride());
                    intent.putExtra("fromOtherPtg", true);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    activity.startActivityForResult(intent, 20202020);
                } catch (Exception e) {
                    PartisanLog.e("MoveDataToOtherPtg", e);
                }
            }
        }
    }

    public static boolean isConnectionDisabled() {
        return SharedConfig.isProxyEnabled()
                && isProxyForDisablingConnection(SharedConfig.currentProxy);
    }

    public static void enableConnection() {
        for (SharedConfig.ProxyInfo proxyInfo : new ArrayList<>(SharedConfig.proxyList)) {
            if (isProxyForDisablingConnection(proxyInfo)) {
                SharedConfig.deleteProxy(proxyInfo);
            }
        }
    }

    private static boolean isProxyForDisablingConnection(SharedConfig.ProxyInfo proxyInfo) {
        return "127.0.0.1".equals(proxyInfo.address) && proxyInfo.port == -1;
    }

    public static void disableConnection() {
        SharedConfig.ProxyInfo proxyInfo = new SharedConfig.ProxyInfo("127.0.0.1", -1, "", "", "");
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

    public static boolean isPtgPackageName(String packageName) {
        return packageName != null && PTG_PACKAGE_NAMES.contains(packageName);
    }

    public static boolean isNewerPtgInstalled(Context context, boolean checkCancelledDate) {
        return getNewestUncheckedPtgPackage(context, checkCancelledDate) != null;
    }

    private static PackageInfo getNewestUncheckedPtgPackage(Context context, boolean checkCancelledDate) {
        return getOtherPartisanTelegramPackages(context).stream()
                .filter(p -> needCheckPackage(p, context, checkCancelledDate))
                .max(Comparator.comparing(p -> p.firstInstallTime))
                .orElse(null);
    }

    private static boolean needCheckPackage(PackageInfo packageInfo, Context context, boolean checkCancelledDate) {
        PackageInfo selfPackage = getSelfPackageInfo(context);
        long checkTimeMin = checkCancelledDate
                ? Math.max(selfPackage.firstInstallTime, getMaxCancelledInstallationDate())
                : selfPackage.firstInstallTime;
        return packageInfo.firstInstallTime > checkTimeMin;
    }

    public static boolean isOlderPtgInstalled(Context context) {
        return getOldestOtherPtgPackage(context) != null;
    }

    private static PackageInfo getOldestOtherPtgPackage(Context context) {
        PackageInfo selfPackage = getSelfPackageInfo(context);
        return getOtherPartisanTelegramPackages(context).stream()
                .filter(p -> p.firstInstallTime < selfPackage.firstInstallTime)
                .min(Comparator.comparing(p -> p.firstInstallTime))
                .orElse(null);
    }

    public static boolean isMigratedPackageInstalled(Context context) {
        return getMigratedPackage(context) != null;
    }

    private static PackageInfo getMigratedPackage(Context context) {
        String packageName = getMigratedPackageName();
        long migratedDate = getMigratedDate();
        if (packageName == null || migratedDate == 0) {
            return null;
        }
        return getOtherPartisanTelegramPackages(context).stream()
                .filter(p -> packageName.equals(p.packageName) && p.firstInstallTime < migratedDate)
                .findAny()
                .orElse(null);
    }

    private static PackageInfo getSelfPackageInfo(Context context) {
        if (context == null) {
            return null;
        }
        return getPackageInfoWithCertificates(context, context.getPackageName());
    }

    private static List<PackageInfo> getOtherPartisanTelegramPackages(Context context) {
        if (context == null) {
            return Collections.emptyList();
        }
        List<PackageInfo> result = new ArrayList<>();
        for (String packageName : PTG_PACKAGE_NAMES) {
            PackageInfo packageInfo = getPackageInfoWithCertificates(context, packageName);
            if (packageInfo == null || Objects.equals(packageInfo.packageName, context.getPackageName())) {
                continue;
            }
            if (isPtg30Signature(packageInfo)) {
                result.add(packageInfo);
            }
        }
        return result;
    }

    private static boolean isPtg30Signature(PackageInfo packageInfo) {
        Signature[] signatures = getSignatures(packageInfo);
        if (signatures == null) {
            return false;
        }
        for (final Signature sig : signatures) {
            try {
                MessageDigest hash = MessageDigest.getInstance("SHA-1");
                String thumbprint = Utilities.bytesToHex(hash.digest(sig.toByteArray()));
                if (thumbprint.equalsIgnoreCase(PTG_30_SIGNATURE) || thumbprint.equalsIgnoreCase(PTG_30_DEBUG_SIGNATURE)) {
                    return true;
                }
            } catch (NoSuchAlgorithmException ignored) {
            }
        }
        return false;
    }

    private static Signature[] getSignatures(PackageInfo packageInfo) {
        if (packageInfo == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.signingInfo.getApkContentsSigners();
        } else {
            return packageInfo.signatures;
        }
    }

    private static PackageInfo getPackageInfoWithCertificates(Context context, String packageName) {
        int flags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            flags = PackageManager.GET_SIGNING_CERTIFICATES;
        } else {
            flags = PackageManager.GET_SIGNATURES;
        }
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    public static synchronized void setStep(Step step) {
        Step oldStep = getStep(); // initialize old step if not initialized
        AppMigrator.step = step;
        if (oldStep.simplify() != step.simplify()) {
            getPrefs().edit()
                    .putString("ptgMigrationStep", step.simplify().toString())
                    .apply();
        }
    }

    public static synchronized Step getStep() {
        if (step == null) {
            String stepStr = getPrefs().getString("ptgMigrationStep", Step.NOT_STARTED.toString());
            step = Step.valueOf(stepStr);
        }
        return step;
    }

    public static boolean isMigrationStarted() {
        return getStep() != Step.NOT_STARTED;
    }

    public static boolean checkMigrationNeedToResume(Context context) {
        if (!isMigrationStarted()) {
            return false;
        }
        if (!TextUtils.isEmpty(getMigratedPackageName()) && !isMigratedPackageInstalled(context)) {
            setStep(Step.NOT_STARTED);
            enableConnection();
            resetMigrationFinished();
            return false;
        } else {
            return true;
        }
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
    }

    public static void uninstallSelf(Context context) {
        if (context == null) {
            return;
        }
        deleteZipFile();
        // we will show the system app settings if the app doesn't have the permission
        boolean deletionAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.REQUEST_DELETE_PACKAGES) == PackageManager.PERMISSION_GRANTED;
        Intent intent = new Intent(deletionAllowed ? Intent.ACTION_DELETE : Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        context.startActivity(intent);
    }

    public static boolean allowStartNewTelegram() {
        return zipFile != null && zipFile.exists();
    }

    private static synchronized long getMaxCancelledInstallationDate() {
        if (maxCancelledInstallationDate == null) {
            maxCancelledInstallationDate = getPrefs()
                    .getLong("ptgMigrationMaxCancelledInstallationDate", 0);
        }
        return maxCancelledInstallationDate;
    }

    public static void updateMaxCancelledInstallationDate() {
        maxCancelledInstallationDate = System.currentTimeMillis();
        getPrefs().edit()
                .putLong("ptgMigrationMaxCancelledInstallationDate", maxCancelledInstallationDate)
                .apply();
    }

    private static synchronized String getMigratedPackageName() {
        if (migratedPackageName == null) {
            migratedPackageName = getPrefs()
                    .getString("migratedPackageName", "");
        }
        return migratedPackageName;
    }

    private static synchronized long getMigratedDate() {
        if (migratedDate == null) {
            migratedDate = getPrefs()
                    .getLong("migratedDate", 0);
        }
        return migratedDate;
    }

    public static void setMigrationFinished(String packageName) {
        migratedPackageName = packageName;
        getPrefs().edit()
                .putString("migratedPackageName", migratedPackageName)
                .apply();

        migratedDate = System.currentTimeMillis();
        getPrefs().edit()
                .putLong("migratedDate", migratedDate)
                .apply();
    }

    public static void resetMigrationFinished() {
        migratedPackageName = null;
        getPrefs().edit()
                .remove("migratedPackageName")
                .apply();

        migratedDate = null;
        getPrefs().edit()
                .remove("migratedDate")
                .apply();
    }

    public static boolean isReceivingZip() {
        return receivingZip;
    }

    public static synchronized void receiveZip(Activity activity) {
        if (receivingZip) {
            return;
        }
        receivingZip = true;
        ZipReceiver.receiveZip(activity);
    }
}
