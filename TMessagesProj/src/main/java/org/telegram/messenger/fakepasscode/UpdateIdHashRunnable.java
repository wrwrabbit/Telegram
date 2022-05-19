package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.Utilities;

public class UpdateIdHashRunnable implements Runnable {
    private final AccountActions accountActions;

    public UpdateIdHashRunnable(AccountActions accountActions) {
        this.accountActions = accountActions;
    }

    @Override
    public void run() {
        accountActions.checkIdHash();
        Utilities.globalQueue.postRunnable(this, 1000);
    }
}
