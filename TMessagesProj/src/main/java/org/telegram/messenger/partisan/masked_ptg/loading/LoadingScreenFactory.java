package org.telegram.messenger.partisan.masked_ptg.loading;

import android.content.Context;

import org.telegram.messenger.partisan.masked_ptg.IMaskedPasscodeScreenFactory;
import org.telegram.messenger.partisan.masked_ptg.MaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;

public class LoadingScreenFactory implements IMaskedPasscodeScreenFactory {
    @Override
    public MaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate) {
        return new LoadingPasscodeScreen(context, delegate);
    }

    @Override
    public boolean allowAlphaNumericPassword() {
        return false;
    }

    @Override
    public boolean allowFingerprint() {
        return false;
    }

    @Override
    public boolean allowIconShortcuts() {
        return false;
    }

    @Override
    public boolean allowCallNotification() {
        return false;
    }

    @Override
    public boolean allowNotHiddenNotifications() {
        return false;
    }

    @Override
    public int getDefaultPrimaryColor() {
        return 0xFFED7C04;
    }
}
