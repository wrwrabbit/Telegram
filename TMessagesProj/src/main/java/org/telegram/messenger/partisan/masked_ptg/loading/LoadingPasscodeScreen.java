package org.telegram.messenger.partisan.masked_ptg.loading;


import static org.telegram.messenger.partisan.masked_ptg.loading.Constants.RESET_PASSCODE_TIME_SEC;

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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.partisan.masked_ptg.MaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.DialogBuilder.DialogButtonWithTimer;

import java.util.concurrent.ThreadLocalRandom;

public class LoadingPasscodeScreen implements MaskedPasscodeScreen {
    private final PasscodeEnteredDelegate delegate;
    private final Context context;

    private FrameLayout backgroundFrameLayout;
    private RelativeLayout relativeLayout;
    private TutorialView tutorialView;
    private TextView titleTextView;
    private RadialProgressView progressBar;

    private Toast lastToast;

    private String passcode = "";
    private long lastPasscodeInputTime;
    private boolean fragmentDetached = false;
    private boolean tutorial = false;

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
        createTutorialLayout(context);
        return backgroundFrameLayout;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createBackgroundFrameLayout() {
        backgroundFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.setWillNotDraw(false);
        backgroundFrameLayout.setBackgroundColor(Color.WHITE);
        backgroundFrameLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.performClick();
                onScreenClicked(event.getX(), event.getY());
            }
            return false;
        });
    }

    private void onScreenClicked(float x, float y) {
        lastPasscodeInputTime = System.currentTimeMillis();
        passcode += getPasscodeDigit(x, y);
        showToast(passcode);
        if (passcode.length() == 4) {
            delegate.passcodeEntered(passcode);
            passcode = "";
        } else if (passcode.length() > 4) {
            passcode = "";
        }
    }

    private int getPasscodeDigit(float x, float y) {
        // 1 | 2 | 3
        // 4 | 5 | 6
        // 7 | 8 | 9
        //     0
        float relativeX = x / backgroundFrameLayout.getWidth();
        float relativeY = y / backgroundFrameLayout.getHeight();
        if (relativeY > Constants.SECTION_BORDERS_Y[2]) {
            return 0;
        } else {
            for (int posX = 0; posX < Constants.SECTION_BORDERS_X.length; posX++) {
                if (relativeX > Constants.SECTION_BORDERS_X[posX]) {
                    continue;
                }
                for (int posY = 0; posY < Constants.SECTION_BORDERS_Y.length; posY++) {
                    if (relativeY > Constants.SECTION_BORDERS_Y[posY]) {
                        continue;
                    }
                    return posY * 3 + posX + 1;
                }
            }
        }
        throw new RuntimeException("Can't find loading screen digit for x = " + x + ", y = " + y);
    }

    private void showToast(String text) {
        if (!tutorial) {
            return;
        }
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
        titleTextView.setText(LocaleController.getString(R.string.LoadingPasscodeScreen_Loading));
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

    private void createTutorialLayout(Context context) {
        tutorialView = new TutorialView(context);
        backgroundFrameLayout.addView(tutorialView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        tutorialView.setVisibility(View.GONE);
    }

    @Override
    public void onShow(boolean fingerprint, boolean animated, boolean tutorial) {
        this.tutorial = tutorial;
        if (tutorial) {
            tutorialView.setVisibility(View.VISIBLE);
            createInstructionDialog().show();
        } else {
            tutorialView.setVisibility(View.GONE);
        }
        Activity parentActivity = AndroidUtilities.findActivity(context);
        if (parentActivity != null) {
            View currentFocus = parentActivity.getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            }
        }
    }

    private AlertDialog createInstructionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MaskedPasscodeScreenInstructionTitle));
        builder.setMessage(LocaleController.formatString(R.string.LoadingPasscodeScreen_Instruction, RESET_PASSCODE_TIME_SEC));
        AlertDialog dialog = builder.create();
        dialog.setCanCancel(false);
        dialog.setCancelable(false);
        DialogButtonWithTimer.setButton(dialog, AlertDialog.BUTTON_NEGATIVE, LocaleController.getString(R.string.OK), 5,
                (dlg, which) -> dlg.dismiss());
        return dialog;
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
        if (!passcode.isEmpty() && System.currentTimeMillis() - lastPasscodeInputTime > RESET_PASSCODE_TIME_SEC * 1000) {
            passcode = "";
            showToast(LocaleController.formatString(R.string.LoadingPasscodeScreen_PasscodeReset, RESET_PASSCODE_TIME_SEC));
        }
        AndroidUtilities.runOnUIThread(this::checkPasscodeResetTime, 1000);
    }

    @Override
    public void onPasscodeError() {
        if (tutorial) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(LocaleController.getString(R.string.MaskedPasscodeScreen_Tutorial));
            builder.setMessage(LocaleController.getString(R.string.MaskedPasscodeScreen_WrongPasscode));
            builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
            builder.create().show();
        }
    }
}
