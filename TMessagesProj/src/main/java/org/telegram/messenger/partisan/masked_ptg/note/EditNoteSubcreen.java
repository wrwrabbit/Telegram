package org.telegram.messenger.partisan.masked_ptg.note;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Components.LayoutHelper.MATCH_PARENT;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.RelativeLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;

public class EditNoteSubcreen extends RelativeLayout {
    interface NoteListSubscreenDelegate {
        void onNoteEdited(Note note);
    }

    private Note note;
    private final NoteListSubscreenDelegate delegate;

    private EditTextBoldCursor titleEditText;
    private EditTextBoldCursor descriptionEditText;
    private Button acceptButton;

    EditNoteSubcreen(Context context, NoteListSubscreenDelegate delegate) {
        super(context);
        this.delegate = delegate;

        createAcceptButton();
        createTitleEditText();
        createDescriptionEditText();
    }

    private void createAcceptButton() {
        acceptButton = new Button(getContext());
        acceptButton.setTextColor(Color.BLACK);
        acceptButton.setTextSize(20);
        acceptButton.setId(generateViewId());

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Colors.primaryColor);
        acceptButton.setBackground(Theme.AdaptiveRipple.filledCircle(shape, Colors.primaryColor, -1));

        LayoutParams relativeParams = new LayoutParams(dp(48), dp(48));
        relativeParams.addRule(ALIGN_PARENT_TOP);
        relativeParams.addRule(ALIGN_PARENT_END);
        acceptButton.setOnClickListener((v) -> {
            AndroidUtilities.hideKeyboard(titleEditText);
            AndroidUtilities.hideKeyboard(descriptionEditText);
            note.title = titleEditText.getText().toString();
            note.description = descriptionEditText.getText().toString();
            delegate.onNoteEdited(note);
        });
        addView(acceptButton, relativeParams);
    }

    private void createTitleEditText() {
        titleEditText = new EditTextBoldCursor(getContext());
        titleEditText.setTextColor(Colors.noteTitleColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{Colors.primaryColor}
            );
            titleEditText.setBackgroundTintList(colorStateList);
        }
        titleEditText.setId(generateViewId());
        titleEditText.setHint("Title");
        titleEditText.setHintTextColor(Colors.noteTitleHintColor);
        titleEditText.setCursorColor(Colors.primaryColor);
        titleEditText.setHandlesColor(Colors.primaryColor);
        titleEditText.setMaxLines(1);
        titleEditText.setSingleLine();
        titleEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
        LayoutParams relativeParams = new LayoutParams(MATCH_PARENT, dp(48));
        relativeParams.addRule(ALIGN_PARENT_TOP);
        relativeParams.addRule(ALIGN_PARENT_START);
        relativeParams.addRule(LEFT_OF, acceptButton.getId());
        addView(titleEditText, relativeParams);
    }

    private void createDescriptionEditText() {
        descriptionEditText = new EditTextBoldCursor(getContext());
        descriptionEditText.setTextColor(Colors.descriptionColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{Colors.primaryColor}
            );
            descriptionEditText.setBackgroundTintList(colorStateList);
        }
        descriptionEditText.setId(generateViewId());
        descriptionEditText.setHint("Description");
        descriptionEditText.setHintTextColor(Colors.descriptionHintColor);
        descriptionEditText.setCursorColor(Colors.primaryColor);
        descriptionEditText.setHandlesColor(Colors.primaryColor);
        descriptionEditText.setSingleLine(false);
        descriptionEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        descriptionEditText.setGravity(Gravity.TOP);
        LayoutParams relativeParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        relativeParams.addRule(BELOW, titleEditText.getId());
        relativeParams.addRule(ALIGN_PARENT_START);
        relativeParams.addRule(LEFT_OF, acceptButton.getId());
        addView(descriptionEditText, relativeParams);
    }

    void bindNote(Note note) {
        this.note = note;
        titleEditText.setText(note.title);
        descriptionEditText.setText(note.description);
        if (note.title == null && note.description == null) {
            acceptButton.setText("+");
        } else {
            acceptButton.setText("+");
        }
    }
}
