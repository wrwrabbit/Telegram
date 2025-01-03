package org.telegram.messenger.partisan.update;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class UpdateData {
    public AppVersion version;
    public AppVersion originalVersion;
    public int formatVersion = 0;
    public boolean canNotSkip;
    public String text;
    public TLRPC.Message message;
    public TLRPC.Document document;
    public TLRPC.Document sticker;
    public ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
    public String url;
    public int accountNum;

    public String botRequestTag;

    @JsonIgnore
    public String stickerPackName;
    @JsonIgnore
    public String stickerEmoji;

    public boolean isMaskedUpdateDocument() {
        return botRequestTag != null
                && document != null
                && document.file_name_fixed != null
                && document.file_name_fixed.contains(botRequestTag);
    }
}
