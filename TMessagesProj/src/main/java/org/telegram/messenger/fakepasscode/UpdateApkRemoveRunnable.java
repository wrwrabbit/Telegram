package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.AppVersion;
import org.telegram.messenger.partisan.UpdateData;

import java.io.File;

public class UpdateApkRemoveRunnable implements Runnable {
    private boolean oldUpdateExists;

    public UpdateApkRemoveRunnable(boolean oldUpdateExists) {
        this.oldUpdateExists = oldUpdateExists;
    }

    @Override
    public void run() {
        try {
            if (SharedConfig.pendingPtgAppUpdate != null && false) {
                if (AppVersion.getCurrentVersion().greaterOrEquals(SharedConfig.pendingPtgAppUpdate.version)) {
                    UpdateData pendingPtgAppUpdateFinal = SharedConfig.pendingPtgAppUpdate;
                    ImageLoader.getInstance(); // init media dirs
                    FileLoader fileLoader = FileLoader.getInstance(pendingPtgAppUpdateFinal.accountNum);
                    File path = fileLoader.getPathToAttach(pendingPtgAppUpdateFinal.document, true);
                    path.delete();
                    SharedConfig.pendingPtgAppUpdate = null;
                    AndroidUtilities.runOnUIThread(SharedConfig::saveConfig);
                }
            } else if (oldUpdateExists) {
                File dir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                File[] files = dir.listFiles(f -> f.getName().endsWith(".apk"));
                for (File f : files) {
                    f.delete();
                }
            }
        } catch (Exception ignore) {
            Utilities.cacheClearQueue.postRunnable(this, 1000);
        }
    }
}
