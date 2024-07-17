package org.telegram.messenger.partisan.masked_ptg;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MaskedPtgUtils {
    public static boolean hasPermission(Context context, String permission) {
        try {
            String[] permissions = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS)
                    .requestedPermissions;
            return Arrays.asList(permissions).stream()
                    .anyMatch(p -> p != null && p.equals(permission));
        } catch (PackageManager.NameNotFoundException ignore) {
            return false;
        }
    }

    public static boolean hasAllPermissions(Context context, String[] requestedPermissions) {
        try {
            String[] manifestPermissions = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS)
                    .requestedPermissions;
            Set<String> manifestPermissionSet = new HashSet<>(Arrays.asList(manifestPermissions));
            return Arrays.asList(requestedPermissions).stream()
                    .allMatch(manifestPermissionSet::contains);
        } catch (PackageManager.NameNotFoundException ignore) {
            return false;
        }
    }
}
