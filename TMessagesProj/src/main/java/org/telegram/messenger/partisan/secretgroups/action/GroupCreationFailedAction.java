package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.AbstractSerializedData;

public class GroupCreationFailedAction extends EncryptedGroupAction {
    public static final int constructor = 0xcdc18852;

    @Override
    public void readParams(AbstractSerializedData stream, boolean exception) {

    }

    @Override
    public void serializeToStream(AbstractSerializedData stream) {
        stream.writeInt32(constructor);
    }
}
