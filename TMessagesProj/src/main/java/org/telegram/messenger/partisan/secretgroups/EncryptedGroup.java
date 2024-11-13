package org.telegram.messenger.partisan.secretgroups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EncryptedGroup {
    private int id;
    private final List<Integer> encryptedChatsIds;
    private String name;

    private EncryptedGroup() {
        encryptedChatsIds = new ArrayList<>();
    }

    public EncryptedGroup(int id, List<Integer> encryptedChatsIds, String name) {
        this.id = id;
        this.encryptedChatsIds = new ArrayList<>(encryptedChatsIds);
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public List<Integer> getEncryptedChatsIds() {
        return Collections.unmodifiableList(encryptedChatsIds);
    }

    public String getName() {
        return name;
    }

    public static class EncryptedGroupBuilder {
        private final EncryptedGroup encryptedGroup;

        public EncryptedGroupBuilder() {
            this.encryptedGroup = new EncryptedGroup();
        }

        public EncryptedGroupBuilder(int id, String name) {
            this();
            encryptedGroup.id = id;
            encryptedGroup.name = name;
        }

        public void setId(int id) {
            encryptedGroup.id = id;
        }

        public void setName(String name) {
            encryptedGroup.name = name;
        }

        public void addEncryptedChatId(int encryptedChatId) {
            encryptedGroup.encryptedChatsIds.add(encryptedChatId);
        }

        public EncryptedGroup create() {
            return encryptedGroup;
        }
    }
}
