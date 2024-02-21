package org.telegram.messenger.partisan.links;

import java.util.Map;

interface PartisanLinkHandler {
    void handle(Map<String, String> parameters);
}
