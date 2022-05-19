package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.RemoveChatsAction;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.ChatRemoveCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FakePasscodeRemoveChatsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private ScrollView scrollView;
    private SpansContainer spansContainer;
    private EditTextBoldCursor editText;
    private RecyclerListView listView;
    private EmptyTextProgressView emptyView;
    private RemoveChatsAdapter adapter;
    private boolean ignoreScrollEvent;
    private Set<Long> selectedDialogs = new HashSet<>();

    private int containerHeight;

    RemoveChatsAction action;
    protected int accountNum;

    private boolean searchWas;
    private boolean searching;
    private GroupCreateSpan currentDeletingSpan;

    private NumberTextView selectedDialogsCountTextView;

    private int fieldY;

    private float progressToActionMode;
    private ValueAnimator actionBarColorAnimator;
    private BackDrawable backDrawable;
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private ActionBarMenuItem deleteItem;
    private ActionBarMenuItem addItem;
    private ActionBarMenuItem editItem;

    private final static int delete = 100;
    private final static int add = 101;
    private final static int edit = 102;

    private static class ItemDecoration extends RecyclerView.ItemDecoration {

        private boolean single;

        public void setSingle(boolean value) {
            single = value;
        }

        @Override
        public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
            int width = parent.getWidth();
            int top;
            int childCount = parent.getChildCount() - (single ? 0 : 1);
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                View nextChild = i < childCount - 1 ? parent.getChildAt(i + 1) : null;
                if (child instanceof GraySectionCell || nextChild instanceof GraySectionCell) {
                    continue;
                }
                top = child.getBottom();
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(72), top, width - (LocaleController.isRTL ? AndroidUtilities.dp(72) : 0), top, Theme.dividerPaint);
            }
        }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            outRect.top = 1;
        }
    }

    private class SpansContainer extends ViewGroup {
        public SpansContainer(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int count = getChildCount();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int maxWidth = width - AndroidUtilities.dp(26);
            int currentLineWidth = 0;
            int y = AndroidUtilities.dp(10);
            int allCurrentLineWidth = 0;
            int allY = AndroidUtilities.dp(10);
            int x;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof GroupCreateSpan)) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
                if (currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                    allCurrentLineWidth = 0;
                }
                x = AndroidUtilities.dp(13) + currentLineWidth;
                child.setTranslationX(x);
                child.setTranslationY(y);
                currentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
                allCurrentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
            }
            int minWidth;
            if (AndroidUtilities.isTablet()) {
                minWidth = AndroidUtilities.dp(530 - 26 - 18 - 57 * 2) / 3;
            } else {
                minWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(26 + 18 + 57 * 2)) / 3;
            }
            if (maxWidth - currentLineWidth < minWidth) {
                currentLineWidth = 0;
                y += AndroidUtilities.dp(32 + 8);
            }
            if (maxWidth - allCurrentLineWidth < minWidth) {
                allY += AndroidUtilities.dp(32 + 8);
            }
            editText.measure(MeasureSpec.makeMeasureSpec(maxWidth - currentLineWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
            int currentHeight = allY + AndroidUtilities.dp(32 + 10);
            int fieldX = currentLineWidth + AndroidUtilities.dp(16);
            fieldY = y;
            containerHeight = currentHeight;
            editText.setTranslationX(fieldX);
            editText.setTranslationY(fieldY);
            setMeasuredDimension(width, containerHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int count = getChildCount();
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }
    }

    public FakePasscodeRemoveChatsActivity(RemoveChatsAction action, int accountNum) {
        super();
        this.action = action;
        this.accountNum = accountNum;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.chatDidCreated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatDidCreated);
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;
        currentDeletingSpan = null;

        actionBar.setBackButtonDrawable(backDrawable = new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("ChatsToRemove", R.string.ChatsToRemove));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (selectedDialogs.isEmpty()) {
                        finishFragment();
                    } else {
                        selectedDialogs.clear();
                        hideActionMode(true);
                    }
                } else if (id == delete) {
                    for (Long dialogId : selectedDialogs) {
                        action.remove(dialogId);
                    }
                    hideActionMode(true);
                    updateHint();
                } else if (id == add || id == edit) {
                    presentFragment(new FakePasscodeRemoveDialogSettingsActivity(action, selectedDialogs, accountNum));
                    selectedDialogs.clear();
                    if (listView != null) {
                        listView.getAdapter().notifyDataSetChanged();
                    }
                    hideActionMode(true);
                    updateHint();
                }
            }
        });

        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), true);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), true);

        fragmentView = new ViewGroup(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);
                int maxSize;
                if (AndroidUtilities.isTablet() || height > width) {
                    maxSize = AndroidUtilities.dp(144);
                } else {
                    maxSize = AndroidUtilities.dp(56);
                }

                scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.AT_MOST));
                listView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
                emptyView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - scrollView.getMeasuredHeight(), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                scrollView.layout(0, 0, scrollView.getMeasuredWidth(), scrollView.getMeasuredHeight());
                listView.layout(0, scrollView.getMeasuredHeight(), listView.getMeasuredWidth(), scrollView.getMeasuredHeight() + listView.getMeasuredHeight());
                emptyView.layout(0, scrollView.getMeasuredHeight(), emptyView.getMeasuredWidth(), scrollView.getMeasuredHeight() + emptyView.getMeasuredHeight());
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == listView || child == emptyView) {
                    parentLayout.drawHeaderShadow(canvas, scrollView.getMeasuredHeight());
                }
                return result;
            }
        };
        ViewGroup frameLayout = (ViewGroup) fragmentView;

        scrollView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                if (ignoreScrollEvent) {
                    ignoreScrollEvent = false;
                    return false;
                }
                rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
                rectangle.top += fieldY + AndroidUtilities.dp(20);
                rectangle.bottom += fieldY + AndroidUtilities.dp(50);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_windowBackgroundWhite));
        frameLayout.addView(scrollView);

        spansContainer = new SpansContainer(context);
        scrollView.addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        spansContainer.setOnClickListener(v -> {
            editText.clearFocus();
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });

        editText = new EditTextBoldCursor(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (currentDeletingSpan != null) {
                    currentDeletingSpan.cancelDeleteAnimation();
                    currentDeletingSpan = null;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!AndroidUtilities.showKeyboard(this)) {
                        clearFocus();
                        requestFocus();
                    }
                }
                return super.onTouchEvent(event);
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorColor(Theme.getColor(Theme.key_groupcreate_cursor));
        editText.setCursorWidth(1.5f);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(null);
        editText.setVerticalScrollBarEnabled(false);
        editText.setHorizontalScrollBarEnabled(false);
        editText.setTextIsSelectable(false);
        editText.setPadding(0, 0, 0, 0);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        spansContainer.addView(editText);
        editText.setHintText(LocaleController.getString("SearchForPeopleAndGroups", R.string.SearchForPeopleAndGroups));

        editText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
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
        editText.setOnKeyListener(new View.OnKeyListener() {

            private boolean wasEmpty;

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        wasEmpty = editText.length() == 0;
                    } else if (event.getAction() == KeyEvent.ACTION_UP && wasEmpty) {
                        updateHint();
                        checkVisibleRows();
                        return true;
                    }
                }
                return false;
            }
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editText.length() != 0) {
                    if (!adapter.searching) {
                        searching = true;
                        searchWas = true;
                        adapter.setSearching(true);
                        listView.setFastScrollVisible(false);
                        listView.setVerticalScrollBarEnabled(true);
                        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                        emptyView.showProgress();
                    }
                    adapter.searchDialogs(editText.getText().toString());
                } else {
                    closeSearch();
                }
            }
        });

        emptyView = new EmptyTextProgressView(context);
        if (getContactsController().isLoadingContacts()) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
        }
        emptyView.setShowAtCenter(true);
        emptyView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
        frameLayout.addView(emptyView);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

        listView = new RecyclerListView(context);
        listView.setFastScrollEnabled(RecyclerListView.FastScroll.LETTER_TYPE);
        listView.setEmptyView(emptyView);
        listView.setAdapter(adapter = new RemoveChatsAdapter(context));
        listView.setLayoutManager(linearLayoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        listView.addItemDecoration(new ItemDecoration());
        frameLayout.addView(listView);
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof ChatRemoveCell) {
                ChatRemoveCell cell = (ChatRemoveCell) view;
                if (!selectedDialogs.isEmpty()) {
                    select(cell);
                } else {
                    long id = cell.getDialogId();
                    if (id == 0) {
                        return;
                    }
                    boolean exists;
                    if (exists = action.contains(id)) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        String buttonText;
                        builder.setMessage(LocaleController.getString("RemoveDialogFromListAlert", R.string.RemoveDialogFromListAlert));
                        builder.setTitle(LocaleController.getString("RemoveDialogFromListTitle", R.string.RemoveDialogFromListTitle));
                        buttonText = LocaleController.getString("ClearSearchRemove", R.string.ClearSearchRemove);
                        builder.setPositiveButton(buttonText, (dialogInterface, i) -> {
                            action.remove(id);
                            SharedConfig.saveConfig();
                            updateHint();
                            if (searching || searchWas) {
                                AndroidUtilities.showKeyboard(editText);
                            } else {
                                cell.setChecked(!exists, true);
                            }
                            if (editText.length() > 0) {
                                editText.setText(null);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        AlertDialog alertDialog = builder.create();
                        showDialog(alertDialog);
                        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        if (button != null) {
                            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                        }
                    } else {
                        if (editText.length() > 0) {
                            editText.setText(null);
                        }
                        presentFragment(new FakePasscodeRemoveDialogSettingsActivity(action, Collections.singletonList(cell.getDialogId()), accountNum));
                    }
                }
            }
        });
        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListenerExtended() {
            @Override
            public boolean onItemClick(View view, int position, float x, float y) {
                select((ChatRemoveCell)view);
                return true;
            }

            @Override
            public void onLongClickRelease() {
            }

            @Override
            public void onMove(float dx, float dy) {
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(editText);
                }
            }
        });
        updateHint();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (editText != null) {
            editText.requestFocus();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateHint();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.contactsDidLoad) {
            if (emptyView != null) {
                emptyView.showTextView();
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            if (listView != null) {
                int mask = (Integer) args[0];
                int count = listView.getChildCount();
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        if (child instanceof ChatRemoveCell) {
                            ((ChatRemoveCell) child).update(mask);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.chatDidCreated) {
            removeSelfFromStack();
        }
    }

    @Override
    public AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(accountNum);
    }

    @Keep
    public void setContainerHeight(int value) {
        containerHeight = value;
        if (spansContainer != null) {
            spansContainer.requestLayout();
        }
    }

    @Keep
    public int getContainerHeight() {
        return containerHeight;
    }

    private void checkVisibleRows() {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof ChatRemoveCell) {
                ChatRemoveCell cell = (ChatRemoveCell) child;
                long id = cell.getDialogId();
                if (id != 0) {
                    cell.setChecked(action.contains(id), true);
                    cell.setCheckBoxEnabled(true);
                }
            }
        }
    }

    private void closeSearch() {
        searching = false;
        searchWas = false;
        adapter.setSearching(false);
        adapter.searchDialogs(null);
        listView.setFastScrollVisible(true);
        listView.setVerticalScrollBarEnabled(false);
        emptyView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
    }

    private void updateHint() {
        actionBar.setSubtitle(LocaleController.formatPluralString("Chats", action.getChatEntriesToRemove().size()));
    }

    private void select(ChatRemoveCell cell) {
        long id = cell.getDialogId();

        if (selectedDialogs.contains(id)) {
            selectedDialogs.remove(id);
            cell.setSelected(false);
        } else {
            selectedDialogs.add(id);
            cell.setSelected(true);
        }
        if (selectedDialogs.isEmpty()) {
            hideActionMode(true);
        } else {
            showOrUpdateActionMode();
        }
    }

    private void showOrUpdateActionMode() {
        boolean updateAnimated = false;
        if (actionBar.isActionModeShowed()) {
            if (selectedDialogs.isEmpty()) {
                hideActionMode(true);
                return;
            }

            updateMenuItemsVisibility();
            updateAnimated = true;
        } else {
            createActionMode(null);
            updateMenuItemsVisibility();
            AndroidUtilities.hideKeyboard(fragmentView.findFocus());
            actionBar.setActionModeOverrideColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            actionBar.showActionMode();

            AnimatorSet animatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            for (int a = 0; a < actionModeViews.size(); a++) {
                View view = actionModeViews.get(a);
                view.setPivotY(ActionBar.getCurrentActionBarHeight() / 2);
                AndroidUtilities.clearDrawableAnimation(view);
                animators.add(ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.1f, 1.0f));
            }
            animatorSet.playTogether(animators);
            animatorSet.setDuration(200);
            animatorSet.start();

            animateActionBarColor(true);
            if (backDrawable != null) {
                backDrawable.setRotation(1, true);
            }
        }
        selectedDialogsCountTextView.setNumber(selectedDialogs.size(), updateAnimated);
    }

    private void updateMenuItemsVisibility() {
        boolean isEdit = selectedDialogs.stream().anyMatch(id -> action.contains(id));
        addItem.setVisibility(isEdit ? View.GONE : View.VISIBLE);
        editItem.setVisibility(isEdit ? View.VISIBLE : View.GONE);
        deleteItem.setVisibility(isEdit ? View.VISIBLE : View.GONE);
    }

    private void animateActionBarColor(boolean forward) {
        if (actionBarColorAnimator != null) {
            actionBarColorAnimator.cancel();
        }
        if (forward) {
            actionBarColorAnimator = ValueAnimator.ofFloat(progressToActionMode, 1f);
        } else {
            actionBarColorAnimator = ValueAnimator.ofFloat(progressToActionMode, 0f);
        }
        actionBarColorAnimator.addUpdateListener(valueAnimator -> {
            progressToActionMode = (float) valueAnimator.getAnimatedValue();
            actionBar.setBackgroundColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefault), Theme.getColor(Theme.key_windowBackgroundWhite), progressToActionMode));
            for (int i = 0; i < actionBar.getChildCount(); i++) {
                if (actionBar.getChildAt(i).getVisibility() == View.VISIBLE && actionBar.getChildAt(i) != actionBar.getActionMode() && actionBar.getChildAt(i) != actionBar.getBackButton()) {
                    actionBar.getChildAt(i).setAlpha(1f - progressToActionMode);
                }
            }
            if (fragmentView != null) {
                fragmentView.invalidate();
            }
        });
        actionBarColorAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        actionBarColorAnimator.setDuration(200);
        actionBarColorAnimator.start();
    }

    private void createActionMode(String tag) {
        if (actionBar.actionModeIsExist(tag)) {
            return;
        }
        final ActionBarMenu actionMode = actionBar.createActionMode(false, tag);
        actionMode.setBackground(null);

        selectedDialogsCountTextView = new NumberTextView(actionMode.getContext());
        selectedDialogsCountTextView.setTextSize(18);
        selectedDialogsCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedDialogsCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedDialogsCountTextView.setOnTouchListener((v, event) -> true);

        deleteItem = actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete));
        addItem = actionMode.addItemWithWidth(add, R.drawable.add, AndroidUtilities.dp(54), LocaleController.getString("Add", R.string.Add));
        editItem = actionMode.addItemWithWidth(edit, R.drawable.msg_edit, AndroidUtilities.dp(54), LocaleController.getString("Edit", R.string.Edit));

        actionModeViews.add(deleteItem);
        actionModeViews.add(addItem);
        actionModeViews.add(editItem);
        updateMenuItemsVisibility();
    }

    private void hideActionMode(boolean animateCheck) {
        actionBar.hideActionMode();
        selectedDialogs.clear();
        if (backDrawable != null) {
            backDrawable.setRotation(0, true);
        }
        animateActionBarColor(false);
        adapter.notifyDataSetChanged();
    }

    public class RemoveChatsAdapter extends RecyclerListView.FastScrollAdapter {

        private Context context;
        private ArrayList<Object> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private SearchAdapterHelper searchAdapterHelper;
        private Runnable searchRunnable;
        private boolean searching;
        private ArrayList<Object> contacts = new ArrayList<>();
        private final int usersStartRow = 0;

        public RemoveChatsAdapter(Context ctx) {
            context = ctx;

            boolean hasSelf = false;
            ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getAllDialogs();

            Set<Long> selectedIds = action.getIds();
            for (Long id: selectedIds) {
                boolean added = false;
                if (DialogObject.isUserDialog(id)) {
                    TLRPC.User user = getMessagesController().getUser(id);
                    if (user != null) {
                        added = true;
                        contacts.add(user);
                        if (UserObject.isUserSelf(user)) {
                            hasSelf = true;
                        }
                    }
                } else if (DialogObject.isEncryptedDialog(id)) {
                    TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(id));
                    if (encryptedChat != null) {
                        added = true;
                        contacts.add(encryptedChat);
                    }
                } else if (DialogObject.isChatDialog(id)) {
                    TLRPC.Chat chat = getMessagesController().getChat(-id);
                    if (chat != null) {
                        added = true;
                        contacts.add(chat);
                    }
                }
                if (!added) {
                    contacts.add(action.get(id));
                }
            }

            for (int a = 0, N = dialogs.size(); a < N; a++) {
                TLRPC.Dialog dialog = dialogs.get(a);
                if (dialog.id == 0 || selectedIds.contains(dialog.id)) {
                    continue;
                }
                if (DialogObject.isUserDialog(dialog.id)) {
                    TLRPC.User user = getMessagesController().getUser(dialog.id);
                    if (user != null) {
                        contacts.add(user);
                        if (UserObject.isUserSelf(user)) {
                            hasSelf = true;
                        }
                    }
                } else if (DialogObject.isEncryptedDialog(dialog.id)) {
                    TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(dialog.id));
                    if (encryptedChat != null) {
                        contacts.add(encryptedChat);
                    }
                } else if (DialogObject.isChatDialog(dialog.id)) {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialog.id);
                    if (chat != null) {
                        contacts.add(chat);
                    }
                }
            }
            if (!hasSelf) {
                TLRPC.User user = getMessagesController().getUser(getUserConfig().clientUserId);
                contacts.add(0, user);
            }

            searchAdapterHelper = new SearchAdapterHelper(false);
            searchAdapterHelper.setAllowGlobalResults(false);
            searchAdapterHelper.setDelegate((searchId) -> {
                if (searchRunnable == null && !searchAdapterHelper.isSearchInProgress()) {
                    emptyView.showTextView();
                }
                notifyDataSetChanged();
            });
        }

        public void setSearching(boolean value) {
            if (searching == value) {
                return;
            }
            searching = value;
            notifyDataSetChanged();
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public int getItemCount() {
            int count;
            if (searching) {
                count = searchResult.size();
                int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
                int globalCount = searchAdapterHelper.getGlobalSearch().size();
                count += localServerCount + globalCount;
                return count;
            } else {
                count = contacts.size();
            }
            return count;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                default:
                case 1:
                    view = new ChatRemoveCell(context, accountNum);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1: {
                    ChatRemoveCell cell = (ChatRemoveCell) holder.itemView;
                    Object object;
                    CharSequence username = null;
                    CharSequence name = null;
                    if (searching) {
                        int localCount = searchResult.size();
                        int globalCount = searchAdapterHelper.getGlobalSearch().size();
                        int localServerCount = searchAdapterHelper.getLocalServerSearch().size();

                        if (position >= 0 && position < localCount) {
                            object = searchResult.get(position);
                        } else if (position >= localCount && position < localServerCount + localCount) {
                            object = searchAdapterHelper.getLocalServerSearch().get(position - localCount);
                        } else if (position > localCount + localServerCount && position < globalCount + localCount + localServerCount) {
                            object = searchAdapterHelper.getGlobalSearch().get(position - localCount - localServerCount);
                        } else {
                            object = null;
                        }
                        if (object != null) {

                            String objectUserName;
                            if (object instanceof TLRPC.User) {
                                objectUserName = ((TLRPC.User) object).username;
                            } else if (object instanceof TLRPC.EncryptedChat){
                                objectUserName = getMessagesController().getUser(((TLRPC.EncryptedChat) object).user_id).username;
                            } else if (object instanceof TLRPC.Chat){
                                objectUserName = ((TLRPC.Chat) object).username;
                            } else {
                                objectUserName = null;
                            }
                            if (position < localCount) {
                                name = searchResultNames.get(position);
                                if (name != null && !TextUtils.isEmpty(objectUserName)) {
                                    if (name.toString().startsWith("@" + objectUserName)) {
                                        username = name;
                                        name = null;
                                    }
                                }
                            } else if (position > localCount && !TextUtils.isEmpty(objectUserName)) {
                                String foundUserName = searchAdapterHelper.getLastFoundUsername();
                                if (foundUserName.startsWith("@")) {
                                    foundUserName = foundUserName.substring(1);
                                }
                                try {
                                    int index;
                                    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                                    spannableStringBuilder.append("@");
                                    spannableStringBuilder.append(objectUserName);
                                    if ((index = AndroidUtilities.indexOfIgnoreCase(objectUserName, foundUserName)) != -1) {
                                        int len = foundUserName.length();
                                        if (index == 0) {
                                            len++;
                                        } else {
                                            index++;
                                        }
                                        spannableStringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    username = spannableStringBuilder;
                                } catch (Exception e) {
                                    username = objectUserName;
                                }
                            }
                        }
                    } else {
                        if (position < usersStartRow) {
                            return;
                        }
                        object = contacts.get(position - usersStartRow);
                    }
                    Long id;
                    if (object instanceof TLRPC.User) {
                        id = ((TLRPC.User) object).id;
                    } else if (object instanceof TLRPC.EncryptedChat) {
                        id = DialogObject.makeEncryptedDialogId(((TLRPC.EncryptedChat) object).id);
                    } else if (object instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) object).id;
                    } else if (object instanceof RemoveChatsAction.RemoveChatEntry) {
                        id = ((RemoveChatsAction.RemoveChatEntry)object).chatId;
                    } else {
                        id = 0L;
                    }
                    cell.setSelected(selectedDialogs.contains(id));
                    cell.setOnSettingsClick(() -> setupChatToRemove(cell, action.get(id)));
                    if (!searching) {
                        StringBuilder builder = new StringBuilder();
                        ArrayList<MessagesController.DialogFilter> filters = getMessagesController().dialogFilters;
                        for (int a = 0, N = filters.size(); a < N; a++) {
                            MessagesController.DialogFilter filter = filters.get(a);
                            if (filter.includesDialog(getAccountInstance(), id)) {
                                if (builder.length() > 0) {
                                    builder.append(", ");
                                }
                                builder.append(filter.name);
                            }
                        }
                        if (object != null) {
                            username = builder;
                        }
                    }
                    cell.setObject(object, name, username);
                    if (id != 0) {
                        cell.setChecked(action.contains(id), false);
                        cell.setCheckBoxEnabled(true);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return 1;
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = (int) (getItemCount() * progress);
            position[1] = 0;
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ChatRemoveCell) {
                ((ChatRemoveCell) holder.itemView).recycle();
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 1;
        }

        public void searchDialogs(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (query == null) {
                searchResult.clear();
                searchResultNames.clear();
                searchAdapterHelper.mergeResults(null);
                searchAdapterHelper.queryServerSearch(null, true, true, false, false, false, 0, false, 0, 0);
                notifyDataSetChanged();
            } else {
                Utilities.searchQueue.postRunnable(searchRunnable = () -> AndroidUtilities.runOnUIThread(() -> {
                    searchAdapterHelper.queryServerSearch(query, true, true, true, true, false, 0, false, 0, 0);
                    Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), new ArrayList<>());
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.length() == 0) {
                            search2 = null;
                        }
                        String[] search = new String[1 + (search2 != null ? 1 : 0)];
                        search[0] = search1;
                        if (search2 != null) {
                            search[1] = search2;
                        }

                        ArrayList<Object> resultArray = new ArrayList<>();
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                        for (int a = 0; a < contacts.size(); a++) {
                            Object object = contacts.get(a);

                            String username;
                            final String[] names = new String[3];

                            if (object instanceof TLRPC.User) {
                                TLRPC.User user = (TLRPC.User) object;
                                names[0] = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                                username = user.username;
                                if (UserObject.isReplyUser(user)) {
                                    names[2] = LocaleController.getString("RepliesTitle", R.string.RepliesTitle).toLowerCase();
                                } else if (user.self) {
                                    names[2] = LocaleController.getString("SavedMessages", R.string.SavedMessages).toLowerCase();
                                }
                            } else if (object instanceof TLRPC.EncryptedChat) {
                                TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat) object;
                                TLRPC.User user = getMessagesController().getUser(encryptedChat.user_id);
                                names[0] = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                                username = user.username;
                            } else if (object instanceof  TLRPC.Chat) {
                                TLRPC.Chat chat = (TLRPC.Chat) object;
                                names[0] = chat.title.toLowerCase();
                                username = chat.username;
                            } else {
                                RemoveChatsAction.RemoveChatEntry entry = (RemoveChatsAction.RemoveChatEntry) object;
                                names[0] = entry.title.toLowerCase();
                                username = null;
                            }
                            names[1] = LocaleController.getInstance().getTranslitString(names[0]);
                            if (names[0].equals(names[1])) {
                                names[1] = null;
                            }

                            int found = 0;
                            for (String q : search) {
                                for (int i = 0; i < names.length; i++) {
                                    final String name = names[i];
                                    if (name != null && (name.startsWith(q) || name.contains(" " + q))) {
                                        found = 1;
                                        break;
                                    }
                                }
                                if (found == 0 && username != null && username.toLowerCase().startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    if (found == 1) {
                                        if (object instanceof TLRPC.User) {
                                            TLRPC.User user = (TLRPC.User) object;
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                        } else if (object instanceof TLRPC.EncryptedChat){
                                            TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat) object;
                                            TLRPC.User user = getMessagesController().getUser(encryptedChat.user_id);
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                        } else if (object instanceof TLRPC.Chat){
                                            TLRPC.Chat chat = (TLRPC.Chat) object;
                                            String title = UserConfig.getChatTitleOverride(currentAccount, chat.id);
                                            if (title == null) {
                                                title = chat.title;
                                            }
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(title, null, q));
                                        } else {
                                            RemoveChatsAction.RemoveChatEntry entry = (RemoveChatsAction.RemoveChatEntry) object;
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(entry.title, null, q));
                                        }
                                    } else {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName("@" + username, null, "@" + q));
                                    }
                                    resultArray.add(object);
                                    break;
                                }
                            }
                        }
                        updateSearchResults(resultArray, resultArrayNames);
                    });
                }), 300);
            }
        }

        private void updateSearchResults(final ArrayList<Object> users, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchRunnable = null;
                searchResult = users;
                searchResultNames = names;
                searchAdapterHelper.mergeResults(searchResult);
                if (searching && !searchAdapterHelper.isSearchInProgress()) {
                    emptyView.showTextView();
                }
                notifyDataSetChanged();
            });
        }

        private void setupChatToRemove(ChatRemoveCell cell, RemoveChatsAction.RemoveChatEntry entry) {
            if (entry == null) {
                return;
            }
            selectedDialogs.clear();
            if (listView != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            hideActionMode(true);
            updateHint();
            presentFragment(new FakePasscodeRemoveDialogSettingsActivity(action, entry, accountNum));
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof ChatRemoveCell) {
                        ((ChatRemoveCell) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(scrollView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));
        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_groupcreate_hintText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_groupcreate_cursor));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatRemoveCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatRemoveCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatRemoveCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxDisabled));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ChatRemoveCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{ChatRemoveCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{ChatRemoveCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ChatRemoveCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanBackground));
        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanText));
        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_groupcreate_spanDelete));
        themeDescriptions.add(new ThemeDescription(spansContainer, 0, new Class[]{GroupCreateSpan.class}, null, null, null, Theme.key_avatar_backgroundBlue));

        return themeDescriptions;
    }
}
