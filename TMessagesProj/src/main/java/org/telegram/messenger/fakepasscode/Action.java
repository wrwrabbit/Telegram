package org.telegram.messenger.fakepasscode;

public interface Action {
    default void setExecutionScheduled() {}
    void execute(FakePasscode fakePasscode);
    default void migrate() {}
}
