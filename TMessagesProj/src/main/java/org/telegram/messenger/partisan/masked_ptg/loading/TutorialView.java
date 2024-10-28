package org.telegram.messenger.partisan.masked_ptg.loading;

import static org.telegram.messenger.partisan.masked_ptg.loading.Constants.SECTION_BORDERS_X;
import static org.telegram.messenger.partisan.masked_ptg.loading.Constants.SECTION_BORDERS_Y;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

class TutorialView extends View {
    private final Paint paint = new Paint();
    private ValueAnimator animator;
    private float colorAlpha = 0.0f;

    public TutorialView(Context context) {
        super(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        paint.setColor(Color.GRAY);
        paint.setTextSize(AndroidUtilities.dp(72));
        paint.setTextAlign(Paint.Align.CENTER);

        animator = ValueAnimator.ofFloat(0, 1).setDuration(2000);
        animator.setInterpolator(new CubicBezierInterpolator(1, -0.67, 0.75, 1));
        animator.addUpdateListener(animation -> {
            colorAlpha = Math.max((float) animation.getAnimatedValue(), 0.0f);
            invalidate();
        });
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // 1 | 2 | 3
        // 4 | 5 | 6
        // 7 | 8 | 9
        //     0

        paint.setAlpha((int)(colorAlpha * 255));
        drawHorizontalLines(canvas);
        drawVerticalLines(canvas);
        drawDigits(canvas);
    }

    private void drawHorizontalLines(Canvas canvas) {
        for (float relativeY : SECTION_BORDERS_Y) {
            canvas.drawLine(0, relativeY * getHeight(), getWidth(), relativeY * getHeight(), paint);
        }
    }

    private void drawVerticalLines(Canvas canvas) {
        float relativeLowerY = SECTION_BORDERS_Y[SECTION_BORDERS_Y.length - 1 - 1]; // SECTION_BORDERS_Y[SECTION_BORDERS_Y.length - 1] is 1.0f
        for (float relativeX : SECTION_BORDERS_X) {
            canvas.drawLine(relativeX * getWidth(), 0, relativeX * getWidth(), relativeLowerY * getHeight(), paint);
        }
    }

    private void drawDigits(Canvas canvas) {
        for (int posX = 0; posX < SECTION_BORDERS_X.length; posX++) {
            for (int posY = 0; posY < SECTION_BORDERS_Y.length - 1; posY++) {
                drawDigit(canvas, posX, posY);
            }
        }

        drawDigit(canvas, 0, SECTION_BORDERS_Y.length - 1);
    }

    private void drawDigit(Canvas canvas, int posX, int posY) {
        int digit;
        float relativeSectionCenterX;
        float relativeSectionCenterY = getSectionCenter(SECTION_BORDERS_Y, posY);
        if (posY < SECTION_BORDERS_Y.length - 1) {
            relativeSectionCenterX = getSectionCenter(SECTION_BORDERS_X, posX);
            digit = posY * 3 + posX + 1;
        } else {
            relativeSectionCenterX = 0.5f;
            digit = 0;
        }
        float textCenterShift = (paint.descent() + paint.ascent()) / 2;
        float x = relativeSectionCenterX * getWidth();
        float y = relativeSectionCenterY * getHeight() - textCenterShift;

        canvas.drawText(Integer.toString(digit), x, y, paint);
    }

    private static float getSectionCenter(float[] sectionBorders, int borderPos) {
        float border = sectionBorders[borderPos];
        float previousBorder = borderPos > 0 ? sectionBorders[borderPos - 1] : 0;
        float sectionLength = border - previousBorder;
        return sectionLength / 2 + previousBorder;
    }
}
