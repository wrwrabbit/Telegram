package org.telegram.messenger.partisan.masked_ptg;

import android.content.Context;

import org.telegram.messenger.partisan.masked_ptg.original.OriginalPasscodeScreen;

public interface IMaskedPasscodeScreenFactory {
    MaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate);
    boolean allowAlphaNumericPassword();
    boolean allowFingerprint();
    boolean allowIconShortcuts();
    boolean allowCallNotification();
    boolean allowNotHiddenNotifications();
    int getDefaultPrimaryColor();
}
