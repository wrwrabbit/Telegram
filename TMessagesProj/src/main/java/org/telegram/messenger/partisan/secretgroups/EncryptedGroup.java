package org.telegram.messenger.partisan.secretgroups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EncryptedGroup {
    private final int id;
    private final List<Integer> encryptedChatsIds;
    private final String name;

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
}
