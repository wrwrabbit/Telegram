package org.telegram.ui.Components;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class CheckBoxSquareThreeState extends View {

    public enum State {
        CHECKED,
        UNCHECKED,
        INDETERMINATE
    }

    private RectF rectF;

    private Bitmap drawBitmap;
    private Canvas drawCanvas;

    private float checkedProgress;
    private float indeterminateProgress;
    private ObjectAnimator checkAnimator;
    private ObjectAnimator indeterminateAnimator;

    private boolean attachedToWindow;
    private boolean isDisabled;
    private State state;
    private boolean isAlert;

    private int key1;
    private int key2;
    private int key3;

    public CheckBoxSquareThreeState(Context context, boolean alert) {
        super(context);
        if (Theme.checkboxSquare_backgroundPaint == null) {
            Theme.createCommonResources(context);
        }

        key1 = isAlert ? Theme.key_dialogCheckboxSquareUnchecked : Theme.key_checkboxSquareUnchecked;
        key2 = isAlert ? Theme.key_dialogCheckboxSquareBackground : Theme.key_checkboxSquareBackground;
        key3 = isAlert ? Theme.key_dialogCheckboxSquareCheck : Theme.key_checkboxSquareCheck;

        rectF = new RectF();
        drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(18), AndroidUtilities.dp(18), Bitmap.Config.ARGB_4444);
        drawCanvas = new Canvas(drawBitmap);
        isAlert = alert;
    }

    public void setColors(int unchecked, int checked, int check) {
        key1 = unchecked;
        key2 = checked;
        key3 = check;
    }

    @Keep
    public void setCheckedProgress(float value) {
        if (checkedProgress == value) {
            return;
        }
        checkedProgress = value;
        invalidate();
    }

    @Keep
    public float getCheckedProgress() {
        return checkedProgress;
    }

    @Keep
    public void setIndeterminateProgress(float value) {
        if (indeterminateProgress == value) {
            return;
        }
        indeterminateProgress = value;
        invalidate();
    }

    @Keep
    public float getIndeterminateProgress() {
        return indeterminateProgress;
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
        }
    }

    private void animateToState(State state) {
        checkAnimator = ObjectAnimator.ofFloat(this, "checkedProgress", state == State.CHECKED ? 1 : 0);
        checkAnimator.setDuration(300);
        checkAnimator.start();

        indeterminateAnimator = ObjectAnimator.ofFloat(this, "indeterminateProgress", state == State.INDETERMINATE ? 1 : 0);
        indeterminateAnimator.setDuration(300);
        indeterminateAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    public void setState(State state, boolean animated) {
        if (state == this.state) {
            return;
        }
        this.state = state;
        if (attachedToWindow && animated) {
            animateToState(state);
        } else {
            cancelCheckAnimator();
            setCheckedProgress(state == State.CHECKED ? 1.0f : 0.0f);
            setIndeterminateProgress(state == State.INDETERMINATE ? 1.0f : 0.0f);
        }
    }

    public void setDisabled(boolean disabled) {
        isDisabled = disabled;
        invalidate();
    }

    public State getState() {
        return state;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }

        float checkProgress;
        float bounceProgress;
        int uncheckedColor = Theme.getColor(key1);
        int color = Theme.getColor(key2);
        float maxProgress = Math.max(checkedProgress, indeterminateProgress);
        if (maxProgress <= 0.5f) {
            bounceProgress = checkProgress = maxProgress / 0.5f;
            int rD = (int) ((Color.red(color) - Color.red(uncheckedColor)) * checkProgress);
            int gD = (int) ((Color.green(color) - Color.green(uncheckedColor)) * checkProgress);
            int bD = (int) ((Color.blue(color) - Color.blue(uncheckedColor)) * checkProgress);
            int c = Color.rgb(Color.red(uncheckedColor) + rD, Color.green(uncheckedColor) + gD, Color.blue(uncheckedColor) + bD);
            Theme.checkboxSquare_backgroundPaint.setColor(c);
        } else {
            bounceProgress = 2.0f - maxProgress / 0.5f;
            checkProgress = 1.0f;
            Theme.checkboxSquare_backgroundPaint.setColor(color);
        }
        if (isDisabled) {
            Theme.checkboxSquare_backgroundPaint.setColor(Theme.getColor(isAlert ? Theme.key_dialogCheckboxSquareDisabled : Theme.key_checkboxSquareDisabled));
        }
        float bounce = AndroidUtilities.dp(1) * bounceProgress;
        rectF.set(bounce, bounce, AndroidUtilities.dp(18) - bounce, AndroidUtilities.dp(18) - bounce);

        drawBitmap.eraseColor(0);
        drawCanvas.drawRoundRect(rectF, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.checkboxSquare_backgroundPaint);

        if (checkProgress != 1) {
            float rad = Math.min(AndroidUtilities.dp(7), AndroidUtilities.dp(7) * checkProgress + bounce);
            rectF.set(AndroidUtilities.dp(2) + rad, AndroidUtilities.dp(2) + rad, AndroidUtilities.dp(16) - rad, AndroidUtilities.dp(16) - rad);
            drawCanvas.drawRect(rectF, Theme.checkboxSquare_eraserPaint);
        }

        if (checkedProgress > 0.5f) {
            Theme.checkboxSquare_checkPaint.setColor(Theme.getColor(key3));

            int endX = (int) (AndroidUtilities.dp(7) - AndroidUtilities.dp(3) * (1.0f - bounceProgress));
            int endY = (int) (AndroidUtilities.dpf2(13) - AndroidUtilities.dp(3) * (1.0f - bounceProgress));
            drawCanvas.drawLine(AndroidUtilities.dp(7), (int) AndroidUtilities.dpf2(13), endX, endY, Theme.checkboxSquare_checkPaint);

            endX = (int) (AndroidUtilities.dpf2(7) + AndroidUtilities.dp(7) * (1.0f - bounceProgress));
            endY = (int) (AndroidUtilities.dpf2(13) - AndroidUtilities.dp(7) * (1.0f - bounceProgress));
            drawCanvas.drawLine((int) AndroidUtilities.dpf2(7), (int) AndroidUtilities.dpf2(13), endX, endY, Theme.checkboxSquare_checkPaint);
        }

        if (indeterminateProgress > 0.5f) {
            Theme.checkboxSquare_checkPaint.setColor(Theme.getColor(key3));

            int startX = AndroidUtilities.dp(4);
            int endX = (int) (AndroidUtilities.dp(4) + AndroidUtilities.dp(10) * (1.0f - bounceProgress));
            int y = (int) AndroidUtilities.dpf2(9);
            drawCanvas.drawLine(startX, y, endX, y, Theme.checkboxSquare_checkPaint);
        }
        canvas.drawBitmap(drawBitmap, 0, 0, null);
    }
}

