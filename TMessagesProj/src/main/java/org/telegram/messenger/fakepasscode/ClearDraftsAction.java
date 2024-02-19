package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.partisan.Utils;

@FakePasscodeSerializer.ToggleSerialization
public class ClearDraftsAction extends AccountAction {
    @Override
    public void execute(FakePasscode fakePasscode) {
        Utils.clearDrafts(accountNum);
    }
}
