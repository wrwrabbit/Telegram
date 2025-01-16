package org.telegram.messenger.partisan;

import android.content.Context;

import com.jakewharton.processphoenix.ProcessPhoenix;

import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FileProtectionSwitcher implements NotificationCenter.NotificationCenterDelegate {
    private final Set<Integer> accountsWithEnabledFileProtection = new HashSet<>();
    private final BaseFragment fragment;
    private boolean enableForAllAccounts;
    private Map<Integer, Boolean> valuesPerAccounts;

    public FileProtectionSwitcher(BaseFragment fragment) {
        this.fragment = fragment;
    }

    public void changeForAllAccounts(boolean value) {
        enableForAllAccounts = value;
        valuesPerAccounts = new HashMap<>();
        startSwitching();
    }

    public void changeForMultipleAccounts(Map<Integer, Boolean> values) {
        if (values.values().stream().allMatch(enabled -> enabled)) {
            enableForAllAccounts = true;
            valuesPerAccounts = new HashMap<>();
        } else if (values.values().stream().allMatch(enabled -> !enabled)) {
            enableForAllAccounts = false;
            valuesPerAccounts = new HashMap<>();
        } else {
            enableForAllAccounts = false;
            valuesPerAccounts = values;
        }
        startSwitching();
    }

    private void startSwitching() {
        AlertDialog enablingFileProtectionDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        fragment.showDialog(enablingFileProtectionDialog);

        if (!enableForAllAccounts && valuesPerAccounts.isEmpty()) {
            SharedConfig.toggleFileProtectionForAllAccountsEnabled();
            Utils.foreachActivatedAccountInstance(accountInstance -> {
                accountInstance.getUserConfig().fileProtectionEnabled = false;
                accountInstance.getUserConfig().saveConfig(false);
            });
            ProcessPhoenix.triggerRebirth(getContext());
            return;
        }

        accountsWithEnabledFileProtection.clear();
        Utils.foreachActivatedAccountInstance(accountInstance -> {
            if (needEnableFileProtectionForAccount(accountInstance.getCurrentAccount())) {
                accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.onDatabaseReset);
                accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.onFileProtectedDbCleared);
                accountInstance.getMessagesStorage().clearLocalDatabase();
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.onDatabaseReset) {
            MessagesStorage.getInstance(account).clearFileProtectedDb();
        } else if (id == NotificationCenter.onFileProtectedDbCleared) {
            accountsWithEnabledFileProtection.add(account);
            if (fileProtectionEnablingFinished()) {
                updateConfigs();
                ProcessPhoenix.triggerRebirth(getContext());
            }
        }
    }

    private void updateConfigs() {
        if (SharedConfig.fileProtectionForAllAccountsEnabled != enableForAllAccounts) {
            SharedConfig.toggleFileProtectionForAllAccountsEnabled();
        }
        Utils.foreachActivatedAccountInstance(accountInstance -> {
            boolean enabled = SharedConfig.fileProtectionForAllAccountsEnabled
                    || valuesPerAccounts.getOrDefault(accountInstance.getCurrentAccount(), false);
            accountInstance.getUserConfig().fileProtectionEnabled = enabled;
            accountInstance.getUserConfig().saveConfig(false);
        });
    }

    private boolean fileProtectionEnablingFinished() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig config = UserConfig.getInstance(a);
            if (config.isClientActivated() && needEnableFileProtectionForAccount(a) && !accountsWithEnabledFileProtection.contains(a)) {
                return false;
            }
        }
        return true;
    }

    private boolean needEnableFileProtectionForAccount(int accountNum) {
        return enableForAllAccounts || valuesPerAccounts.getOrDefault(accountNum, false);
    }

    private Context getContext() {
        return fragment.getContext();
    }
}
