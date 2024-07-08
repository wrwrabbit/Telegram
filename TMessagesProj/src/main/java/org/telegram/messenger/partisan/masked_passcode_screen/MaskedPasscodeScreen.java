package org.telegram.messenger.partisan.masked_passcode_screen;

public interface MaskedPasscodeScreen {
    void init();
    void onShow(boolean fingerprint, boolean animated);
    void onMeasure(int widthMeasureSpec, int heightMeasureSpec);
    default void onPasscodeError() {}
}
