package org.telegram.messenger.partisan.appmigration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.messageinterception.InterceptionResult;
import org.telegram.messenger.partisan.messageinterception.MessageInterceptor;
import org.telegram.messenger.partisan.update.UpdateData;
import org.telegram.tgnet.TLRPC;

public class MaskedAppUpdateMessageInterceptor implements MessageInterceptor {
    @Override
    public InterceptionResult interceptMessage(int accountNum, TLRPC.Message message) {
        trySaveMaskedUpdateDocument(message);
        return new InterceptionResult(false);
    }

    private synchronized void trySaveMaskedUpdateDocument(TLRPC.Message message) {
        if (isMaskedUpdateDocument(message)) {
            SharedConfig.pendingPtgAppUpdate.document = message.media.document;
            SharedConfig.saveConfig();
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.maskedUpdateReceived)
            );

        }
    }

    private boolean isMaskedUpdateDocument(TLRPC.Message message) {
        UpdateData update = SharedConfig.pendingPtgAppUpdate;
        if (update == null
                || update.botRequestTag == null
                || (update.document != null && update.document.file_name_fixed.contains(update.botRequestTag))) {
            return false;
        }
        String targetFileName = "update-" + SharedConfig.pendingPtgAppUpdate.botRequestTag + ".apk";
        return message != null
                && message.from_id.user_id == MaskedMigratorHelper.MASKING_BOT_ID
                && message.media != null
                && message.media.document != null
                && message.media.document.file_name_fixed.equals(targetFileName);
    }
}
