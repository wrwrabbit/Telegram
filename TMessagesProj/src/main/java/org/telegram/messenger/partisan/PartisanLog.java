package org.telegram.messenger.partisan;

import android.content.Context;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;

public class PartisanLog {
    private static Boolean logsEnabledFromSharedConfig = null;

    public static void handleException(Exception e) {
        e("error", e);
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            throw new Error(e);
        }
    }

    public static void e(final String message) {
        if (logsAllowed()) {
            FileLog.e(message);
        }
    }

    public static void e(final String message, final Throwable exception) {
        if (logsAllowed()) {
            FileLog.e(message, exception);
        }
    }

    public static void d(final String message) {
        if (logsAllowed()) {
            FileLog.d(message);
        }
    }

    private synchronized static boolean getLogsEnabledFromSharedConfig() {
        if (logsEnabledFromSharedConfig == null) {
            logsEnabledFromSharedConfig = ApplicationLoader.applicationContext
                    .getSharedPreferences("systemConfig", Context.MODE_PRIVATE)
                    .getBoolean("logsEnabled", BuildVars.DEBUG_VERSION);
        }
        return logsEnabledFromSharedConfig;
    }

    private static boolean logsAllowed() {
        return BuildVars.LOGS_ENABLED ||
                getLogsEnabledFromSharedConfig() && !FakePasscodeUtils.isFakePasscodeActivated();
    }
}
