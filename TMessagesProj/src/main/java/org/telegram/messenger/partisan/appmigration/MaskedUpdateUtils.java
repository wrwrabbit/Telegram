package org.telegram.messenger.partisan.appmigration;

import android.app.Activity;
import android.os.Bundle;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.Utils;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.nio.charset.StandardCharsets;

public class MaskedUpdateUtils {
    public static void requestMaskedUpdateBuild(int currentAccount, Activity activity) {
        SharedConfig.pendingPtgAppUpdate.botRequestTag = generateRequestTag();
        SharedConfig.saveConfig();
        String requestString = makeUpdateRequestString();
        if (requestString == null) {
            SharedConfig.pendingPtgAppUpdate.botRequestTag = null;
            SharedConfig.saveConfig();
            return;
        }
        byte[] requestBytes = requestString.getBytes(StandardCharsets.UTF_8);
        long dialogId = MaskedMigratorHelper.MASKING_BOT_ID;
        String filename = "update-" + SharedConfig.pendingPtgAppUpdate.botRequestTag + ".json";
        Utils.sendBytesAsFile(currentAccount, dialogId, filename, requestBytes);
        presentChatActivity(activity);
    }

    private static String makeUpdateRequestString() {
        byte[] templateBytes = Utils.readAssetBytes("update-request-template.json");
        if (templateBytes == null) {
            return null;
        }
        String templateStr = new String(templateBytes);
        return templateStr.replace(
                "\"update_tag\": \"\"",
                "\"update_tag\": \"" + SharedConfig.pendingPtgAppUpdate.botRequestTag + "\"");
    }

    private static String generateRequestTag() {
        byte[] randomBytes = new byte[16];
        Utilities.random.nextBytes(randomBytes);
        return Utilities.bytesToHex(randomBytes);
    }

    private static void presentChatActivity(Activity activity) {
        Bundle args = new Bundle();
        args.putLong("user_id", MaskedMigratorHelper.MASKING_BOT_ID);
        if (activity instanceof LaunchActivity) {
            LaunchActivity launchActivity = (LaunchActivity) activity;
            launchActivity.presentFragment(new ChatActivity(args));
            launchActivity.drawerLayoutContainer.closeDrawer();
        }
    }
}
