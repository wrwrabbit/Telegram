package org.telegram.messenger.partisan.masked_passcode_screen;

import android.view.View;

public interface MaskedPasscodeScreen {
    View createView();
    void onShow(boolean fingerprint, boolean animated);
    void onMeasure(int widthMeasureSpec, int heightMeasureSpec);
    default void onPasscodeError() {}
}
