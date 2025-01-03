package org.telegram.messenger.partisan;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

public class BlackListLoader implements NotificationCenter.NotificationCenterDelegate {
    public interface BlackListLoaderDelegate {
        void onLoaded(boolean loadingFinished);
    }

    private final int accountNum;
    private final int timeoutMilliseconds;
    private final BlackListLoaderDelegate delegate;

    private BlackListLoader(int accountNum, int timeoutMilliseconds, BlackListLoaderDelegate delegate) {
        this.accountNum = accountNum;
        this.timeoutMilliseconds = timeoutMilliseconds;
        this.delegate = delegate;
    }

    public static void load(int accountNum, int timeoutMilliseconds, BlackListLoaderDelegate delegate) {
        new BlackListLoader(accountNum, timeoutMilliseconds, delegate).loadInternal();
    }

    private void loadInternal() {
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.blockedUsersDidLoad);
        MessagesController controller = AccountInstance.getInstance(accountNum).getMessagesController();
        controller.getBlockedPeers(true);
        Utilities.globalQueue.postRunnable(this::onTimeout, timeoutMilliseconds);
    }

    @Override
    public synchronized void didReceivedNotification(int id, int account, Object... args) {
        if (account != accountNum) {
            return;
        }

        MessagesController controller = AccountInstance.getInstance(accountNum).getMessagesController();
        if (controller.blockedEndReached) {
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.blockedUsersDidLoad);
        }
        delegate.onLoaded(controller.blockedEndReached);
    }

    private synchronized void onTimeout() {
        MessagesController controller = AccountInstance.getInstance(accountNum).getMessagesController();
        if (!controller.blockedEndReached) {
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.blockedUsersDidLoad)
            );
            delegate.onLoaded(true);
        }
    }
}
