package org.telegram.messenger.fakepasscode;

import android.os.SystemClock;
import android.text.TextUtils;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.NotificationsSettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class FakePasscodeUtils {
    public static FakePasscode getActivatedFakePasscode() {
        if (SharedConfig.fakePasscodeActivatedIndex > -1
                && SharedConfig.fakePasscodeActivatedIndex < SharedConfig.fakePasscodes.size()) {
            return SharedConfig.fakePasscodes.get(SharedConfig.fakePasscodeActivatedIndex);
        } else {
            return null;
        }
    }

    public static boolean isFakePasscodeActivated() {
        return SharedConfig.fakePasscodeActivatedIndex != -1;
    }

    public static boolean checkMessage(int accountNum, TLRPC.Message message) {
        return checkMessage(accountNum, message.dialog_id, message.from_id != null ? message.from_id.user_id : null, message.message, message.date);
    }

    public static boolean checkMessage(int accountNum, long dialogId, Long senderId, String message) {
        return checkMessage(accountNum, dialogId, senderId, message, null);
    }

    public static boolean checkMessage(int accountNum, long dialogId, Long senderId, String message, Integer date) {
        if (message != null) {
            tryToActivatePasscodeByMessage(accountNum, senderId, message, date);
        }
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode == null) {
            return true;
        }
        return !passcode.needDeleteMessage(accountNum, dialogId);
    }

    private synchronized static void tryToActivatePasscodeByMessage(int accountNum, Long senderId, String message, Integer date) {
        if (message.isEmpty() || senderId != null && UserConfig.getInstance(accountNum).clientUserId == senderId) {
            return;
        }
        for (int i = 0; i < SharedConfig.fakePasscodes.size(); i++) {
            FakePasscode passcode = SharedConfig.fakePasscodes.get(i);
            if (passcode.activationMessage.isEmpty()) {
                continue;
            }
            if (date != null && passcode.activationDate != null && date < passcode.activationDate) {
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

    public static String getFakePhoneNumber(int accountNum) {
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode != null) {
            String number = passcode.actionsResult.getFakePhoneNumber(accountNum);
            if (number != null) {
                return number;
            }
        }
        return SharedConfig.phoneOverride;
    }

    public static String getFakePhoneNumber(int accountNum, String fallback) {
        String fakeNumber = getFakePhoneNumber(accountNum);
        if (TextUtils.isEmpty(fakeNumber)) {
            return fallback;
        } else {
            return fakeNumber;
        }
    }

    public static <T> List<T> filterItems(List<T> items, Optional<Integer> account, BiPredicate<T, ChatFilter> filter) {
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode == null || items == null) {
            return items;
        }
        List<T> filteredItems = items;
        for (Map.Entry<Integer, RemoveChatsResult> pair : passcode.actionsResult.removeChatsResults.entrySet()) {
            Integer accountNum = pair.getKey();
            if (accountNum != null && (!account.isPresent() || accountNum.equals(account.get()))) {
                filteredItems = filteredItems.stream().filter(i -> filter.test(i, pair.getValue())).collect(Collectors.toList());
            }
        }
        for (AccountActions actions : passcode.getFilteredAccountActions()) {
            Integer accountNum = actions.getAccountNum();
            if (accountNum != null && (!account.isPresent() ||  accountNum.equals(account.get()))) {
                filteredItems = filteredItems.stream().filter(i -> filter.test(i, actions.getRemoveChatsAction())).collect(Collectors.toList());
            }
        }
        return new FilteredArrayList<>(filteredItems, items);
    }

    public static List<TLRPC.Dialog> filterDialogs(List<TLRPC.Dialog> dialogs, Optional<Integer> account) {
        return filterItems(dialogs, account, (dialog, filter) -> !filter.isHideChat(Utils.getChatOrUserId(dialog.id, account)));
    }

    public static List<TLRPC.TL_topPeer> filterHints(List<TLRPC.TL_topPeer> hints, int account) {
        return filterItems(hints, Optional.of(account), (peer, filter) ->
                !filter.isHideChat(peer.peer.chat_id)
                        && !filter.isHideChat(peer.peer.channel_id)
                        && !filter.isHideChat(peer.peer.user_id));
    }

    public static List<TLRPC.Peer> filterPeers(List<TLRPC.Peer> peers, int account) {
        return filterItems(peers, Optional.of(account), (peer, action) -> !isHidePeer(peer, action));
    }

    public static List<TLRPC.TL_sendAsPeer> filterSendAsPeers(List<TLRPC.TL_sendAsPeer> peers, int account) {
        return filterItems(peers, Optional.of(account), (peer, action) -> !isHidePeer(peer.peer, action));
    }

    public static boolean isHidePeer(TLRPC.Peer peer, int account) {
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode == null || peer == null) {
            return false;
        }
        RemoveChatsResult result = passcode.actionsResult.getRemoveChatsResult(account);
        if (result != null && isHidePeer(peer, result)) {
            return true;
        }
        for (AccountActions actions : passcode.getFilteredAccountActions()) {
            Integer accountNum = actions.getAccountNum();
            if (accountNum != null && accountNum.equals(account)) {
                return isHidePeer(peer, actions.getRemoveChatsAction());
            }
        }
        return false;
    }

    private static boolean isHidePeer(TLRPC.Peer peer, ChatFilter filter) {
        return filter.isHideChat(peer.chat_id)
                || filter.isHideChat(peer.channel_id)
                || filter.isHideChat(peer.user_id);
    }

    public static List<TLRPC.TL_contact> filterContacts(List<TLRPC.TL_contact> contacts, int account) {
        return filterItems(contacts, Optional.of(account), (contact, filter) -> !filter.isHideChat(contact.user_id));
    }

    public static List<Long> filterDialogIds(List<Long> ids, int account) {
        return filterItems(ids, Optional.of(account), (id, filter) -> !filter.isHideChat(id));
    }

    public static List<MessagesController.DialogFilter> filterFolders(List<MessagesController.DialogFilter> folders, int account) {
        return filterItems(folders, Optional.of(account), (folder, filter) -> !filter.isHideFolder(folder.id));
    }

    public static List<NotificationsSettingsActivity.NotificationException> filterNotificationExceptions(
            List<NotificationsSettingsActivity.NotificationException> exceptions, int account) {
        return filterItems(exceptions, Optional.of(account), (e, filter) -> !filter.isHideChat(e.did));
    }

    public static boolean isHideChat(long chatId, int account) {
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode == null) {
            return false;
        }
        RemoveChatsResult result = passcode.actionsResult.getRemoveChatsResult(account);
        if (result != null && result.isHideChat(chatId)) {
            return true;
        }
        AccountActions actions = passcode.getAccountActions(account);
        return actions != null && actions.getRemoveChatsAction().isHideChat(chatId);
    }

    public static boolean isHideFolder(int folderId, int account) {
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode == null) {
            return false;
        }
        RemoveChatsResult result = passcode.actionsResult.getRemoveChatsResult(account);
        if (result != null && result.isHideFolder(folderId)) {
            return true;
        }
        AccountActions actions = passcode.getAccountActions(account);
        return actions != null && actions.getRemoveChatsAction().isHideFolder(folderId);
    }

    public static boolean isHideAccount(int account) {
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode == null) {
            return false;
        }
        if (passcode.actionsResult.hiddenAccounts.contains(account)) {
            return true;
        }
        AccountActions actions = passcode.getAccountActions(account);
        return actions != null && actions.isHideAccount();
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
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode != null) {
            for (AccountActions actions : passcode.accountActions) {
                actions.getRemoveChatsAction().checkPendingRemovalChats();
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
            for (AccountActions actions : fakePasscode.getFilteredAccountActions()) {
                if (actions.isLogOutOrHideAccount()) {
                    result.put(actions.getAccountNum(), true);
                }
            }
        }
        return result;
    }

    public static boolean isHideMessage(int accountNum, Long dialogId, Integer messageId) {
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode == null) {
            return false;
        }

        RemoveChatsResult removeChatsResult = passcode.actionsResult.getRemoveChatsResult(accountNum);
        if (removeChatsResult != null && removeChatsResult.isHideChat(dialogId)) {
            return true;
        }
        AccountActions actions = passcode.getAccountActions(accountNum);
        if (actions != null) {
            RemoveChatsAction removeChatsAction = passcode.getAccountActions(accountNum).getRemoveChatsAction();
            boolean hideAccount = passcode.getAccountActions(accountNum).isHideAccount();
            if (hideAccount || removeChatsAction.isHideChat(dialogId)) {
                return true;
            }
        }

        if (messageId != null) {
            TelegramMessageResult telegramMessageResult = passcode.actionsResult.getTelegramMessageResult(accountNum);
            if (telegramMessageResult != null && telegramMessageResult.isSosMessage(dialogId, messageId)) {
                return true;
            }
        }
        return false;
    }

    public static FakePasscode getFingerprintFakePasscode() {
        for (FakePasscode passcode : SharedConfig.fakePasscodes) {
            if (passcode.activateByFingerprint) {
                return passcode;
            }
        }
        return null;
    }

    public static boolean isPreventStickersBulletin() {
        FakePasscode passcode = getActivatedFakePasscode();
        if (passcode == null) {
            return false;
        }
        return passcode.accountActions.stream().anyMatch(AccountActions::isPreventStickersBulletin);
    }

    public static boolean isNeedSwitchAccount() {
        if (!isFakePasscodeActivated()) {
            return false;
        }
        boolean isCurrentAccountCorrect = false;
        int accountIndex = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated() && !FakePasscodeUtils.isHideAccount(a)) {
                if (a == UserConfig.selectedAccount) {
                    isCurrentAccountCorrect = true;
                    break;
                }
                accountIndex++;
                if (accountIndex >= UserConfig.getFakePasscodeMaxAccountCount()) {
                    break;
                }
            }
        }
        return !isCurrentAccountCorrect;
    }

    private static boolean isFakePasscodeWithTimerExist() {
        return SharedConfig.fakePasscodes.stream().anyMatch(f -> f.activateByTimerTime != null);
    }

    public static void updateLastPauseFakePasscodeTime() {
        if (SharedConfig.lastPauseFakePasscodeTime == 0 && isFakePasscodeWithTimerExist()) {
            SharedConfig.lastPauseFakePasscodeTime = SystemClock.elapsedRealtime() / 1000;
        }
    }

    public static void tryActivateByTimer() {
        if (SharedConfig.lastPauseFakePasscodeTime == 0) {
            return;
        }
        long uptime = SystemClock.elapsedRealtime() / 1000;
        long duration = uptime - SharedConfig.lastPauseFakePasscodeTime;
        FakePasscode activePasscode = getActivatedFakePasscode();
        long activatedPasscodeDuration = activePasscode != null && activePasscode.activateByTimerTime != null
            ? activePasscode.activateByTimerTime
            : 0;
        List<FakePasscode> sortedPasscodes = SharedConfig.fakePasscodes.stream()
                .filter(p -> p.activateByTimerTime != null && activatedPasscodeDuration < p.activateByTimerTime && p.activateByTimerTime <= duration)
                .sorted(Comparator.comparingInt(p -> p.activateByTimerTime))
                .collect(Collectors.toList());
        if (!sortedPasscodes.isEmpty()) {
            for (FakePasscode passcode : sortedPasscodes) {
                passcode.executeActions();
            }
            FakePasscode lastPasscode = sortedPasscodes.get(sortedPasscodes.size() - 1);
            SharedConfig.fakePasscodeActivated(SharedConfig.fakePasscodes.indexOf(lastPasscode));
            SharedConfig.saveConfig();
        }
    }
}
