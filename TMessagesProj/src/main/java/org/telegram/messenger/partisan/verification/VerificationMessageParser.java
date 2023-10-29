package org.telegram.messenger.partisan.verification;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.partisan.AppVersion;
import org.telegram.messenger.partisan.UpdateData;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class VerificationMessageParser {
    public static class ParsingResult {
        List<VerificationDatabase.ChatInfo> chatsToAdd = new ArrayList<>();
        List<Long> chatsToRemove = new ArrayList<>();
    }
    private int currentChatType;
    private boolean newLine = true;
    private boolean controlLine = true;
    private int blockStart = 0;
    private final int currentAccount;

    public VerificationMessageParser(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public ParsingResult parseMessage(MessageObject message) {
        if (message.messageText == null) {
            return null;
        }

        ParsingResult result = new ParsingResult();
        try {
            String[] lines = message.messageText.toString().split("\n");
            for (String line : lines) {
                if (line.startsWith("#")) {
                    processControlLine(line.substring(1));
                } else if (line.startsWith("+")) {
                    result.chatsToAdd.add(parseChatInfo(line.substring(1)));
                } else if (line.startsWith("-")) {
                    VerificationDatabase.ChatInfo info = parseChatInfo(line.substring(1));
                    result.chatsToRemove.add(info.chatId);
                }
            }
            return result;
        } catch (Exception ignore) {
        }
        return null;
    }

    private VerificationDatabase.ChatInfo parseChatInfo(String chatInfoStr) {
        VerificationDatabase.ChatInfo info = new VerificationDatabase.ChatInfo();
        info.type = currentChatType;
        if (chatInfoStr.contains("=")) {
            String[] parts = chatInfoStr.split("=");
            info.username = parts[0];
            info.chatId = Integer.parseInt(parts[1]);
        } else {
            info.username = null;
            info.chatId = Integer.parseInt(chatInfoStr);
        }
        return info;
    }

    private void processControlLine(String command) {
        if (command.equals("verified")) {
            currentChatType = VerificationDatabase.TYPE_VERIFIED;
        } else if (command.equals("scam")) {
            currentChatType = VerificationDatabase.TYPE_SCAM;
        } else if (command.equals("fake")) {
            currentChatType = VerificationDatabase.TYPE_FAKE;
        }
    }
}
