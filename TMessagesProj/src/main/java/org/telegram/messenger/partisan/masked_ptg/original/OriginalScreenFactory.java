package org.telegram.messenger.partisan.masked_ptg.original;

import android.content.Context;

import org.telegram.messenger.partisan.masked_ptg.IMaskedPasscodeScreenFactory;
import org.telegram.messenger.partisan.masked_ptg.AbstractMaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;

public class OriginalScreenFactory implements IMaskedPasscodeScreenFactory {
    @Override
    public AbstractMaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate, boolean unlockingApp) {
        return new OriginalPasscodeScreen(context, delegate, unlockingApp);
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
