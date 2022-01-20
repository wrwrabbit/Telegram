/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.SavedChannelCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SavedChannelsAdapter extends RecyclerListView.SelectionAdapter {

    private final Context mContext;
    private int dialogsCount;
    private long openedDialogId;
    private int currentCount;
    private final ArrayList<Long> selectedDialogs = new ArrayList<>();
    private final int currentAccount;
    private boolean isReordering;

    List<TLRPC.Chat> chats = new ArrayList<>();
    Set<String> failedLoadChats = new HashSet<>();
    private boolean chatLoading = false;

    LongSparseArray<MessageObject> messageMap = new LongSparseArray<>();

    public SavedChannelsAdapter(Context context, int account) {
        mContext = context;
        currentAccount = account;
    }

    public void loadChats() {
        Set<String> names = UserConfig.getInstance(currentAccount).savedChannels;
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (String name : names) {
            TLObject obj = controller.getUserOrChat(name);
            if (obj instanceof TLRPC.Chat) {
                addChat((TLRPC.Chat)obj);
            }
        }
        loadOtherChats();
    }

    private void loadOtherChats() {
        Set<String> names = new HashSet<>(UserConfig.getInstance(currentAccount).savedChannels);
        Set<String> existedNames = new HashSet<>();
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (String name : names) {
            TLObject obj = controller.getUserOrChat(name);
            if (obj instanceof TLRPC.Chat) {
                existedNames.add(name);
            }
        }
        names.removeAll(existedNames);
        names.removeAll(failedLoadChats);
        if (!names.isEmpty() && !chatLoading) {
            chatLoading = true;
            String username = names.iterator().next();
            Utilities.globalQueue.postRunnable(() -> resolveUsername(username), 1000);
        }
    }

    private void addChat(TLRPC.Chat chat) {
        int position = getInsertPosition(chats, chat);
        if (position < 0) {
            MessagesController.getInstance(currentAccount).loadMessages(-chat.id, 0, false, 1, 0, 0, false, 0, 0, 2, 0, 0, 0, 0, 1);

            int insertPosition = -(position + 1);
            int oldSize = chats.size();
            chats.add(insertPosition, chat);
            notifyItemInserted(insertPosition);
        }
    }

    private int getInsertPosition(List<TLRPC.Chat> chats, TLRPC.Chat chat) {
        List<String> pinnedChannels = UserConfig.getInstance(currentAccount).pinnedSavedChannels;
        int position = Collections.binarySearch(chats, chat, (a, b) -> {
            int aIndex = pinnedChannels.indexOf(a.username);
            int bIndex = pinnedChannels.indexOf(b.username);
            if (aIndex == -1 && bIndex == -1) {
                MessageObject aMessage = getMessage(-a.id);
                MessageObject bMessage = getMessage(-b.id);
                if (aMessage != null && bMessage != null) {
                    return bMessage.messageOwner.date - aMessage.messageOwner.date;
                } else {
                    return Boolean.compare(aMessage == null, bMessage == null);
                }
            } else {
                aIndex = aIndex != -1 ? aIndex : pinnedChannels.size();
                bIndex = bIndex != -1 ? bIndex : pinnedChannels.size();
                return aIndex - bIndex;
            }
        });
        if (position >= 0 && chats.get(position).id != chat.id) {
            position = -chats.size() - 1;
        }
        return position;
    }

    public void fixChatPosition(String userName) {
        if (chats != null) {
            for (int i = 0; i < chats.size(); i++) {
                if (chats.get(i).username != null && chats.get(i).username.equals(userName)) {
                    fixChatPosition(i);
                }
            }
        }
    }

    public void fixChatPosition(int oldPosition) {
        TLRPC.Chat chat = chats.get(oldPosition);
        chats.remove(oldPosition);
        int position = getInsertPosition(chats, chat);
        if (position < 0) {
            int insertPosition = -(position + 1);
            chats.add(insertPosition, chat);
            if (insertPosition != oldPosition) {
                //return insertPosition;
                //notifyDataSetChanged();
                //notifyItemRemoved(oldPosition);
                //notifyItemInserted(insertPosition);
            }
        }
        //return -1;
    }

    public MessageObject getMessage(long dialogId) {
        return messageMap.get(dialogId);
    }

    public void setOpenedDialogId(long id) {
        openedDialogId = id;
    }

    public void onReorderStateChanged(boolean reordering) {
        isReordering = reordering;
    }

    public boolean isDataSetChanged() {
        int current = currentCount;
        return current != getItemCount() || current == 1;
    }

    @Override
    public int getItemCount() {
        List<TLRPC.Chat> list = getChatsArray();
        dialogsCount = list.size();
        if (dialogsCount == 0 && !isChatsEndReached()) {
            return (currentCount = 0);
        }
        int count = dialogsCount;
        if (!isChatsEndReached() || dialogsCount == 0) {
            count++;
        }
        if (dialogsCount != 0) {
            count++;
            if (dialogsCount > 10) {
                count++;
            }
        }
        currentCount = count;

        return count;
    }

    public TLObject getItem(int i) {
        List<TLRPC.Chat> list = getChatsArray();
        if (i < 0 || i >= list.size()) {
            return null;
        }
        return list.get(i);
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        if (holder.itemView instanceof SavedChannelCell) {
            SavedChannelCell dialogCell = (SavedChannelCell) holder.itemView;
            dialogCell.onReorderStateChanged(isReordering, false);
            dialogCell.setDialogIndex(holder.getAdapterPosition());
            dialogCell.checkCurrentDialogIndex();
            dialogCell.setChecked(selectedDialogs.contains(dialogCell.getChatId()), false);
        }
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int viewType = holder.getItemViewType();
        return viewType != 1 && viewType != 5 && viewType != 3 && viewType != 8 && viewType != 7 && viewType != 9 && viewType != 10 && viewType != 11;
    }

    @Override
    @NonNull
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = new SavedChannelCell(this, mContext, true, false, currentAccount, null);
                break;
            case 1:
                FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                flickerLoadingView.setIsSingleCell(true);
                flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
                view = flickerLoadingView;
                break;
            case 10: {
                view = new LastEmptyView(mContext);
                break;
            }
            case 11: {
                view = new TextInfoPrivacyCell(mContext);
                Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                combinedDrawable.setFullsize(true);
                view.setBackgroundDrawable(combinedDrawable);
                break;
            }
            default: {
                view = new TextCell(mContext);
            }
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, viewType == 5 ? RecyclerView.LayoutParams.MATCH_PARENT : RecyclerView.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        if (holder.getItemViewType() == 0) {
            SavedChannelCell cell = (SavedChannelCell) holder.itemView;
            TLRPC.Chat chat = (TLRPC.Chat) getItem(i);
            TLRPC.Chat nextChat = (TLRPC.Chat) getItem(i + 1);
            cell.useSeparator = nextChat != null;
            cell.fullSeparator = false;
            if (AndroidUtilities.isTablet()) {
                cell.setDialogSelected(chat.id == openedDialogId);
            }
            cell.setChecked(selectedDialogs.contains(chat.id), false);
            cell.setChat(chat);
        }
    }

    @NonNull
    public synchronized List<TLRPC.Chat> getChatsArray() {
        return chats;
    }

    @Override
    public int getItemViewType(int i) {
        if (dialogsCount > 10 && i == currentCount - 2) {
            return 11;
        }
        int size = getChatsArray().size();
        if (i == size) {
            if (!isChatsEndReached()) {
                return 1;
            } else {
                return 10;
            }
        } else if (i > size) {
            return 10;
        }
        return 0;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public class LastEmptyView extends View {

        public boolean moving;

        public LastEmptyView(Context context) {
            super(context);
        }

        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = getChatsArray().size();
            View parent = (View) getParent();
            int height;
            int paddingTop = parent.getPaddingTop();
            if (size == 0 || paddingTop == 0) {
                height = 0;
            } else {
                height = MeasureSpec.getSize(heightMeasureSpec);
                if (height == 0) {
                    height = parent.getMeasuredHeight();
                }
                if (height == 0) {
                    height = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                }
                int cellHeight = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72);
                int dialogsHeight = size * cellHeight + (size - 1);
                int archiveHeight = (0);
                if (dialogsHeight < height) {
                    height = height - dialogsHeight + archiveHeight;
                    height -= AndroidUtilities.statusBarHeight;
                    if (height < 0) {
                        height = 0;
                    }
                } else if (dialogsHeight - height < archiveHeight) {
                    height = archiveHeight - (dialogsHeight - height);
                    height -= AndroidUtilities.statusBarHeight;
                    if (height < 0) {
                        height = 0;
                    }
                } else {
                    height = 0;
                }
            }
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
        }

    }

    public boolean isChatsEndReached() {
        Set<String> names = new HashSet<>(UserConfig.getInstance(currentAccount).savedChannels);
        Set<String> existedNames = new HashSet<>();
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (String name : names) {
            TLObject obj = controller.getUserOrChat(name);
            if (obj instanceof TLRPC.Chat) {
                existedNames.add(name);
            }
        }
        names.removeAll(existedNames);
        names.removeAll(failedLoadChats);
        return names.isEmpty();
    }

    private void resolveUsername(String username) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            chatLoading = false;
            if (response != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);

                    if (res.chats.size() == 1) {
                        addChat(res.chats.get(0));
                    }
                    loadOtherChats();
                });
            } else {
                synchronized (this) {
                    failedLoadChats.add(username);
                }
                loadOtherChats();
            }
        });
    }

    public void messagesDidLoad(long dialogId, List<MessageObject> messages) {
        if (chats.stream().anyMatch(c -> c.id == -dialogId)) {
            for (MessageObject m : messages) {
                if (messageMap.indexOfKey(dialogId) < 0 || m.getId() > messageMap.get(dialogId).getId()) {
                    messageMap.put(dialogId, m);
                }
                /*
                if (message == null || m.getId() > message.getId()) {
                    message = m;
                    lastUnreadState = message.isUnread();
                    currentEditDate = message.messageOwner.edit_date;
                    lastSendState = message.messageOwner.send_state;
                }
                 */
            }
            //update(0, true, false);
            for (int i = 0; i < chats.size(); i++) {
                if (chats.get(i).id == -dialogId) {
                    //notifyItemChanged(i);
                    fixChatPosition(i);
                    break;
                }
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
        }
    }

    public boolean addOrRemoveSelectedDialog(long did) {
        if (selectedDialogs.contains(did)) {
            selectedDialogs.remove(did);
            return false;
        } else {
            selectedDialogs.add(did);
            return true;
        }
    }

    public List<String> getSelectedUserNames() {
        return chats.stream().filter(c -> selectedDialogs.contains(-c.id))
                .map(c -> c.username).collect(Collectors.toList());
    }

    public void clearSelectedDialogs() {
        selectedDialogs.clear();
    }

    public int getSelectedDialogCount() {
        return selectedDialogs.size();
    }

    public boolean containsSelectedDialog(long did) {
        return selectedDialogs.contains(did);
    }

    public void moveItem(int fromPosition, int toPosition) {
        Collections.swap(chats, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    public void removeItems(List<String> selectedUsernames) {
        chats = new ArrayList<>(chats.stream().filter(c -> !selectedUsernames.contains(c.username)).collect(Collectors.toList()));
    }
}
