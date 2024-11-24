package org.telegram.messenger.partisan.masked_ptg.note;

import android.content.Context;

import org.telegram.messenger.partisan.masked_ptg.IMaskedPasscodeScreenFactory;
import org.telegram.messenger.partisan.masked_ptg.MaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;

public class NoteScreenFactory implements IMaskedPasscodeScreenFactory {
    @Override
    public MaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate) {
        return new NotePasscodeScreen(context, delegate);
    }

    @Override
    public boolean allowAlphaNumericPassword() {
        return true;
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
        return Colors.primaryColor;
    }
}
