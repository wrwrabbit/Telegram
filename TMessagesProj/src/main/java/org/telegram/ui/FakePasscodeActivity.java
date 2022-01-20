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
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.AccountActions;
import org.telegram.messenger.fakepasscode.FakePasscode;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DrawerUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogBuilder.DialogTemplate;
import org.telegram.ui.DialogBuilder.DialogType;
import org.telegram.ui.DialogBuilder.FakePasscodeDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FakePasscodeActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private TextView titleTextView;
    private EditTextBoldCursor passwordEditText;

    private TextSettingsCell changeNameCell;

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

    private int deletePasscodeRow;
    private int deletePasscodeDetailRow;

    List<Integer> accountNumbers = new ArrayList<>();

    private boolean creating;
    private FakePasscode fakePasscode;

    private final static int done_button = 1;

    public FakePasscodeActivity(int type, FakePasscode fakePasscode, boolean creating) {
        super();
        this.type = type;
        this.fakePasscode = fakePasscode;
        this.creating = creating;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
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
                    if (passcodeSetStep == 0) {
                        processNext();
                    } else if (passcodeSetStep == 1) {
                        processDone();
                    }
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
                        if (type == 1 && SharedConfig.passcodeType == 0) {
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
                if (SharedConfig.passcodeType == 0) {
                    actionBar.setTitle(LocaleController.getString("PasscodePIN", R.string.PasscodePIN));
                    InputFilter[] filterArray = new InputFilter[1];
                    filterArray[0] = new InputFilter.LengthFilter(4);
                    passwordEditText.setFilters(filterArray);
                    passwordEditText.setInputType(InputType.TYPE_CLASS_PHONE);
                    passwordEditText.setKeyListener(DigitsKeyListener.getInstance("1234567890"));
                } else if (SharedConfig.passcodeType == 1) {
                    actionBar.setTitle(LocaleController.getString("PasscodePassword", R.string.PasscodePassword));
                    passwordEditText.setFilters(new InputFilter[0]);
                    passwordEditText.setKeyListener(null);
                    passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            } else {
                actionBar.setTitle(LocaleController.getString("Passcode", R.string.Passcode));
            }
        } else {
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
                    presentFragment(new FakePasscodeActivity(1, fakePasscode, false));
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
                    AccountActions actions = fakePasscode.getAccountActions(accountNumbers.get(position - firstAccountRow));
                    presentFragment(new FakePasscodeAccountActionsActivity(actions), false);
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
        }

        return fragmentView;
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
        if (type != 0) {
            AndroidUtilities.runOnUIThread(() -> {
                if (passwordEditText != null) {
                    passwordEditText.requestFocus();
                    AndroidUtilities.showKeyboard(passwordEditText);
                }
            }, 200);
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
        accountNumbers.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accountNumbers.add(a);
                lastAccountRow = rowCount++;
            }
        }
        Collections.sort(accountNumbers, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });

        accountDetailRow = rowCount++;

        deletePasscodeRow = rowCount++;
        deletePasscodeDetailRow = rowCount++;
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

    private void processNext() {
        if (passwordEditText.getText().length() == 0 || SharedConfig.passcodeType == 0 && passwordEditText.getText().length() != 4) {
            onPasscodeError();
            return;
        }
        SharedConfig.PasscodeCheckResult passcodeCheckResult = SharedConfig.checkPasscode(passwordEditText.getText().toString());
        if (passcodeCheckResult.isRealPasscodeSuccess || passcodeCheckResult.fakePasscode != null) {
            try {
                Toast.makeText(getParentActivity(), LocaleController.getString("PasscodeInUse", R.string.PasscodeInUse), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.shakeView(titleTextView, 2, 0);
            passwordEditText.setText("");
            return;
        }
        if (SharedConfig.passcodeType == 0) {
            actionBar.setTitle(LocaleController.getString("PasscodePIN", R.string.PasscodePIN));
        } else {
            actionBar.setTitle(LocaleController.getString("PasscodePassword", R.string.PasscodePassword));
        }
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
                byte[] passcodeBytes = firstPassword.getBytes("UTF-8");
                byte[] bytes = new byte[32 + passcodeBytes.length];
                System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, 0, 16);
                System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                System.arraycopy(SharedConfig.passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                fakePasscode.passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
            } catch (Exception e) {
                FileLog.e(e);
            }

            SharedConfig.saveConfig();
            getMediaDataController().buildShortcuts();
            if (creating) {
                SharedConfig.fakePasscodes.add(fakePasscode);
                SharedConfig.fakePasscodeIndex++;
                SharedConfig.saveConfig();
                presentFragment(new FakePasscodeActivity(0, fakePasscode, false), true);
            } else {
                finishFragment();
            }
            passwordEditText.clearFocus();
            AndroidUtilities.hideKeyboard(passwordEditText);
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
                    || (firstAccountRow <= position && position <= lastAccountRow) || position == deletePasscodeRow;
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
                    DrawerUserCell cell = new DrawerUserCell(mContext);
                    view = cell;
                    cell.setFakePasscodeMode(true);
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
                    } else if (position == deletePasscodeDetailRow) {
                        cell.setText(LocaleController.getString("DeleteFakePasscodeInfo", R.string.DeleteFakePasscodeInfo));
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 3: {
                    DrawerUserCell cell = (DrawerUserCell) holder.itemView;
                    cell.setAccount(accountNumbers.get(position - firstAccountRow));
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
                    || position == badTriesToActivateRow) {
                return 1;
            } else if (position == changeFakePasscodeDetailRow || position == allowFakePasscodeLoginDetailRow
                    || position == clearAfterActivationDetailRow || position == deleteOtherPasscodesAfterActivationDetailRow
                    || position == actionsDetailRow || position == activationMessageDetailRow
                    || position == badTriesToActivateDetailRow || position == accountDetailRow
                    || position == deletePasscodeDetailRow) {
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

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckCell.class, TextSettingsCell.class, DrawerUserCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
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

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{DrawerUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText7));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        return themeDescriptions;
    }
}
