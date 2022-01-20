package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public class TerminateOtherSessionsAction extends AccountAction {
    public TerminateOtherSessionsAction() {}

    public TerminateOtherSessionsAction(int accountNum) {
        this.accountNum = accountNum;
    }

    @Override
    public void execute() {
        TLRPC.TL_auth_resetAuthorizations req = new TLRPC.TL_auth_resetAuthorizations();
        ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                UserConfig userConfig = getUserConfig();
                if (!userConfig.isClientActivated()) {
                    continue;
                }
                userConfig.registeredForPush = false;
                userConfig.saveConfig(false);
                MessagesController.getInstance(a).registerForPush(SharedConfig.pushString);
                ConnectionsManager.getInstance(a).setUserId(userConfig.getClientUserId());
            }
        });
    }
}
