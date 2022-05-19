package org.telegram.messenger.fakepasscode;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private static final Pattern FOREIGN_AGENT_REGEX = Pattern.compile("данное\\s*сообщение\\s*\\(материал\\)\\s*создано\\s*и\\s*\\(или\\)\\s*распространено\\s*иностранным\\s*средством\\s*массовой\\s*информации,\\s*выполняющим\\s*функции\\s*иностранного\\s*агента,\\s*и\\s*\\(или\\)\\s*российским\\s*юридическим\\s*лицом,\\s*выполняющим\\s*функции\\s*иностранного\\s*агента[\\.\\s\\r\\n]*");

    static Location getLastLocation() {
        LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = lm.getProviders(true);
        Location l = null;
        for (int i = providers.size() - 1; i >= 0; i--) {
            l = lm.getLastKnownLocation(providers.get(i));
            if (l != null) {
                break;
            }
        }
        return l;
    }

    static String getLastLocationString() {
        Location loc = Utils.getLastLocation();
        if (loc != null) {
            return " " + LocaleController.getString("Geolocation", R.string.Geolocation) + ":" + loc.getLatitude() + ", " + loc.getLongitude();
        } else {
            return "";
        }
    }

    public static void clearCache(Runnable callback) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean imagesCleared = false;
            for (int a = 0; a < 7; a++) {
                int type = -1;
                int documentsMusicType = 0;
                if (a == 0) {
                    type = FileLoader.MEDIA_DIR_IMAGE;
                } else if (a == 1) {
                    type = FileLoader.MEDIA_DIR_VIDEO;
                } else if (a == 2) {
                    type = FileLoader.MEDIA_DIR_DOCUMENT;
                    documentsMusicType = 1;
                } else if (a == 3) {
                    type = FileLoader.MEDIA_DIR_DOCUMENT;
                    documentsMusicType = 2;
                } else if (a == 4) {
                    type = FileLoader.MEDIA_DIR_AUDIO;
                } else if (a == 5) {
                    type = 100;
                } else if (a == 6) {
                    type = FileLoader.MEDIA_DIR_CACHE;
                }
                if (type == -1) {
                    continue;
                }
                File file;
                if (type == 100) {
                    file = new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache");
                } else {
                    file = FileLoader.checkDirectory(type);
                }
                if (file != null) {
                    Utilities.clearDir(file.getAbsolutePath(), documentsMusicType, Long.MAX_VALUE, false);
                }

                if (type == FileLoader.MEDIA_DIR_CACHE) {
                    imagesCleared = true;
                } else if (type == FileLoader.MEDIA_DIR_IMAGE) {
                    imagesCleared = true;
                } else if (type == 100) {
                    imagesCleared = true;
                }
            }
            final boolean imagesClearedFinal = imagesCleared;

            AndroidUtilities.runOnUIThread(() -> {
                if (imagesClearedFinal) {
                    ImageLoader.getInstance().clearMemory();
                }
                for (int i = UserConfig.MAX_ACCOUNT_COUNT - 1; i >= 0; i--) {
                    if (UserConfig.getInstance(i).isClientActivated()) {
                        DownloadController controller = DownloadController.getInstance(i);
                        controller.deleteRecentFiles(new ArrayList<>(controller.recentDownloadingFiles));
                        controller.deleteRecentFiles(new ArrayList<>(controller.downloadingFiles));
                    }
                }
                if (callback != null) {
                    try {
                        callback.run();
                    } catch (Exception ignored) {
                    }
                }
            });
        });
    }

    public static void deleteDialog(int accountNum, long id) {
        deleteDialog(accountNum, id, false);
    }

    public static void deleteDialog(int accountNum, long id, boolean revoke) {
        AccountInstance account = AccountInstance.getInstance(accountNum);
        MessagesController messagesController = account.getMessagesController();
        TLRPC.Chat chat = null;
        TLRPC.User user = null;
        if (id > 0) {
            user = messagesController.getUser(id);
        } else {
            chat = messagesController.getChat(-id);
        }
        if (chat != null) {
            if (ChatObject.isNotInChat(chat)) {
                messagesController.deleteDialog(id, 0, revoke);
            } else {
                TLRPC.User currentUser = messagesController.getUser(account.getUserConfig().getClientUserId());
                messagesController.deleteParticipantFromChat((int) -id, currentUser, null);
            }
        } else {
            messagesController.deleteDialog(id, 0, revoke);
        }
    }

    public static long getChatOrUserId(long id, Optional<Integer> account) {
        if (id >= Integer.MIN_VALUE || !account.isPresent()) {
            return id;
        } else {
            MessagesController controller = MessagesController.getInstance(account.get());
            return controller.getEncryptedChat((int) (id >> 32)).user_id;
        }
    }

    public static void cleanAutoDeletable(int messageId, int currentAccount, long dialogId) {
        RemoveAsReadMessages.load();
        Map<String, List<RemoveAsReadMessages.RemoveAsReadMessage>> curAccountMessages =
                RemoveAsReadMessages.messagesToRemoveAsRead.get("" + currentAccount);

        if (curAccountMessages == null || curAccountMessages.get("" + dialogId) == null) {
            return;
        }

        for (RemoveAsReadMessages.RemoveAsReadMessage messageToRemove : new ArrayList<>(curAccountMessages.get("" + dialogId))) {
            if (messageToRemove.getId() == messageId) {
                RemoveAsReadMessages.messagesToRemoveAsRead.get("" + currentAccount).get("" + dialogId).remove(messageToRemove);
            }
        }

        if (curAccountMessages.get("" + dialogId) != null
                && curAccountMessages.get("" + dialogId).isEmpty()) {
            RemoveAsReadMessages.messagesToRemoveAsRead.get("" + currentAccount).remove("" + dialogId);
        }
        RemoveAsReadMessages.save();
    }

    public static void startDeleteProcess(int currentAccount, List<MessageObject> messages) {
        Map<Long, List<MessageObject>> dialogMessages = new HashMap<>();
        for (MessageObject message : messages) {
            long dialogId = message.messageOwner.dialog_id;
            if (!dialogMessages.containsKey(dialogId)) {
                dialogMessages.put(dialogId, new ArrayList<>());
            }
            dialogMessages.get(dialogId).add(message);
        }
        for (Map.Entry<Long, List<MessageObject>> entry : dialogMessages.entrySet()) {
            startDeleteProcess(currentAccount, entry.getKey(), entry.getValue());
        }
    }

    public static void startDeleteProcess(int currentAccount, long currentDialogId,
                                          List<MessageObject> messages) {
        RemoveAsReadMessages.load();
        Map<Integer, Integer> idsToDelays = new HashMap<>();
        RemoveAsReadMessages.messagesToRemoveAsRead.putIfAbsent("" + currentAccount, new HashMap<>());
        for (MessageObject message : messages) {
            for (RemoveAsReadMessages.RemoveAsReadMessage messageToRemove :
                    RemoveAsReadMessages.messagesToRemoveAsRead.get("" + currentAccount)
                            .getOrDefault("" + currentDialogId, new ArrayList<>())) {
                if (messageToRemove.getId() == message.getId()) {
                    idsToDelays.put(message.getId(), messageToRemove.getScheduledTimeMs());
                    messageToRemove.setReadTime(System.currentTimeMillis());
                }
            }
        }
        RemoveAsReadMessages.save();

        for (Map.Entry<Integer, Integer> idToMs : idsToDelays.entrySet()) {
            ArrayList<Integer> ids = new ArrayList<>();
            ids.add(idToMs.getKey());
            int delay = idToMs.getValue();
            Utilities.globalQueue.postRunnable(() -> {
                AndroidUtilities.runOnUIThread(() -> {
                    MessagesController.getInstance(currentAccount).deleteMessages(ids, null, null, currentDialogId,
                            true, false, false, 0,
                            null, false, false);
                    cleanAutoDeletable(ids.get(0), currentAccount, currentDialogId);
                });
            }, Math.max(delay, 0));
        }
        RemoveAsReadMessages.save();
    }

    public static boolean isNetworkConnected() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            AccountInstance account = AccountInstance.getInstance(i);
            ConnectionsManager connectionsManager = account.getConnectionsManager();
            int connectionState = connectionsManager.getConnectionState();
            if (connectionState != ConnectionsManager.ConnectionStateWaitingForNetwork) {
                return true;
            }
        }
        return false;
    }

    public static String fixStringMessage(String message) {
        if (message == null) {
            return null;
        }
        CharSequence fixedMessage = fixMessage(message);
        if (fixedMessage == null) {
            return null;
        }
        return fixedMessage.toString();
    }

    public static void fixTlrpcMessage(TLRPC.Message message) {
        if (message == null) {
            return;
        }
        if (SharedConfig.cutForeignAgentsText && SharedConfig.fakePasscodeActivatedIndex == -1) {
            try {
                SpannableString source = new SpannableString(message.message);
                for (TLRPC.MessageEntity entity : message.entities) {
                    source.setSpan(entity, entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                CharSequence result = cutForeignAgentPart(source);
                message.message = result.toString();
                if (result instanceof Spannable) {
                    Spannable spannable = (Spannable) result;
                    TLRPC.MessageEntity[] entities = spannable.getSpans(0, result.length(), TLRPC.MessageEntity.class);
                    for (TLRPC.MessageEntity entity : entities) {
                        entity.offset = spannable.getSpanStart(entity);
                        entity.length = spannable.getSpanEnd(entity) - entity.offset;
                    }
                    message.entities.clear();
                    message.entities.addAll(Arrays.asList(entities));
                }
            } catch (Exception e) {
                message.message = fixStringMessage(message.message);
            }

        }
    }

    public static CharSequence fixMessage(CharSequence message) {
        if (message == null) {
            return null;
        }
        CharSequence fixedMessage = message;
        if (SharedConfig.cutForeignAgentsText && SharedConfig.fakePasscodeActivatedIndex == -1) {
            fixedMessage = cutForeignAgentPart(message);
        }
        return fixedMessage;
    }

    private static CharSequence cutForeignAgentPart(CharSequence message) {
        String lowerCased = message.toString().toLowerCase(Locale.ROOT);
        Matcher matcher = FOREIGN_AGENT_REGEX.matcher(lowerCased);
        int lastEnd = -1;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        while (matcher.find()) {
            if (lastEnd == -1) {
                builder.append(message.subSequence(0, matcher.start()));
            } else {
                builder.append(message.subSequence(lastEnd, matcher.start()));
            }
            lastEnd = matcher.end();
        }
        if (lastEnd != -1) {
            builder.append(message.subSequence(lastEnd, message.length()));
            if (builder.length() != 0) {
                return SpannableString.valueOf(builder);
            } else {
                return "Empty Message";
            }
        } else {
            return cutTrimmedForeignAgentPart(message, lowerCased);
        }
    }

    private static CharSequence cutTrimmedForeignAgentPart(CharSequence message, String lowerCased) {
        int startIndex = lowerCased.indexOf("данное сообщение (материал) создано и (или) распространено");
        if (startIndex != -1) {
            int endIndex = lowerCased.length();
            while (endIndex > 0 && lowerCased.charAt(endIndex - 1) == '.' || lowerCased.charAt(endIndex - 1) == '…') {
                endIndex--;
            }
            String endPart = lowerCased.substring(startIndex, endIndex);
            String foreignAgentText = "данное сообщение (материал) создано и (или) распространено иностранным средством массовой информации, выполняющим функции иностранного агента, и (или) российским юридическим лицом, выполняющим функции иностранного агента";
            if (foreignAgentText.startsWith(endPart)) {
                while (startIndex > 0 && Character.isWhitespace(message.charAt(startIndex - 1))) {
                    startIndex--;
                }
                if (startIndex > 0) {
                    return message.toString().substring(0, startIndex);
                } else {
                    return "Empty Message";
                }
            }
        }
        return message;
    }
}
