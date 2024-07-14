package org.telegram.messenger.partisan.masked_passcode_screen;

import android.content.Context;

import org.telegram.messenger.partisan.masked_passcode_screen.calculator.CalculatorPasscodeScreen;

public class MaskedPasscodeConfig {
    public static MaskedPasscodeScreen createScreen(Context context, PasscodeEnteredDelegate delegate) {
        return new CalculatorPasscodeScreen(context, delegate);
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
}
