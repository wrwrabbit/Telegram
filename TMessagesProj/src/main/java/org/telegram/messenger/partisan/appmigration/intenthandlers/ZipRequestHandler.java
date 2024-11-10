package org.telegram.messenger.partisan.appmigration.intenthandlers;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;

import org.telegram.messenger.partisan.appmigration.AppMigrator;
import org.telegram.messenger.partisan.appmigration.AppMigratorPreferences;
import org.telegram.messenger.partisan.appmigration.MigrationZipBuilder;
import org.telegram.messenger.partisan.appmigration.PackageUtils;

public class ZipRequestHandler extends AbstractIntentHandler {
    @Override
    public boolean needHandleIntent(Intent intent, Activity activity) {
        return intent.getBooleanExtra("requestZip", false);
    }

    @Override
    public void handleIntent(Intent intent, Activity activity) {
        String maskedPtgSignatureThumbprint = AppMigratorPreferences.getInstalledMaskedPtgPackageSignature();
        PackageInfo callingPackageInfo = PackageUtils.getPackageInfoWithCertificates(activity, activity.getCallingPackage());
        String callingPackageSignatureThumbprint = PackageUtils.getPackageSignatureThumbprint(callingPackageInfo);
        if (maskedPtgSignatureThumbprint != null && maskedPtgSignatureThumbprint.equals(callingPackageSignatureThumbprint)) {
            MigrationZipBuilder.makeZip(activity, new MigrationZipBuilder.MakeZipDelegate() {
                @Override
                public void makeZipCompleted() {
                    Intent data = AppMigrator.createIntentWithMigrationInfo(activity);
                    activity.setResult(Activity.RESULT_OK, data);
                    finishActivity(activity);
                }

                @Override
                public void makeZipFailed() {
                    finishActivity(activity);
                }
            });
        } else {
            finishActivity(activity);
        }
    }

    private void finishActivity(Activity activity) {
        closeLastFragment(activity);
        activity.finish();
    }
}
