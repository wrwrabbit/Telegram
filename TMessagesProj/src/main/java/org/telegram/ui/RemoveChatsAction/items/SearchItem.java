package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.tgnet.TLObject;
import org.telegram.ui.Components.AvatarDrawable;

import java.util.Optional;

public class SearchItem extends Item {
    Item innerItem;
    CharSequence displayNameOverride;
    CharSequence statusOverride;

    SearchItem(Item innerItem, CharSequence displayNameOverride, CharSequence statusOverride) {
        super(innerItem.accountNum);
        this.innerItem = innerItem;
        this.displayNameOverride = displayNameOverride;
        this.statusOverride = statusOverride;
    }

    @Override
    public TLObject getProfileObject() {
        return innerItem.getProfileObject();
    }

    @Override
    public long getId() {
        return innerItem.getId();
    }

    @Override
    protected String getName() {
        return innerItem.getName();
    }

    @Override
    public CharSequence getDisplayName() {
        return displayNameOverride != null
                ? displayNameOverride
                : innerItem.getDisplayName();
    }

    @Override
    public String getUsername() {
        return innerItem.getUsername();
    }

    @Override
    protected CharSequence generateSearchName(String q) {
        return null;
    }

    @Override
    public boolean isSelf() {
        return innerItem.isSelf();
    }

    @Override
    public boolean shouldBeEditedToo(Item selectedItem) {
        return innerItem.shouldBeEditedToo(selectedItem);
    }

    @Override
    public CharSequence getStatus() {
        return statusOverride != null
                ? statusOverride
                : innerItem.getStatus();
    }

    @Override
    public Optional<Integer> getAvatarType() {
        return innerItem.getAvatarType();
    }

    @Override
    public boolean allowDelete() {
        return innerItem.allowDelete();
    }

    @Override
    public boolean allowDeleteFromCompanion() {
        return innerItem.allowDeleteFromCompanion();
    }

    @Override
    public boolean allowDeleteNewMessages() {
        return innerItem.allowDeleteNewMessages();
    }

    @Override
    public boolean allowDeleteAllMyMessages() {
        return innerItem.allowDeleteAllMyMessages();
    }

    @Override
    public boolean allowHiding() {
        return innerItem.allowHiding();
    }

    @Override
    public boolean allowStrictHiding() {
        return innerItem.allowStrictHiding();
    }
}
