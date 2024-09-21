package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BadPasscodeAttempt;
import org.telegram.messenger.FingerprintController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.masked_ptg.MaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.MaskedPtgConfig;

public class PasscodeView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private boolean showed = false;

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.passcodeDismissed) {
            if (args[0] != this) {
                setVisibility(GONE);
            }
        } else if (id == NotificationCenter.fakePasscodeActivated) {
            if (FakePasscodeUtils.isFakePasscodeActivated() && !FakePasscodeUtils.getActivatedFakePasscode().passcodeEnabled()) {
                appUnlocked();
            }
        }
    }

    public interface PasscodeViewDelegate {
        void didAcceptedPassword(PasscodeView view);
    }

    private PasscodeViewDelegate delegate;

    MaskedPasscodeScreen screen;

    public PasscodeView(final Context context) {
        super(context);


        setWillNotDraw(false);
        setVisibility(GONE);

        backgroundFrameLayout = new FrameLayout(context) {

            private Paint paint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                if (backgroundDrawable != null) {
                    if (backgroundDrawable instanceof MotionBackgroundDrawable || backgroundDrawable instanceof ColorDrawable || backgroundDrawable instanceof GradientDrawable) {
                        backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        backgroundDrawable.draw(canvas);
                    } else {
                        float scaleX = (float) getMeasuredWidth() / (float) backgroundDrawable.getIntrinsicWidth();
                        float scaleY = (float) (getMeasuredHeight() + keyboardHeight) / (float) backgroundDrawable.getIntrinsicHeight();
                        float scale = Math.max(scaleX, scaleY);
                        int width = (int) Math.ceil(backgroundDrawable.getIntrinsicWidth() * scale);
                        int height = (int) Math.ceil(backgroundDrawable.getIntrinsicHeight() * scale);
                        int x = (getMeasuredWidth() - width) / 2;
                        int y = (getMeasuredHeight() - height + keyboardHeight) / 2;
                        backgroundDrawable.setBounds(x, y, x + width, y + height);
                        backgroundDrawable.draw(canvas);
                    }
                } else {
                    super.onDraw(canvas);
                }
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
            }

            @Override
            public void setBackgroundColor(int color) {
                paint.setColor(color);
            }
        };
        backgroundFrameLayout.setWillNotDraw(false);
        addView(backgroundFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.passcode_lock, 58, 58);
        imageView.setAutoRepeat(false);
        addView(imageView, LayoutHelper.createFrame(58, 58, Gravity.LEFT | Gravity.TOP));

        passwordFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.addView(passwordFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        passcodeTextView = new TextView(context);
        passcodeTextView.setTextColor(0xffffffff);
        passcodeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18.33f);
        passcodeTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        passcodeTextView.setTypeface(AndroidUtilities.bold());
        passcodeTextView.setAlpha(0f);
        passwordFrameLayout.addView(passcodeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 128));

        retryTextView = new TextView(context);
        retryTextView.setTextColor(0xffffffff);
        retryTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        retryTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        retryTextView.setVisibility(INVISIBLE);
        backgroundFrameLayout.addView(retryTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        passwordEditText2 = new AnimatingTextView(context);
        passwordFrameLayout.addView(passwordEditText2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 70, 0, 70, 46));

        passwordEditText = new EditTextBoldCursor(context);
        passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
        passwordEditText.setTextColor(0xffffffff);
        passwordEditText.setMaxLines(1);
        passwordEditText.setLines(1);
        passwordEditText.setGravity(Gravity.CENTER_HORIZONTAL);
        passwordEditText.setSingleLine(true);
        passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordEditText.setTypeface(Typeface.DEFAULT);
        passwordEditText.setBackgroundDrawable(null);
        passwordEditText.setCursorColor(0xffffffff);
        passwordEditText.setCursorSize(dp(32));
        passwordFrameLayout.addView(passwordEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 70, 0, 70, 0));
        passwordEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                processDone(false);
                return true;
            }
            return false;
        });
        passwordEditText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                    boolean needAnimation = false;
                    MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) backgroundDrawable;
                    motionBackgroundDrawable.setAnimationProgressProvider(null);
                    float progress = motionBackgroundDrawable.getPosAnimationProgress();
                    boolean next;
                    if (count == 0 && after == 1) {
                        motionBackgroundDrawable.switchToNextPosition(true);
                        needAnimation = true;
                        next = true;
                    } else if (count == 1 && after == 0) {
                        motionBackgroundDrawable.switchToPrevPosition(true);
                        needAnimation = true;
                        next = false;
                    } else {
                        next = false;
                    }

                    if (needAnimation) {
                        if (progress >= 1f) {
                            animateBackground(motionBackgroundDrawable);
                        } else {
                            backgroundSpringQueue.offer(()-> {
                                if (next) {
                                    motionBackgroundDrawable.switchToNextPosition(true);
                                } else {
                                    motionBackgroundDrawable.switchToPrevPosition(true);
                                }
                                animateBackground(motionBackgroundDrawable);
                            });
                            backgroundSpringNextQueue.offer(next);

                            List<Runnable> remove = new ArrayList<>();
                            List<Integer> removeIndex = new ArrayList<>();
                            for (int i = 0; i < backgroundSpringQueue.size(); i++) {
                                Runnable callback = backgroundSpringQueue.get(i);
                                boolean qNext = backgroundSpringNextQueue.get(i);

                                if (qNext != next) {
                                    remove.add(callback);
                                    removeIndex.add(i);
                                }
                            }
                            for (Runnable callback : remove) {
                                backgroundSpringQueue.remove(callback);
                            }
                            for (int i : removeIndex) {
                                if (i < backgroundSpringNextQueue.size()) {
                                    backgroundSpringNextQueue.remove(i);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (passwordEditText.length() == 4 && SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN) {
                    processDone(false);
                }
            }
        });
        passwordEditText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public void onDestroyActionMode(ActionMode mode) {
            }

            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }
        });

        checkImage = new ImageView(context);
        checkImage.setImageResource(R.drawable.passcode_check);
        checkImage.setScaleType(ImageView.ScaleType.CENTER);
        checkImage.setBackgroundResource(R.drawable.bar_selector_lock);
        passwordFrameLayout.addView(checkImage, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 10, 4));
        checkImage.setContentDescription(LocaleController.getString(R.string.Done));
        checkImage.setOnClickListener(v -> processDone(false));

        fingerprintImage = new ImageView(context);
        fingerprintImage.setImageResource(R.drawable.fingerprint);
        fingerprintImage.setScaleType(ImageView.ScaleType.CENTER);
        fingerprintImage.setBackgroundResource(R.drawable.bar_selector_lock);
        passwordFrameLayout.addView(fingerprintImage, LayoutHelper.createFrame(BUTTON_SIZE, BUTTON_SIZE, Gravity.BOTTOM | Gravity.LEFT, 10, 0, 0, 4));
        fingerprintImage.setContentDescription(LocaleController.getString(R.string.AccDescrFingerprint));
        fingerprintImage.setOnClickListener(v -> checkFingerprint());

        screen = MaskedPtgConfig.createScreen(context, password -> processDone(false, password));
        addView(screen.createView());
        if (screen != null) {
            return;
        }

        setWillNotDraw(false);
        setVisibility(GONE);
    }

    public void setDelegate(PasscodeViewDelegate delegate) {
        this.delegate = delegate;
    }

    public void processDone(boolean fingerprint, String password) {
        if (!fingerprint) {
            if (password.length() == 0) {
                if (screen != null) {
                    screen.onPasscodeError();
                }
                return;
            }
            SharedConfig.PasscodeCheckResult result;
            if (SharedConfig.passcodeRetryInMs > 0) {
                result = SharedConfig.PasscodeCheckResult.createFailedResult();
            } else {
                result = SharedConfig.checkPasscode(password);
            }
            synchronized (FakePasscode.class) {
                if (SharedConfig.fakePasscodeActivatedIndex != SharedConfig.fakePasscodes.indexOf(result.fakePasscode)) {
                    result.activateFakePasscode();
                    SharedConfig.saveConfig();
                }
                if (!result.allowLogin() || result.fakePasscode != null && !result.fakePasscode.replaceOriginalPasscode
                        || SharedConfig.bruteForceProtectionEnabled && SharedConfig.bruteForceRetryInMillis > 0) {
                    BadPasscodeAttempt badAttempt = new BadPasscodeAttempt(BadPasscodeAttempt.AppUnlockType, result.fakePasscode != null);
                    SharedConfig.addBadPasscodeAttempt(badAttempt);
                    badAttempt.takePhotos(getContext());
                }
            }
            if (!result.allowLogin() || SharedConfig.bruteForceProtectionEnabled && SharedConfig.bruteForceRetryInMillis > 0) {
                if (SharedConfig.passcodeRetryInMs <= 0) {
                    SharedConfig.increaseBadPasscodeTries();
                }
                if (SharedConfig.passcodeRetryInMs > 0) {
                    checkRetryTextView();
                }
                if (screen != null) {
                    screen.onPasscodeError();
                }
                return;
            }
        } else {
            FakePasscode fakePasscode = FakePasscodeUtils.getFingerprintFakePasscode();
            synchronized (FakePasscode.class) {
                if (fakePasscode != null) {
                    fakePasscode.executeActions();
                } else if (FakePasscodeUtils.isFakePasscodeActivated()) {
                    FakePasscodeUtils.getActivatedFakePasscode().deactivate();
                }
                SharedConfig.fakePasscodeActivated(SharedConfig.fakePasscodes.indexOf(fakePasscode));
                SharedConfig.saveConfig();
                if (fakePasscode != null && !fakePasscode.replaceOriginalPasscode) {
                    BadPasscodeAttempt badAttempt = new BadPasscodeAttempt(BadPasscodeAttempt.AppUnlockType, true);
                    SharedConfig.addBadPasscodeAttempt(badAttempt);
                    badAttempt.takePhotos(getContext());
                }
            }
            if (fakePasscode != null && !fakePasscode.allowLogin) {
                SharedConfig.increaseBadPasscodeTries();
                if (SharedConfig.passcodeRetryInMs > 0) {
                    checkRetryTextView();
                }
                return;
            }
        }
        SharedConfig.badPasscodeTries = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && FingerprintController.isKeyReady() && FingerprintController.checkDeviceFingerprintsChanged()) {
            FingerprintController.deleteInvalidKey();
        }

        appUnlocked();
    }

    private void appUnlocked() {
        SharedConfig.setAppLocked(false);
        SharedConfig.saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
        setOnTouchListener(null);
        if (delegate != null) {
            delegate.didAcceptedPassword(this);
        }

        AndroidUtilities.runOnUIThread(this::hidePasscodeView);
    }

    private void hidePasscodeView() {
        if (!showed) {
            AndroidUtilities.runOnUIThread(this::hidePasscodeView, 500);
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(shownT, 0);
        va.addUpdateListener(anm -> {
            shownT = (float) anm.getAnimatedValue();
            onAnimationUpdate(shownT);
            setAlpha(shownT);
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(View.GONE);
                onHidden();
                onAnimationUpdate(shownT = 0f);
                setAlpha(0f);
            }
        });
        va.setDuration(420);
        va.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        va.start();
    }

    private float shownT;
    protected void onAnimationUpdate(float open) {

    }

    protected void onHidden() {

    }

    private Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            checkRetryTextView();
            AndroidUtilities.runOnUIThread(checkRunnable, 100);
        }
    };

    private void checkRetryTextView() {
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime > SharedConfig.lastUptimeMillis) {
            SharedConfig.passcodeRetryInMs -= (currentTime - SharedConfig.lastUptimeMillis);
            if (SharedConfig.passcodeRetryInMs < 0) {
                SharedConfig.passcodeRetryInMs = 0;
            }
            SharedConfig.bruteForceRetryInMillis -= (currentTime - SharedConfig.lastUptimeMillis);
            if (SharedConfig.bruteForceRetryInMillis < 0) {
                SharedConfig.bruteForceRetryInMillis = 0;
            }
        }
        SharedConfig.lastUptimeMillis = currentTime;
        SharedConfig.saveConfig();
        if (SharedConfig.passcodeRetryInMs > 0) {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            AndroidUtilities.runOnUIThread(checkRunnable, 100);
        } else {
            if (!SharedConfig.bruteForceProtectionEnabled || SharedConfig.bruteForceRetryInMillis <= 0) {
                AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            }
        }
    }

    public void onResume() {
        checkRetryTextView();
    }

    public boolean onBackPressed() {
        return screen.onBackPressed();
    }

    public void onPause() {
        AndroidUtilities.cancelRunOnUIThread(checkRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (screen != null) {
            screen.onAttachedToWindow();
        }

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didGenerateFingerprintKeyPair);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.passcodeDismissed);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.fakePasscodeActivated);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (screen != null) {
            screen.onDetachedFromWindow();
        }

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didGenerateFingerprintKeyPair);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.passcodeDismissed);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.fakePasscodeActivated);
    }

    public void onShow(boolean fingerprint, boolean animated) {
        onShow(fingerprint, animated, -1, -1, null, null);
    }

    public void onShow(boolean fingerprint, boolean animated, int x, int y, Runnable onShow, Runnable onStart) {
        checkRetryTextView();
        Activity parentActivity = AndroidUtilities.findActivity(getContext());
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD) {

        } else {
            if (parentActivity != null) {
                View currentFocus = parentActivity.getCurrentFocus();
                if (currentFocus != null) {
                    currentFocus.clearFocus();
                    AndroidUtilities.hideKeyboard(((Activity) getContext()).getCurrentFocus());
                    AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
                }
            }
        }
        if (getVisibility() == View.VISIBLE) {
            return;
        }
        setTranslationY(0);
        setVisibility(View.VISIBLE);
        if (screen != null) {
            screen.onShow(fingerprint, animated);
        }
        checkRetryTextView();
        if (animated) {
            setAlpha(0.0f);
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    setAlpha(1.0f);
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    AndroidUtilities.runOnUIThread(() -> performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING), 350);
                }
            });
            requestLayout();
        } else {
            setAlpha(1.0f);
            onAnimationUpdate(shownT = 1f);
            if (onShow != null) {
                onShow.run();
            }
        }

        setOnTouchListener((v, event) -> true);
        showed = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (screen != null) {
            screen.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
