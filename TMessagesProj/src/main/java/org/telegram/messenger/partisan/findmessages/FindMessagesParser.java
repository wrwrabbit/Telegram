package org.telegram.messenger.partisan.findmessages;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FindMessagesParser {
    private final int accountNum;
    private TLRPC.Message message;

    FindMessagesParser(int accountNum, TLRPC.Message message) {
        this.accountNum = accountNum;
        this.message = message;
    }

    public static void processDocument(int accountNum, TLRPC.Message message) {
        PartisanLog.d("[FindMessages] process document");
        new FindMessagesParser(accountNum, message).loadAndProcessJson();
    }

    void loadAndProcessJson() {
        TLRPC.TL_upload_getFile req = makeGetFileRequest(message.media.document);
        ConnectionsManager.getInstance(accountNum).sendRequest(req, this::parseJson);
        PartisanLog.d("[FindMessages] document");
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

    private void parseJson(TLObject response, TLRPC.TL_error error) {
        PartisanLog.d("[FindMessages] document response received");
        if (error != null || !(response instanceof TLRPC.TL_upload_file)) {
            PartisanLog.d("[FindMessages] document response contains error");
            String errorText;
            if (error != null) {
                errorText = "Code = " + error.code + ", text = " + error.text;
            } else {
                errorText = "Response was null";
            }
            handleError(errorText);
            return;
        }
        TLRPC.TL_upload_file resp = (TLRPC.TL_upload_file) response;
        String str = new String(resp.bytes.readData(resp.bytes.limit(), false), StandardCharsets.UTF_8);
        PartisanLog.d("[FindMessages] document contains next string: " + str);
        try {
            JSONArray arr = new JSONArray(str);
            Map<Long, FindMessagesItem> messagesToDelete = new HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                FindMessagesItem item = new FindMessagesItem();
                item.chatId = -obj.getLong("chat_id");
                item.username = obj.getString("chat_username");
                JSONArray messageIdsArray = obj.getJSONArray("message_ids");
                for (int j = 0; j < messageIdsArray.length(); j++) {
                    item.messageIds.add(messageIdsArray.getInt(j));
                }
                messagesToDelete.put(item.chatId, item);
                PartisanLog.d("[FindMessages] added item for chatId = " + item.chatId + ". Message count = " + item.messageIds.size());
            }
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getInstance(accountNum).postNotificationName(NotificationCenter.findMessagesJsonParsed, messagesToDelete, null)
            );
        } catch (JSONException e) {
            PartisanLog.e("[FindMessages] document: error during parsing", e);
            handleError(e.getMessage());
        } finally {
            AndroidUtilities.runOnUIThread(() -> {
                ArrayList<Integer> ids = new ArrayList<>();
                ids.add(message.id);
                MessagesController.getInstance(accountNum).deleteMessages(ids, null, null, message.dialog_id, true, false);
            });
        }
    }

    private void handleError(String errorMessage) {
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(accountNum).postNotificationName(NotificationCenter.findMessagesJsonParsed, null, errorMessage)
        );
    }
}
