package org.telegram.messenger.partisan.masked_ptg.original;

import android.content.Context;

import org.telegram.messenger.partisan.masked_ptg.IMaskedPasscodeScreenFactory;
import org.telegram.messenger.partisan.masked_ptg.MaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;

public class OriginalScreenFactory implements IMaskedPasscodeScreenFactory {
    @Override
    public MaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate) {
        return new OriginalPasscodeScreen(context, delegate);
    }

    @Override
    public boolean allowAlphaNumericPassword() {
        return true;
    }

    @Override
    public boolean allowFingerprint() {
        return true;
    }

    @Override
    public boolean allowIconShortcuts() {
        return true;
    }

    @Override
    public boolean allowCallNotification() {
        return true;
    }

    @Override
    public boolean allowNotHiddenNotifications() {
        return true;
    }

    @Override
    public int getDefaultPrimaryColor() {
        return 0xFF2AABEE;
    }
}
