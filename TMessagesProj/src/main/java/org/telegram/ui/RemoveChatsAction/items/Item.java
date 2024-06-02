package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.RemoveChatsAction;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.stream.Collectors;

public abstract class Item {
    protected final int accountNum;

    public abstract TLObject getProfileObject();
    public abstract Long getId();
    protected abstract String getName();
    protected String getAlternativeName() {
        return null;
    }
    public CharSequence getDisplayName() {
        return UserConfig.getChatTitleOverride(accountNum, getId(), getName());
    }
    public abstract String getUsername();
    protected abstract CharSequence generateSearchName(String q);
    public boolean isSelf() {
        return false;
    }
    public boolean shouldBeEditedToo(Item selectedItem) {
        return false;
    }

    protected Item(int accountNum) {
        this.accountNum = accountNum;
    }

    public static Item tryCreateItemById(int accountNum, RemoveChatsAction action, Long id) {
        MessagesController messagesController = MessagesController.getInstance(accountNum);
        if (DialogObject.isUserDialog(id)) {
            TLRPC.User user = messagesController.getUser(id);
            return user != null ? new UserItem(accountNum, user) : null;
        } else if (DialogObject.isChatDialog(id)) {
            TLRPC.Chat chat = messagesController.getChat(-id);
            return chat != null ? new ChatItem(accountNum, chat) : null;
        } else if (DialogObject.isEncryptedDialog(id)) {
            int encryptedChatId = DialogObject.getEncryptedChatId(id);
            TLRPC.EncryptedChat encryptedChat = messagesController.getEncryptedChat(encryptedChatId);
            return encryptedChat != null ? new EncryptedChatItem(accountNum, encryptedChat) : null;
        } else {
            RemoveChatsAction.RemoveChatEntry removeChatEntry = action.get(id);
            return removeChatEntry != null ? new RemoveChatEntryItem(accountNum, removeChatEntry) : null;
        }
    }

    public CharSequence getStatus() {
        return getMessagesController().dialogFilters
                .stream()
                .filter(f -> f.includesDialog(getAccountInstance(), getId()))
                .map(f -> f.name)
                .collect(Collectors.joining(", "));
    }

    private String getSearchName() {
        return getName();
    }

    public SearchItem search(String query) {
        if (matchesQueryByName(query)) {
            return new SearchItem(this, generateSearchName(query), null);
        } else if (matchesQueryByUsername(query)) {
            return new SearchItem(this, null, generateSearchUsername(query));
        } else {
            return null;
        }
    }

    private CharSequence generateSearchUsername(String query) {
        return AndroidUtilities.generateSearchName("@" + getUsername(), null, "@" + query);
    }

    private boolean matchesQueryByName(String query) {
        String name = getSearchName().toLowerCase();
        if (nameMatches(name, query)) {
            return true;
        }
        String translitName = LocaleController.getInstance().getTranslitString(name);
        if (nameMatches(translitName, query)) {
            return true;
        }
        return nameMatches(getAlternativeName(), query);
    }

    private boolean matchesQueryByUsername(String query) {
        String username = getUsername();
        return username != null && username.toLowerCase().startsWith(query);
    }

    private boolean nameMatches(String name, String query) {
        if (name == null) {
            return false;
        }
        return name.startsWith(query) || name.contains(" " + query);
    }

    private AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(accountNum);
    }

    protected MessagesController getMessagesController() {
        return getAccountInstance().getMessagesController();
    }

    protected UserConfig getUserConfig() {
        return getAccountInstance().getUserConfig();
    }
}
