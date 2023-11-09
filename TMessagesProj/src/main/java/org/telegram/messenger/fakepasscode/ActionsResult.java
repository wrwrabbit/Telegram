package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.SharedConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ActionsResult {

    public static class HiddenAccountEntry {
        public int accountNum;
        public boolean strictHiding;

        public HiddenAccountEntry() {}

        public HiddenAccountEntry(int accountNum, boolean strictHiding) {
            this.accountNum = accountNum;
            this.strictHiding = strictHiding;
        }

        public boolean isHideAccount(long accountNum, boolean strictHiding) {
            if (this.accountNum != accountNum) {
                return false;
            }
            if (strictHiding && !this.strictHiding) {
                return false;
            }
            return true;
        }
    }

    public Map<Integer, RemoveChatsResult> removeChatsResults = new HashMap<>();
    public Map<Integer, TelegramMessageResult> telegramMessageResults = new HashMap<>();
    public Map<Integer, String> fakePhoneNumbers = new HashMap<>();
    @Deprecated
    public Set<Integer> hiddenAccounts = Collections.synchronizedSet(new HashSet<>());
    public List<HiddenAccountEntry> hiddenAccountEntries = Collections.synchronizedList(new ArrayList<>());

    @JsonIgnore
    public Set<Action> actionsPreventsLogoutAction = Collections.synchronizedSet(new HashSet<>());
    @JsonIgnore
    private long activationTime = 0;

    public RemoveChatsResult getRemoveChatsResult(int accountNum) {
        return removeChatsResults.get(accountNum);
    }

    public RemoveChatsResult getOrCreateRemoveChatsResult(int accountNum) {
        return putIfAbsent(removeChatsResults, accountNum, new RemoveChatsResult());
    }

    public TelegramMessageResult getTelegramMessageResult(int accountNum) {
        return telegramMessageResults.get(accountNum);
    }

    public TelegramMessageResult getOrCreateTelegramMessageResult(int accountNum) {
        return putIfAbsent(telegramMessageResults, accountNum, new TelegramMessageResult());
    }

    public void putFakePhoneNumber(int accountNum, String phoneNumber) {
        fakePhoneNumbers.put(accountNum, phoneNumber);
    }

    public String getFakePhoneNumber(int accountNum) {
        return fakePhoneNumbers.get(accountNum);
    }

    public boolean isHideAccount(int accountNum, boolean strictHiding) {
        return hiddenAccountEntries.stream().anyMatch(entry -> entry.isHideAccount(accountNum, strictHiding));
    }

    private static <T> T putIfAbsent(Map<Integer, T> map, int accountNum, T value) {
        T result = map.get(accountNum);
        if (result == null) {
            result = value;
            map.put(accountNum, result);
        }
        return result;
    }

    public void migrate() {
        if (removeChatsResults != null) {
            removeChatsResults.values().stream().forEach(RemoveChatsResult::migrate);
        }
        if (hiddenAccounts != null) {
            hiddenAccountEntries = hiddenAccounts.stream()
                    .map(id -> new HiddenAccountEntry(id, false))
                    .collect(Collectors.toList());
            hiddenAccounts.clear();
        }
        SharedConfig.saveConfig();
    }

    public void setActivated() {
        activationTime = System.currentTimeMillis();
    }

    public boolean isJustActivated() {
        if (System.currentTimeMillis() - activationTime < 30 * 1000) {
            return true;
        } else {
            activationTime = 0;
            return false;
        }
    }
}
