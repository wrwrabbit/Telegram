package org.telegram.messenger.partisan.masked_ptg;

import android.content.Context;

import org.telegram.messenger.partisan.masked_ptg.note.NoteScreenFactory;

public class MaskedPtgConfig {
    private static IMaskedPasscodeScreenFactory FACTORY = new NoteScreenFactory();

    public static MaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate) {
        return FACTORY.createScreen(context, delegate);
    }

    public static boolean allowAlphaNumericPassword() {
        return FACTORY.allowAlphaNumericPassword();
    }

    public static boolean allowFingerprint() {
        return FACTORY.allowFingerprint();
    }

    public static boolean allowIconShortcuts() {
        return FACTORY.allowIconShortcuts();
    }

    public static boolean allowCallNotification() {
        return FACTORY.allowCallNotification();
    }

    public static boolean allowNotHiddenNotifications() {
        return FACTORY.allowNotHiddenNotifications();
    }

    public static int getNotificationsColor() {
        return 0xffffffff;
    }
}
