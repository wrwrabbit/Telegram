package org.telegram.messenger.partisan.appmigration;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.PartisanVersion;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.LauncherIconController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class AppMigrator {
    public static final int MIGRATE_TO_REGULAR_PTG_CODE = 20202020;
    public static final int CONFIRM_SIGNATURE_CODE = 20202021;

    private static final String PTG_SIGNATURE = "54EACD58409061FFADD5930A9D8B0A13E5A2B0561A486E5E6B5600480A5BC32A";
    private static final String PTG_DEBUG_SIGNATURE = "7A7D4936FAD1A022F4DB2B24B8C9687B80C79099D986C05C541D002308872421";
    private static final List<String> PTG_PACKAGE_NAMES = Arrays.asList(
            "org.telegram.messenger.web",
            "org.telegram.messenger"
    );
    private static final List<String> PTG_DEBUG_PACKAGE_NAMES = Arrays.asList(
            "org.telegram.messenger.alpha",
            "org.telegram.messenger.beta"
    );

    public static boolean startNewTelegram(Activity activity) {
        Intent intent = createNewTelegramIntent(activity);
        if (intent == null) {
            return false;
        }

        try {
            disableConnection();
            activity.startActivityForResult(intent, MIGRATE_TO_REGULAR_PTG_CODE);
            return true;
        } catch (Exception e) {
            enableConnection();
            PartisanLog.e("MoveDataToOtherPtg", e);
            return false;
        }
    }

    private static Intent createNewTelegramIntent(Activity activity) {
        if (AppMigratorPreferences.isMigrationToMaskedPtg()) {
            return createNewTelegramIntent(activity, AppMigratorPreferences.getInstalledMaskedPtgPackageName(), "org.telegram.messenger.DefaultIcon");
        } else {
            ActivityInfo activityInfo = getNewestUncheckedPtgActivity(activity);
            if (activityInfo == null) {
                return null;
            }
            return createNewTelegramIntent(activity, activityInfo);
        }
    }

    private static ActivityInfo getNewestUncheckedPtgActivity(Context context) {
        Intent searchIntent = new Intent(Intent.ACTION_MAIN);
        searchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infoList = context.getPackageManager().queryIntentActivities(searchIntent, 0);
        PackageInfo newestPackage = AppMigrator.getNewestUncheckedPtgPackage(context, false);
        if (newestPackage == null) {
            return null;
        }
        for (ResolveInfo info : infoList) {
            ActivityInfo activityInfo = info.activityInfo;
            if (activityInfo != null && TextUtils.equals(activityInfo.packageName, newestPackage.packageName)) {
                return activityInfo;
            }
        }
        return null;
    }

    private static Intent createNewTelegramIntent(Context context, ActivityInfo activityInfo) {
        return createNewTelegramIntent(context, activityInfo.applicationInfo.packageName, activityInfo.name);
    }

    private static Intent createNewTelegramIntent(Context context, String packageName, String activityName) {
        Intent intent;
        boolean isMaskedPtgPackage = packageName.equals(AppMigratorPreferences.getInstalledMaskedPtgPackageName());
        if (!isMaskedPtgPackage) {
            intent = createIntentWithMigrationInfo(context);
        } else {
            intent = createIntentWithSignatureConfirmationRequest(context);
        }
        intent.setAction(Intent.ACTION_MAIN);
        intent.setClassName(packageName, activityName);

        if (isMaskedPtgPackage || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    public static Intent createIntentWithMigrationInfo(Context context) {
        Intent intent = new Intent();
        intent.setDataAndType(MigrationZipBuilder.getZipUri(context), "application/zip");
        intent.putExtra("zipPassword", MigrationZipBuilder.getPasswordBytes());
        intent.putExtra("packageName", context.getPackageName());
        intent.putExtra("language", LocaleController.getInstance().getLanguageOverride());
        intent.putExtra("fromOtherPtg", true);
        intent.putExtra("version", PartisanVersion.PARTISAN_VERSION_STRING);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private static Intent createIntentWithSignatureConfirmationRequest(Context context) {
        Intent intent = new Intent();
        intent.putExtra("fromOtherPtg", true);
        intent.putExtra("signatureConfirmationRequired", true);
        ComponentName componentName = LauncherIconController
                .getAvailableIcons()
                .get(LauncherIconController.getSelectedIconIndex())
                .getComponentName(context);
        intent.putExtra("packageName", componentName.getPackageName());
        intent.putExtra("activityName", componentName.getClassName());
        return intent;
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

    public static boolean isProxyForDisablingConnection(SharedConfig.ProxyInfo proxyInfo) {
        return "127.0.0.1".equals(proxyInfo.address) && proxyInfo.port == -1;
    }

    public static void disableConnection() {
        if (isConnectionDisabled()) {
            return;
        }
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
        return packageName != null && getPtgPackageNames().contains(packageName);
    }

    public static boolean isNewerPtgInstalled(Context context, boolean checkCancelledDate) {
        return getNewestUncheckedPtgPackage(context, checkCancelledDate) != null
                || AppMigratorPreferences.isMigrationToMaskedPtg();
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
                ? Math.max(selfPackage.firstInstallTime, AppMigratorPreferences.getMaxCancelledInstallationDate())
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
        return Objects.equals(AppMigratorPreferences.getMigratedPackageName(), AppMigratorPreferences.getInstalledMaskedPtgPackageName())
                || getMigratedPackage(context) != null;
    }

    private static PackageInfo getMigratedPackage(Context context) {
        String packageName = AppMigratorPreferences.getMigratedPackageName();
        long migratedDate = AppMigratorPreferences.getMigratedDate();
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
        return PackageUtils.getPackageInfoWithCertificates(context, context.getPackageName());
    }

    private static List<PackageInfo> getOtherPartisanTelegramPackages(Context context) {
        if (context == null) {
            return Collections.emptyList();
        }
        List<PackageInfo> result = new ArrayList<>();
        for (String packageName : getPtgPackageNames()) {
            PackageInfo packageInfo = PackageUtils.getPackageInfoWithCertificates(context, packageName);
            if (packageInfo == null || Objects.equals(packageInfo.packageName, context.getPackageName())) {
                continue;
            }
            if (isPtgSignature(packageInfo)) {
                result.add(packageInfo);
            }
        }
        return result;
    }

    private static List<String> getPtgPackageNames() {
        if (isDebugApp()) {
            return PTG_DEBUG_PACKAGE_NAMES;
        } else {
            return PTG_PACKAGE_NAMES;
        }
    }

    private static boolean isDebugApp() {
        return BuildVars.isAlphaApp() || BuildVars.isBetaApp();
    }

    private static boolean isPtgSignature(PackageInfo packageInfo) {
        return PackageUtils.isPackageSignatureThumbprint(packageInfo, getPtgSignature());
    }

    private static String getPtgSignature() {
        if (isDebugApp()) {
            return PTG_DEBUG_SIGNATURE;
        } else {
            return PTG_SIGNATURE;
        }
    }

    public static boolean isMigrationStarted() {
        return AppMigratorPreferences.getStep() != Step.NOT_STARTED;
    }

    public static boolean checkMigrationNeedToResume(Context context) {
        if (!isMigrationStarted()) {
            return false;
        }
        if (!TextUtils.isEmpty(AppMigratorPreferences.getMigratedPackageName()) && !isMigratedPackageInstalled(context)) {
            AppMigratorPreferences.setStep(Step.NOT_STARTED);
            enableConnection();
            AppMigratorPreferences.resetMigrationFinished();
            return false;
        } else {
            return true;
        }
    }

    public static void uninstallSelf(Context context) {
        if (context == null) {
            return;
        }
        MigrationZipBuilder.deleteZipFile();
        // we will show the system app settings if the app doesn't have the permission
        boolean deletionAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.REQUEST_DELETE_PACKAGES) == PackageManager.PERMISSION_GRANTED;
        Intent intent = new Intent(deletionAllowed ? Intent.ACTION_DELETE : Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        context.startActivity(intent);
    }

    public static boolean readyToStartNewTelegram() {
        return MigrationZipBuilder.zipFileExists();
    }

    public static boolean appAlreadyHasAccounts() {
        return UserConfig.getActivatedAccountsCount(true) > 0;
    }
}
