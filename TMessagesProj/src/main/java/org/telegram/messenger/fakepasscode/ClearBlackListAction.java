package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;

public class ClearBlackListAction extends AccountAction implements NotificationCenter.NotificationCenterDelegate {

    @Override
    public void execute() {
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.blockedUsersDidLoad);
        MessagesController controller = AccountInstance.getInstance(accountNum).getMessagesController();
        controller.getBlockedPeers(true);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != accountNum) {
            return;
        }

        MessagesController controller = AccountInstance.getInstance(accountNum).getMessagesController();
        if (controller.blockedEndReached) {
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.blockedUsersDidLoad);
        }
        for (int i = 0; i < controller.blockePeers.size(); i++) {
            long userId = controller.blockePeers.keyAt(i);
            int blocked = controller.blockePeers.get(userId);
            if (blocked == 0) {
                continue;
            }
            //controller.unblockPeer(userId);
        }
    }
}
