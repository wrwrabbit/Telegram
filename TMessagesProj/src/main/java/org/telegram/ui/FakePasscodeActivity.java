/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.AccountActions;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AccountActionsCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.CustomPhoneKeyboardView;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.Components.TransformableLoginButtonView;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


public class FakePasscodeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    public final static int TYPE_FAKE_PASSCODE_SETTINGS = 0,
            TYPE_SETUP_FAKE_PASSCODE = 1,
            TYPE_ENTER_BACKUP_CODE = 2,
            TYPE_ENTER_RESTORE_CODE = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            TYPE_FAKE_PASSCODE_SETTINGS,
            TYPE_SETUP_FAKE_PASSCODE,
            TYPE_ENTER_BACKUP_CODE,
            TYPE_ENTER_RESTORE_CODE
    })
    public @interface FakePasscodeActivityType {}

    private RLottieImageView lockImageView;

    private TextSettingsCell changeNameCell;

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private TextView titleTextView;
    private TextViewSwitcher descriptionTextSwitcher;
    private OutlineTextContainerView outlinePasswordView;
    private EditTextBoldCursor passwordEditText;
    private CodeFieldContainer codeFieldContainer;
    private TextView passcodesDoNotMatchTextView;

    private ImageView passwordButton;

    private CustomPhoneKeyboardView keyboardView;

    private FrameLayout floatingButtonContainer;
    private VerticalPositionAutoAnimator floatingAutoAnimator;
    private TransformableLoginButtonView floatingButtonIcon;
    private Animator floatingButtonAnimator;

    @FakePasscodeActivityType
    private int type;
    private int passcodeSetStep = 0;
    private String firstPassword;

    private int rowCount;

    private int changeNameRow;
    private int changeFakePasscodeRow;
    private int changeFakePasscodeDetailRow;

    private int allowFakePasscodeLoginRow;
    private int allowFakePasscodeLoginDetailRow;

    private int clearAfterActivationRow;
    private int clearAfterActivationDetailRow;

    private int deleteOtherPasscodesAfterActivationRow;
    private int deleteOtherPasscodesAfterActivationDetailRow;

    private int activationMessageRow;
    private int activationMessageDetailRow;

    private int badTriesToActivateRow;
    private int badTriesToActivateDetailRow;

    private int actionsHeaderRow;
    private int smsRow;
    private int clearTelegramCacheRow;
    private int clearProxiesRow;
    private int actionsDetailRow;

    private int accountHeaderRow;
    private int firstAccountRow;
    private int lastAccountRow;
    private int accountDetailRow;

    private int backupPasscodeRow;
    private int backupPasscodeDetailRow;

    private int deletePasscodeRow;
    private int deletePasscodeDetailRow;

    List<AccountActionsCellInfo> accounts = new ArrayList<>();

    private boolean creating;
    private FakePasscode fakePasscode;
    private byte[] encryptedPasscode;

    TextCheckCell frontPhotoTextCell;
    TextCheckCell backPhotoTextCell;

    private ActionBarMenuItem otherItem;

    private boolean postedHidePasscodesDoNotMatch;
    private Runnable hidePasscodesDoNotMatch = () -> {
        postedHidePasscodesDoNotMatch = false;
        AndroidUtilities.updateViewVisibilityAnimated(passcodesDoNotMatchTextView, false);
    };

    private Runnable onShowKeyboardCallback;

    private final static int done_button = 1;

    public FakePasscodeActivity(@FakePasscodeActivityType int type, FakePasscode fakePasscode, boolean creating) {
        super();
        this.type = type;
        this.fakePasscode = fakePasscode;
        this.creating = creating;
    }

    public FakePasscodeActivity(byte[] encodedPasscode) {
        super();
        this.type = TYPE_ENTER_RESTORE_CODE;
        this.encryptedPasscode = encodedPasscode;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (type == TYPE_SETUP_FAKE_PASSCODE) {
                        if (passcodeSetStep == 0) {
                            processNext();
                        } else if (passcodeSetStep == 1) {
                            processDone();
                        }
                    } else if (type == TYPE_ENTER_BACKUP_CODE || type == TYPE_ENTER_RESTORE_CODE) {
                        processDone();
                    }
                }
            }
        });

        View fragmentContentView;
        FrameLayout frameLayout = new FrameLayout(context);
        if (type == TYPE_FAKE_PASSCODE_SETTINGS) {
            fragmentContentView = frameLayout;
        } else {
            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            scrollView.setFillViewport(true);
            fragmentContentView = scrollView;
        }
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int frameBottom;
                if (keyboardView.getVisibility() != View.GONE && measureKeyboardHeight() >= AndroidUtilities.dp(20)) {
                    if (isCustomKeyboardVisible()) {
                        fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight() - AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) + measureKeyboardHeight());
                    } else {
                        fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight());
                    }
                } else if (keyboardView.getVisibility() != View.GONE) {
                    fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight() - AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                } else {
                    fragmentContentView.layout(0, 0, getMeasuredWidth(), frameBottom = getMeasuredHeight());
                }

                keyboardView.layout(0, frameBottom, getMeasuredWidth(), frameBottom + AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                notifyHeightChanged();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);

                int frameHeight = height;
                if (keyboardView.getVisibility() != View.GONE && measureKeyboardHeight() < AndroidUtilities.dp(20)) {
                    frameHeight -= AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP);
                }
                fragmentContentView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(frameHeight, MeasureSpec.EXACTLY));
                keyboardView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP), MeasureSpec.EXACTLY));
            }
        };
        contentView.setDelegate((keyboardHeight, isWidthGreater) -> {
            if (keyboardHeight >= AndroidUtilities.dp(20) && onShowKeyboardCallback != null) {
                onShowKeyboardCallback.run();
                onShowKeyboardCallback = null;
            }
        });
        fragmentView = contentView;
        contentView.addView(fragmentContentView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        keyboardView = new CustomPhoneKeyboardView(context);
        keyboardView.setVisibility(isCustomKeyboardVisible() ? View.VISIBLE : View.GONE);
        contentView.addView(keyboardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));

        switch (type) {
            case TYPE_FAKE_PASSCODE_SETTINGS: {
                actionBar.setTitle(LocaleController.getString("FakePasscode", R.string.FakePasscode));
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
                    if (position == changeNameRow) {
                        AlertDialog.Builder alert = new AlertDialog.Builder(getParentActivity());
                        final EditTextCaption editText = new EditTextCaption(getParentActivity(), null);
                        editText.setText(fakePasscode.name);
                        editText.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
                        editText.setHintColor(Theme.getColor(Theme.key_chat_messagePanelHint));
                        editText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
                        editText.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor));
                        alert.setTitle(LocaleController.getString("ChangeFakePasscodeName", R.string.ChangeFakePasscodeName));
                        alert.setView(editText);
                        alert.setPositiveButton(LocaleController.getString("Done", R.string.Done), (dialog, whichButton) -> {
                            fakePasscode.name = editText.getText().toString();
                            SharedConfig.saveConfig();
                            changeNameCell.setTextAndValue(LocaleController.getString("ChangeFakePasscodeName", R.string.ChangeFakePasscodeName),
                                    fakePasscode.name, true);
                        });
                        showDialog(alert.create());
                    } else if (position == changeFakePasscodeRow) {
                        presentFragment(new FakePasscodeActivity(TYPE_SETUP_FAKE_PASSCODE, fakePasscode, false));
                    } else if (position == allowFakePasscodeLoginRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        fakePasscode.allowLogin = !fakePasscode.allowLogin;
                        SharedConfig.saveConfig();
                        cell.setChecked(fakePasscode.allowLogin);
                    } else if (position == clearAfterActivationRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        fakePasscode.clearAfterActivation = !fakePasscode.clearAfterActivation;
                        SharedConfig.saveConfig();
                        cell.setChecked(fakePasscode.clearAfterActivation);
                    } else if (position == deleteOtherPasscodesAfterActivationRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        fakePasscode.deleteOtherPasscodesAfterActivation = !fakePasscode.deleteOtherPasscodesAfterActivation;
                        SharedConfig.saveConfig();
                        cell.setChecked(fakePasscode.deleteOtherPasscodesAfterActivation);
                    } else if (position == smsRow) {
                        Activity parentActivity = getParentActivity();
                        if (ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(parentActivity, new String[]{Manifest.permission.SEND_SMS}, 2002);
                        } else {
                            FakePasscodeSmsActivity activity = new FakePasscodeSmsActivity(fakePasscode.smsAction);
                            presentFragment(activity);
                        }
                    } else if (position == clearTelegramCacheRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        fakePasscode.clearCacheAction.enabled = !fakePasscode.clearCacheAction.enabled;
                        SharedConfig.saveConfig();
                        cell.setChecked(fakePasscode.clearCacheAction.enabled);
                        updateRows();
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                    } else if (position == clearProxiesRow) {
                        TextCheckCell cell = (TextCheckCell) view;
                        fakePasscode.clearProxiesAction.enabled = !fakePasscode.clearProxiesAction.enabled;
                        SharedConfig.saveConfig();
                        cell.setChecked(fakePasscode.clearProxiesAction.enabled);
                        updateRows();
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                    } else if (position == activationMessageRow) {
                        DialogTemplate template = new DialogTemplate();
                        template.type = DialogType.EDIT;
                        template.title = LocaleController.getString("ActivationMessage", R.string.ActivationMessage);
                        template.addEditTemplate(fakePasscode.activationMessage, LocaleController.getString("Message", R.string.Message), false);
                        template.positiveListener = views -> {
                            fakePasscode.activationMessage = ((EditTextCaption)views.get(0)).getText().toString();
                            SharedConfig.saveConfig();
                            TextSettingsCell cell = (TextSettingsCell) view;
                            String value = fakePasscode.activationMessage.isEmpty() ? LocaleController.getString("Disabled", R.string.Disabled) : fakePasscode.activationMessage;
                            cell.setTextAndValue(LocaleController.getString("ActivationMessage", R.string.ActivationMessage), value, false);
                            if (listAdapter != null) {
                                listAdapter.notifyDataSetChanged();
                            }
                        };
                        template.negativeListener = (dlg, whichButton) -> {
                            fakePasscode.activationMessage = "";
                            TextSettingsCell cell = (TextSettingsCell) view;
                            cell.setTextAndValue(LocaleController.getString("ActivationMessage", R.string.ActivationMessage), LocaleController.getString("Disabled", R.string.Disabled), false);
                            if (listAdapter != null) {
                                listAdapter.notifyDataSetChanged();
                            }
                        };
                        AlertDialog dialog = FakePasscodeDialogBuilder.build(getParentActivity(), template);
                        showDialog(dialog);
                    } else if (position == badTriesToActivateRow) {
                        String title = LocaleController.getString("BadPasscodeTriesToActivate", R.string.BadPasscodeTriesToActivate);
                        int selected = -1;
                        final int[] values = new int[] {1, 2, 3, 4, 5, 7, 10, 15, 20, 25, 30};
                        if (fakePasscode.badTriesToActivate == null) {
                            selected = 0;
                        } else{
                            for (int i = 0; i < values.length; i++) {
                                if (fakePasscode.badTriesToActivate <= values[i]) {
                                    selected = i + 1;
                                    break;
                                }
                            }
                        }
                        String[] items = new String[values.length + 1];
                        items[0] = LocaleController.getString("Disabled", R.string.Disabled);
                        for (int i = 0; i < values.length; i++) {
                            items[i + 1] = String.valueOf(values[i]);
                        }
                        Consumer<Integer> onClicked = which -> {
                            if (which == 0) {
                                fakePasscode.badTriesToActivate = null;
                            } else {
                                fakePasscode.badTriesToActivate = values[which - 1];
                            }
                            SharedConfig.saveConfig();
                            listAdapter.notifyDataSetChanged();
                        };
                        AlertDialog dialog = EnumDialogBuilder.build(getParentActivity(), title, selected, items, onClicked);
                        if (dialog == null) {
                            return;
                        }
                        showDialog(dialog);
                    } else if (firstAccountRow <= position && position <= lastAccountRow) {
                        AccountActionsCellInfo info = accounts.get(position - firstAccountRow);
                        if (info.accountNum != null) {
                            AccountActions actions = fakePasscode.getOrCreateAccountActions(info.accountNum);
                            presentFragment(new FakePasscodeAccountActionsActivity(actions, fakePasscode), false);
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            String buttonText;
                            builder.setMessage(LocaleController.getString("DeleteOldAccountActionsInfo", R.string.DeleteOldAccountActionsInfo));
                            builder.setTitle(LocaleController.getString("DeleteOldAccountActions", R.string.DeleteOldAccountActions));
                            buttonText = LocaleController.getString("Delete", R.string.Delete);
                            builder.setPositiveButton(buttonText, (dialogInterface, i) -> {
                                fakePasscode.accountActions.remove(info.actions);
                                SharedConfig.saveConfig();
                                listAdapter.notifyDataSetChanged();
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            AlertDialog alertDialog = builder.create();
                            showDialog(alertDialog);
                            TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                            if (button != null) {
                                button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                            }
                        }
                    } else if (position == backupPasscodeRow) {
                        FakePasscodeActivity activity = new FakePasscodeActivity(TYPE_ENTER_BACKUP_CODE, fakePasscode, false);
                        presentFragment(activity);
                    } else if (position == deletePasscodeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        String buttonText;
                        builder.setMessage(LocaleController.getString("AreYouSureDeleteFakePasscode", R.string.AreYouSureDeleteFakePasscode));
                        builder.setTitle(LocaleController.getString("DeleteFakePasscode", R.string.DeleteFakePasscode));
                        buttonText = LocaleController.getString("Delete", R.string.Delete);
                        builder.setPositiveButton(buttonText, (dialogInterface, i) -> {
                            fakePasscode.onDelete();
                            SharedConfig.fakePasscodes = SharedConfig.fakePasscodes.stream()
                                    .filter(a -> a != fakePasscode).collect(Collectors.toCollection(ArrayList::new));
                            SharedConfig.saveConfig();
                            finishFragment();
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        AlertDialog alertDialog = builder.create();
                        showDialog(alertDialog);
                        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        if (button != null) {
                            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                        }
                    }
                });
                break;
            }
            case TYPE_ENTER_BACKUP_CODE:
            case TYPE_ENTER_RESTORE_CODE:
            case TYPE_SETUP_FAKE_PASSCODE: {
                if (actionBar != null) {
                    actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

                    actionBar.setBackButtonImage(R.drawable.ic_ab_back);
                    actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
                    actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
                    actionBar.setCastShadows(false);

                    actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                        @Override
                        public void onItemClick(int id) {
                            if (id == -1) {
                                finishFragment();
                            }
                        }
                    });
                }

                FrameLayout codeContainer = new FrameLayout(context);

                LinearLayout innerLinearLayout = new LinearLayout(context);
                innerLinearLayout.setOrientation(LinearLayout.VERTICAL);
                innerLinearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
                frameLayout.addView(innerLinearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                lockImageView = new RLottieImageView(context);
                lockImageView.setAnimation(R.raw.tsv_setup_intro, 120, 120);
                lockImageView.setAutoRepeat(false);
                lockImageView.playAnimation();
                lockImageView.setVisibility(!AndroidUtilities.isSmallScreen() && AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y ? View.VISIBLE : View.GONE);
                innerLinearLayout.addView(lockImageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL));

                titleTextView = new TextView(context);
                titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                if (type == TYPE_SETUP_FAKE_PASSCODE) {
                    if (SharedConfig.passcodeEnabled()) {
                        titleTextView.setText(LocaleController.getString("EnterNewPasscode", R.string.EnterNewPasscode));
                    } else {
                        titleTextView.setText(LocaleController.getString("EnterNewFirstPasscode", R.string.EnterNewFirstPasscode));
                    }
                } else {
                    titleTextView.setText(LocaleController.getString("EnterCurrentFakePasscode", R.string.EnterCurrentFakePasscode));
                }
                titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
                innerLinearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

                descriptionTextSwitcher = new TextViewSwitcher(context);
                descriptionTextSwitcher.setFactory(() -> {
                    TextView tv = new TextView(context);
                    tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
                    tv.setGravity(Gravity.CENTER_HORIZONTAL);
                    tv.setLineSpacing(AndroidUtilities.dp(2), 1);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                    return tv;
                });
                descriptionTextSwitcher.setInAnimation(context, R.anim.alpha_in);
                descriptionTextSwitcher.setOutAnimation(context, R.anim.alpha_out);
                innerLinearLayout.addView(descriptionTextSwitcher, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 8, 20, 0));

                passcodesDoNotMatchTextView = new TextView(context);
                passcodesDoNotMatchTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                passcodesDoNotMatchTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
                passcodesDoNotMatchTextView.setText(LocaleController.getString(R.string.PasscodesDoNotMatchTryAgain));
                passcodesDoNotMatchTextView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
                AndroidUtilities.updateViewVisibilityAnimated(passcodesDoNotMatchTextView, false, 1f, false);
                frameLayout.addView(passcodesDoNotMatchTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

                outlinePasswordView = new OutlineTextContainerView(context);
                outlinePasswordView.setText(LocaleController.getString(R.string.EnterPassword));

                passwordEditText = new EditTextBoldCursor(context);
                passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                passwordEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                passwordEditText.setBackground(null);
                passwordEditText.setMaxLines(1);
                passwordEditText.setLines(1);
                passwordEditText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                passwordEditText.setSingleLine(true);
                if (type == TYPE_SETUP_FAKE_PASSCODE) {
                    passcodeSetStep = 0;
                    passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
                } else {
                    passcodeSetStep = 1;
                    passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                }
                passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                passwordEditText.setTypeface(Typeface.DEFAULT);
                passwordEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
                passwordEditText.setCursorSize(AndroidUtilities.dp(20));
                passwordEditText.setCursorWidth(1.5f);

                int padding = AndroidUtilities.dp(16);
                passwordEditText.setPadding(padding, padding, padding, padding);

                passwordEditText.setOnFocusChangeListener((v, hasFocus) -> outlinePasswordView.animateSelection(hasFocus ? 1 : 0));

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                linearLayout.setGravity(Gravity.CENTER_VERTICAL);
                linearLayout.addView(passwordEditText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

                passwordButton = new ImageView(context);
                passwordButton.setImageResource(R.drawable.msg_message);
                passwordButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                passwordButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1));
                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, type == TYPE_SETUP_FAKE_PASSCODE && passcodeSetStep == 0, 0.1f, false);

                AtomicBoolean isPasswordShown = new AtomicBoolean(false);
                passwordEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (type == TYPE_SETUP_FAKE_PASSCODE && passcodeSetStep == 0) {
                            if (TextUtils.isEmpty(s) && passwordButton.getVisibility() != View.GONE) {
                                if (isPasswordShown.get()) {
                                    passwordButton.callOnClick();
                                }
                                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, false, 0.1f, true);
                            } else if (!TextUtils.isEmpty(s) && passwordButton.getVisibility() != View.VISIBLE) {
                                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, true, 0.1f, true);
                            }
                        }
                    }
                });

                passwordButton.setOnClickListener(v -> {
                    isPasswordShown.set(!isPasswordShown.get());

                    int selectionStart = passwordEditText.getSelectionStart(), selectionEnd = passwordEditText.getSelectionEnd();
                    passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | (isPasswordShown.get() ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                    passwordEditText.setSelection(selectionStart, selectionEnd);

                    passwordButton.setColorFilter(Theme.getColor(isPasswordShown.get() ? Theme.key_windowBackgroundWhiteInputFieldActivated : Theme.key_windowBackgroundWhiteHintText));
                });
                linearLayout.addView(passwordButton, LayoutHelper.createLinearRelatively(24, 24, 0, 0, 0, 14, 0));

                outlinePasswordView.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                codeContainer.addView(outlinePasswordView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 0));

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
                        if (postedHidePasscodesDoNotMatch) {
                            codeFieldContainer.removeCallbacks(hidePasscodesDoNotMatch);
                            hidePasscodesDoNotMatch.run();
                        }
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
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

                codeFieldContainer = new CodeFieldContainer(context) {
                    @Override
                    protected void processNextPressed() {
                        if (passcodeSetStep == 0) {
                            postDelayed(()->processNext(), 260);
                        } else {
                            processDone();
                        }
                    }
                };
                codeFieldContainer.setNumbersCount(4, CodeFieldContainer.TYPE_PASSCODE);
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.setShowSoftInputOnFocusCompat(!isCustomKeyboardVisible());
                    f.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    f.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
                    f.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            if (postedHidePasscodesDoNotMatch) {
                                codeFieldContainer.removeCallbacks(hidePasscodesDoNotMatch);
                                hidePasscodesDoNotMatch.run();
                            }
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {}

                        @Override
                        public void afterTextChanged(Editable s) {}
                    });
                    f.setOnFocusChangeListener((v, hasFocus) -> {
                        keyboardView.setEditText(f);
                        keyboardView.setDispatchBackWhenEmpty(true);
                    });
                }
                codeContainer.addView(codeFieldContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 40, 10, 40, 0));

                innerLinearLayout.addView(codeContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 72));

                if (type == TYPE_SETUP_FAKE_PASSCODE) {
                    frameLayout.setTag(Theme.key_windowBackgroundWhite);
                }

                floatingButtonContainer = new FrameLayout(context);
                if (Build.VERSION.SDK_INT >= 21) {
                    StateListAnimator animator = new StateListAnimator();
                    animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                    animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                    floatingButtonContainer.setStateListAnimator(animator);
                    floatingButtonContainer.setOutlineProvider(new ViewOutlineProvider() {
                        @SuppressLint("NewApi")
                        @Override
                        public void getOutline(View view, Outline outline) {
                            outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                        }
                    });
                }
                floatingAutoAnimator = VerticalPositionAutoAnimator.attach(floatingButtonContainer);
                frameLayout.addView(floatingButtonContainer, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 24, 16));
                floatingButtonContainer.setOnClickListener(view -> {
                    if (type == TYPE_SETUP_FAKE_PASSCODE) {
                        if (passcodeSetStep == 0) {
                            processNext();
                        } else {
                            processDone();
                        }
                    } else if (type == TYPE_ENTER_BACKUP_CODE || type == TYPE_ENTER_RESTORE_CODE) {
                        processDone();
                    }
                });

                floatingButtonIcon = new TransformableLoginButtonView(context);
                floatingButtonIcon.setTransformType(TransformableLoginButtonView.TRANSFORM_ARROW_CHECK);
                floatingButtonIcon.setProgress(0f);
                floatingButtonIcon.setColor(Theme.getColor(Theme.key_chats_actionIcon));
                floatingButtonIcon.setDrawBackground(false);
                floatingButtonContainer.setContentDescription(LocaleController.getString(R.string.Next));
                floatingButtonContainer.addView(floatingButtonIcon, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60));

                Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
                if (Build.VERSION.SDK_INT < 21) {
                    Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
                    shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
                    combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                    drawable = combinedDrawable;
                }
                floatingButtonContainer.setBackground(drawable);

                updateFields();
                break;
            }
        }

        return fragmentView;
    }

    @Override
    public boolean hasForceLightStatusBar() {
        return type != TYPE_FAKE_PASSCODE_SETTINGS;
    }

    /**
     * Sets custom keyboard visibility
     *
     * @param visible   If it should be visible
     * @param animate   If change should be animated
     */
    private void setCustomKeyboardVisible(boolean visible, boolean animate) {
        if (visible) {
            AndroidUtilities.hideKeyboard(fragmentView);
            AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        } else {
            AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
        }

        if (!animate) {
            keyboardView.setVisibility(visible ? View.VISIBLE : View.GONE);
            keyboardView.setAlpha(visible ? 1 : 0);
            keyboardView.setTranslationY(visible ? 0 : AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
            fragmentView.requestLayout();
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(visible ? 0 : 1, visible ? 1 : 0).setDuration(150);
            animator.setInterpolator(visible ? CubicBezierInterpolator.DEFAULT : Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                keyboardView.setAlpha(val);
                keyboardView.setTranslationY((1f - val) * AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) * 0.75f);
                fragmentView.requestLayout();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (visible) {
                        keyboardView.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!visible) {
                        keyboardView.setVisibility(View.GONE);
                    }
                }
            });
            animator.start();
        }
    }

    /**
     * Sets floating button visibility
     *
     * @param visible   If it should be visible
     * @param animate   If change should be animated
     */
    private void setFloatingButtonVisible(boolean visible, boolean animate) {
        if (floatingButtonAnimator != null) {
            floatingButtonAnimator.cancel();
            floatingButtonAnimator = null;
        }
        if (!animate) {
            floatingAutoAnimator.setOffsetY(visible ? 0 : AndroidUtilities.dp(70));
            floatingButtonContainer.setAlpha(visible ? 1f : 0f);
            floatingButtonContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(visible ? 0 : 1, visible ? 1 : 0).setDuration(150);
            animator.setInterpolator(visible ? AndroidUtilities.decelerateInterpolator : AndroidUtilities.accelerateInterpolator);
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                floatingAutoAnimator.setOffsetY(AndroidUtilities.dp(70) * (1f - val));
                floatingButtonContainer.setAlpha(val);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (visible) {
                        floatingButtonContainer.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!visible) {
                        floatingButtonContainer.setVisibility(View.GONE);
                    }
                    if (floatingButtonAnimator == animation) {
                        floatingButtonAnimator = null;
                    }
                }
            });
            animator.start();
            floatingButtonAnimator = animator;
        }
    }

    private void animateSuccessAnimation(Runnable callback) {
        if (!isPinCode()) {
            callback.run();
            return;
        }
        for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
            CodeNumberField field = codeFieldContainer.codeField[i];
            field.postDelayed(()-> field.animateSuccessProgress(1f), i * 75L);
        }
        codeFieldContainer.postDelayed(() -> {
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.animateSuccessProgress(0f);
            }
            callback.run();
        }, codeFieldContainer.codeField.length * 75L + 350L);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        setCustomKeyboardVisible(isCustomKeyboardVisible(), false);
        if (lockImageView != null) {
            lockImageView.setVisibility(!AndroidUtilities.isSmallScreen() && AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y ? View.VISIBLE : View.GONE);
        }
        for (CodeNumberField f : codeFieldContainer.codeField) {
            f.setShowSoftInputOnFocusCompat(!isCustomKeyboardVisible());
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 2002 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            AndroidUtilities.runOnUIThread(() -> {
                FakePasscodeSmsActivity activity = new FakePasscodeSmsActivity(fakePasscode.smsAction);
                presentFragment(activity);
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (type != TYPE_FAKE_PASSCODE_SETTINGS && !isCustomKeyboardVisible()) {
            AndroidUtilities.runOnUIThread(this::showKeyboard, 200);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);

        if (isCustomKeyboardVisible()) {
            AndroidUtilities.hideKeyboard(fragmentView);
            AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        }
        updateRows();
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetPasscode && (args.length == 0 || (Boolean) args[0])) {
            if (type == TYPE_FAKE_PASSCODE_SETTINGS) {
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private void updateRows() {
        rowCount = 0;

        changeNameRow = rowCount++;
        changeFakePasscodeRow = rowCount++;
        changeFakePasscodeDetailRow = rowCount++;

        allowFakePasscodeLoginRow = rowCount++;
        allowFakePasscodeLoginDetailRow = rowCount++;

        clearAfterActivationRow =  rowCount++;
        clearAfterActivationDetailRow =  rowCount++;

        deleteOtherPasscodesAfterActivationRow =  rowCount++;
        deleteOtherPasscodesAfterActivationDetailRow =  rowCount++;

        activationMessageRow = rowCount++;
        activationMessageDetailRow = rowCount++;

        badTriesToActivateRow = rowCount++;
        badTriesToActivateDetailRow = rowCount++;

        actionsHeaderRow = rowCount++;
        smsRow = rowCount++;
        clearTelegramCacheRow = rowCount++;
        clearProxiesRow = rowCount++;
        actionsDetailRow = rowCount++;

        accountHeaderRow = rowCount++;
        firstAccountRow = rowCount;
        lastAccountRow = firstAccountRow - 1;
        accounts.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts.add(new AccountActionsCellInfo(a));
                lastAccountRow = rowCount++;
            }
        }
        Collections.sort(accounts, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1.accountNum).loginTime;
            long l2 = UserConfig.getInstance(o2.accountNum).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });
        if (fakePasscode != null) {
            for (AccountActions actions : fakePasscode.accountActions) {
                if (actions.getAccountNum() == null) {
                    accounts.add(new AccountActionsCellInfo(actions));
                    lastAccountRow = rowCount++;
                }
            }
        }

        accountDetailRow = rowCount++;

        backupPasscodeRow = rowCount++;
        backupPasscodeDetailRow = rowCount++;

        deletePasscodeRow = rowCount++;
        deletePasscodeDetailRow = rowCount++;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && type != TYPE_FAKE_PASSCODE_SETTINGS) {
            showKeyboard();
        }
    }

    private void showKeyboard() {
        if (isPinCode()) {
            codeFieldContainer.codeField[0].requestFocus();
            if (!isCustomKeyboardVisible()) {
                AndroidUtilities.showKeyboard(codeFieldContainer.codeField[0]);
            }
        } else if (isPassword()) {
            passwordEditText.requestFocus();
            AndroidUtilities.showKeyboard(passwordEditText);
        }
    }

    private void updateFields() {
        String text;
        if (type == TYPE_FAKE_PASSCODE_SETTINGS) {
            text = LocaleController.getString(R.string.EnterYourPasscodeInfo);
        } else if (passcodeSetStep == 0) {
            text = LocaleController.getString(SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.CreateFakePasscodeInfoPIN : R.string.CreateFakePasscodeInfoPassword);
        } else text = descriptionTextSwitcher.getCurrentView().getText().toString();

        boolean animate = !(descriptionTextSwitcher.getCurrentView().getText().equals(text) || TextUtils.isEmpty(descriptionTextSwitcher.getCurrentView().getText()));
        if (type == TYPE_FAKE_PASSCODE_SETTINGS) {
            descriptionTextSwitcher.setText(LocaleController.getString(R.string.EnterYourPasscodeInfo), animate);
        } else if (passcodeSetStep == 0) {
            descriptionTextSwitcher.setText(LocaleController.getString(SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? R.string.CreateFakePasscodeInfoPIN : R.string.CreateFakePasscodeInfoPassword), animate);
        }
        if (isPinCode()) {
            AndroidUtilities.updateViewVisibilityAnimated(codeFieldContainer, true, 1f, animate);
            AndroidUtilities.updateViewVisibilityAnimated(outlinePasswordView, false, 1f, animate);
        } else if (isPassword()) {
            AndroidUtilities.updateViewVisibilityAnimated(codeFieldContainer, false, 1f, animate);
            AndroidUtilities.updateViewVisibilityAnimated(outlinePasswordView, true, 1f, animate);
        }
        boolean show = isPassword();
        if (show) {
            onShowKeyboardCallback = () -> {
                setFloatingButtonVisible(show, animate);
                AndroidUtilities.cancelRunOnUIThread(onShowKeyboardCallback);
            };
            AndroidUtilities.runOnUIThread(onShowKeyboardCallback, 3000); // Timeout for floating keyboard
        } else {
            setFloatingButtonVisible(show, animate);
        }
        setCustomKeyboardVisible(isCustomKeyboardVisible(), animate);
        showKeyboard();
    }

    /**
     * @return If custom keyboard should be visible
     */
    private boolean isCustomKeyboardVisible() {
        return isPinCode() && type != TYPE_FAKE_PASSCODE_SETTINGS && !AndroidUtilities.isTablet() &&
                AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y && !AndroidUtilities.isAccessibilityTouchExplorationEnabled();
    }

    private void processNext() {
        if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD && passwordEditText.getText().length() == 0 || SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN && codeFieldContainer.getCode().length() != 4) {
            onPasscodeError();
            return;
        }

        if (otherItem != null) {
            otherItem.setVisibility(View.GONE);
        }

        titleTextView.setText(LocaleController.getString("ConfirmCreatePasscode", R.string.ConfirmCreatePasscode));
        descriptionTextSwitcher.setText(AndroidUtilities.replaceTags(LocaleController.getString("PasscodeReinstallNotice", R.string.PasscodeReinstallNotice)));
        firstPassword = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
        passwordEditText.setText("");
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        for (CodeNumberField f : codeFieldContainer.codeField) f.setText("");
        showKeyboard();
        passcodeSetStep = 1;
    }

    private boolean isPinCode() {
        return SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN;
    }

    private boolean isPassword() {
        return SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PASSWORD;
    }

    private void processDone() {
        if (isPassword() && passwordEditText.getText().length() == 0) {
            onPasscodeError();
            return;
        }
        String password = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
        if (type == TYPE_SETUP_FAKE_PASSCODE) {
            if (!firstPassword.equals(password)) {
                invalidPasscodeEntered();
                return;
            }

            fakePasscode.passcodeHash = FakePasscode.calculateHash(firstPassword, SharedConfig.passcodeSalt);
            SharedConfig.saveConfig();

            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.clearFocus();
                AndroidUtilities.hideKeyboard(f);
            }
            keyboardView.setEditText(null);

            animateSuccessAnimation(() -> {
                getMediaDataController().buildShortcuts();
                if (creating) {
                    SharedConfig.fakePasscodes.add(fakePasscode);
                    SharedConfig.fakePasscodeIndex++;
                    SharedConfig.saveConfig();
                    presentFragment(new FakePasscodeActivity(TYPE_FAKE_PASSCODE_SETTINGS, fakePasscode, false), true);
                } else {
                    finishFragment();
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetPasscode);
            });
        } else if (type == TYPE_ENTER_BACKUP_CODE) {
            String passcodeString = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
            if (Objects.equals(FakePasscode.calculateHash(passcodeString, SharedConfig.passcodeSalt), fakePasscode.passcodeHash)) {
                presentFragment(new FakePasscodeBackupActivity(fakePasscode, passcodeString), true);
            } else {
                invalidPasscodeEntered();
            }
        } else if (type == TYPE_ENTER_RESTORE_CODE) {
            String passcodeString = isPinCode() ? codeFieldContainer.getCode() : passwordEditText.getText().toString();
            FakePasscode passcode = FakePasscode.deserializeEncrypted(encryptedPasscode, passcodeString);
            if (passcode != null) {
                SharedConfig.fakePasscodes.add(passcode);
                SharedConfig.saveConfig();
                if (parentLayout.fragmentsStack.size() >= 2) {
                    parentLayout.removeFragmentFromStack(parentLayout.fragmentsStack.size() - 2);
                }
                presentFragment(new FakePasscodeActivity(TYPE_FAKE_PASSCODE_SETTINGS, passcode, false), true);
            } else {
                invalidPasscodeEntered();
            }
        }
    }

    private void invalidPasscodeEntered() {
        AndroidUtilities.updateViewVisibilityAnimated(passcodesDoNotMatchTextView, true);
        for (CodeNumberField f : codeFieldContainer.codeField) {
            f.setText("");
        }
        if (isPinCode()) {
            codeFieldContainer.codeField[0].requestFocus();
        }
        passwordEditText.setText("");
        onPasscodeError();

        codeFieldContainer.removeCallbacks(hidePasscodesDoNotMatch);
        codeFieldContainer.post(()->{
            codeFieldContainer.postDelayed(hidePasscodesDoNotMatch, 3000);
            postedHidePasscodesDoNotMatch = true;
        });
    }

    private void onPasscodeError() {
        if (getParentActivity() == null) return;
        try {
            fragmentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}
        if (isPinCode()) {
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.animateErrorProgress(1f);
            }
        } else {
            outlinePasswordView.animateError(1f);
        }
        AndroidUtilities.shakeViewSpring(isPinCode() ? codeFieldContainer : outlinePasswordView, isPinCode() ? 10 : 4, () -> AndroidUtilities.runOnUIThread(()->{
            if (isPinCode()) {
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.animateErrorProgress(0f);
                }
            } else {
                outlinePasswordView.animateError(0f);
            }
        }, isPinCode() ? 150 : 1000));
    }

    private class AccountActionsCellInfo {
        public Integer accountNum;
        public AccountActions actions;

        public AccountActionsCellInfo(Integer accountNum) {
            this.accountNum = accountNum;
        }

        public AccountActionsCellInfo(AccountActions actions) {
            this.actions = actions;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == changeNameRow || position == changeFakePasscodeRow || position == allowFakePasscodeLoginRow
                    || position ==  clearAfterActivationRow || position == deleteOtherPasscodesAfterActivationRow
                    || position == smsRow || position == clearTelegramCacheRow || position == clearProxiesRow
                    || position == activationMessageRow || position == badTriesToActivateRow
                    || (firstAccountRow <= position && position <= lastAccountRow) || position == backupPasscodeRow
                    || position == deletePasscodeRow;
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
                {
                    AccountActionsCell cell = new AccountActionsCell(mContext);
                    view = cell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 4:
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
                    if (position == allowFakePasscodeLoginRow) {
                        textCell.setTextAndCheck(LocaleController.getString("AllowFakePasscodeLogin", R.string.AllowFakePasscodeLogin), fakePasscode.allowLogin, false);
                    } else if (position == clearAfterActivationRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ClearAfterActivation", R.string.ClearAfterActivation), fakePasscode.clearAfterActivation, false);
                    } else if (position == deleteOtherPasscodesAfterActivationRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DeleteOtherPasscodesAfterActivation", R.string.DeleteOtherPasscodesAfterActivation), fakePasscode.deleteOtherPasscodesAfterActivation, false);
                    } else if (position == clearTelegramCacheRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ClearTelegramCacheOnFakeLogin", R.string.ClearTelegramCacheOnFakeLogin), fakePasscode.clearCacheAction.enabled, true);
                    } else if (position == clearProxiesRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ClearProxiesOnFakeLogin", R.string.ClearProxiesOnFakeLogin), fakePasscode.clearProxiesAction.enabled, false);
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == changeNameRow) {
                        changeNameCell = textCell;
                        textCell.setTextAndValue(LocaleController.getString("ChangeFakePasscodeName", R.string.ChangeFakePasscodeName), fakePasscode.name, true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == changeFakePasscodeRow) {
                        textCell.setText(LocaleController.getString("ChangeFakePasscode", R.string.ChangeFakePasscode), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == smsRow) {
                        textCell.setTextAndValue(LocaleController.getString("FakePasscodeSmsActionTitle", R.string.FakePasscodeSmsActionTitle), String.valueOf(fakePasscode.smsAction.messages.size()), true);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == activationMessageRow) {
                        String value = fakePasscode.activationMessage.isEmpty() ? LocaleController.getString("Disabled", R.string.Disabled) : fakePasscode.activationMessage;
                        textCell.setTextAndValue(LocaleController.getString("ActivationMessage", R.string.ActivationMessage), value, false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == badTriesToActivateRow) {
                        String value = fakePasscode.badTriesToActivate == null ? LocaleController.getString("Disabled", R.string.Disabled) : String.valueOf(fakePasscode.badTriesToActivate);
                        textCell.setTextAndValue(LocaleController.getString("BadPasscodeTriesToActivate", R.string.BadPasscodeTriesToActivate), value, false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == backupPasscodeRow) {
                        textCell.setText(LocaleController.getString("BackupFakePasscode", R.string.BackupFakePasscode), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteRedText2);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    } else if (position == deletePasscodeRow) {
                        textCell.setText(LocaleController.getString("DeleteFakePasscode", R.string.DeleteFakePasscode), false);
                        textCell.setTag(Theme.key_windowBackgroundWhiteRedText2);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText2));
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == changeFakePasscodeDetailRow) {
                        cell.setText(LocaleController.getString("ChangeFakePasscodeInfo", R.string.ChangeFakePasscodeInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if  (position == allowFakePasscodeLoginDetailRow) {
                        cell.setText(LocaleController.getString("AllowFakePasscodeLoginInfo", R.string.AllowFakePasscodeLoginInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if  (position == clearAfterActivationDetailRow) {
                        cell.setText(LocaleController.getString("ClearAfterActivationDetails", R.string.ClearAfterActivationDetails));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if  (position == deleteOtherPasscodesAfterActivationDetailRow) {
                        cell.setText(LocaleController.getString("DeleteOtherPasscodesAfterActivationDetails", R.string.DeleteOtherPasscodesAfterActivationDetails));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if  (position == activationMessageDetailRow) {
                        cell.setText(LocaleController.getString("ActivationMessageInfo", R.string.ActivationMessageInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if  (position == badTriesToActivateDetailRow) {
                        cell.setText(LocaleController.getString("BadPasscodeTriesToActivateInfo", R.string.BadPasscodeTriesToActivateInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if  (position == actionsDetailRow) {
                        cell.setText(LocaleController.getString("FakePasscodeActionsInfo", R.string.FakePasscodeActionsInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == accountDetailRow) {
                        cell.setText(LocaleController.getString("FakePasscodeAccountsInfo", R.string.FakePasscodeAccountsInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == backupPasscodeDetailRow) {
                        cell.setText(LocaleController.getString("BackupFakePasscodeInfo", R.string.BackupFakePasscodeInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == deletePasscodeDetailRow) {
                        cell.setText(LocaleController.getString("DeleteFakePasscodeInfo", R.string.DeleteFakePasscodeInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 3: {
                    AccountActionsCell cell = (AccountActionsCell) holder.itemView;
                    AccountActionsCellInfo info = accounts.get(position - firstAccountRow);
                    cell.setAccount(info.accountNum, info.actions, position != lastAccountRow);
                    break;
                }
                case 4: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == actionsHeaderRow) {
                        headerCell.setText(LocaleController.getString("FakePasscodeActionsHeader", R.string.FakePasscodeActionsHeader));
                    } else if (position == accountHeaderRow) {
                        headerCell.setText(LocaleController.getString("FakePasscodeAccountsHeader", R.string.FakePasscodeAccountsHeader));
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == allowFakePasscodeLoginRow || position == clearTelegramCacheRow || position == clearProxiesRow
                    || position == clearAfterActivationRow || position == deleteOtherPasscodesAfterActivationRow) {
                return 0;
            } else if (position == changeNameRow || position == changeFakePasscodeRow
                    || position == smsRow || position == deletePasscodeRow || position == activationMessageRow
                    || position == badTriesToActivateRow || position == backupPasscodeRow) {
                return 1;
            } else if (position == changeFakePasscodeDetailRow || position == allowFakePasscodeLoginDetailRow
                    || position == clearAfterActivationDetailRow || position == deleteOtherPasscodesAfterActivationDetailRow
                    || position == actionsDetailRow || position == activationMessageDetailRow
                    || position == badTriesToActivateDetailRow || position == accountDetailRow
                    || position == backupPasscodeDetailRow || position == deletePasscodeDetailRow) {
                return 2;
            } else if (firstAccountRow <= position && position <= lastAccountRow) {
                return 3;
            } else if (position == actionsHeaderRow || position == accountHeaderRow) {
                return 4;
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
