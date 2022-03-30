package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.Date;

public class ClearCacheAction implements Action {
    public boolean enabled = false;

    @Override
    public void execute() {
        if (enabled) {
            //Utils.clearCache(null);
        }
    }
}
