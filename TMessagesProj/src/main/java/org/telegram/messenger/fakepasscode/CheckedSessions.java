package org.telegram.messenger.fakepasscode;

import java.util.ArrayList;
import java.util.List;

public class CheckedSessions {
    private int mode = SelectionMode.SELECTED;
    private List<Long> sessions = new ArrayList<>();

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public List<Long> getSessions() {
        return sessions;
    }

    void setSessions(List<Long> sessions) {
        this.sessions = sessions;
    }
}
