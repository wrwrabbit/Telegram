package org.telegram.messenger.partisan;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

class UpdateMessageParser {
    private Pattern VERSION_REGEX = Pattern.compile("(\\d+).(\\d+).(\\d+)");
    private String TARGET_FILE_NAME = "PTelegram.apk";
    private UpdateData currentUpdate;
    private MessageObject currentMessage;
    private boolean newLine = true;
    private boolean controlLine = true;
    private int blockStart = 0;
    private String lang = "en";
    private int langInaccuracy = 0;

    private final int currentAccount;
    private final Map<Long, List<MessageObject>> messagesByGroupId = new HashMap<>();

    public UpdateMessageParser(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public UpdateData parseMessage(MessageObject message) {
        if (message.getGroupId() != 0) {
            if (!messagesByGroupId.containsKey(message.getGroupId())) {
                messagesByGroupId.put(message.getGroupId(), new ArrayList<>());
            }
            List<MessageObject> messages = messagesByGroupId.get(message.getGroupId());
            if (messages != null) {
                messages.add(message);
            }
        }
        if (!message.isReply() || message.replyMessageObject.getDocument() == null
                || message.messageText == null) {
            return null;
        }

        currentUpdate = new UpdateData();
        currentUpdate.accountNum = currentAccount;
        MessageObject targetMessage = findTargetFileMessage(message);
        if (targetMessage == null) {
            return null;
        }
        currentUpdate.message = targetMessage.messageOwner;
        currentUpdate.document = targetMessage.getDocument();
        currentMessage = message;
        try {
            CharSequence text = message.messageText;
            newLine = true;
            controlLine = true;
            blockStart = 0;
            lang = "en";
            langInaccuracy = Integer.MAX_VALUE;
            for (int pos = 0; pos <= text.length(); pos++) {
                if (newLine && pos < text.length() && text.charAt(pos) == '#') {
                    if (blockStart < pos - 1) {
                        processDescription(text, blockStart, pos);
                    }
                    controlLine = true;
                    blockStart = pos + 1;
                }
                if (pos < text.length() && text.charAt(pos) == '\n') {
                    newLine = true;
                    if (controlLine) {
                        processControlLine(text.subSequence(blockStart, pos).toString());
                        controlLine = false;
                        blockStart = pos + 1;
                    }
                }
                if (pos == text.length()) {
                    if (controlLine) {
                        processControlLine(text.subSequence(blockStart, pos).toString());
                        controlLine = false;
                        blockStart = pos + 1;
                    } else {
                        if (blockStart < pos) {
                            processDescription(text, blockStart, pos);
                        }
                    }
                }
            }
            return currentUpdate;
        } catch (Exception ignore) {
        }
        return null;
    }

    private MessageObject findTargetFileMessage(MessageObject descriptionMessage) {
        MessageObject replyMessage = descriptionMessage.replyMessageObject;
        if (replyMessage == null) {
            return null;
        }
        if (isTargetDocument(replyMessage.getDocument())) {
            return replyMessage;
        } else if (replyMessage.getGroupId() != 0) {
            List<MessageObject> messagesFromGroup = messagesByGroupId.get(replyMessage.getGroupId());
            if (messagesFromGroup != null) {
                for (MessageObject message : messagesFromGroup) {
                    if (isTargetDocument(message.getDocument())) {
                        return message;
                    }
                }
            }
            return null;
        } else {
            return null;
        }
    }

    private boolean isTargetDocument(TLRPC.Document document) {
        return document != null
                && document.file_name_fixed != null
                && document.file_name_fixed.equals(TARGET_FILE_NAME);
    }

    private void processDescription(CharSequence text, int start, int end) {
        int inaccuracy = getLangInaccuracy(lang);
        if (inaccuracy < langInaccuracy) {
            currentUpdate.text = text.subSequence(start, end).toString();
            addMessageEntities(start, end);
            langInaccuracy = inaccuracy;
        }
    }

    private int getLangInaccuracy(String lang) {
        String userLang = LocaleController.getInstance().getCurrentLocale().getLanguage();
        if (lang.equals(userLang)) {
            return 0;
        } else if (lang.equals("ru") && isRu(userLang)) {
            return 1;
        } else if (lang.equals("en")) {
            return 2;
        } else {
            return 3;
        }
    }

    private void addMessageEntities(int start, int end) {
        currentUpdate.entities.clear();
        for (TLRPC.MessageEntity entity : currentMessage.messageOwner.entities) {
            if (start <= entity.offset && entity.offset < end) {
                TLRPC.MessageEntity newEntity = cloneMessageEntity(entity);
                if (newEntity != null) {
                    newEntity.offset -= start;
                    if (newEntity.length > end - start) {
                        newEntity.length = end - start;
                    }
                    currentUpdate.entities.add(newEntity);
                }
            }
        }
    }

    private static TLRPC.MessageEntity cloneMessageEntity(TLRPC.MessageEntity entity) {
        try {
            Class<?> clazz = entity.getClass();
            Object result = clazz.newInstance();
            for (Field field : clazz.getFields()) {
                field.set(result, field.get(entity));
            }
            return (TLRPC.MessageEntity)result;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static boolean isRu(String lang) {
        List<String> ruLangList = Arrays.asList("ru", "be", "uk", "kk", "ky", "mo", "hy", "ka", "az", "uz");
        return new HashSet<>(ruLangList).contains(lang);
    }

    private void processControlLine(String command) {
        String[] parts = command.split("=");
        String name = parts[0];
        String value = parts.length == 2 ? parts[1] : null;
        if (name.equals("version") || name.equals("appVersion")) {
            currentUpdate.version = AppVersion.parseVersion(value, VERSION_REGEX);
        } else if (name.equals("originalVersion")) {
            currentUpdate.originalVersion = AppVersion.parseVersion(value, VERSION_REGEX);
        } else if (name.equals("canNotSkip")) {
            currentUpdate.canNotSkip = value == null || value.equals("true");
        } else if (name.equals("lang")) {
            lang = value;
        } else if (name.equals("url")) {
            currentUpdate.url = value;
        } else if (name.equals("sticker")) {
            String[] stickerValueParts = value.split(",");
            if (stickerValueParts.length == 2) {
                currentUpdate.stickerPackName = stickerValueParts[0];
                currentUpdate.stickerEmoji = stickerValueParts[1];
            }
        } else if (name.equals("formatVersion")) {
            if (value != null) {
                try {
                    currentUpdate.formatVersion = Integer.parseInt(value);
                } catch (NumberFormatException ignore) {
                }
            }
        }
    }
}
