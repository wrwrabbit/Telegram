package org.telegram.messenger.partisan;

import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.FileLog;

public class PartisanDatabaseMigrationHelper {
    private static int LAST_PARTISAN_DB_VERSION = 1;

    public static void updateDb(SQLiteDatabase database) throws Exception {
        int currentVersion = database.executeInt("PRAGMA partisan_version");
        if (currentVersion == LAST_PARTISAN_DB_VERSION) {
            return;
        }
        PartisanLog.d("PartisanDatabaseMigrationHelper start db migration from " + currentVersion + " to " + LAST_PARTISAN_DB_VERSION);

        currentVersion = migrate(database, currentVersion);

        if (currentVersion != LAST_PARTISAN_DB_VERSION) {
            throw new Exception("version != LAST_PARTISAN_DB_VERSION");
        }

        FileLog.d("PartisanDatabaseMigrationHelper db migration finished to version " + currentVersion);
    }

    private static int migrate(SQLiteDatabase database, int currentVersion) throws Exception {
        if (currentVersion == 0) {
            database.executeFast("DROP TABLE IF EXISTS enc_groups").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_group_inner_chats").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_group_virtual_messages").stepThis().dispose();
            database.executeFast("DROP TABLE IF EXISTS enc_group_virtual_messages_to_messages_v2").stepThis().dispose();

            database.executeFast("CREATE TABLE enc_groups(encrypted_group_id INTEGER PRIMARY KEY, name TEXT)").stepThis().dispose();
            database.executeFast("CREATE TABLE enc_group_inner_chats(encrypted_group_id INTEGER, encrypted_chat_id INTEGER, " +
                    "PRIMARY KEY(encrypted_group_id, encrypted_chat_id), " +
                    "FOREIGN KEY (encrypted_chat_id) REFERENCES enc_chats(uid) ON DELETE CASCADE)").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS enc_group_inner_chats_idx ON enc_group_inner_chats(encrypted_chat_id);").stepThis().dispose();

            database.executeFast("CREATE TABLE enc_group_virtual_messages(encrypted_group_id INTEGER, virtual_message_id INTEGER, " +
                    "PRIMARY KEY(encrypted_group_id, virtual_message_id), " +
                    "FOREIGN KEY (encrypted_group_id) REFERENCES enc_groups(encrypted_group_id) ON DELETE CASCADE)").stepThis().dispose();
            database.executeFast("CREATE TABLE enc_group_virtual_messages_to_messages_v2(encrypted_group_id INTEGER, virtual_message_id INTEGER, encrypted_chat_id INTEGER, real_message_id INTEGER, " +
                    "PRIMARY KEY(encrypted_group_id, virtual_message_id, encrypted_chat_id), " +
                    "FOREIGN KEY (encrypted_group_id, virtual_message_id) REFERENCES enc_group_virtual_messages(encrypted_group_id, virtual_message_id) ON DELETE CASCADE," +
                    "FOREIGN KEY (encrypted_group_id, encrypted_chat_id) REFERENCES enc_group_inner_chats(encrypted_group_id, encrypted_chat_id) ON DELETE CASCADE)").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS enc_group_virtual_messages_to_messages_v2_idx ON enc_group_virtual_messages_to_messages_v2(encrypted_group_id, encrypted_chat_id, real_message_id);").stepThis().dispose();

            currentVersion = 1;
            database.executeFast("PRAGMA partisan_version = " + currentVersion).stepThis().dispose();
        }
        return currentVersion;
    }


}
