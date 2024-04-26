package org.telegram.messenger.partisan.links;

import java.util.Map;

public interface PartisanLinkHandler {
    void handleLinkAction(Map<String, String> parameters);
}
