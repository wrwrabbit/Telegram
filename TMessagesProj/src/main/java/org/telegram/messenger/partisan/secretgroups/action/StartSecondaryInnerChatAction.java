package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.AbstractSerializedData;

public class StartSecondaryInnerChatAction extends EncryptedGroupAction {
    public static final int constructor = 0x19fbd964;

    public long externalGroupId;

    @Override
    public void readParams(AbstractSerializedData stream, boolean exception) {
        externalGroupId = stream.readInt64(exception);
    }

    @Override
    public void serializeToStream(AbstractSerializedData stream) {
        stream.writeInt32(constructor);

        stream.writeInt64(externalGroupId);
    }
}
