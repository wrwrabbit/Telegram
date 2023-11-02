package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TerminateOtherSessionsAction extends AccountAction {
    private int mode = 0;
    private List<Long> sessions = new ArrayList<>();

    public TerminateOtherSessionsAction() {}

    public TerminateOtherSessionsAction(int accountNum) {
        this.accountNum = accountNum;
    }

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (mode == SelectionMode.SELECTED) {
            terminateSelectedSessions(fakePasscode);
        } else if (mode == SelectionMode.EXCEPT_SELECTED) {
            terminateExceptSelectedSessions(fakePasscode);
        }
    }

    private void terminateSelectedSessions(FakePasscode fakePasscode) {
        List<Long> sessionsToTerminate = sessions;
        if (!sessionsToTerminate.isEmpty()) {
            fakePasscode.actionsResult.actionsPreventsLogoutAction.add(this);
        }
        Set<Long> terminatedSessions = Collections.synchronizedSet(new HashSet<>(sessionsToTerminate));
        for (Long session : sessionsToTerminate) {
            TLRPC.TL_account_resetAuthorization req = new TLRPC.TL_account_resetAuthorization();
            req.hash = session;
            ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> {
                terminatedSessions.remove(session);
                if (terminatedSessions.isEmpty()) {
                    fakePasscode.actionsResult.actionsPreventsLogoutAction.remove(this);
                }
            });
        }
    }

    private void terminateExceptSelectedSessions(FakePasscode fakePasscode) {
        fakePasscode.actionsResult.actionsPreventsLogoutAction.add(this);
        TLRPC.TL_account_getAuthorizations req = new TLRPC.TL_account_getAuthorizations();
        ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TLRPC.TL_account_authorizations res = (TLRPC.TL_account_authorizations) response;
                Set<TLRPC.TL_authorization> terminatedAuthorizations = Collections.synchronizedSet(new HashSet<>(res.authorizations));
                for (TLRPC.TL_authorization authorization : res.authorizations) {
                    if ((authorization.flags & 1) == 0 && !sessions.contains(authorization.hash)) {
                        TLRPC.TL_account_resetAuthorization terminateReq = new TLRPC.TL_account_resetAuthorization();
                        terminateReq.hash = authorization.hash;
                        ConnectionsManager.getInstance(accountNum).sendRequest(terminateReq, (tResponse, tError) -> {
                            terminatedAuthorizations.remove(authorization);
                            if (terminatedAuthorizations.isEmpty()) {
                                fakePasscode.actionsResult.actionsPreventsLogoutAction.remove(this);
                            }
                        });
                    }
                }
            } else {
                fakePasscode.actionsResult.actionsPreventsLogoutAction.remove(this);
            }
        }));
    }

    public List<Long> getSessions() {
        return sessions;
    }

    public void setSessions(List<Long> sessions) {
        this.sessions = sessions;
        SharedConfig.saveConfig();
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        SharedConfig.saveConfig();
    }

    @Override
    public void migrate() {
        super.migrate();
        mode = 1;
        sessions = new ArrayList<>();
    }
}
