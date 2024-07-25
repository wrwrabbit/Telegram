package org.telegram.messenger.partisan.masked_ptg.loading;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.partisan.masked_ptg.MaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.util.concurrent.ThreadLocalRandom;

public class LoadingPasscodeScreen implements MaskedPasscodeScreen {
    private final int RESET_PASSCODE_TIME_MILLIS = 5 * 1000;

    private final PasscodeEnteredDelegate delegate;
    private final Context context;

    private FrameLayout backgroundFrameLayout;
    RelativeLayout relativeLayout;
    private TextView titleTextView;
    private RadialProgressView progressBar;

    private boolean needShowPasscodeToast = BuildVars.DEBUG_PRIVATE_VERSION;
    Toast lastToast;

    private String passcode = "";
    private long lastPasscodeInputTime;
    private boolean fragmentDetached = false;

    public LoadingPasscodeScreen(Context context, PasscodeEnteredDelegate delegate) {
        this.context = context;
        this.delegate = delegate;
    }

    @Override
    public View createView() {
        createBackgroundFrameLayout();
        createRelativeLayout(context);
        createTitleTextView(context);
        createProgressBar(context);
        return backgroundFrameLayout;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createBackgroundFrameLayout() {
        backgroundFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.setWillNotDraw(false);
        backgroundFrameLayout.setBackgroundColor(Color.WHITE);
        backgroundFrameLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
                onScreenClicked(event.getX(), event.getY());
            }
            return false;
        });
        backgroundFrameLayout.setOnLongClickListener((v) -> {
            if (BuildVars.DEBUG_PRIVATE_VERSION) {
                needShowPasscodeToast = !needShowPasscodeToast;
                showToast(Boolean.toString(needShowPasscodeToast));
            }
            return true;
        });
    }

    private void onScreenClicked(float x, float y) {
        lastPasscodeInputTime = System.currentTimeMillis();
        passcode += getPasscodeDigit(x, y);
        showPasscodeToast();
        if (passcode.length() == 4) {
            delegate.passcodeEntered(passcode);
            passcode = "";
        } else if (passcode.length() > 4) {
            passcode = "";
        }
    }

    private int getPasscodeDigit(float x, float y) {
        int regionWidth = backgroundFrameLayout.getWidth() / 3;
        int posX = Math.min((int)x / regionWidth, 2);
        float relativeY = y / backgroundFrameLayout.getHeight();
        int posY;
        if (relativeY > .85) {
            posY = 0;
        } else if (relativeY > .65) {
            posY = 3;
        } else if (relativeY > .35) {
            posY = 2;
        } else {
            posY = 1;
        }
        int digit;
        if (posY == 0) {
            digit = 0;
        } else {
            digit = (posY - 1) * 3 + posX + 1;
        }
        return digit;
    }

    private void showPasscodeToast() {
        if (!needShowPasscodeToast) {
            return;
        }
        showToast(passcode);
    }

    private void showToast(String text) {
        if (lastToast != null) {
            lastToast.cancel();
        }
        lastToast = Toast.makeText(context, text, Toast.LENGTH_SHORT);
        lastToast.show();
    }

    private void createRelativeLayout(Context context) {
        relativeLayout = new RelativeLayout(context);
        backgroundFrameLayout.addView(relativeLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void createTitleTextView(Context context) {
        titleTextView = new TextView(context);
        titleTextView.setTextColor(Color.DKGRAY);
        titleTextView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        titleTextView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            titleTextView.setId(View.generateViewId());
        } else {
            titleTextView.setId(ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE / 2, Integer.MAX_VALUE));
        }
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        relativeLayout.addView(titleTextView, relativeParams);
        titleTextView.setText("Loading...");
    }

    private void createProgressBar(Context context) {
        progressBar = new RadialProgressView(context);
        progressBar.setSize(AndroidUtilities.dp(64));
        progressBar.setProgressColor(0xFFED7C04);
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        relativeParams.addRule(RelativeLayout.BELOW, titleTextView.getId());
        relativeLayout.addView(progressBar, relativeParams);
    }

    @Override
    public void onShow(boolean fingerprint, boolean animated) {
        Activity parentActivity = AndroidUtilities.findActivity(context);
        if (parentActivity != null) {
            View currentFocus = parentActivity.getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        passcode = "";
        fragmentDetached = false;
        AndroidUtilities.runOnUIThread(this::checkPasscodeResetTime, 1000);
    }

    @Override
    public void onDetachedFromWindow() {
        fragmentDetached = true;
    }

    private void checkPasscodeResetTime() {
        if (fragmentDetached) {
            return;
        }
        if (!passcode.isEmpty() && System.currentTimeMillis() - lastPasscodeInputTime > RESET_PASSCODE_TIME_MILLIS) {
            passcode = "";
            if (needShowPasscodeToast) {
                showToast("Passcode reset");
            }
        }
        AndroidUtilities.runOnUIThread(this::checkPasscodeResetTime, 1000);
    }
}
