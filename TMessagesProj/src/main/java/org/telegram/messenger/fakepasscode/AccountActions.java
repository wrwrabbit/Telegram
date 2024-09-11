package org.telegram.messenger.fakepasscode;

import android.util.Base64;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AccountActions implements Action {
    @FakePasscodeSerializer.Ignore
    Integer accountNum = null;
    RemoveChatsAction removeChatsAction = new RemoveChatsAction();
    TelegramMessageAction telegramMessageAction = new TelegramMessageAction();
    private DeleteContactsAction deleteContactsAction = null;
    private DeleteStickersAction deleteStickersAction = null;
    private ClearSearchHistoryAction clearSearchHistoryAction = null;
    private ClearBlackListAction clearBlackListAction = null;
    private ClearSavedChannelsAction clearSavedChannelsAction = null;
    private ClearDraftsAction clearDraftsAction = null;
    TerminateOtherSessionsAction terminateOtherSessionsAction = new TerminateOtherSessionsAction();
    private LogOutAction logOutAction = null;
    HideAccountAction hideAccountAction = null;
    String fakePhone = "";
    CheckedSessions sessionsToHide = new CheckedSessions();
    private String salt= null;
    String idHash = null;

    public static boolean updateIdHashEnabled = true;

    AccountActions() {
        if (updateIdHashEnabled) {
            Utilities.globalQueue.postRunnable(new UpdateIdHashRunnable(this), 1000);
        }
    }

    @Override
    public void setExecutionScheduled() {
        Arrays.asList(removeChatsAction, telegramMessageAction, deleteContactsAction,
                deleteStickersAction, clearSearchHistoryAction, clearBlackListAction,
                clearSavedChannelsAction, clearDraftsAction, terminateOtherSessionsAction,
                hideAccountAction, logOutAction)
                .stream()
                .filter(Objects::nonNull)
                .forEach(Action::setExecutionScheduled);
    }

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (accountNum == null) {
            return;
        }


        fakePasscode.actionsResult.putFakePhoneNumber(accountNum, fakePhone);
        Arrays.asList(telegramMessageAction, removeChatsAction, deleteContactsAction,
                deleteStickersAction, clearSearchHistoryAction, clearBlackListAction,
                clearSavedChannelsAction, clearDraftsAction, terminateOtherSessionsAction,
                hideAccountAction)
                .stream()
                .filter(Objects::nonNull)
                .forEach(action -> {
                    action.setAccountNum(accountNum);
                    action.execute(fakePasscode);
                });
        if (logOutAction != null) {
            logOutAction.setAccountNum(accountNum);
            new CheckLogOutActionRunnable(logOutAction, fakePasscode).run();
        }
    }

    private byte[] getSalt() {
        if (salt == null) {
            byte[] saltBytes = new byte[16];
            Utilities.random.nextBytes(saltBytes);
            salt = Base64.encodeToString(saltBytes, Base64.DEFAULT);
        }
        return Base64.decode(salt, Base64.DEFAULT);
    }

    private String calculateIdHash(TLRPC.User user) {
        String phoneDigits = user.phone.replaceAll("[^0-9]", "");
        if (phoneDigits.length() < 4) {
            throw new RuntimeException("Can't calculate id hash: invalid phone");
        }
        int phoneId = Integer.parseInt(phoneDigits.substring(phoneDigits.length() - 4));
        long sum = (user.id % 10_000 + phoneId) % 10_000;
        byte[] sumBytes = Long.toString(sum).getBytes();
        byte[] bytes = new byte[32 + sumBytes.length];
        byte[] salt = getSalt();
        System.arraycopy(salt, 0, bytes, 0, 16);
        System.arraycopy(sumBytes, 0, bytes, 16, sumBytes.length);
        System.arraycopy(salt, 0, bytes, sumBytes.length + 16, 16);
        return Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
    }

    void checkIdHash() {
        if (accountNum != null) {
            UserConfig userConfig = UserConfig.getInstance(accountNum);
            if (userConfig.isConfigLoaded()) {
                if (userConfig.isClientActivated()) {
                    idHash = calculateIdHash(userConfig.getCurrentUser());
                } else {
                    accountNum = null;
                }
            }
        } else {
            checkAccountNum();
        }
    }

    public void checkAccountNum() {
        if (idHash != null) {
            for (int a = 0; a <UserConfig.MAX_ACCOUNT_COUNT; a++) {
                UserConfig userConfig = UserConfig.getInstance(a);
                if (userConfig.isClientActivated()) {
                    if (idHash.equals(calculateIdHash(userConfig.getCurrentUser()))) {
                        for (FakePasscode fakePasscode : SharedConfig.fakePasscodes) {
                            List<AccountActions> actions = fakePasscode.accountActions;
                            final int aFinal = a;
                            if (actions.contains(this) && actions.stream().noneMatch(action -> action.accountNum == aFinal)) {
                                accountNum = a;
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    public TelegramMessageAction getTelegramMessageAction() {
        return telegramMessageAction;
    }

    public Integer getAccountNum() {
        return accountNum;
    }

    public RemoveChatsAction getRemoveChatsAction() {
        return removeChatsAction;
    }

    public void setAccountNum(Integer accountNum) {
        this.accountNum = accountNum;
    }

    public String getFakePhone() {
        return fakePhone;
    }

    public CheckedSessions getSessionsToHide() {
        return sessionsToHide;
    }

    public HideAccountAction getHideAccountAction() {
        return hideAccountAction;
    }

    public String getIdHash() {
        return idHash;
    }

    public void setFakePhone(String fakePhone) {
        this.fakePhone = fakePhone;
    }

    public TerminateOtherSessionsAction getTerminateOtherSessionsAction() {
        return terminateOtherSessionsAction;
    }

    @SuppressWarnings("unchecked")
    private static class ActionReverser<T extends AccountAction> {
        private final T action;

        public ActionReverser(T action) {
            this.action = action;
        }

        private Class<T> getGenericClass() {
            ParameterizedType genericSuperclass = (ParameterizedType)getClass().getGenericSuperclass();
            if (genericSuperclass == null || genericSuperclass.getActualTypeArguments().length == 0) {
                return null;
            }
            return (Class<T>) genericSuperclass.getActualTypeArguments()[0];
        }


        private T reverse() {
            if (action != null) {
                return null;
            }
            Class<T> cls = getGenericClass();
            if (cls == null) {
                return null;
            }
            cls.getConstructors()[0].getParameterTypes();
            return Arrays.stream(cls.getConstructors())
                    .filter(constructor -> constructor.getParameterTypes().length == 0)
                    .findAny()
                    .map(constructor -> {
                        try {
                            return (T) constructor.newInstance();
                        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                            return null;
                        }
                    })
                    .orElse(null);
        }
    }

    private <T extends AccountAction> T reverse(T action) {
        return new ActionReverser<>(action).reverse();
    }

    public void toggleDeleteContactsAction() {
        deleteContactsAction = reverse(deleteContactsAction);
        SharedConfig.saveConfig();
    }

    public void toggleDeleteStickersAction() {
        deleteStickersAction = reverse(deleteStickersAction);
        SharedConfig.saveConfig();
    }

    public void toggleClearSearchHistoryAction() {
        clearSearchHistoryAction = reverse(clearSearchHistoryAction);
        SharedConfig.saveConfig();
    }

    public void toggleClearBlackListAction() {
        clearBlackListAction = reverse(clearBlackListAction);
        SharedConfig.saveConfig();
    }

    public void toggleClearSavedChannelsAction() {
        clearSavedChannelsAction = reverse(clearSavedChannelsAction);
        SharedConfig.saveConfig();
    }

    public void toggleClearDraftsAction() {
        clearDraftsAction = reverse(clearDraftsAction);
        SharedConfig.saveConfig();
    }

    public void toggleLogOutAction() {
        logOutAction = reverse(logOutAction);
        SharedConfig.saveConfig();
    }

    public void toggleHideAccountAction() {
        hideAccountAction = reverse(hideAccountAction);
        SharedConfig.saveConfig();
    }

    public void setSessionsToHide(List<Long> sessions) {
        sessionsToHide.setSessions(sessions);
        SharedConfig.saveConfig();
    }

    public boolean isDeleteContacts() {
        return deleteContactsAction != null;
    }

    public boolean isDeleteStickers() {
        return deleteStickersAction != null;
    }

    public boolean isClearSearchHistory() {
        return clearSearchHistoryAction != null;
    }

    public boolean isClearBlackList() {
        return clearBlackListAction != null;
    }

    public boolean isClearSavedChannels() {
        return clearSavedChannelsAction != null;
    }

    public boolean isClearDraftsAction() {
        return clearDraftsAction != null;
    }

    public boolean isLogOut() {
        return logOutAction != null;
    }

    public boolean isHideAccount() {
        return isHideAccount(false);
    }

    public boolean isHideAccount(boolean strictHiding) {
        if (hideAccountAction == null) {
            return false;
        }

        return !strictHiding || hideAccountAction.strictHiding;
    }

    public boolean isLogOutOrHideAccount() {
        return logOutAction != null || hideAccountAction != null;
    }

    public boolean isPreventStickersBulletin() {
        return deleteStickersAction != null && deleteStickersAction.isPreventBulletin();
    }

    public int getChatsToRemoveCount() {
        return removeChatsAction.getChatEntriesToRemove().size();
    }

    public void removeFakePhone() {
        fakePhone = "";
    }

    <T extends AccountAction> void setAction(T action) {
        for (Field field : AccountAction.class.getDeclaredFields()) {
            if (field.getType() == action.getClass()) {
                field.setAccessible(true);
                try {
                    field.set(this, action);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
