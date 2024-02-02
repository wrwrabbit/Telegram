package org.telegram.messenger.fakepasscode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FindMessagesHelper {
    private int accountNum;
    private TLRPC.Message message;
    private Runnable onSuccess;
    private Runnable onError;
    Set<Long> chatIdsToParse = ConcurrentHashMap.newKeySet();
    private boolean wasError = false;

    private FindMessagesHelper(int accountNum, TLRPC.Message message, Runnable onSuccess, Runnable onError) {
        this.accountNum = accountNum;
        this.message = message;
        this.onSuccess = onSuccess;
        this.onError = onError;
    }

    private void loadAndProcessJson() {
        TLRPC.TL_upload_getFile req = makeGetFileRequest(message.media.document);
        ConnectionsManager.getInstance(accountNum).sendRequest(req, this::processJson);
    }

    private TLRPC.TL_upload_getFile makeGetFileRequest(TLRPC.Document document) {
        TLRPC.TL_upload_getFile req = new TLRPC.TL_upload_getFile();
        req.location = makeFileLocation(document);
        req.offset = 0;
        req.limit = 1024 * 512;
        req.cdn_supported = false;
        return req;
    }

    private TLRPC.InputFileLocation makeFileLocation(TLRPC.Document document) {
        TLRPC.InputFileLocation location = new TLRPC.TL_inputDocumentFileLocation();
        location.id = document.id;
        location.access_hash = document.access_hash;
        location.file_reference = document.file_reference;
        location.thumb_size = "";
        if (location.file_reference == null) {
            location.file_reference = new byte[0];
        }
        return location;
    }

    private void processJson(TLObject response, TLRPC.TL_error error) {
        if (error != null || !(response instanceof TLRPC.TL_upload_file)) {
            onError.run();
        }
        TLRPC.TL_upload_file resp = (TLRPC.TL_upload_file) response;
        String str = new String(resp.bytes.readData(resp.bytes.limit(), false), StandardCharsets.UTF_8);
        try {
            JSONArray arr = new JSONArray(str);
            for (int i = 0; i < arr.length(); i++) {
                chatIdsToParse.add(-arr.getJSONObject(i).getLong("chat_id"));
            }
            for (int i = 0; i < arr.length(); i++) {
                processJsonChat(arr.getJSONObject(i));
            }
        } catch (JSONException e) {
            Utils.handleException(e);
            onError.run();
        }
    }

    private void processJsonChat(JSONObject obj) throws JSONException {
        long chatId = -obj.getLong("chat_id");
        String username = obj.getString("chat_username");
        JSONArray messageIdsArray = obj.getJSONArray("message_ids");
        ArrayList<Integer> messageIds = new ArrayList<>();
        for (int j = 0; j < messageIdsArray.length(); j++) {
            messageIds.add(messageIdsArray.getInt(j));
        }
        if (MessagesController.getInstance(accountNum).getDialog(chatId) != null) {
            deleteMessages(chatId, messageIds);
        } else {
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = username;
            ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> {
                if (response != null) {
                    deleteMessages(chatId, messageIds);
                } else {
                    wasError = true;
                    chatProcessed(chatId);
                }
            });
        }
    }

    private void deleteMessages(long chatId, ArrayList<Integer> messageIds) {
        AndroidUtilities.runOnUIThread(() -> {
            MessagesController.getInstance(accountNum).deleteMessages(messageIds, null, null, chatId, true, false);
            chatProcessed(chatId);
        });
    }

    private void chatProcessed(long chatId) {
        chatIdsToParse.remove(chatId);
        if (chatIdsToParse.isEmpty()) {
            if (!wasError) {
                onSuccess.run();
            } else {
                onError.run();
            }
        }
    }

    public static void deletionAccepted(int accountNum, TLRPC.Message message, Runnable onSuccess, Runnable onError) {
        new FindMessagesHelper(accountNum, message, onSuccess, onError).loadAndProcessJson();
    }

    public static boolean checkIsUserMessagesJson(int accountNum, TLRPC.Message message) {
        if (message.from_id.user_id == 6092224989L && message.media != null
                && message.media.document != null
                && message.media.document.file_name_fixed.equals("file")) {
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(accountNum).postNotificationName(NotificationCenter.findMessagesJsonReceived, message);
            });
            return true;
        } else {
            return false;
        }
    }
}
