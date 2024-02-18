package org.telegram.messenger.partisan.findmessages;

import org.telegram.tgnet.TLRPC;

class FileLoadingException extends RuntimeException  {
    private boolean dataCenterException = false;

    private FileLoadingException() {
    }

    FileLoadingException(Exception cause) {
        super(cause);
    }

    FileLoadingException(String message) {
        super(message);
    }

    static FileLoadingException makeTlrpcException(TLRPC.TL_error error) {
        if (error == null) {
            return new FileLoadingException("Response was null");
        } else if (error.text.contains("FILE_MIGRATE_")) {
            return FileLoadingException.makeDataCenterException();
        } else {
            return new FileLoadingException("Code = " + error.code + ", text = " + error.text);
        }
    }

    private static FileLoadingException makeDataCenterException() {
        FileLoadingException exception = new FileLoadingException();
        exception.dataCenterException = true;
        return exception;
    }

    boolean isDataCenterException() {
        return dataCenterException;
    }
}
