package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.Utilities;

import java.io.File;

public class ClearCacheAction implements Action {
    public boolean enabled = false;

    @Override
    public void execute() {
        Utilities.globalQueue.postRunnable(() -> {
            boolean imagesCleared = false;
            for (int a = 0; a < 7; a++) {
                int type = -1;
                int documentsMusicType = 0;
                if (a == 0) {
                    type = FileLoader.MEDIA_DIR_IMAGE;
                } else if (a == 1) {
                    type = FileLoader.MEDIA_DIR_VIDEO;
                } else if (a == 2) {
                    type = FileLoader.MEDIA_DIR_DOCUMENT;
                    documentsMusicType = 1;
                } else if (a == 3) {
                    type = FileLoader.MEDIA_DIR_DOCUMENT;
                    documentsMusicType = 2;
                } else if (a == 4) {
                    type = FileLoader.MEDIA_DIR_AUDIO;
                } else if (a == 5) {
                    type = 100;
                } else if (a == 6) {
                    type = FileLoader.MEDIA_DIR_CACHE;
                }
                if (type == -1) {
                    continue;
                }
                File file;
                if (type == 100) {
                    file = new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache");
                } else {
                    file = FileLoader.checkDirectory(type);
                }
                if (file != null) {
                    Utilities.clearDir(file.getAbsolutePath(), documentsMusicType, Long.MAX_VALUE, false);
                }

                if (type == FileLoader.MEDIA_DIR_CACHE) {
                    imagesCleared = true;
                } else if (type == FileLoader.MEDIA_DIR_IMAGE) {
                    imagesCleared = true;
                } else if (type == 100) {
                    imagesCleared = true;
                }
            }
            final boolean imagesClearedFinal = imagesCleared;

            AndroidUtilities.runOnUIThread(() -> {
                if (imagesClearedFinal) {
                    ImageLoader.getInstance().clearMemory();
                }
            });
        });
    }
}
