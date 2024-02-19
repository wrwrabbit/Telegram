package org.telegram.messenger.partisan.findmessages;

import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.tgnet.NativeByteBuffer;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.stream.IntStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class DocumentDecryptor {
    private final NativeByteBuffer fileBuffer;
    private final String fileCaption; // Contains decryption key part
    private byte[] initializationVector;
    private byte[] encryptedPayload;

    private DocumentDecryptor(NativeByteBuffer fileBuffer, String fileCaption) {
        this.fileBuffer = fileBuffer;
        this.fileCaption = fileCaption;
    }

    static byte[] decrypt(NativeByteBuffer fileBuffer, String fileCaption) {
        return new DocumentDecryptor(fileBuffer, fileCaption).decryptInternal();
    }

    private byte[] decryptInternal() {
        try {
            readFileContentParts();
            Cipher cipher = createCipher();
            byte[] decryptedPayload = decryptPayload(cipher);
            PartisanLog.d("[FindMessages] document contains next payload: "
                    + new String(decryptedPayload, StandardCharsets.UTF_8));
            return decryptedPayload;
        } catch (GeneralSecurityException e) {
            throw new FileLoadingException(e);
        }
    }

    private void readFileContentParts() {
        initializationVector = fileBuffer.readData(16, false);
        encryptedPayload = fileBuffer.readData(fileBuffer.limit() - 16, false);
    }

    private Cipher createCipher() throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        cipher.init(Cipher.DECRYPT_MODE, createKeySpec(), createIvSpec());
        return cipher;
    }

    private SecretKeySpec createKeySpec() {
        byte[] keySourceBytes = concatByteArrays(getUserIdBytes(), getCaptionBytes());
        byte[] keyBytes = Utilities.computeSHA256(keySourceBytes, 0, keySourceBytes.length);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] getUserIdBytes() {
        long userId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        return String.valueOf(userId).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] getCaptionBytes() {
        return fileCaption.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concatByteArrays(byte[] first, byte[] second) {
        final byte[] combined = new byte[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private IvParameterSpec createIvSpec() {
        return new IvParameterSpec(initializationVector);
    }

    private byte[] decryptPayload(Cipher cipher) throws GeneralSecurityException {
        byte[] decryptedPayload = cipher.doFinal(encryptedPayload);
        int zeroIndex = IntStream.range(0, decryptedPayload.length)
                .filter(i -> decryptedPayload[i] == 0)
                .findFirst()
                .orElse(decryptedPayload.length);
        return Arrays.copyOf(decryptedPayload, zeroIndex);
    }
}
