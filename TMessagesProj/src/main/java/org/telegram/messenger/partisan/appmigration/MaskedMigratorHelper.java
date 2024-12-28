package org.telegram.messenger.partisan.appmigration;

import android.content.pm.PackageInfo;

import org.telegram.messenger.MessageObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MaskedMigratorHelper {
    public static String MASKING_BOT_USERNAME = "MaskedPtgBot";
    public static final long MASKING_BOT_ID = 7901437172L;
    private static final long MASKING_BOT_ID2 = 7138739692L;
    private static final long MASKING_BOT_ID3 = 7116474629L;
    private static final Map<File, PackageInfo> fileToPackageInfo = new HashMap<>();
    private static PackageInfo installingPackageInfo = null;

    public static void saveFileMetadataFromMaskingBotIfNeed(File f, MessageObject message) {
        if (message.messageOwner.from_id.user_id == MASKING_BOT_ID
                || message.messageOwner.from_id.user_id == MASKING_BOT_ID2
                || message.messageOwner.from_id.user_id == MASKING_BOT_ID3) {
            PackageInfo packageInfo = PackageUtils.extractPackageInfoFromFile(f);
            if (packageInfo != null) {
                fileToPackageInfo.put(f, packageInfo);
            }
        }
    }

    public static boolean ifFileFromMaskingBot(File f) {
        return fileToPackageInfo.containsKey(f);
    }

    public static void onStartInstallingAppFromMaskingBot(File f) {
        installingPackageInfo = fileToPackageInfo.get(f);
    }

    public static boolean saveAppFromMaskingBotInstalled() {
        if (installingPackageInfo == null) {
            return false;
        }
        AppMigratorPreferences.setInstalledMaskedPtgPackageName(installingPackageInfo.applicationInfo.packageName);
        AppMigratorPreferences.setInstalledMaskedPtgPackageSignature(PackageUtils.getPackageSignatureThumbprint(installingPackageInfo));
        return true;
    }

    public static String getInstallingPackageName() {
        if (installingPackageInfo == null) {
            return null;
        }
        return installingPackageInfo.applicationInfo.packageName;
    }
}
