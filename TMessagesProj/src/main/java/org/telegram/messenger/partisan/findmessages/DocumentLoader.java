package org.telegram.messenger.partisan.findmessages;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

class DocumentLoader {
    private final int accountNum;
    private final TLRPC.Message message;
    private int datacenterId = ConnectionsManager.DEFAULT_DATACENTER_ID;

    private DocumentLoader(int accountNum, TLRPC.Message message) {
        this.accountNum = accountNum;
        this.message = message;
    }

    static void loadDocument(int accountNum, TLRPC.Message message) {
        PartisanLog.d("[FindMessages] process document");
        new DocumentLoader(accountNum, message).loadAndProcessJson();
    }

    void loadAndProcessJson() {
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
        try {
            PartisanLog.d("[FindMessages] document response received");
            validateResponse(response, error);
            MessagesToDelete messagesToDelete = parseResponse(response);
            notifyMessagesParsed(messagesToDelete);
        } catch (FileLoadingException e) {
            handleException(e);
        } finally {
            deleteFileMessage(); // Message with the document must be deleted after parsing
        }
    }

    private void validateResponse(TLObject response, TLRPC.TL_error error) {
        if (error == null && response instanceof TLRPC.TL_upload_file) {
            return;
        }
        PartisanLog.d("[FindMessages] document response contains an error");
        if (error != null && error.text.contains("FILE_MIGRATE_")) {
            handleFileMigrate(error);
        }
        throw FileLoadingException.makeTlrpcException(error);
    }

    private void handleFileMigrate(TLRPC.TL_error error) {
        parseDatacenterId(error.text);
        loadAndProcessJson();
    }

    private void parseDatacenterId(String errorText) {
        String errorMsg = errorText.replace("FILE_MIGRATE_", "");
        Scanner scanner = new Scanner(errorMsg);
        scanner.useDelimiter("");
        try {
            datacenterId = scanner.nextInt();
        } catch (Exception e) {
            PartisanLog.e("[FindMessages] FILE_MIGRATE: error during parsing " + errorText, e);
            throw new FileLoadingException(e);
        }
    }

    private MessagesToDelete parseResponse(TLObject response) {
        TLRPC.TL_upload_file resp = (TLRPC.TL_upload_file) response;
        byte[] decryptedPayload = DocumentDecryptor.decrypt(resp.bytes, message.message);
        return DocumentParser.parse(accountNum, decryptedPayload);
    }

    private void notifyMessagesParsed(MessagesToDelete messagesToDelete) {
        AndroidUtilities.runOnUIThread(() -> getNotificationCenter()
                .postNotificationName(NotificationCenter.findMessagesFileLoaded, messagesToDelete));
    }

    private void handleException(FileLoadingException exception) {
        if (exception.isDataCenterException()) {
            return; // Exception means that we should retry request with a different datacenterId
        }
        Throwable actualThrowable = exception.getCause() != null
                ? (Exception) exception.getCause()
                : exception;
        PartisanLog.e("[FindMessages] handleError: thrown exception", actualThrowable);
        AndroidUtilities.runOnUIThread(() -> getNotificationCenter()
                .postNotificationName(NotificationCenter.findMessagesFileLoaded,
                        actualThrowable.getMessage()));
    }

    private NotificationCenter getNotificationCenter() {
        return NotificationCenter.getInstance(accountNum);
    }

    private void deleteFileMessage() {
        AndroidUtilities.runOnUIThread(() -> {
            ArrayList<Integer> ids = new ArrayList<>();
            ids.add(message.id);
            MessagesController.getInstance(accountNum).deleteMessages(
                    ids, null, null,
                    message.dialog_id, true, false);
        });
    }
}
