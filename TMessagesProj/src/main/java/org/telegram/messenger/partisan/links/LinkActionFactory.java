package org.telegram.messenger.partisan.links;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

class LinkActionFactory {
    private static final Map<String, Class<? extends PartisanLinkHandler>> linkActionClasses = createLinkActionClassesMap();

    static PartisanLinkHandler createLinkAction(String actionName) {
        Class<? extends PartisanLinkHandler> linkActionClass = linkActionClasses.get(actionName);
        if (linkActionClass == null) {
            return new UnknownActionLinkHandler();
        }
        try {
            Constructor<? extends PartisanLinkHandler> constructor = linkActionClass.getDeclaredConstructor();
            return constructor.newInstance();
        } catch (ReflectiveOperationException ignore) {
            return new UnknownActionLinkHandler();
        }
    }

    private static Map<String, Class<? extends PartisanLinkHandler>> createLinkActionClassesMap() {
        Map<String, Class<? extends PartisanLinkHandler>> linkActionClasses = new HashMap<>();
        linkActionClasses.put("auto-delete-messages", FindMessagesLinkHandler.class);
        return linkActionClasses;
    }
}
