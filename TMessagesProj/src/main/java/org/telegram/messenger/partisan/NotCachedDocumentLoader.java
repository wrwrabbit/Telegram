package org.telegram.messenger.partisan;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.Scanner;

public class NotCachedDocumentLoader {
    public interface DocumentLoaderDelegate {
        void onDocumentLoaded(byte[] content);
        void onDocumentLoadingError();
    }

    private static class DocumentLoadingException extends RuntimeException  {
        public boolean dataCenterException = false;
        public Integer targetDatacenterId = null;

        private DocumentLoadingException() {
        }

        public DocumentLoadingException(Exception cause) {
            super(cause);
        }

        public DocumentLoadingException(String message) {
            super(message);
        }
    }

    private final int accountNum;
    private final TLRPC.Document document;
    private final DocumentLoaderDelegate delegate;
    private int datacenterId = ConnectionsManager.DEFAULT_DATACENTER_ID;
    private byte[] documentPayload = new byte[0];

    private NotCachedDocumentLoader(int accountNum, TLRPC.Document document, DocumentLoaderDelegate delegate) {
        this.accountNum = accountNum;
        this.document = document;
        this.delegate = delegate;
    }

    public static void loadDocument(int accountNum, TLRPC.Document document, DocumentLoaderDelegate delegate) {
        PartisanLog.d("[FindMessages] process document");
        new NotCachedDocumentLoader(accountNum, document, delegate).loadDocumentInternal();
    }

    private void loadDocumentInternal() {
        loadNextChunk();
    }

    private void loadNextChunk() {
        TLRPC.TL_upload_getFile req = makeGetFileRequest(document);
        ConnectionsManager.getInstance(accountNum).sendRequest(req, this::processResponse, null,
                null, 0, datacenterId, ConnectionsManager.ConnectionTypeGeneric, true);
        PartisanLog.d("[FindMessages] load document, datacenter = " + datacenterId);
    }

    private TLRPC.TL_upload_getFile makeGetFileRequest(TLRPC.Document document) {
        TLRPC.TL_upload_getFile req = new TLRPC.TL_upload_getFile();
        req.location = makeFileLocation(document);
        req.offset = documentPayload.length;
        req.limit = 1024 * 32;
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
            updateDocumentPayload(response);
            if (isLoadingFinished()) {
                notifyMessagesParsed();
            } else {
                loadNextChunk();
            }
        } catch (DocumentLoadingException e) {
            handleException(e);
        }
    }

    private void validateResponse(TLObject response, TLRPC.TL_error error) {
        if (error == null && response instanceof TLRPC.TL_upload_file) {
            return;
        }
        PartisanLog.d("[FindMessages] document response contains an error");
        throw makeTlrpcException(error);
    }

    private static DocumentLoadingException makeTlrpcException(TLRPC.TL_error error) {
        if (error == null) { // error is null and response is null
            return new DocumentLoadingException("Response was null");
        } else if (error.text.contains("FILE_MIGRATE_")) {
            DocumentLoadingException exception = new DocumentLoadingException();
            exception.dataCenterException = true;
            exception.targetDatacenterId = parseDatacenterId(error.text);
            return exception;
        } else {
            return new DocumentLoadingException("Code = " + error.code + ", text = " + error.text);
        }
    }

    private static int parseDatacenterId(String errorText) {
        String errorMsg = errorText.replace("FILE_MIGRATE_", "");
        Scanner scanner = new Scanner(errorMsg);
        scanner.useDelimiter("");
        try {
            return scanner.nextInt();
        } catch (Exception e) {
            PartisanLog.e("[FindMessages] FILE_MIGRATE: error during parsing " + errorText, e);
            throw new DocumentLoadingException(e);
        }
    }

    private void updateDocumentPayload(TLObject response) {
        TLRPC.TL_upload_file resp = (TLRPC.TL_upload_file) response;
        byte[] newPayloadChunk = resp.bytes.readData(resp.bytes.limit(), false);
        documentPayload = Utils.concatByteArrays(documentPayload, newPayloadChunk);
    }

    private boolean isLoadingFinished() {
        return documentPayload.length == document.size;
    }

    private void notifyMessagesParsed() {
        delegate.onDocumentLoaded(documentPayload);
    }

    private void handleException(DocumentLoadingException exception) {
        if (exception.dataCenterException) { // Exception means that we should retry request with a different datacenterId
            handleDataCenterException(exception);
            return;
        }
        Throwable actualThrowable = exception.getCause() != null
                ? (Exception) exception.getCause()
                : exception;
        PartisanLog.e("[FindMessages] handleError: thrown exception", actualThrowable);
        delegate.onDocumentLoadingError();
    }

    private void handleDataCenterException(DocumentLoadingException exception) {
        datacenterId = exception.targetDatacenterId;
        loadDocumentInternal();
    }
}
