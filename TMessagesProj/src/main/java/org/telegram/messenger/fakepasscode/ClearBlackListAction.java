package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@FakePasscodeSerializer.ToggleSerialization
public class ClearBlackListAction extends AccountAction implements NotificationCenter.NotificationCenterDelegate {

    @JsonIgnore
    private FakePasscode fakePasscode;

    @Override
    public void execute(FakePasscode fakePasscode) {
        this.fakePasscode = fakePasscode;
        fakePasscode.actionsResult.actionsPreventsLogoutAction.add(this);
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
        Set<Long> notBlockedPeers = Collections.synchronizedSet(new HashSet<>());
        for (int i = 0; i < controller.blockePeers.size(); i++) {
            notBlockedPeers.add(controller.blockePeers.keyAt(i));
        }
        for (int i = 0; i < controller.blockePeers.size(); i++) {
            long userId = controller.blockePeers.keyAt(i);
            int blocked = controller.blockePeers.get(userId);
            if (blocked == 0) {
                continue;
            }
            controller.unblockPeer(userId, () -> {
                notBlockedPeers.remove(userId);
                if (notBlockedPeers.isEmpty()) {
                    fakePasscode.actionsResult.actionsPreventsLogoutAction.remove(this);
                }
            });
        }
    }
}
