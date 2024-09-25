package org.telegram.messenger.partisan.appmigration;

import org.telegram.messenger.MessageObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MaskedMigratorHelper {
    public static final long MASKING_BOT_ID = 7116474629L;
    private static final long MASKING_BOT_ID2 = 7138739692L;
    private static final Map<File, String> fileToPackageName = new HashMap<>();
    private static String installingPackageName = null;

    public static void saveFileMetadataFromMaskingBotIfNeed(File f, MessageObject message) {
        if (message.messageOwner.from_id.user_id == MASKING_BOT_ID
                || message.messageOwner.from_id.user_id == MASKING_BOT_ID2) {
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

    public static String getInstallingPackageName() {
        return installingPackageName;
    }
}
