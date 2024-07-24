package org.telegram.messenger.partisan.masked_ptg;

import android.view.View;

public interface MaskedPasscodeScreen {
    View createView();
    void onShow(boolean fingerprint, boolean animated);
    default void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {}
    default void onPasscodeError() {}
    default void onAttachedToWindow() {}
    default void onDetachedFromWindow() {}
    default boolean onBackPressed () {
        return true;
    }
}
