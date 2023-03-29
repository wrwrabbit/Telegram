package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.Utilities;

public class InnerFakePasscodeTimer implements Runnable {
    private static InnerFakePasscodeTimer instance;

    public static synchronized void schedule() {
        if (instance == null) {
            instance = new InnerFakePasscodeTimer();
            Utilities.globalQueue.postRunnable(instance, 60 * 1000);
        }
    }

    @Override
    public void run() {
        FakePasscodeUtils.tryActivateByTimer();
        Utilities.globalQueue.postRunnable(this, 1000);
    }
}
