package org.telegram.messenger.partisan.masked_ptg.note;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

class NoteCell extends FrameLayout {
    private TextView titleTextView;
    private TextView descriptionTextView;

    NoteCell(Context context) {
        super(context);
        createTitle(context);
        createDescription(context);
        setupSelf();
    }

    private void createTitle(Context context) {
        titleTextView = new TextView(context);
        titleTextView.setTextColor(Colors.noteTitleColor);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setLines(1);
        titleTextView.setMaxLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setTypeface(Typeface.create(titleTextView.getTypeface(), Typeface.BOLD));
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 11, 21, 0));
    }

    private void createDescription(Context context) {
        descriptionTextView = new TextView(context);
        descriptionTextView.setTextColor(Colors.descriptionColor);
        descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descriptionTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        descriptionTextView.setLines(1);
        descriptionTextView.setMaxLines(3);
        descriptionTextView.setPadding(0, 0, 0, 0);
        descriptionTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(descriptionTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 35, 21, 11));
    }

    private void setupSelf() {
        setClipChildren(false);

        int rad = dp(9);
        float[] radii = new float[]{rad, rad, rad, rad, rad, rad, rad, rad};
        ShapeDrawable backgroundDrawable = new ShapeDrawable(new RoundRectShape(radii, null, null));
        backgroundDrawable.getPaint().setColor(Colors.cellBackgroundColor);

        rad = 9;
        radii = new float[]{rad, rad, rad, rad, rad, rad, rad, rad};
        setBackground(Theme.AdaptiveRipple.filledRect(backgroundDrawable, Colors.cellBackgroundRippleColor, radii));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }

    void setTitleAndDescription(String title, String description) {
        titleTextView.setText(title);
        descriptionTextView.setText(description);
    }
}
