package org.telegram.messenger.partisan.masked_ptg.note;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.partisan.masked_ptg.AbstractMaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.MaskedPtgConfig;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;
import org.telegram.messenger.partisan.masked_ptg.TutorialType;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class NotePasscodeScreen extends AbstractMaskedPasscodeScreen
        implements NoteListSubscreen.NoteListSubscreenDelegate
        , EditNoteSubcreen.NoteListSubscreenDelegate {

    private Note currentNote;
    private int currentNotePos = -1;

    private FrameLayout backgroundFrameLayout;
    private NoteListSubscreen noteListSubscreen;
    private EditNoteSubcreen editNoteSubscreen;

    public NotePasscodeScreen(Context context, PasscodeEnteredDelegate delegate, boolean unlockingApp) {
        super(context, delegate, unlockingApp);
        NoteStorage.loadNotes();
    }

    @Override
    public View createView() {
        createBackgroundFrameLayout();

        createNoteListSubscreen();
        createSingleNoteSubscreen();
        return backgroundFrameLayout;
    }

    private void createNoteListSubscreen() {
        noteListSubscreen = new NoteListSubscreen(context, this);
        FrameLayout.LayoutParams params = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        params.setMargins(0, AndroidUtilities.statusBarHeight, 0, AndroidUtilities.navigationBarHeight);
        backgroundFrameLayout.addView(noteListSubscreen, params);
    }

    private void createSingleNoteSubscreen() {
        editNoteSubscreen = new EditNoteSubcreen(context, this);
        FrameLayout.LayoutParams params = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        params.setMargins(0, AndroidUtilities.statusBarHeight, 0, AndroidUtilities.navigationBarHeight);
        editNoteSubscreen.setVisibility(View.GONE);
        backgroundFrameLayout.addView(editNoteSubscreen, params);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createBackgroundFrameLayout() {
        backgroundFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.setWillNotDraw(false);
        backgroundFrameLayout.setBackgroundColor(Colors.screenBackgroundColor);
    }

    @Override
    public void onShow(boolean fingerprint, boolean animated, TutorialType tutorialType) {
        Activity parentActivity = AndroidUtilities.findActivity(context);
        if (parentActivity != null) {
            View currentFocus = parentActivity.getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            }
        }
        setTutorial(tutorialType);
    }

    private void setTutorial(TutorialType tutorialType) {
        this.tutorialType = tutorialType;
        noteListSubscreen.setTutorial(tutorialType != TutorialType.DISABLED);
        editNoteSubscreen.setTutorial(tutorialType != TutorialType.DISABLED);
        if (tutorialType == TutorialType.FULL) {
            createInstructionDialog().show();
        }
    }

    private AlertDialog createInstructionDialog() {
        String message = LocaleController.getString(R.string.NotePasscodeScreen_Instruction);
        return createMaskedPasscodeScreenInstructionDialog(message, 0);
    }

    @Override
    public void onNoteClicked(int pos) {
        currentNotePos = pos;
        editNoteSubscreen.bindNote(NoteStorage.notes.get(pos));
        showEditNoteSubscreen();
    }

    @Override
    public void onNoteSelected(int pos) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context, key -> {
            if (key == Theme.key_dialogBackground) {
                return Colors.dialogBackgroundColor;
            } else if (key == Theme.key_dialogTextBlack) {
                return Colors.noteTitleColor;
            } else if (key == Theme.key_dialogButton) {
                return getPrimaryColor();
            }
            return getPrimaryColor();
        });
        dialogBuilder.setTitle(LocaleController.getString(R.string.AppName));
        dialogBuilder.setMessage("Delete the note?");
        dialogBuilder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dlg, which) -> {
            NoteStorage.notes.remove(pos);
            NoteStorage.saveNotes();
            noteListSubscreen.notifyNoteRemoved(pos);
        });
        dialogBuilder.setNeutralButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = dialogBuilder.create();
        int rad = dp(9);
        float[] radii = new float[]{rad, rad, rad, rad, rad, rad, rad, rad};
        ShapeDrawable backgroundDrawable = new ShapeDrawable(new RoundRectShape(radii, null, null));
        backgroundDrawable.getPaint().setColor(Colors.cellBackgroundColor);
        dialog.getWindow().setBackgroundDrawable(backgroundDrawable);
        dialog.show();
    }

    @Override
    public void onAddNote() {
        currentNotePos = -1;
        editNoteSubscreen.bindNote(new Note());
        showEditNoteSubscreen();
    }

    @Override
    public void onNoteEdited(Note note) {
        currentNote = note; // If passcode fails, onPasscodeError will be called. Otherwise the note with a correct passcode will not be added.
        delegate.passcodeEntered(note.title);
        currentNote = null;
        hideEditNoteSubscreen();
    }

    private void hideEditNoteSubscreen() {
        showEditNoteSubscreen(false);
    }

    private void showEditNoteSubscreen() {
        showEditNoteSubscreen(true);
    }

    private void showEditNoteSubscreen(boolean show) {
        noteListSubscreen.setVisibility(show ? View.GONE : View.VISIBLE);
        editNoteSubscreen.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPasscodeError() {
        if (currentNotePos != -1) {
            noteListSubscreen.notifyNoteEdited(currentNotePos);
        } else {
            NoteStorage.notes.add(currentNote);
            noteListSubscreen.notifyNoteAdded();
        }
        NoteStorage.saveNotes();
        if (tutorialType != TutorialType.DISABLED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(LocaleController.getString(R.string.MaskedPasscodeScreen_Tutorial));
            builder.setMessage(LocaleController.getString(R.string.MaskedPasscodeScreen_WrongPasscode));
            builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
            builder.create().show();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (editNoteSubscreen.getVisibility() == View.VISIBLE) {
            AndroidUtilities.runOnUIThread(this::hideEditNoteSubscreen, 200);
            return false;
        } else {
            return true;
        }
    }

    private int getPrimaryColor() {
        return MaskedPtgConfig.getPrimaryColor(context);
    }
}
