package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.NotificationsSettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class FakePasscode implements NotificationCenter.NotificationCenterDelegate {
    public boolean allowLogin = true;
    public String name;
    public String passcodeHash = "";
    public String activationMessage = "";
    public Integer badTriesToActivate;
    public boolean clearAfterActivation;
    public boolean deleteOtherPasscodesAfterActivation;

    public ClearCacheAction clearCacheAction = new ClearCacheAction();
    public List<RemoveChatsAction> removeChatsActions = Collections.synchronizedList(new ArrayList<>());
    public SosMessageAction familySosMessageAction = new SosMessageAction();
    public SosMessageAction trustedContactSosMessageAction = new SosMessageAction();
    public SmsAction smsAction = new SmsAction();
    public ClearProxiesAction clearProxiesAction = new ClearProxiesAction();
    public List<TelegramMessageAction> telegramMessageAction = Collections.synchronizedList(new ArrayList<>());
    public List<DeleteContactsAction> deleteContactsActions = Collections.synchronizedList(new ArrayList<>());
    public List<DeleteStickersAction> deleteStickersActions = Collections.synchronizedList(new ArrayList<>());
    public List<ClearSearchHistoryAction> clearSearchHistoryActions = Collections.synchronizedList(new ArrayList<>());
    public List<ClearBlackListAction> clearBlackListActions = Collections.synchronizedList(new ArrayList<>());
    public List<ClearSavedChannelsAction> clearSavedChannelsActions = Collections.synchronizedList(new ArrayList<>());
    public List<TerminateOtherSessionsAction> terminateOtherSessionsActions = Collections.synchronizedList(new ArrayList<>());
    public List<LogOutAction> logOutActions = Collections.synchronizedList(new ArrayList<>());
    public List<HideAccountAction> hideAccountActions = Collections.synchronizedList(new ArrayList<>());

    public Map<Integer, String> phoneNumbers = new HashMap<>();

    public FakePasscode() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.appDidLogout);
        }
    }

    List<Action> actions()
    {
        List<Action> result = new ArrayList<>(Arrays.asList(clearCacheAction, smsAction));
        result.addAll(telegramMessageAction);
        result.addAll(removeChatsActions);
        result.addAll(deleteContactsActions);
        result.addAll(deleteStickersActions);
        result.addAll(clearSearchHistoryActions);
        result.addAll(clearBlackListActions);
        result.addAll(clearSavedChannelsActions);
        result.addAll(terminateOtherSessionsActions);
        result.addAll(logOutActions);
        result.addAll(hideAccountActions);
        result.add(clearProxiesAction);
        return result;
    }

    public AccountActions getAccountActions(int accountNum) {
        return new AccountActions(accountNum, this);
    }

    public void executeActions() {
        if (SharedConfig.fakePasscodeActivatedIndex == SharedConfig.fakePasscodes.indexOf(this)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (Action action : actions()) {
                try {
                    action.execute();
                } catch (Exception ignored) {
                    try {
                        action.execute();
                    } catch (Exception ignored2) {
                    }
                }
            }
            if (deleteOtherPasscodesAfterActivation) {
                SharedConfig.fakePasscodes = SharedConfig.fakePasscodes.stream()
                        .filter(passcode -> passcode == this).collect(Collectors.toList());
            }
            if (clearAfterActivation) {
                clear();
            }
        });
    }

    private void clear() {
        activationMessage = "";
        badTriesToActivate = null;
        clearAfterActivation = false;
        deleteOtherPasscodesAfterActivation = false;

        clearCacheAction = new ClearCacheAction();
        removeChatsActions.stream().forEach(RemoveChatsAction::clear);
        familySosMessageAction = new SosMessageAction();
        trustedContactSosMessageAction = new SosMessageAction();
        smsAction = new SmsAction();
        clearProxiesAction = new ClearProxiesAction();
        telegramMessageAction = Collections.synchronizedList(new ArrayList<>());
        deleteContactsActions = Collections.synchronizedList(new ArrayList<>());
        deleteStickersActions = Collections.synchronizedList(new ArrayList<>());
        clearSearchHistoryActions = Collections.synchronizedList(new ArrayList<>());
        clearBlackListActions = Collections.synchronizedList(new ArrayList<>());
        clearSavedChannelsActions = Collections.synchronizedList(new ArrayList<>());
        terminateOtherSessionsActions = Collections.synchronizedList(new ArrayList<>());
        logOutActions = Collections.synchronizedList(new ArrayList<>());
        SharedConfig.saveConfig();
    }

    public void migrate() {
        if (familySosMessageAction != null) {
            if (familySosMessageAction.isFilled()) {
                smsAction.addMessage(familySosMessageAction.phoneNumber, familySosMessageAction.message, false);
            }
            familySosMessageAction = null;
        }
        if (trustedContactSosMessageAction != null) {
            if (trustedContactSosMessageAction.isFilled()) {
                smsAction.addMessage(trustedContactSosMessageAction.phoneNumber, trustedContactSosMessageAction.message, false);
            }
            trustedContactSosMessageAction = null;
        }
        actions().stream().forEach(Action::migrate);
    }

    private void removeAccount(int accountNum) {
        removeChatsActions.removeIf(a -> a.accountNum == accountNum);
        deleteContactsActions.removeIf(a -> a.accountNum == accountNum);
        deleteStickersActions.removeIf(a -> a.accountNum == accountNum);
        clearSearchHistoryActions.removeIf(a -> a.accountNum == accountNum);
        clearBlackListActions.removeIf(a -> a.accountNum == accountNum);
        clearSavedChannelsActions.removeIf(a -> a.accountNum == accountNum);
        terminateOtherSessionsActions.removeIf(a -> a.accountNum == accountNum);
        logOutActions.removeIf(a -> a.accountNum == accountNum);
        hideAccountActions.removeIf(a -> a.accountNum == accountNum);
        telegramMessageAction.removeIf(a -> a.accountNum == accountNum);
    }

    public void onDelete() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).removeObserver(this, NotificationCenter.appDidLogout);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            removeAccount(account);
        }
    }

    public static boolean checkMessage(int accountNum, long dialogId, Long senderId, String message) {
        if (message != null) {
            tryToActivatePasscodeByMessage(accountNum, senderId, message);
        }
        if (SharedConfig.fakePasscodeActivatedIndex == -1) {
            return true;
        }
        if (SharedConfig.fakePasscodes.isEmpty()) {
            return true;
        }
        FakePasscode passcode = SharedConfig.fakePasscodes.get(SharedConfig.fakePasscodeActivatedIndex);
        return !passcode.needDeleteMessage(accountNum, dialogId);
    }

    public static boolean needHideMessage(int accountNum, long dialogId) {
        if (SharedConfig.fakePasscodeActivatedIndex == -1) {
            return false;
        }
        if (!SharedConfig.fakePasscodes.isEmpty()) {
            return false;
        }
        FakePasscode passcode = SharedConfig.fakePasscodes.get(SharedConfig.fakePasscodeActivatedIndex);
        RemoveChatsAction removeChatsAction = passcode.removeChatsActions.stream().filter(a -> a.accountNum == accountNum).findFirst().orElse(null);
        boolean hideAccount = passcode.getAccountActions(accountNum).isHideAccount();
        return hideAccount || removeChatsAction != null && removeChatsAction.isHideChat(dialogId);
    }

    private synchronized static void tryToActivatePasscodeByMessage(int accountNum, Long senderId, String message) {
        if (message.isEmpty() || senderId != null && UserConfig.getInstance(accountNum).clientUserId == senderId) {
            return;
        }
        for (int i = 0; i < SharedConfig.fakePasscodes.size(); i++) {
            FakePasscode passcode = SharedConfig.fakePasscodes.get(i);
            if (passcode.activationMessage.isEmpty()) {
                continue;
            }
            if (passcode.activationMessage.equals(message)) {
                passcode.executeActions();
                SharedConfig.fakePasscodeActivated(i);
                SharedConfig.saveConfig();
                break;
            }
        }
    }

    private boolean needDeleteMessage(int accountNum, long dialogId) {
        AccountActions accountActions = getAccountActions(accountNum);
        RemoveChatsAction action = accountActions.getRemoveChatsAction();
        if (action == null)
            return false;
        return action.isRemoveNewMessagesFromChat(dialogId);
    }

    public static String getFakePhoneNumber(int accountNum) {
        if (SharedConfig.fakePasscodeActivatedIndex == -1) {
            return null;
        }
        FakePasscode passcode = SharedConfig.fakePasscodes.get(SharedConfig.fakePasscodeActivatedIndex);
        if (!passcode.phoneNumbers.containsKey(accountNum)) {
            return null;
        }
        return passcode.phoneNumbers.get(accountNum);
    }

    public static <T> List<T> filterItems(List<T> items, Optional<Integer> account, BiPredicate<T, RemoveChatsAction> filter) {
        if (SharedConfig.fakePasscodeActivatedIndex == -1 || items == null) {
            return items;
        }
        FakePasscode passcode = SharedConfig.fakePasscodes.get(SharedConfig.fakePasscodeActivatedIndex);
        List<T> filteredItems = items;
        for (RemoveChatsAction action : passcode.removeChatsActions) {
            if (!account.isPresent() || action.accountNum == account.get()) {
                filteredItems = filteredItems.stream().filter(i -> filter.test(i, action)).collect(Collectors.toList());
            }
        }
        return filteredItems;
    }

    public static List<TLRPC.Dialog> filterDialogs(List<TLRPC.Dialog> dialogs, Optional<Integer> account) {
        return filterItems(dialogs, account, (dialog, action) -> !action.isHideChat(Utils.getChatOrUserId(dialog.id, account)));
    }

    public static List<TLRPC.TL_topPeer> filterHints(List<TLRPC.TL_topPeer> hints, int account) {
        return filterItems(hints, Optional.of(account), (peer, action) ->
                !action.isHideChat(peer.peer.chat_id)
            && !action.isHideChat(peer.peer.channel_id)
            && !action.isHideChat(peer.peer.user_id));
    }

    public static List<TLRPC.Peer> filterPeers(List<TLRPC.Peer> peers, int account) {
        return filterItems(peers, Optional.of(account), (peer, action) -> !isHidePeer(peer, action));
    }

    public static boolean isHidePeer(TLRPC.Peer peer, int account) {
        if (SharedConfig.fakePasscodeActivatedIndex == -1 || peer == null) {
            return false;
        }
        FakePasscode passcode = SharedConfig.fakePasscodes.get(SharedConfig.fakePasscodeActivatedIndex);
        for (RemoveChatsAction action : passcode.removeChatsActions) {
            if (action.accountNum == account) {
                return isHidePeer(peer, action);
            }
        }
        return false;
    }

    public static boolean isHidePeer(TLRPC.Peer peer, RemoveChatsAction action) {
        return action.isHideChat(peer.chat_id)
                || action.isHideChat(peer.channel_id)
                || action.isHideChat(peer.user_id)
                || action.isRemovedChat(peer.chat_id)
                || action.isRemovedChat(peer.channel_id)
                || action.isRemovedChat(peer.user_id);
    }

    public static List<TLRPC.TL_contact> filterContacts(List<TLRPC.TL_contact> contacts, int account) {
        return filterItems(contacts, Optional.of(account), (contact, action) -> !action.isHideChat(contact.user_id));
    }

    public static List<Long> filterDialogIds(List<Long> ids, int account) {
        return filterItems(ids, Optional.of(account), (id, action) -> !action.isHideChat(id));
    }

    public static List<MessagesController.DialogFilter> filterFolders(List<MessagesController.DialogFilter> folders, int account) {
        return filterItems(folders, Optional.of(account), (folder, action) -> !action.isHideFolder(folder.id));
    }

    public static List<NotificationsSettingsActivity.NotificationException> filterNotificationExceptions(
            List<NotificationsSettingsActivity.NotificationException> exceptions, int account) {
        return filterItems(exceptions, Optional.of(account), (e, action) -> !action.isHideChat(e.did));
    }

    public static boolean isHideChat(long chatId, int account) {
        if (SharedConfig.fakePasscodeActivatedIndex == -1) {
            return false;
        }
        FakePasscode passcode = SharedConfig.fakePasscodes.get(SharedConfig.fakePasscodeActivatedIndex);
        for (RemoveChatsAction action : passcode.removeChatsActions) {
            if (action.accountNum == account) {
                return action.isHideChat(chatId);
            }
        }
        return false;
    }

    public static boolean isHideFolder(int folderId, int account) {
        if (SharedConfig.fakePasscodeActivatedIndex == -1) {
            return false;
        }
        FakePasscode passcode = SharedConfig.getActivatedFakePasscode();
        for (RemoveChatsAction action : passcode.removeChatsActions) {
            if (action.accountNum == account) {
                return action.isHideFolder(folderId);
            }
        }
        return false;
    }

    public static boolean isHideAccount(int account) {
        if (SharedConfig.fakePasscodeActivatedIndex == -1) {
            return false;
        }
        FakePasscode passcode = SharedConfig.getActivatedFakePasscode();
        for (HideAccountAction action : passcode.hideAccountActions) {
            if (action.accountNum == account) {
                return true;
            }
        }
        return false;
    }

    public int getHideOrLogOutCount() {
        Set<Integer> hiddenAccounts = hideAccountActions.stream().map(a -> a.accountNum)
                .collect(Collectors.toSet());
        hiddenAccounts.addAll(logOutActions.stream().map(a -> a.accountNum)
                .collect(Collectors.toSet()));
        return hiddenAccounts.size();
    }

    public int getHideAccountCount() {
        Set<Integer> hiddenAccounts = hideAccountActions.stream().map(a -> a.accountNum)
                .collect(Collectors.toSet());
        return hiddenAccounts.size();
    }

    public boolean autoAddAccountHidings() {
        int targetCount = UserConfig.getActivatedAccountsCount() - UserConfig.FAKE_PASSCODE_MAX_ACCOUNT_COUNT;
        if (targetCount > getHideOrLogOutCount()) {
            for (int i = UserConfig.MAX_ACCOUNT_COUNT - 1; i >= 0; i--) {
                int acc = i;
                if (UserConfig.getInstance(acc).isClientActivated() && !isHideAccount(acc)
                        && logOutActions.stream().noneMatch(a -> a.accountNum == acc)) {
                    HideAccountAction action = new HideAccountAction();
                    action.accountNum = acc;
                    hideAccountActions.add(action);
                    if (targetCount <= getHideOrLogOutCount()) {
                        break;
                    }
                }
            }
            SharedConfig.saveConfig();
            return true;
        } else {
            return false;
        }
    }

    public static boolean autoAddHidingsToAllFakePasscodes() {
        boolean result = false;
        for (FakePasscode fakePasscode: SharedConfig.fakePasscodes) {
            result |= fakePasscode.autoAddAccountHidings();
        }
        return result;
    }

    public static void cleanupHiddenAccountSystemNotifications() {
        Map<Integer, Boolean> hideMap = getLogoutOrHideAccountMap();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            Boolean hidden = hideMap.get(i);
            if (hidden != null && hidden) {
                NotificationsController.getInstance(i).cleanupSystemSettings();
            }
        }
    }

    public static void checkPendingRemovalChats() {
        if (RemoveChatsAction.pendingRemovalChatsChecked) {
            return;
        }
        FakePasscode passcode = SharedConfig.getActivatedFakePasscode();
        if (passcode != null) {
            for (RemoveChatsAction action : passcode.removeChatsActions) {
                action.checkPendingRemovalChats();
            }
        }
        RemoveChatsAction.pendingRemovalChatsChecked = true;
    }

    public static Map<Integer, Boolean> getLogoutOrHideAccountMap() {
        Map<Integer, Boolean> result = new HashMap<>();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            result.put(i, UserConfig.getInstance(i).isClientActivated() ? false : null);
        }
        for (FakePasscode fakePasscode: SharedConfig.fakePasscodes) {
            for (LogOutAction action: fakePasscode.logOutActions) {
                result.put(action.accountNum, true);
            }
            for (HideAccountAction action: fakePasscode.hideAccountActions) {
                result.put(action.accountNum, true);
            }
        }
        return result;
    }
}
