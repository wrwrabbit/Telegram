package org.telegram.messenger.partisan.appmigration.intenthandlers;

import android.app.Activity;
import android.content.Intent;

import org.telegram.messenger.partisan.appmigration.MigrationReceiveActivity;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.LaunchActivity;

public abstract class AbstractIntentHandler {
    public abstract boolean needHandleIntent(Intent intent, Activity activity);
    public abstract void handleIntent(Intent intent, Activity activity);

    public BaseFragment getFragmentToPresent(Intent intent) {
        return new MigrationReceiveActivity();
    }

    protected void closeLastFragment(Activity activity) {
        if (!(activity instanceof LaunchActivity)) {
            throw new RuntimeException("Invalid migration activity type.");
        }
        LaunchActivity launchActivity = (LaunchActivity)activity;
        INavigationLayout actionBarLayout = launchActivity.getActionBarLayout();
        if (actionBarLayout.getLastFragment() instanceof MigrationReceiveActivity) {
            actionBarLayout.closeLastFragment(false);
        }
    }
}
