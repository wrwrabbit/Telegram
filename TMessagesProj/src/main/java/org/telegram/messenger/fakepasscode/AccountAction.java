package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.UserConfig;

public abstract class AccountAction implements Action {
    @JsonIgnore
    protected int accountNum = 0;

    public void setAccountNum(int accountNum) {
        this.accountNum = accountNum;
    }

    protected UserConfig getUserConfig() {
        return UserConfig.getInstance(accountNum);
    }
}
