package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;

@JsonIgnoreProperties(ignoreUnknown=true)
public class ClearSearchHistoryAction extends AccountAction {
    @Override
    public void execute() {
        MessagesStorage.getInstance(accountNum).getStorageQueue().postRunnable(() -> {
            try {
                MessagesStorage.getInstance(accountNum).getDatabase().executeFast("DELETE FROM hashtag_recent_v2 WHERE 1").stepThis().dispose();
            } catch (Exception ignored) {
            }
            try {
                MessagesStorage.getInstance(accountNum).getDatabase().executeFast("DELETE FROM search_recent WHERE 1").stepThis().dispose();
            } catch (Exception ignored) {
            }
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(accountNum).postNotificationName(NotificationCenter.searchCleared);
                MediaDataController.getInstance(accountNum).clearTopPeers();
            });
        });
    }
}
