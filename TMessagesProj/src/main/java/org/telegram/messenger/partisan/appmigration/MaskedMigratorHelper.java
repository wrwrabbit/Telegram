package org.telegram.messenger.partisan.appmigration;

import org.telegram.messenger.MessageObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MaskedMigratorHelper {
    private static final long MASKING_BOT_ID = 7116474629L;
    private static final Map<File, String> fileToPackageName = new HashMap<>();
    public static String installingPackageName = null;

    public static void saveFileMetadataFromMaskingBotIfNeed(File f, MessageObject message) {
        if (message.messageOwner.from_id.user_id == MASKING_BOT_ID) {
            String packageName = AndroidManifestExtractor.extractPackageNameFromApk(f);
            if (packageName != null) {
                fileToPackageName.put(f, packageName);
            }
        }
    }

    public static boolean ifFileFromMaskingBot(File f) {
        return fileToPackageName.containsKey(f);
    }

    public static void onStartInstallingAppFromMaskingBot(File f) {
        installingPackageName = fileToPackageName.get(f);
    }

    public static boolean saveAppFromMaskingBotInstalled() {
        if (installingPackageName == null) {
            return false;
        }
        AppMigrator.setInstalledMaskedPtgPackageName(installingPackageName);
        return true;
    }
}
