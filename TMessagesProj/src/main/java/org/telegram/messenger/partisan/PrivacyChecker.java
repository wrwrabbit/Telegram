package org.telegram.messenger.partisan;

import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_ADDED_BY_PHONE;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_CALLS;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_FORWARDS;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_INVITE;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_LASTSEEN;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_P2P;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_PHONE;
import static org.telegram.ui.PrivacyControlActivity.PRIVACY_RULES_TYPE_PHOTO;

import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

public class PrivacyChecker implements NotificationCenter.NotificationCenterDelegate {
    private final int accountNum;
    private final Consumer<Boolean> handler;

    private PrivacyChecker(int accountNum, Consumer<Boolean> handler) {
        this.accountNum = accountNum;
        this.handler = handler;
    }

    public static void fix(int account, Runnable onError, Runnable onSuccess) {
        setupPrivacySettings(account, PRIVACY_RULES_TYPE_PHONE, onError, () -> {
            setupPrivacySettings(account, PRIVACY_RULES_TYPE_FORWARDS, onError, () -> {
                setupPrivacySettings(account, PRIVACY_RULES_TYPE_PHOTO, onError, () -> {
                    setupPrivacySettings(account, PRIVACY_RULES_TYPE_P2P, onError, () -> {
                        setupPrivacySettings(account, PRIVACY_RULES_TYPE_CALLS, onError, () -> {
                            setupPrivacySettings(account, PRIVACY_RULES_TYPE_INVITE, onError, () -> {
                                setupPrivacySettings(account, PRIVACY_RULES_TYPE_LASTSEEN, onError, onSuccess);
                            });
                        });
                    });
                });
            });
        });
    }

    private static void setupPrivacySettings(int account, int rulesType, Runnable onError, Runnable onSuccess) {
        TLRPC.TL_account_setPrivacy req = new TLRPC.TL_account_setPrivacy();
        if (rulesType == PRIVACY_RULES_TYPE_PHONE) {
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneNumber();
            TLRPC.TL_account_setPrivacy req2 = new TLRPC.TL_account_setPrivacy();
            req2.key = new TLRPC.TL_inputPrivacyKeyAddedByPhone();
            req2.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
            ConnectionsManager.getInstance(account).sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_account_privacyRules privacyRules = (TLRPC.TL_account_privacyRules) response;
                    ContactsController.getInstance(account).setPrivacyRules(privacyRules.rules, PRIVACY_RULES_TYPE_ADDED_BY_PHONE);
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
        } else if (rulesType == PRIVACY_RULES_TYPE_FORWARDS) {
            req.key = new TLRPC.TL_inputPrivacyKeyForwards();
        } else if (rulesType == PRIVACY_RULES_TYPE_PHOTO) {
            req.key = new TLRPC.TL_inputPrivacyKeyProfilePhoto();
        } else if (rulesType == PRIVACY_RULES_TYPE_P2P) {
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneP2P();
        } else if (rulesType == PRIVACY_RULES_TYPE_CALLS) {
            req.key = new TLRPC.TL_inputPrivacyKeyPhoneCall();
        } else if (rulesType == PRIVACY_RULES_TYPE_INVITE) {
            req.key = new TLRPC.TL_inputPrivacyKeyChatInvite();
        } else {
            req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        }

        if (rulesType == PRIVACY_RULES_TYPE_PHOTO || rulesType == PRIVACY_RULES_TYPE_INVITE) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
        } else {
            req.rules.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
        }

        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_account_privacyRules privacyRules = (TLRPC.TL_account_privacyRules) response;
                MessagesController.getInstance(account).putUsers(privacyRules.users, false);
                MessagesController.getInstance(account).putChats(privacyRules.chats, false);
                ContactsController.getInstance(account).setPrivacyRules(privacyRules.rules, rulesType);
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } else {
                if (onError != null) {
                    onError.run();
                }
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public static void check(int accountNum, Consumer<Boolean> handler) {
        PrivacyChecker checker = new PrivacyChecker(accountNum, handler);
        checker.check();
    }

    private void check() {
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.privacyRulesUpdated);
        AndroidUtilities.runOnUIThread(() -> ContactsController.getInstance(accountNum).loadPrivacySettings());
    }

    private boolean isGoodPrivacy() {
        ContactsController controller = ContactsController.getInstance(accountNum);
        if (controller.getPrivacyRules(PRIVACY_RULES_TYPE_PHONE).stream().anyMatch(r -> r instanceof TLRPC.TL_privacyValueAllowAll))
            return false;
        if (controller.getPrivacyRules(PRIVACY_RULES_TYPE_LASTSEEN).stream().anyMatch(r -> r instanceof TLRPC.TL_privacyValueAllowAll))
            return false;
        if (controller.getPrivacyRules(PRIVACY_RULES_TYPE_CALLS).stream().anyMatch(r -> r instanceof TLRPC.TL_privacyValueAllowAll))
            return false;
        return true;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            ContactsController controller = ContactsController.getInstance(accountNum);
            if (controller.getPrivacyRules(PRIVACY_RULES_TYPE_PHONE) == null
                    || controller.getPrivacyRules(PRIVACY_RULES_TYPE_LASTSEEN) == null
                    || controller.getPrivacyRules(PRIVACY_RULES_TYPE_CALLS) == null) {
                return;
            }
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.privacyRulesUpdated);
            handler.accept(isGoodPrivacy());
        }
    }
}
