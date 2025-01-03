package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.partisan.BlackListLoader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@FakePasscodeSerializer.ToggleSerialization
public class ClearBlackListAction extends AccountAction {

    @JsonIgnore
    private FakePasscode fakePasscode;
    @JsonIgnore
    Set<Long> notBlockedPeers = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void execute(FakePasscode fakePasscode) {
        this.fakePasscode = fakePasscode;
        fakePasscode.actionsResult.actionsPreventsLogoutAction.add(this);
        BlackListLoader.load(accountNum, 5000, this::onBlockedPeersLoaded);
    }

    private void onBlockedPeersLoaded(boolean loadingFinished) {
        MessagesController controller = AccountInstance.getInstance(accountNum).getMessagesController();
        if (loadingFinished && controller.getUnfilteredBlockedPeers().size() == 0) {
            fakePasscode.actionsResult.actionsPreventsLogoutAction.remove(this);
        }
        for (int i = 0; i < controller.getUnfilteredBlockedPeers().size(); i++) {
            long userId = controller.getUnfilteredBlockedPeers().keyAt(i);
            int blocked = controller.getUnfilteredBlockedPeers().get(userId);
            if (blocked == 0) {
                continue;
            }
            if (notBlockedPeers.add(controller.getUnfilteredBlockedPeers().keyAt(i))) {
                controller.unblockPeer(userId, () -> {
                    notBlockedPeers.remove(userId);
                    if (notBlockedPeers.isEmpty()) {
                        fakePasscode.actionsResult.actionsPreventsLogoutAction.remove(this);
                    }
                });
            }
        }
    }
}
