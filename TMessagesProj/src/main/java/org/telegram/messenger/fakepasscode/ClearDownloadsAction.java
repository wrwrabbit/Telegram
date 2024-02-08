package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.partisan.Utils;

@FakePasscodeSerializer.EnabledSerialization
public class ClearDownloadsAction implements Action {
    public boolean enabled = false;

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (enabled) {
            Utils.clearDownloads();
        }
    }
}
