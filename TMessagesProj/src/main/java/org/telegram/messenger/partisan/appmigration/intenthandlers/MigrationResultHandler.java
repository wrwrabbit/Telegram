package org.telegram.messenger.partisan.appmigration.intenthandlers;

import android.app.Activity;
import android.content.Intent;

import org.telegram.messenger.partisan.appmigration.AppMigrationActivity;
import org.telegram.ui.ActionBar.BaseFragment;

public class MigrationResultHandler extends AbstractIntentHandler {
    @Override
    public boolean needHandleIntent(Intent intent, Activity activity) {
        return intent.hasExtra("success");
    }

    @Override
    public void handleIntent(Intent intent, Activity activity) {
        closeLastFragment(activity);
    }

    @Override
    public BaseFragment getFragmentToPresent(Intent intent) {
        AppMigrationActivity appMigrationActivity = new AppMigrationActivity();
        appMigrationActivity.setMigrationResultIntent(intent);
        return appMigrationActivity;
    }
}
