package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

@FakePasscodeSerializer.ToggleSerialization
public class DeleteContactsAction extends AccountAction {
    public DeleteContactsAction() {}

    public DeleteContactsAction(int accountNum) {
        this.accountNum = accountNum;
    }

    @Override
    public void execute(FakePasscode fakePasscode) {
        ContactsController contactsController = ContactsController.getInstance(accountNum);
        MessagesController messagesController = MessagesController.getInstance(accountNum);
        ArrayList<TLRPC.TL_contact> contacts = new ArrayList<>(contactsController.contacts);
        for (TLRPC.TL_contact contact : contacts) {
            ArrayList<TLRPC.User> users = new ArrayList<>();
            users.add(messagesController.getUser(contact.user_id));
            contactsController.deleteContact(users, false);
        }
    }
}
