package org.telegram.messenger.fakepasscode;

import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RemoveAsReadMessages {
    public static class RemoveAsReadMessage {
        private int id;
        private long readTime = -1;
        private int scheduledTimeMs;

        public RemoveAsReadMessage() {
        }

        public RemoveAsReadMessage(int id, int scheduledTimeMs) {
            this.id = id;
            this.scheduledTimeMs = scheduledTimeMs;
        }

        public RemoveAsReadMessage(int id, int scheduledTimeMs, long readTime) {
            this.id = id;
            this.scheduledTimeMs = scheduledTimeMs;
            this.readTime = readTime;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getScheduledTimeMs() {
            return scheduledTimeMs;
        }

        public void setScheduledTimeMs(int scheduledTimeMs) {
            this.scheduledTimeMs = scheduledTimeMs;
        }

        public long getReadTime() {
            return readTime;
        }

        public void setReadTime(long readTime) {
            this.readTime = readTime;
        }
    }

    private static class StartupMessageLoader implements NotificationCenter.NotificationCenterDelegate {
        private final Map<String, Map<String, List<RemoveAsReadMessage>>> messagesToRemoveAsRead;
        private final int classGuid = ConnectionsManager.generateClassGuid();
        Map<String, Set<String>> dialogsToLoad = new HashMap<>();

        private StartupMessageLoader(Map<String, Map<String, List<RemoveAsReadMessage>>> messagesToRemoveAsRead) {
            this.messagesToRemoveAsRead = messagesToRemoveAsRead;
        }

        public static void load(Map<String, Map<String, List<RemoveAsReadMessage>>> messagesToRemoveAsRead) {
            new StartupMessageLoader(messagesToRemoveAsRead).loadInternal();
        }

        public void loadInternal() {
            for (Map.Entry<String, Map<String, List<RemoveAsReadMessage>>> accountEntry : messagesToRemoveAsRead.entrySet()) {
                if (accountEntry.getValue().isEmpty()) {
                    continue;
                }
                Set<String> dialogs = accountEntry.getValue().keySet();
                if (dialogs.isEmpty()) {
                    continue;
                }
                dialogsToLoad.put(accountEntry.getKey(), new HashSet<>(dialogs));
                NotificationCenter.getInstance(Integer.parseInt(accountEntry.getKey())).addObserver(this, NotificationCenter.updateInterfaces);
            }
        }

        @Override
        public synchronized void didReceivedNotification(int id, int account, Object... args) {
            MessagesController controller = MessagesController.getInstance(account);
            for (Map.Entry<String, List<RemoveAsReadMessage>> dialogEntry : messagesToRemoveAsRead.get(Integer.valueOf(account).toString()).entrySet()) {
                if (dialogEntry.getValue().isEmpty()) {
                    continue;
                }
                dialogsToLoad.remove(dialogEntry.getKey());
                long dialogId = Integer.parseInt(dialogEntry.getKey());
                if (dialogId > 0 && controller.getUser(dialogId) == null) {
                    continue;
                }
                for (RemoveAsReadMessage message : dialogEntry.getValue()) {
                    controller.loadMessages(dialogId, 0, false, 1, message.id + 1, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 1);
                }
            }
            if (dialogsToLoad.isEmpty()) {
                NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.updateInterfaces);
            }
        }
    }

    public static Map<String, Map<String, List<RemoveAsReadMessage>>> messagesToRemoveAsRead = new HashMap<>();
    public static Map<String, Integer> delays = new HashMap<>();
    private static final Object sync = new Object();
    private static boolean isLoaded = false;

    public static void load() {
        synchronized (sync) {
            if (isLoaded) {
                return;
            }

            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("removeasreadmessages", Context.MODE_PRIVATE);
                ObjectMapper mapper = new ObjectMapper();
                mapper.enableDefaultTyping();
                String messagesToRemoveAsReadString = preferences.getString("messagesToRemoveAsRead", null);
                String delaysString = preferences.getString("delays", null);
                messagesToRemoveAsRead = mapper.readValue(messagesToRemoveAsReadString, HashMap.class);
                delays = mapper.readValue(delaysString, HashMap.class);
                isLoaded = true;
            } catch (Exception ignored) {
            }
        }

        StartupMessageLoader.load(messagesToRemoveAsRead);
    }

    public static void save() {
        synchronized (sync) {
            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("removeasreadmessages", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                ObjectMapper mapper = new ObjectMapper();
                mapper.enableDefaultTyping();
                String messagesToRemoveAsReadString = mapper.writeValueAsString(messagesToRemoveAsRead);
                String delaysString = mapper.writeValueAsString(delays);
                editor.putString("messagesToRemoveAsRead", messagesToRemoveAsReadString);
                editor.putString("delays", delaysString);
                editor.commit();
            } catch (Exception ignored) {
            }
        }
    }
}
