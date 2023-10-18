package org.telegram.messenger.fakepasscode;

import static java.util.stream.Collectors.toCollection;

import org.telegram.messenger.SharedConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DeleteOtherFakePasscodesAction implements Action {
    private int mode;
    private List<UUID> selected = new ArrayList<>();

    public DeleteOtherFakePasscodesAction() {
        this(SelectionMode.SELECTED);
    }

    public DeleteOtherFakePasscodesAction(int mode) {
        this.mode = mode;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public List<UUID> getSelected() {
        return selected;
    }

    public void setSelected(List<UUID> selected) {
        this.selected = selected;
    }

    public void verifySelected() {
        selected = selected.stream()
                .filter(uuid -> SharedConfig.fakePasscodes.stream().anyMatch(p -> p.uuid.equals(uuid)))
                .collect(toCollection(ArrayList::new));
    }

    public boolean isEnabled() {
        return mode == SelectionMode.EXCEPT_SELECTED || !selected.isEmpty();
    }

    @Override
    public void execute(FakePasscode fakePasscode) {
        List<FakePasscode> newFakePasscodes = new ArrayList<>();
        int current = -1;
        for (int i = 0; i < SharedConfig.fakePasscodes.size(); i++) {
            FakePasscode fakePasscodeToDelete = SharedConfig.fakePasscodes.get(i);
            if (fakePasscode.uuid.equals(fakePasscodeToDelete.uuid)) {
                newFakePasscodes.add(fakePasscodeToDelete);
                current = newFakePasscodes.size() - 1;
            } else if (mode == SelectionMode.SELECTED) {
                if (!selected.contains(fakePasscodeToDelete.uuid)) {
                    newFakePasscodes.add(fakePasscodeToDelete);
                }
            } else {
                if (selected.contains(fakePasscodeToDelete.uuid)) {
                    newFakePasscodes.add(fakePasscodeToDelete);
                }
            }
        }
        if (mode == SelectionMode.SELECTED) {
            selected.clear();
        }
        SharedConfig.fakePasscodeActivatedIndex = current;
        SharedConfig.fakePasscodes = newFakePasscodes;
        SharedConfig.saveConfig();
    }
}
