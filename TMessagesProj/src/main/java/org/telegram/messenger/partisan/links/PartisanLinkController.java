package org.telegram.messenger.partisan.links;

import android.net.Uri;

import org.telegram.messenger.fakepasscode.FakePasscodeUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PartisanLinkController {
    private static final PartisanLinkController instance = new PartisanLinkController();
    private final Map<String, PartisanLinkHandler> linkHandlers = new ConcurrentHashMap<>();

    public static PartisanLinkController getInstance() {
        return instance;
    }

    public void addActionHandler(String actionName, PartisanLinkHandler handler) {
        linkHandlers.put(actionName, handler);
    }

    public void removeActionHandler(String actionName) {
        linkHandlers.remove(actionName);
    }

    public static boolean tryProcessAction(Uri uri) {
        return instance.tryProcessActionInternal(uri);
    }

    private boolean tryProcessActionInternal(Uri uri) {
        LinkParser parser = new LinkParser(uri);
        if (FakePasscodeUtils.isFakePasscodeActivated() || !parser.isPartisanLink()) {
            return false;
        }
        String actionName = parser.getActionName();
        PartisanLinkHandler handler = getLinkHandler(actionName);
        handler.handleLinkAction(parser.getActionParams());
        return true;
    }

    private PartisanLinkHandler getLinkHandler(String actionName) {
        PartisanLinkHandler handler = linkHandlers.get(actionName);
        if (handler == null) {
            handler = new UnknownActionLinkHandler();
        }
        return handler;
    }
}
