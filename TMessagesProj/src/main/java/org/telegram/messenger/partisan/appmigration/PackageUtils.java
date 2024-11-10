package org.telegram.messenger.partisan.appmigration;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PackageUtils {
    public static Signature[] getSignatures(PackageInfo packageInfo) {
        if (packageInfo == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.signingInfo.getApkContentsSigners();
        } else {
            return packageInfo.signatures;
        }
    }

    public static String getSignatureThumbprint(Signature signature) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-1");
            return Utilities.bytesToHex(hash.digest(signature.toByteArray()));
        } catch (NoSuchAlgorithmException ignored) {
            return null;
        }
    }

    public static String getPackageSignatureThumbprint(PackageInfo packageInfo) {
        Signature[] signatures = PackageUtils.getSignatures(packageInfo);
        if (signatures.length != 1) {
            return null;
        }
        return PackageUtils.getSignatureThumbprint(signatures[0]);
    }

    public static boolean isPackageSignatureThumbprint(PackageInfo packageInfo, String targetThumbprint) {
        Signature[] signatures = PackageUtils.getSignatures(packageInfo);
        if (signatures == null) {
            return false;
        }
        for (final Signature signature : signatures) {
            String thumbprint = PackageUtils.getSignatureThumbprint(signature);
            if (thumbprint != null && thumbprint.equalsIgnoreCase(targetThumbprint)) {
                return true;
            }
        }
        return false;
    }

    public static PackageInfo extractPackageInfoFromFile(File f) {
        PackageManager pm = ApplicationLoader.applicationContext.getPackageManager();
        return pm.getPackageArchiveInfo(f.getPath(), PackageManager.GET_SIGNATURES);
    }

    public static PackageInfo getPackageInfoWithCertificates(Context context, String packageName) {
        int flags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            flags = PackageManager.GET_SIGNING_CERTIFICATES;
        } else {
            flags = PackageManager.GET_SIGNATURES;
        }
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }
}
