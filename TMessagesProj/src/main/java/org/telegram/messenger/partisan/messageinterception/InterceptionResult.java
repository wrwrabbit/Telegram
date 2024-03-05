package org.telegram.messenger.partisan.messageinterception;

public class InterceptionResult {
    private boolean preventMessageSaving;

    public InterceptionResult(boolean preventMessageSaving) {
        this.preventMessageSaving = preventMessageSaving;
    }

    public boolean isPreventMessageSaving() {
        return preventMessageSaving;
    }

    public boolean isAllowMessageSaving() {
        return !preventMessageSaving;
    }

    public InterceptionResult merge(InterceptionResult otherResult) {
        boolean preventMessageSaving = this.preventMessageSaving || otherResult.preventMessageSaving;
        return new InterceptionResult(preventMessageSaving);
    }
}
