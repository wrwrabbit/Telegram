package org.telegram.messenger.partisan;

import java.util.Arrays;

public enum SecurityIssue {
    ROOT,
    USB_DEBUGGING,
    TWO_STEP_VERIFICATION,
    PRIVACY;

    public boolean isGlobal() {
        return Arrays.asList(ROOT, USB_DEBUGGING).contains(this);
    }
}
