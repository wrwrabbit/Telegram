package org.telegram.messenger.partisan.secretgroups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class EncryptedGroup {
    private long externalId; // external id for identifying a group in the secret group protocol
    private int internalId; // internal chatId (part of dialogId) for dialog list, and other internal actions. Must be unique among encrypted chats
    private List<InnerEncryptedChat> innerChats = new ArrayList<>();
    private String name;
    private long ownerUserId;
    private EncryptedGroupState state;

    public int getInternalId() {
        return internalId;
    }

    public long getExternalId() {
        return externalId;
    }

    public List<InnerEncryptedChat> getInnerChats() {
        return Collections.unmodifiableList(innerChats);
    }

    public InnerEncryptedChat getInnerChatByEncryptedChatId(int chatId) {
        return innerChats.stream()
                .filter(c -> c.getEncryptedChatId().isPresent() && c.getEncryptedChatId().get() == chatId)
                .findAny()
                .orElse(null);
    }

    public InnerEncryptedChat getInnerChatByUserId(long userId) {
        return innerChats.stream()
                .filter(c -> c.getUserId() == userId)
                .findAny()
                .orElse(null);
    }

    private InnerEncryptedChat getOwnerInnerChat() {
        return getInnerChatByUserId(ownerUserId);
    }

    public int getOwnerEncryptedChatId() {
        return getOwnerInnerChat().getEncryptedChatId().get();
    }

    public boolean allInnerChatsMatchState(InnerEncryptedChatState state) {
        return innerChats.stream().allMatch(c -> c.getState() == state);
    }

    public List<Integer> getInnerEncryptedChatIds() {
        return innerChats.stream()
                .map(InnerEncryptedChat::getEncryptedChatId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public List<Long> getInnerUserIds() {
        return innerChats.stream().map(InnerEncryptedChat::getUserId).collect(Collectors.toList());
    }

    public String getName() {
        return name;
    }

    public long getOwnerUserId() {
        return ownerUserId;
    }

    public EncryptedGroupState getState() {
        return state;
    }

    public void setState(EncryptedGroupState state) {
        this.state = state;
    }

    public static class EncryptedGroupBuilder {
        private final EncryptedGroup encryptedGroup;

        public EncryptedGroupBuilder() {
            this.encryptedGroup = new EncryptedGroup();
        }

        public void setExternalId(long id) {
            encryptedGroup.externalId = id;
        }

        public void setInternalId(int id) {
            encryptedGroup.internalId = id;
        }

        public void setName(String name) {
            encryptedGroup.name = name;
        }

        public void setInnerChats(List<InnerEncryptedChat> innerChats) {
            encryptedGroup.innerChats = new ArrayList<>(innerChats);
        }

        public void setOwnerUserId(long ownerUserId) {
            encryptedGroup.ownerUserId = ownerUserId;
        }

        public void setState(EncryptedGroupState state) {
            encryptedGroup.state = state;
        }

        public EncryptedGroup create() {
            return encryptedGroup;
        }
    }
}