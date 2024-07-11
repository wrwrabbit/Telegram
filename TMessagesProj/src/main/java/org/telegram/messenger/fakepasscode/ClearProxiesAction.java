package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.appmigration.AppMigrator;

import java.util.ArrayList;
import java.util.List;

@FakePasscodeSerializer.EnabledSerialization
public class ClearProxiesAction implements Action {
    public boolean enabled = false;

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (!enabled) {
            return;
        }
        List<SharedConfig.ProxyInfo> proxies = new ArrayList<>(SharedConfig.proxyList);
        for (SharedConfig.ProxyInfo proxy : proxies) {
            if (!AppMigrator.isProxyForDisablingConnection(proxy)) {
                SharedConfig.deleteProxy(proxy);
            }
        }
    }
}