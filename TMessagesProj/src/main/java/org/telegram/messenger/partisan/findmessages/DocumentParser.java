package org.telegram.messenger.partisan.findmessages;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.partisan.PartisanLog;

import java.nio.charset.StandardCharsets;

class DocumentParser {
    private final int accountNum;
    private final byte[] filePayload;

    private DocumentParser(int accountNum, byte[] filePayload) {
        this.accountNum = accountNum;
        this.filePayload = filePayload;
    }

    static MessagesToDelete parse(int accountNum, byte[] filePayload) {
        try {
            DocumentParser parser = new DocumentParser(accountNum, filePayload);
            return parser.parseJson();
        } catch (JSONException e) {
            throw new FileLoadingException(e);
        }
    }

    private MessagesToDelete parseJson() throws JSONException {
        String jsonStr = new String(filePayload, StandardCharsets.UTF_8);
        JSONArray arr = new JSONArray(jsonStr);
        MessagesToDelete messagesToDelete = new MessagesToDelete(accountNum);
        for (int i = 0; i < arr.length(); i++) {
            FindMessagesChatData chatData = parseItem(arr.getJSONObject(i));
            messagesToDelete.add(chatData);
        }
        return messagesToDelete;
    }

    private static FindMessagesChatData parseItem(JSONObject obj) throws JSONException {
        FindMessagesChatData chatData = new FindMessagesChatData();
        chatData.chatId = -obj.getLong("chat_id");
        chatData.username = obj.getString("chat_username");
        JSONArray messageIdsArray = obj.getJSONArray("message_ids");
        for (int j = 0; j < messageIdsArray.length(); j++) {
            chatData.messageIds.add(messageIdsArray.getInt(j));
        }
        PartisanLog.d("[FindMessages] parsed chatData for chatId = " + chatData.chatId + "." +
                " Message count = " + chatData.messageIds.size());
        return chatData;
    }
}
