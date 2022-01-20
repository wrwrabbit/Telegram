package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class ClearSavedChannelsAction extends AccountAction {
    @Override
    public void execute() {
        UserConfig userConfig = getUserConfig();
        List<String> savedChannels = Arrays.asList(userConfig.defaultChannels.split(","));
        userConfig.savedChannels = new HashSet<>(savedChannels);
        userConfig.pinnedSavedChannels = new ArrayList<>(savedChannels);
    }
}
