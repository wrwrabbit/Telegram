package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.NotificationCenter;

@FakePasscodeSerializer.ToggleSerialization
public class HideAccountAction extends AccountAction {
    @Override
    public void execute(FakePasscode fakePasscode) {
        fakePasscode.actionsResult.hiddenAccounts.add(accountNum);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.accountHidingChanged);
        AccountInstance.getInstance(accountNum).getNotificationsController().removeAllNotifications();
    }
}
