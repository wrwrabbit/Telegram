package org.telegram.messenger.partisan.masked_ptg;

import android.content.Context;

import org.telegram.messenger.partisan.masked_ptg.loading.LoadingPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.original.OriginalPasscodeScreen;

public class MaskedPtgConfig {
    public static MaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate) {
        return new OriginalPasscodeScreen(context, delegate);
    }

    public static boolean allowAlphaNumericPassword() {
        return false;
    }

    public static boolean allowFingerprint() {
        return false;
    }

    public static boolean allowIconShortcuts() {
        return false;
    }

    public static boolean allowCallNotification() {
        return false;
    }

    public static boolean allowNotHiddenNotifications() {
        return false;
    }

    public static int getNotificationsColor() {
        return 0xff11acfa;
    }
}
