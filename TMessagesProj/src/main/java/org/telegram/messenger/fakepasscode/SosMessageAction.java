package org.telegram.messenger.fakepasscode;

import android.telephony.SmsManager;

import org.telegram.messenger.SharedConfig;

public class SosMessageAction implements Action {
    public boolean enabled = false;
    public String phoneNumber = "";
    public String message = "";

    @Override
    public void execute() {
        SmsManager manager = SmsManager.getDefault();
        if (enabled && !phoneNumber.isEmpty() && !message.isEmpty()) {
            manager.sendTextMessage(phoneNumber, null, message, null, null);
        }
    }
}
