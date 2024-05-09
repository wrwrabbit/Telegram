package org.telegram.messenger.fakepasscode.results;

public class HideAccountResult {
    public int accountNum;
    public boolean strictHiding;

    public HideAccountResult() {}

    public HideAccountResult(int accountNum, boolean strictHiding) {
        this.accountNum = accountNum;
        this.strictHiding = strictHiding;
    }

    public boolean isHideAccount(long accountNum, boolean strictHiding) {
        if (this.accountNum != accountNum) {
            return false;
        }
        if (strictHiding && !this.strictHiding) {
            return false;
        }
        return true;
    }
}
