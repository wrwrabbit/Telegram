package org.telegram.messenger.partisan.appmigration;

public enum Step {
    MAKE_ZIP,
    MAKE_ZIP_FAILED,
    MAKE_ZIP_LOCKED,
    MAKE_ZIP_COMPLETED,

    UNINSTALL_SELF;

    Step simplify() {
        switch (this) {
            case MAKE_ZIP:
            case MAKE_ZIP_FAILED:
            case MAKE_ZIP_LOCKED:
            case MAKE_ZIP_COMPLETED:
            default:
                return MAKE_ZIP;
            case UNINSTALL_SELF:
                return UNINSTALL_SELF;
        }
    }
}
