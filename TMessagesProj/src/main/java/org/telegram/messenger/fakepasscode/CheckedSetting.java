package org.telegram.messenger.fakepasscode;

import java.util.ArrayList;
import java.util.List;

public class CheckedSetting<Type> {
    private boolean activated = false;
    private int mode = SelectionMode.SELECTED;
    private List<Type> selected = new ArrayList<>();

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public List<Type> getSelected() {
        return selected;
    }

    public void setSelected(List<Type> selected) {
        this.selected = selected;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public boolean isActivated() {
        return activated;
    }
}
