package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroup;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.Components.AvatarDrawable;

import java.util.Optional;

public class EncryptedGroupItem extends Item {
    private final EncryptedGroup encryptedGroup;

    EncryptedGroupItem(int accountNum, EncryptedGroup encryptedGroup) {
        super(accountNum);
        this.encryptedGroup = encryptedGroup;
    }

    @Override
    public TLObject getProfileObject() {
        return null;
    }

    @Override
    public long getId() {
        return DialogObject.makeEncryptedDialogId(encryptedGroup.getInternalId());
    }

    @Override
    protected String getName() {
        return encryptedGroup.getName();
    }

    @Override
    public String getUsername() {
        return null;
    }

    @Override
    public CharSequence generateSearchName(String query) {
        return AndroidUtilities.generateSearchName(encryptedGroup.getName(), null, query);
    }

    public boolean allowDeleteFromCompanion() {
        return true;
    }

    public boolean allowHiding() {
        return false;
    }

    @Override
    public Optional<Integer> getAvatarType() {
        return Optional.of(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);
    }
}
