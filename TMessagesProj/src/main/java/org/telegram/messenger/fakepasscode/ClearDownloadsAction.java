package org.telegram.messenger.fakepasscode;

@FakePasscodeSerializer.EnabledSerialization
public class ClearDownloadsAction implements Action {
    public boolean enabled = false;

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (enabled) {
            Utils.clearDownloads(null);
        }
    }
}
