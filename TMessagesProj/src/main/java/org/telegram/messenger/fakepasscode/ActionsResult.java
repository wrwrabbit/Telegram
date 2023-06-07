package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    private Set<Integer> hiddenAccounts = Collections.synchronizedSet(new HashSet<>());
    public Set<HiddenAccountEntry> hiddenAccountEntries = Collections.synchronizedSet(new HashSet<>());

    @JsonIgnore
    public Set<Action> actionsPreventsLogoutAction = Collections.synchronizedSet(new HashSet<>());

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
    }
}
