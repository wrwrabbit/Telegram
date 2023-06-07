package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.NotificationCenter;

@FakePasscodeSerializer.ToggleSerialization
public class HideAccountAction extends AccountAction {
    public boolean strictHiding;

    @Override
    public void execute(FakePasscode fakePasscode) {
        fakePasscode.actionsResult.hiddenAccountEntries.add(new ActionsResult.HiddenAccountEntry(accountNum, strictHiding));
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.accountHidingChanged);
        AccountInstance.getInstance(accountNum).getNotificationsController().removeAllNotifications();
    }
}
