package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.MessagesController;

public class LogOutAction implements Action {
    public int accountNum = 0;

    public LogOutAction() {}

    public LogOutAction(int accountNum) {
        this.accountNum = accountNum;
    }

    @Override
    public void execute() {
        MessagesController.getInstance(accountNum).performLogout(1);
    }
}
