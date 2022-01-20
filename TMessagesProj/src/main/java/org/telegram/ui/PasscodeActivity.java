/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BadPasscodeAttempt;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.messenger.support.fingerprint.FingerprintManagerCompat;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PasscodeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private TextView titleTextView;
    private EditTextBoldCursor passwordEditText;
    private TextView dropDown;
    private ActionBarMenuItem dropDownContainer;
    private Drawable dropDownDrawable;

    private int type;
    private int currentPasswordType = 0;
    private int passcodeSetStep = 0;
    private String firstPassword;

    private int badPasscodeTries;
    private long lastPasscodeTry;

    private int passcodeRow;
    private int changePasscodeRow;
    private int passcodeDetailRow;
    private int captureRow;
    private int captureDetailRow;
    private int fingerprintRow;
    private int autoLockRow;
    private int autoLockDetailRow;

    private int bruteForceProtectionRow;
    private int bruteForceProtectionDetailRow;

    private int clearCacheOnLockRow;
    private int clearCacheOnLockDetailRow;

    private int badPasscodeAttemptsRow;
    private int badPasscodePhotoFrontRow;
    private int badPasscodePhotoBackRow;
    private int badPasscodeMuteAudioRow;
    private int badPasscodeAttemptsDetailRow;

    private int fakePasscodesHeaderRow;
    private int firstFakePasscodeRow;
    private int lastFakePasscodeRow;
    private int addFakePasscodeRow;
    private int fakePasscodeDetailRow;
    private int partisanSettingsRow;
    private int partisanSettingsDetailRow;
    private int rowCount;

    TextCheckCell frontPhotoTextCell;
    TextCheckCell backPhotoTextCell;

    private final static int done_button = 1;
    private final static int pin_item = 2;
    private final static int password_item = 3;

    public PasscodeActivity(int type) {
        super();
        this.type = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        if (type == 0) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetPasscode);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (type == 0) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        }
    }

    @Override
    public View createView(Context context) {
        if (type != 3) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (passcodeSetStep == 0) {
                        processNext();
                    } else if (passcodeSetStep == 1) {
                        processDone();
                    }
                } else if (id == pin_item) {
                    currentPasswordType = 0;
                    updateDropDownTextView();
                } else if (id == password_item) {
                    currentPasswordType = 1;
                    updateDropDownTextView();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        if (type != 0) {
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

            titleTextView = new TextView(context);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            if (type == 1) {
                if (SharedConfig.passcodeEnabled()) {
                    titleTextView.setText(LocaleController.getString("EnterNewPasscode", R.string.EnterNewPasscode));
                } else {
                    titleTextView.setText(LocaleController.getString("EnterNewFirstPasscode", R.string.EnterNewFirstPasscode));
                }
            } else {
                titleTextView.setText(LocaleController.getString("EnterCurrentPasscode", R.string.EnterCurrentPasscode));
            }
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            frameLayout.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 38, 0, 0));

            passwordEditText = new EditTextBoldCursor(context);
            passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            passwordEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            passwordEditText.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            passwordEditText.setMaxLines(1);
            passwordEditText.setLines(1);
            passwordEditText.setGravity(Gravity.CENTER_HORIZONTAL);
            passwordEditText.setSingleLine(true);
            if (type == 1) {
                passcodeSetStep = 0;
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            } else {
                passcodeSetStep = 1;
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            }
            passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            passwordEditText.setTypeface(Typeface.DEFAULT);
            passwordEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            passwordEditText.setCursorSize(AndroidUtilities.dp(20));
            passwordEditText.setCursorWidth(1.5f);
            frameLayout.addView(passwordEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 40, 90, 40, 0));
            passwordEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (passcodeSetStep == 0) {
                    processNext();
                    return true;
                } else if (passcodeSetStep == 1) {
                    processDone();
                    return true;
                }
                return false;
            });
            passwordEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (passwordEditText.length() == 4) {
                        if (type == 2 && SharedConfig.passcodeType == 0) {
                            processDone();
                        } else if (type == 1 && currentPasswordType == 0) {
                            if (passcodeSetStep == 0) {
                                processNext();
                            } else if (passcodeSetStep == 1) {
                                processDone();
                            }
                        }
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

            if (type == 1) {
                frameLayout.setTag(Theme.key_windowBackgroundWhite);
                dropDownContainer = new ActionBarMenuItem(context, menu, 0, 0);
                dropDownContainer.setSubMenuOpenSide(1);
                dropDownContainer.addSubItem(pin_item, LocaleController.getString("PasscodePIN", R.string.PasscodePIN));
                dropDownContainer.addSubItem(password_item, LocaleController.getString("PasscodePassword", R.string.PasscodePassword));
                actionBar.addView(dropDownContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 64 : 56, 0, 40, 0));
                dropDownContainer.setOnClickListener(view -> dropDownContainer.toggleSubMenu());

                dropDown = new TextView(context);
                dropDown.setGravity(Gravity.LEFT);
                dropDown.setSingleLine(true);
                dropDown.setLines(1);
                dropDown.setMaxLines(1);
                dropDown.setEllipsize(TextUtils.TruncateAt.END);
                dropDown.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
                dropDown.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                dropDownDrawable = context.getResources().getDrawable(R.drawable.ic_arrow_drop_down).mutate();
                dropDownDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultTitle), PorterDuff.Mode.MULTIPLY));
                dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, dropDownDrawable, null);
                dropDown.setCompoundDrawablePadding(AndroidUtilities.dp(4));
                dropDown.setPadding(0, 0, AndroidUtilities.dp(10), 0);
                dropDownContainer.addView(dropDown, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 1));
            } else {
                actionBar.setTitle(LocaleController.getString("Passcode", R.string.Passcode));
            }

            updateDropDownTextView();
        } else {
            actionBar.setTitle(LocaleController.getString("Passcode", R.string.Passcode));
            frameLayout.setTag(Theme.key_windowBackgroundGray);
            frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            listView = new RecyclerListView(context);
            listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }
            });
            listView.setVerticalScrollBarEnabled(false);
            listView.setItemAnimator(null);
            listView.setLayoutAnimation(null);
            frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            listView.setAdapter(listAdapter = new ListAdapter(context));
            listView.setOnItemClickListener((view, position) -> {
                if (!view.isEnabled()) {
                    return;
                }
                if (position == changePasscodeRow) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (SharedConfig.fakePasscodes.isEmpty() || SharedConfig.getActivatedFakePasscode() != null) {
                        presentFragment(new PasscodeActivity(1));
                    } else {
                        requireFakePasscodesDeletionConfirmation((dialogInterface, i) -> presentFragment(new PasscodeActivity(1)));
                    }
                } else if (position == passcodeRow) {
                    TextCheckCell cell = (TextCheckCell) view;
                    if (SharedConfig.passcodeEnabled()) {
                        if (SharedConfig.fakePasscodes.isEmpty() || SharedConfig.getActivatedFakePasscode() != null) {
                            resetPasscode();
                            cell.setChecked(SharedConfig.passcodeEnabled());
                        } else {
                            requireFakePasscodesDeletionConfirmation((dialogInterface, i) -> {
                                resetPasscode();
                                cell.setChecked(SharedConfig.passcodeEnabled());
                            });
                        }
                    } else {
                        presentFragment(new PasscodeActivity(1));
                    }
                } else if (position == autoLockRow) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AutoLock", R.string.AutoLock));
                    final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                    numberPicker.setMinValue(0);
                    int fakePasscodeValueShift = SharedConfig.getActivatedFakePasscode() == null ? 0 : 1;
                    numberPicker.setMaxValue(5 - fakePasscodeValueShift);
                    if (SharedConfig.getAutoLockIn() == 0) {
                        numberPicker.setValue(0);
                    } else if (SharedConfig.getAutoLockIn() == 1) {
                        numberPicker.setValue(1);
                    } else if (SharedConfig.getAutoLockIn() == 60) {
                        numberPicker.setValue(2 - fakePasscodeValueShift);
                    } else if (SharedConfig.getAutoLockIn() == 60 * 5) {
                        numberPicker.setValue(3 - fakePasscodeValueShift);
                    } else if (SharedConfig.getAutoLockIn() == 60 * 60) {
                        numberPicker.setValue(4 - fakePasscodeValueShift);
                    } else if (SharedConfig.getAutoLockIn() == 60 * 60 * 5) {
                        numberPicker.setValue(5 - fakePasscodeValueShift);
                    }
                    numberPicker.setFormatter(value -> {
                        if (value == 0) {
                            return LocaleController.getString("AutoLockDisabled", R.string.AutoLockDisabled);
                        } else if (fakePasscodeValueShift == 0 && value == 1) {
                            return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Seconds", 1));
                        } else if (value == 2 - fakePasscodeValueShift) {
                            return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Minutes", 1));
                        } else if (value == 3 - fakePasscodeValueShift) {
                            return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Minutes", 5));
                        } else if (value == 4 - fakePasscodeValueShift) {
                            return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Hours", 1));
                        } else if (value == 5 - fakePasscodeValueShift) {
                            return LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Hours", 5));
                        }
                        return "";
                    });
                    builder.setView(numberPicker);
                    builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), (dialog, which) -> {
                        which = numberPicker.getValue();
                        if (which == 0) {
                            SharedConfig.autoLockIn = 0;
                        } else if (fakePasscodeValueShift == 0 && which == 1) {
                            SharedConfig.autoLockIn = 1;
                        } else if (which == 2 - fakePasscodeValueShift) {
                            SharedConfig.autoLockIn = 60;
                        } else if (which == 3 - fakePasscodeValueShift) {
                            SharedConfig.autoLockIn = 60 * 5;
                        } else if (which == 4 - fakePasscodeValueShift) {
                            SharedConfig.autoLockIn = 60 * 60;
                        } else if (which == 5 - fakePasscodeValueShift) {
                            SharedConfig.autoLockIn = 60 * 60 * 5;
                        }
                        listAdapter.notifyItemChanged(position);
                        UserConfig.getInstance(currentAccount).saveConfig(false);
                    });
                    showDialog(builder.create());
                } else if (position == bruteForceProtectionRow) {
                    SharedConfig.bruteForceProtectionEnabled = !SharedConfig.bruteForceProtectionEnabled;
                    SharedConfig.saveConfig();
                    ((TextCheckCell) view).setChecked(SharedConfig.bruteForceProtectionEnabled);
                } else if (position == clearCacheOnLockRow) {
                    SharedConfig.clearCacheOnLock = !SharedConfig.clearCacheOnLock;
                    SharedConfig.saveConfig();
                    ((TextCheckCell) view).setChecked(SharedConfig.clearCacheOnLock);
                } else if (position == badPasscodeAttemptsRow) {
                    presentFragment(new BadPasscodeAttemptsActivity());
                } else if (position == badPasscodePhotoFrontRow) {
                    Activity parentActivity = getParentActivity();
                    if (SharedConfig.takePhotoWithBadPasscodeFront || ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        SharedConfig.takePhotoWithBadPasscodeFront = !SharedConfig.takePhotoWithBadPasscodeFront;
                        SharedConfig.saveConfig();
                        ((TextCheckCell) view).setChecked(SharedConfig.takePhotoWithBadPasscodeFront);
                    } else {
                        ActivityCompat.requestPermissions(parentActivity, new String[]{Manifest.permission.CAMERA}, 2000);
                    }
                    updateRows();
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                } else if (position == badPasscodePhotoBackRow) {
                    Activity parentActivity = getParentActivity();
                    if (SharedConfig.takePhotoWithBadPasscodeBack || ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        SharedConfig.takePhotoWithBadPasscodeBack = !SharedConfig.takePhotoWithBadPasscodeBack;
                        SharedConfig.saveConfig();
                        ((TextCheckCell) view).setChecked(SharedConfig.takePhotoWithBadPasscodeBack);
                    } else {
                        ActivityCompat.requestPermissions(parentActivity, new String[]{Manifest.permission.CAMERA}, 2001);
                    }
                    updateRows();
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                } else if (position == badPasscodeMuteAudioRow) {
                    SharedConfig.takePhotoMuteAudio = !SharedConfig.takePhotoMuteAudio;
                    SharedConfig.saveConfig();
                    ((TextCheckCell) view).setChecked(SharedConfig.takePhotoMuteAudio);
                } else if (position == fingerprintRow) {
                    SharedConfig.useFingerprint = !SharedConfig.useFingerprint;
                    UserConfig.getInstance(currentAccount).saveConfig(false);
                    ((TextCheckCell) view).setChecked(SharedConfig.useFingerprint);
                } else if (position == captureRow) {
                    SharedConfig.allowScreenCapture = !SharedConfig.allowScreenCapture;
                    UserConfig.getInstance(currentAccount).saveConfig(false);
                    ((TextCheckCell) view).setChecked(SharedConfig.allowScreenCapture);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
                    if (!SharedConfig.allowScreenCapture) {
                        AlertsCreator.showSimpleAlert(PasscodeActivity.this, LocaleController.getString("ScreenCaptureAlert", R.string.ScreenCaptureAlert));
                    }
                } else if (firstFakePasscodeRow <= position && position <= lastFakePasscodeRow) {
                    presentFragment(new FakePasscodeActivity(0, SharedConfig.fakePasscodes.get(position - firstFakePasscodeRow), false));
                } else if (position == addFakePasscodeRow) {
                    FakePasscode fakePasscode = new FakePasscode();
                    fakePasscode.name = LocaleController.getString("FakePasscode", R.string.FakePasscode) + " " + (SharedConfig.fakePasscodeIndex);
                    fakePasscode.autoAddAccountHidings();
                    presentFragment(new FakePasscodeActivity(1, fakePasscode, true));
                } else if (position == partisanSettingsRow) {
                    presentFragment(new PartisanSettingsActivity());
                }
            });
        }

        return fragmentView;
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 2000 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            AndroidUtilities.runOnUIThread(() -> {
                SharedConfig.takePhotoWithBadPasscodeFront = !SharedConfig.takePhotoWithBadPasscodeFront;
                SharedConfig.saveConfig();
                frontPhotoTextCell.setChecked(SharedConfig.takePhotoWithBadPasscodeFront);
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            });
        } else if (requestCode == 2001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            AndroidUtilities.runOnUIThread(() -> {
                SharedConfig.takePhotoWithBadPasscodeBack = !SharedConfig.takePhotoWithBadPasscodeBack;
                SharedConfig.saveConfig();
                backPhotoTextCell.setChecked(SharedConfig.takePhotoWithBadPasscodeBack);
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (type != 0) {
            AndroidUtilities.runOnUIThread(() -> {
                if (passwordEditText != null) {
                    passwordEditText.requestFocus();
                    AndroidUtilities.showKeyboard(passwordEditText);
                }
            }, 200);
        }
        fixLayoutInternal();
        updateRows();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetPasscode) {
            if (type == 0) {
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private void updateRows() {
        rowCount = 0;
        fakePasscodesHeaderRow = -1;
        firstFakePasscodeRow = -1;
        lastFakePasscodeRow = -1;
        addFakePasscodeRow = -1;
        fakePasscodeDetailRow = -1;
        bruteForceProtectionRow = -1;
        clearCacheOnLockRow = -1;
        bruteForceProtectionDetailRow = -1;
        badPasscodeAttemptsRow = -1;
        badPasscodePhotoFrontRow = -1;
        badPasscodePhotoBackRow = -1;
        badPasscodeMuteAudioRow = -1;
        badPasscodeAttemptsDetailRow = -1;
        partisanSettingsRow = -1;
        partisanSettingsDetailRow = -1;

        passcodeRow = rowCount++;
        changePasscodeRow = rowCount++;
        passcodeDetailRow = rowCount++;
        if (SharedConfig.passcodeEnabled()) {
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    FingerprintManagerCompat fingerprintManager = FingerprintManagerCompat.from(ApplicationLoader.applicationContext);
                    if (fingerprintManager.isHardwareDetected()) {
                        fingerprintRow = rowCount++;
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            autoLockRow = rowCount++;
            autoLockDetailRow = rowCount++;
            captureRow = rowCount++;
            captureDetailRow = rowCount++;
            if (SharedConfig.getActivatedFakePasscode() == null) {
                bruteForceProtectionRow = rowCount++;
                bruteForceProtectionDetailRow = rowCount++;

                clearCacheOnLockRow = rowCount++;
                clearCacheOnLockDetailRow = rowCount++;

                badPasscodeAttemptsRow = rowCount++;
                badPasscodePhotoFrontRow = rowCount++;
                badPasscodePhotoBackRow = rowCount++;
                if (SharedConfig.takePhotoWithBadPasscodeBack || SharedConfig.takePhotoWithBadPasscodeFront) {
                    badPasscodeMuteAudioRow = rowCount++;
                }
                badPasscodeAttemptsDetailRow = rowCount++;

                fakePasscodesHeaderRow = rowCount++;
                if (!SharedConfig.fakePasscodes.isEmpty())
                {
                    firstFakePasscodeRow = rowCount;
                    lastFakePasscodeRow = firstFakePasscodeRow + SharedConfig.fakePasscodes.size() - 1;
                    rowCount = lastFakePasscodeRow + 1;
                }
                addFakePasscodeRow = rowCount++;
                fakePasscodeDetailRow = rowCount++;
                partisanSettingsRow = rowCount++;
                partisanSettingsDetailRow = rowCount++;
            }
        } else {
            captureRow = -1;
            captureDetailRow = -1;
            fingerprintRow = -1;
            autoLockRow = -1;
            autoLockDetailRow = -1;
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    fixLayoutInternal();
                    return true;
                }
            });
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && type != 0) {
            AndroidUtilities.showKeyboard(passwordEditText);
        }
    }

    private void updateDropDownTextView() {
        if (dropDown != null) {
            if (currentPasswordType == 0) {
                dropDown.setText(LocaleController.getString("PasscodePIN", R.string.PasscodePIN));
            } else if (currentPasswordType == 1) {
                dropDown.setText(LocaleController.getString("PasscodePassword", R.string.PasscodePassword));
            }
        }
        if (type == 1 && currentPasswordType == 0 || type == 2 && SharedConfig.passcodeType == 0) {
            InputFilter[] filterArray = new InputFilter[1];
            filterArray[0] = new InputFilter.LengthFilter(4);
            passwordEditText.setFilters(filterArray);
            passwordEditText.setInputType(InputType.TYPE_CLASS_PHONE);
            passwordEditText.setKeyListener(DigitsKeyListener.getInstance("1234567890"));
        } else if (type == 1 && currentPasswordType == 1 || type == 2 && SharedConfig.passcodeType == 1) {
            passwordEditText.setFilters(new InputFilter[0]);
            passwordEditText.setKeyListener(null);
            passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
    }

    private void processNext() {
        if (passwordEditText.getText().length() == 0 || currentPasswordType == 0 && passwordEditText.getText().length() != 4) {
            onPasscodeError();
            return;
        }
        if (currentPasswordType == 0) {
            actionBar.setTitle(LocaleController.getString("PasscodePIN", R.string.PasscodePIN));
        } else {
            actionBar.setTitle(LocaleController.getString("PasscodePassword", R.string.PasscodePassword));
        }
        dropDownContainer.setVisibility(View.GONE);
        titleTextView.setText(LocaleController.getString("ReEnterYourPasscode", R.string.ReEnterYourPasscode));
        firstPassword = passwordEditText.getText().toString();
        passwordEditText.setText("");
        passcodeSetStep = 1;
    }

    private void processDone() {
        if (passwordEditText.getText().length() == 0) {
            onPasscodeError();
            return;
        }
        if (type == 1) {
            if (!firstPassword.equals(passwordEditText.getText().toString())) {
                try {
                    Toast.makeText(getParentActivity(), LocaleController.getString("PasscodeDoNotMatch", R.string.PasscodeDoNotMatch), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                AndroidUtilities.shakeView(titleTextView, 2, 0);
                passwordEditText.setText("");
                return;
            }

            try {
                if (SharedConfig.getActivatedFakePasscode() == null) {
                    SharedConfig.passcodeSalt = new byte[16];
                    Utilities.random.nextBytes(SharedConfig.passcodeSalt);
                }
                byte[] passcodeBytes = firstPassword.getBytes("UTF-8");
                byte[] bytes = new byte[32 + passcodeBytes.length];
                System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, 0, 16);
                System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                if (SharedConfig.getActivatedFakePasscode() != null) {
                    SharedConfig.getActivatedFakePasscode().passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
                } else {
                    SharedConfig.passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
                    for (FakePasscode passcode: SharedConfig.fakePasscodes) {
                        passcode.onDelete();
                    }
                    SharedConfig.fakePasscodes.clear();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (SharedConfig.getActivatedFakePasscode() != null) {
                SharedConfig.allowScreenCapture = true;
            } else {
                SharedConfig.autoLockIn = 60;
            }

            SharedConfig.passcodeType = currentPasswordType;
            SharedConfig.saveConfig();
            getMediaDataController().buildShortcuts();
            finishFragment();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
        } else if (type == 2) {
            if (SharedConfig.passcodeRetryInMs > 0) {
                int value = Math.max(1, (int) Math.ceil(SharedConfig.passcodeRetryInMs / 1000.0));
                Toast.makeText(getParentActivity(), LocaleController.formatString("TooManyTries", R.string.TooManyTries, LocaleController.formatPluralString("Seconds", value)), Toast.LENGTH_SHORT).show();
                passwordEditText.setText("");
                onPasscodeError();
                return;
            }
            SharedConfig.PasscodeCheckResult result = SharedConfig.checkPasscode(passwordEditText.getText().toString());
            synchronized (FakePasscode.class) {
                if (!result.allowLogin() || result.fakePasscode != null) {
                    BadPasscodeAttempt badAttempt = new BadPasscodeAttempt(BadPasscodeAttempt.PasscodeSettingsType, result.fakePasscode != null);
                    SharedConfig.badPasscodeAttemptList.add(badAttempt);
                    SharedConfig.saveConfig();
                    badAttempt.takePhoto(getParentActivity());
                }
                if (!result.allowLogin() || result.isRealPasscodeSuccess && SharedConfig.fakePasscodeActivatedIndex != -1
                    || result.fakePasscode != null && SharedConfig.getActivatedFakePasscode() != result.fakePasscode) {
                    SharedConfig.increaseBadPasscodeTries();
                    passwordEditText.setText("");
                    onPasscodeError();
                    return;
                }
                SharedConfig.fakePasscodeActivated(SharedConfig.fakePasscodes.indexOf(result.fakePasscode));
                SharedConfig.badPasscodeTries = 0;
                SharedConfig.saveConfig();
            }
            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
            PasscodeActivity passcodeActivity = new PasscodeActivity(0);
            presentFragment(passcodeActivity, true);
        }
    }

    private void onPasscodeError() {
        if (getParentActivity() == null) {
            return;
        }
        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        AndroidUtilities.shakeView(titleTextView, 2, 0);
    }

    private void fixLayoutInternal() {
        if (dropDownContainer != null) {
            if (!AndroidUtilities.isTablet()) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) dropDownContainer.getLayoutParams();
                layoutParams.topMargin = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                dropDownContainer.setLayoutParams(layoutParams);
            }
            if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                dropDown.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            } else {
                dropDown.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            }
        }
    }

    private void requireFakePasscodesDeletionConfirmation(final DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setMessage(LocaleController.getString("AllFakePasscodesWillBeDeleted", R.string.AllFakePasscodesWillBeDeleted));
        builder.setTitle(LocaleController.getString("ConfirmDeletion", R.string.ConfirmDeletion));
        builder.setPositiveButton(LocaleController.getString("Continue", R.string.Continue), listener);
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
    }

    private void resetPasscode() {
        if (SharedConfig.getActivatedFakePasscode() != null) {
            SharedConfig.getActivatedFakePasscode().passcodeHash = "";
        } else {
            SharedConfig.passcodeHash = "";
            for (FakePasscode passcode: SharedConfig.fakePasscodes) {
                passcode.onDelete();
            }
            SharedConfig.fakePasscodes.clear();
        }
        SharedConfig.appLocked = false;
        SharedConfig.saveConfig();
        getMediaDataController().buildShortcuts();
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof TextSettingsCell) {
                TextSettingsCell textCell = (TextSettingsCell) child;
                textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
                break;
            }
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private Boolean hasWidgets;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == passcodeRow || position == fingerprintRow || position == autoLockRow
                    || position == badPasscodeAttemptsRow || position == badPasscodePhotoFrontRow
                    || position == badPasscodePhotoBackRow || position == badPasscodeMuteAudioRow
                    || position == bruteForceProtectionRow || position == clearCacheOnLockRow
                    || position == captureRow || SharedConfig.passcodeEnabled() && position == changePasscodeRow
                    || (firstFakePasscodeRow <= position && position <= lastFakePasscodeRow)
                    || position == addFakePasscodeRow
                    || position == partisanSettingsRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 3:
                default:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    if (position == passcodeRow) {
                        textCell.setTextAndCheck(LocaleController.getString("Passcode", R.string.Passcode), SharedConfig.passcodeEnabled(), true);
                    } else if (position == fingerprintRow) {
                        textCell.setTextAndCheck(LocaleController.getString("UnlockFingerprint", R.string.UnlockFingerprint), SharedConfig.useFingerprint, true);
                    } else if (position == captureRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ScreenCapture", R.string.ScreenCapture), SharedConfig.allowScreenCapture, false);
                    } else if (position == bruteForceProtectionRow) {
                        textCell.setTextAndCheck(LocaleController.getString("BruteForceProtection", R.string.BruteForceProtection), SharedConfig.bruteForceProtectionEnabled, false);
                    } else if (position == clearCacheOnLockRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ClearCacheOnLock", R.string.ClearCacheOnLock), SharedConfig.clearCacheOnLock, false);
                    } else if (position == badPasscodePhotoFrontRow) {
                        frontPhotoTextCell = textCell;
                        textCell.setTextAndCheck(LocaleController.getString("TakePhotoWithFrontCamera", R.string.TakePhotoWithFrontCamera), SharedConfig.takePhotoWithBadPasscodeFront, true);
                    } else if (position == badPasscodePhotoBackRow) {
                        backPhotoTextCell = textCell;
                        textCell.setTextAndCheck(LocaleController.getString("TakePhotoWithBackCamera", R.string.TakePhotoWithBackCamera), SharedConfig.takePhotoWithBadPasscodeBack, SharedConfig.takePhotoWithBadPasscodeFront || SharedConfig.takePhotoWithBadPasscodeBack);
                    } else if (position == badPasscodeMuteAudioRow) {
                        textCell.setTextAndCheck(LocaleController.getString("MuteAudioWhenTakingPhoto", R.string.MuteAudioWhenTakingPhoto), SharedConfig.takePhotoMuteAudio, false);
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == changePasscodeRow) {
                        textCell.setText(LocaleController.getString("ChangePasscode", R.string.ChangePasscode), false);
                        if (!SharedConfig.passcodeEnabled()) {
                            textCell.setTag(Theme.key_windowBackgroundWhiteGrayText7);
                            textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
                        } else {
                            textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                            textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        }
                    } else if (position == autoLockRow) {
                        String val;
                        if (SharedConfig.getAutoLockIn() == 0) {
                            val = LocaleController.formatString("AutoLockDisabled", R.string.AutoLockDisabled);
                        } else if (SharedConfig.getAutoLockIn() < 60) {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Seconds", SharedConfig.getAutoLockIn()));
                        } else if (SharedConfig.getAutoLockIn() < 60 * 60) {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Minutes", SharedConfig.getAutoLockIn() / 60));
                        } else if (SharedConfig.getAutoLockIn() < 60 * 60 * 24) {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Hours", (int) Math.ceil(SharedConfig.getAutoLockIn() / 60.0f / 60)));
                        } else {
                            val = LocaleController.formatString("AutoLockInTime", R.string.AutoLockInTime, LocaleController.formatPluralString("Days", (int) Math.ceil(SharedConfig.getAutoLockIn() / 60.0f / 60 / 24)));
                        }
                        textCell.setTextAndValue(LocaleController.getString("AutoLock", R.string.AutoLock), val, true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == badPasscodeAttemptsRow) {
                        textCell.setTextAndValue(LocaleController.getString("BadPasscodeAttempts", R.string.BadPasscodeAttempts), String.valueOf(SharedConfig.badPasscodeAttemptList.size()),true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (firstFakePasscodeRow <= position && position <= lastFakePasscodeRow) {
                        textCell.setText(SharedConfig.fakePasscodes.get(position - firstFakePasscodeRow).name, true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == addFakePasscodeRow) {
                        textCell.setText(LocaleController.getString("AddFakePasscode", R.string.AddFakePasscode), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlueText4);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    } else if (position == partisanSettingsRow) {
                        textCell.setText(LocaleController.getString("PartisanSettings", R.string.PartisanSettings), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == passcodeDetailRow) {
                        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(LocaleController.getString("ChangePasscodeInfo", R.string.ChangePasscodeInfo));
                        if (hasWidgets == null) {
                            SharedPreferences preferences = mContext.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);
                            hasWidgets = !preferences.getAll().isEmpty();
                        }
                        if (hasWidgets) {
                            stringBuilder.append("\n\n").append(AndroidUtilities.replaceTags(LocaleController.getString("WidgetPasscodeEnable2", R.string.WidgetPasscodeEnable2)));
                        }
                        cell.setText(stringBuilder);
                        if (autoLockDetailRow != -1) {
                            cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        } else {
                            cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        }
                    } else if (position == autoLockDetailRow) {
                        cell.setText(LocaleController.getString("AutoLockInfo", R.string.AutoLockInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == captureDetailRow) {
                        cell.setText(LocaleController.getString("ScreenCaptureInfo", R.string.ScreenCaptureInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == bruteForceProtectionDetailRow) {
                        cell.setText(LocaleController.getString("BruteForceProtectionInfo", R.string.BruteForceProtectionInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == clearCacheOnLockDetailRow) {
                        cell.setText(LocaleController.getString("ClearCacheOnLockInfo", R.string.ClearCacheOnLockInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == badPasscodeAttemptsDetailRow) {
                        cell.setText(LocaleController.getString("BadPasscodeAttemptsInfo", R.string.BadPasscodeAttemptsInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == fakePasscodeDetailRow) {
                        cell.setText(LocaleController.getString("FakePasscodeActionsInfo", R.string.FakePasscodeActionsInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == partisanSettingsDetailRow) {
                        cell.setText(LocaleController.getString("PartisanSettingsInfo", R.string.PartisanSettingsInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 3: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == fakePasscodesHeaderRow) {
                        headerCell.setText(LocaleController.getString("FakePasscodes", R.string.FakePasscodes));
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == passcodeRow || position == fingerprintRow || position == captureRow
                    || position == bruteForceProtectionRow || position == clearCacheOnLockRow
                    || position == badPasscodePhotoFrontRow || position == badPasscodePhotoBackRow
                    || position == badPasscodeMuteAudioRow) {
                return 0;
            } else if (position == changePasscodeRow || position == autoLockRow
                    || position == addFakePasscodeRow || position == badPasscodeAttemptsRow
                    || (firstFakePasscodeRow <= position && position <= lastFakePasscodeRow)
                    || position == partisanSettingsRow) {
                return 1;
            } else if (position == autoLockDetailRow || position == captureDetailRow
                    || position == bruteForceProtectionDetailRow || position == clearCacheOnLockDetailRow
                    || position == badPasscodeAttemptsDetailRow || position == fakePasscodeDetailRow
                    || position == passcodeDetailRow || position == partisanSettingsDetailRow) {
                return 2;
            } else if (position == fakePasscodesHeaderRow) {
                return 3;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckCell.class, TextSettingsCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        themeDescriptions.add(new ThemeDescription(dropDown, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(dropDown, 0, null, null, new Drawable[]{dropDownDrawable}, null, Theme.key_actionBarDefaultTitle));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText7));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        return themeDescriptions;
    }
}
