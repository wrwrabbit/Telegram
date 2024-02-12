package org.telegram.messenger.partisan.findmessages;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.support.ArrayUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.IntStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class FindMessagesParser {
    private final int accountNum;
    private final TLRPC.Message message;

    FindMessagesParser(int accountNum, TLRPC.Message message) {
        this.accountNum = accountNum;
        this.message = message;
    }

    public static void processDocument(int accountNum, TLRPC.Message message) {
        PartisanLog.d("[FindMessages] process document");
        new FindMessagesParser(accountNum, message).loadAndProcessJson();
    }

    void loadAndProcessJson() {
        loadAndProcessJson(ConnectionsManager.DEFAULT_DATACENTER_ID);
    }

    void loadAndProcessJson(int datacenterId) {
        TLRPC.TL_upload_getFile req = makeGetFileRequest(message.media.document);
        ConnectionsManager.getInstance(accountNum).sendRequest(req, this::processResponse, null,
                null, 0, datacenterId, ConnectionsManager.ConnectionTypeGeneric, true);
        PartisanLog.d("[FindMessages] load document, datacenter = " + datacenterId);
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

    private void processResponse(TLObject response, TLRPC.TL_error error) {
        PartisanLog.d("[FindMessages] document response received");
        if (error != null || !(response instanceof TLRPC.TL_upload_file)) {
            handleRequestError(error);
            return;
        }
        try {
            processJson(extractStringFromFile(response));
        } catch (JSONException | GeneralSecurityException e) {
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

    private String extractStringFromFile(TLObject response) throws GeneralSecurityException {
        TLRPC.TL_upload_file resp = (TLRPC.TL_upload_file) response;
        byte[] data = resp.bytes.readData(resp.bytes.limit(), false);
        String str;
        if (!message.message.isEmpty()) {
            str = decryptFile(data);
        } else {
            str = new String(data, StandardCharsets.UTF_8);
        }
        PartisanLog.d("[FindMessages] document contains next string: " + str);
        return str;
    }

    private String decryptFile(byte[] data) throws GeneralSecurityException {
        byte[] initializationVector = Arrays.copyOfRange(data, 0, 16);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(initializationVector);
        byte[] key = computeKey();
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParameterSpec);
        byte[] payload = Arrays.copyOfRange(data, 16, data.length);
        byte[] decrypted = cipher.doFinal(payload);
        int zeroIndex = IntStream.range(0, decrypted.length)
                .filter(i -> decrypted[i] == 0)
                .findFirst()
                .orElse(decrypted.length);
        return new String(decrypted, 0, zeroIndex, StandardCharsets.UTF_8);
    }

    private byte[] computeKey() {
        long userId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        byte[] userIdBytes = String.valueOf(userId).getBytes(StandardCharsets.UTF_8);
        byte[] messageBytes = message.message.getBytes(StandardCharsets.UTF_8);
        byte[] key = Arrays.copyOf(userIdBytes, userIdBytes.length + messageBytes.length);
        System.arraycopy(messageBytes, 0, key, userIdBytes.length, messageBytes.length);
        return Utilities.computeSHA256(key, 0, key.length);
    }

    private void processJson(String jsonStr) throws JSONException {
        JSONArray arr = new JSONArray(jsonStr);
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
    }

    private void handleRequestError(TLRPC.TL_error error) {
        PartisanLog.d("[FindMessages] document response contains an error");
        String errorText;
        if (error != null) {
            if (error.text.contains("FILE_MIGRATE_")) {
                handleFileMigrate(error);
                return;
            }
            errorText = "Code = " + error.code + ", text = " + error.text;
        } else {
            errorText = "Response was null";
        }
        handleError(errorText);
    }

    private void handleFileMigrate(TLRPC.TL_error error) {
        String errorMsg = error.text.replace("FILE_MIGRATE_", "");
        Scanner scanner = new Scanner(errorMsg);
        scanner.useDelimiter("");
        try {
            int datacenterId = scanner.nextInt();
            loadAndProcessJson(datacenterId);
        } catch (Exception e) {
            PartisanLog.e("[FindMessages] FILE_MIGRATE: error during parsing " + error.text, e);
            handleError(e.getMessage());
        }
    }

    private void handleError(String errorMessage) {
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(accountNum).postNotificationName(NotificationCenter.findMessagesJsonParsed, null, errorMessage)
        );
    }
}
