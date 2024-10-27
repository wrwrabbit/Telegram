package org.telegram.messenger.partisan.masked_ptg.note;

import android.content.Context;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

import org.telegram.messenger.R;

class TutorialArrow extends AppCompatImageView {
    private final Animation animation;

    TutorialArrow(@NonNull Context context, int rotation, float fromAnimationLength, float toAnimationLength) {
        super(context);

        setImageResource(R.drawable.photo_arrowshape);
        setColorFilter(Colors.tipColor);
        setRotation(45 - rotation); // 45 - because the drawable is pointing right-up by default. Minus - because setRotation is clockwise.

        float rad = (float) Math.toRadians(rotation);
        float fromXDelta = (float) Math.cos(rad) * fromAnimationLength;
        float fromYDelta = (float) -Math.sin(rad) * fromAnimationLength;
        float toXDelta = (float) Math.cos(rad) * toAnimationLength;
        float toYDelta = (float) -Math.sin(rad) * toAnimationLength;

        animation = new TranslateAnimation(fromXDelta, toXDelta, fromYDelta, toYDelta);

        animation.setInterpolator(new AccelerateInterpolator());
        animation.setDuration(700);
        animation.setRepeatCount(Animation.INFINITE);
        animation.setRepeatMode(Animation.REVERSE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation(animation);
    }

    @Override
    public void setVisibility(int visibility) {
        int oldVisibility = getVisibility();
        super.setVisibility(visibility);

        if (oldVisibility != visibility) {
            clearAnimation();
            if (visibility == View.VISIBLE) {
                animation.reset();
                startAnimation(animation);
            }
        }
    }
}
