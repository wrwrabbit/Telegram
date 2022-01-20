package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.UserConfig;

public abstract class AccountAction implements Action {
    public int accountNum = 0;

    protected UserConfig getUserConfig() {
        return UserConfig.getInstance(accountNum);
    }
}
