package org.telegram.messenger.partisan.links;

import android.net.Uri;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class LinkParser {
    private static final String TARGET_HOST = "t.me";
    private static final String TARGET_GROUP = "cpartisans_security";
    private static final String ACTION_KEY = "ptg-action";
    private final Uri uri;

    public LinkParser(Uri uri) {
        this.uri = uri;
    }

    boolean isPartisanLink() {
        return getActionName() != null;
    }

    String getActionName() {
        try {
            if (isHostValid() && isGroupValid()) {
                return uri.getQueryParameter(ACTION_KEY);
            } else {
                return null;
            }
        } catch (UnsupportedOperationException ignore) {
            return null;
        }
    }

    private boolean isHostValid() {
        String host = uri.getHost();
        return host != null && host.equals(TARGET_HOST);
    }

    private boolean isGroupValid() {
        List<String> pathSegments = uri.getPathSegments();
        return pathSegments != null
                && !pathSegments.isEmpty()
                && pathSegments.get(0).equals(TARGET_GROUP);
    }

    Map<String, String> getActionParams() {
        try {
            Set<String> parameterNames = uri.getQueryParameterNames();
            return parameterNames.stream().collect(Collectors.toMap(n -> n, uri::getQueryParameter));
        } catch (Exception ignore) {
            return Collections.emptyMap();
        }
    }
}
