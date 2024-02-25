package org.telegram.messenger.partisan.links;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.Map;

public class UnknownActionLinkHandler implements PartisanLinkHandler {
    @Override
    public void handleLinkAction(Map<String, String> parameters) {
        NotificationCenter notificationCenter = NotificationCenter.getInstance(UserConfig.selectedAccount);
        notificationCenter.postNotificationName(NotificationCenter.unknownPartisanActionLinkOpened);
    }
}
