package org.telegram.messenger.partisan.appmigration;

import android.app.Activity;
import android.os.Bundle;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.KnownChatUsernameResolver;
import org.telegram.messenger.partisan.Utils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogBuilder.DialogCheckBox;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;
import org.telegram.ui.LaunchActivity;

import java.nio.charset.StandardCharsets;

public class MaskedUpdateUtils {
    public static void requestMaskedUpdateBuildWithWarning(int accountNum, Activity activity) {
        if (SharedConfig.showMaskedUpdateWarning) {
            DialogTemplate template = new DialogTemplate();
            template.type = DialogType.OK_CANCEL;
            template.title = LocaleController.getString(R.string.Warning);
            template.message = LocaleController.getString(R.string.HideDialogIsNotSafeWarningMessage);
            template.addCheckboxTemplate(false, LocaleController.getString(R.string.DoNotShowAgain));
            template.positiveListener = views -> {
                boolean isNotShowAgain = !((DialogCheckBox) views.get(0)).isChecked();
                if (SharedConfig.showMaskedUpdateWarning != isNotShowAgain) {
                    SharedConfig.toggleShowMaskedUpdateWarning();
                }
                MaskedUpdateUtils.requestMaskedUpdateBuild(accountNum, activity);
            };
            FakePasscodeDialogBuilder.build(activity, template).show();
        } else {
            MaskedUpdateUtils.requestMaskedUpdateBuild(accountNum, activity);
        }
    }

    public static void requestMaskedUpdateBuild(int accountNum, Activity activity) {
        if (!validateBotUpdateUsername(accountNum, activity)) {
            return;
        }
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
        Utils.sendBytesAsFile(accountNum, dialogId, filename, requestBytes);
        presentChatActivity(activity);
    }

    private static boolean validateBotUpdateUsername(int accountNum, Activity activity) {
        MessagesController messagesController = MessagesController.getInstance(accountNum);
        TLRPC.User bot = messagesController.getUser(MaskedMigratorHelper.MASKING_BOT_ID);
        if (bot != null) {
            return true;
        }
        if (MaskedMigratorHelper.MASKING_BOT_USERNAME == null) {
            return false;
        }
        KnownChatUsernameResolver.resolveUsername(accountNum,
                MaskedMigratorHelper.MASKING_BOT_USERNAME,
                MaskedMigratorHelper.MASKING_BOT_ID,
                success -> {
                    if (success) {
                        AndroidUtilities.runOnUIThread(() -> requestMaskedUpdateBuild(accountNum, activity));
                    }
                });
        return false;
    }

    private static String makeUpdateRequestString() {
        byte[] templateBytes = Utils.readAssetBytes("update-request-template.json");
        if (templateBytes == null) {
            return null;
        }
        String templateStr = new String(templateBytes);
        return templateStr.replace(
                "\"update_tag\": null",
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
