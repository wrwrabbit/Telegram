package org.telegram.messenger.partisan.verification;

import android.os.Looper;
import android.util.LongSparseArray;

import androidx.core.util.Pair;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.CacheByChatsController;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FilePathDatabase;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NativeLoader;
import org.telegram.ui.Storage.CacheModel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class VerificationDatabase {
    public static final int TYPE_VERIFIED = 1;
    public static final int TYPE_SCAM = 2;
    public static final int TYPE_FAKE = 3;

    private DispatchQueue dispatchQueue;
    private SQLiteDatabase database;
    private File cacheFile;
    private File shmCacheFile;

    private final static int LAST_DB_VERSION = 1;

    private final static String DATABASE_NAME = "verified";
    private final static String DATABASE_BACKUP_NAME = "verified_backup";
    boolean databaseCreated;

    private static VerificationDatabase instance;

    public static synchronized VerificationDatabase getInstance() {
        if (instance == null) {
            instance = new VerificationDatabase();
        }
        return instance;
    }

    public void createDatabase(int tryCount, boolean fromBackup) {
        File filesDir = ApplicationLoader.getFilesDirFixed();
        cacheFile = new File(filesDir, DATABASE_NAME + ".db");
        shmCacheFile = new File(filesDir, DATABASE_NAME + ".db-shm");

        boolean createTable = false;

        if (!cacheFile.exists()) {
            createTable = true;
        }
        try {
            database = new SQLiteDatabase(cacheFile.getPath());
            database.executeFast("PRAGMA secure_delete = ON").stepThis().dispose();
            database.executeFast("PRAGMA temp_store = MEMORY").stepThis().dispose();

            if (createTable) {
                database.executeFast("CREATE TABLE verified_storage(storage_id INTEGER, storage_name TEXT, chat_username TEXT, chat_id INTEGER, last_check_time INTEGER, last_post_id INTEGER, PRIMARY KEY(storage_id));").stepThis().dispose();
                database.executeFast("CREATE TABLE verified(storage_id INTEGER, chat_id INTEGER, username TEXT, type INTEGER, PRIMARY KEY(storage_id, chat_id));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS chat_id_in_verified ON verified(chat_id);").stepThis().dispose();

                fill_database();

                database.executeFast("PRAGMA user_version = " + LAST_DB_VERSION).stepThis().dispose();
            } else {
                int version = database.executeInt("PRAGMA user_version");
                if (version == 0) {
                    throw new Exception("malformed");
                }
                migrateDatabase(version);
                //migration
            }
            if (!fromBackup) {
                createBackup();
            }
        } catch (Exception e) {
            if (tryCount < 4) {
                if (!fromBackup && restoreBackup()) {
                    createDatabase(tryCount + 1, true);
                    return;
                } else {
                    cacheFile.delete();
                    shmCacheFile.delete();
                    createDatabase(tryCount + 1, false);
                }
            }
            if (BuildVars.DEBUG_VERSION) {
                FileLog.e(e);
            }
        }
    }

    private void fill_database() throws Exception {
        SQLitePreparedStatement statement = database.executeFast("INSERT INTO verified_storage (storage_name, chat_username, chat_id, last_check_time, last_post_id) VALUES(?, ?, ?, ?, ?)");
        statement.requery();
        statement.bindString(1, "Cyber Partisans");
        statement.bindString(2, "ptg_verification_test");
        statement.bindInteger(3, -1998338265);
        statement.bindInteger(4, 0);
        statement.bindInteger(5, 0);
        statement.step();
        statement.dispose();

        SQLiteCursor cursor = database.queryFinalized("SELECT last_insert_rowid()");
        int storage_id;
        if (cursor.next()) {
            storage_id = cursor.intValue(0);
        } else {
            throw new Exception("storage_id error");
        }
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,989056630 ,\"cpartisans_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5106491425,\"cpartisans_urgent_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5217087258,\"partisan_qa_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5259637648,\"cpartisans_join_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,2066143564,\"partisan_telegram_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5817399651,\"facement_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1477761243,\"Busliniybot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1680003670,\"dns_coord_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5197056745,\"TGBel_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5269881457,\"BelarusAndUkraineBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5248690359,\"occupant_info_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1764081723,\"bz_support_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1826798139,\"ReturnWhatStolen_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1203525499,\"belarusy_zarubezhja_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1201956582,\"zerkalo_editor\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,781931059 ,\"euroradio_minsk\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,6153646860,\"info_charter97\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,783723940 ,\"SvabodaBelarus\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,733628894 ,\"Motolko_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5233981354,\"HajunBYbot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1408155238,\"BelsatBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5088044675,\"basta11_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1254883880,\"BGMnews_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1807622699,\"sanctionswatch_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1235073753,\"MKBelbot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1610868721,\"real_belarus_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1270329713,\"strana_official_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1325073348,\"KYKYmediabot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1326631129,\"belteanewsbot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1578684412,\"nick_and_mikeBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1306844446,\"fraw_marta_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1448750362,\"dze_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1314492187,\"menskrazam_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1207923033,\"TheVillageBelarusBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5611596810,\"BOR_pochta_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1445915448,\"BLsupport_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1632896478,\"zamkadombot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1835507118,\"ByProsvetVolunteerBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1512107110,\"by_prosvet_feedback_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1263764071,\"fuckshtok\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1428483199,\"Dmbolk_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1464870731,\"belhalat_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1271955412,\"ChatHonest_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,688209485 ,\"viasna_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1635921527,\"stachka_by_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1715255901,\"ruh_connect_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5394894107,\"nau_by_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5054426164,\"kpd_by_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5010895223,\"hiveguide_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1383135185,\"belzhd_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1265159441,\"belzhd_editor\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1855819538,\"plan_peramoga_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5955987812,\"bypol_chats_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1468118581,\"AskOffice_Bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1313749067,\"FourOneOne4111\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5263169835,\"EXOMON_support_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,2009270454,\"balaganoff_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5048219469,\"mail_by\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,6007283902,\"Rezbat_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5737683598,\"pkk_reserve_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,6508533990,\"belpolinfobot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,6271362579,\"RuchBelNac_BOT\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5937370959,\"rushennie_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5494132715,\"vb_contact_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,863584518 ,\"worldprotest_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,1647034311,\"vybor_by_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5507948945,\"spasisebyabot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5884078727,\"Rus_ni_peace_da_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",1,5829538792,\"Yug_mopedi_bot\")").stepThis().dispose();

        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",2,2059952039,\"cpartisan_sanonbot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",2,2007785891,\"cpartisans_anon_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",2,1153790653,\"Bypoll\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",2,1754069446,\"predateliby\")").stepThis().dispose();

        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5735310739,\"face_menty_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1854185893,\"busliniy_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1786043956,\"dns_cord_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5276622916,\"TG_Bel_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5617305774,\"occupint_info_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5681177489,\"kdp_by_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5410932720,\"pian_peiramoga_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1839964132,\"plan_pieramoga_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5716598480,\"belzdh_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1284911026,\"tutbay_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5626034674,\"Matolko_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6058124728,\"motolkobot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5747297986,\"Motolkohelps_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6035117215,\"motolko_news_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5740485675,\"MKBbelbot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5028668462,\"cpartisan_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5127654526,\"ByProsvetVolunteer_Bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1854185893,\"Busliniy_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6050276725,\"buslinybot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6074163432,\"busliny_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1786043956,\"dns_cord_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5645487088,\"BelarusAndUkrainieBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6293881487,\"BelarusAndUkraine_Bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5606443190,\"BelarusAndUkrainBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5794806573,\"pkk_reservy_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5543028382,\"pkk_reserv_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5652124632,\"pkk_reserved_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5731843616,\"pkk_reserver_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1356576596,\"nick_and_mike_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5599734900,\"radiosvabodabot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1793463571,\"honestpeople_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5017880312,\"honest_peopIe_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1599192547,\"strana_svyaz_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5661955867,\"Haiun_BYBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5948492749,\"bypol_proverka_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1407287511,\"ByPol_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5840583779,\"OSB_By_Pol_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5995726427,\"planperamogabot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6239869956,\"plan_peramogabot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1375049419,\"SupolkaBY_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,403342504 ,\"FacementBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1810832442,\"plan_piramoga_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1709650512,\"pieramoga_plan_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1709527783,\"plan_peramog_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1532380090,\"peramoga_plan_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6031551222,\"Razbat_bot\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6293972219,\"Razbat_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1707221120,\"belwariors\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1970634134,\"belwarriors_ru\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5606345142,\"Razbat_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6292242979,\"BelarusAndIUkraineBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5474204114,\"BelarussianAndUkraineBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5914511754,\"BelarusAdnUkraineBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5630096528,\"HajunBYanon_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5614673918,\"HajynBYbot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5757560610,\"HajunBEL_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5821054654,\"HajunB_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5810084409,\"HajunBYnash_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5695418211,\"HajunBLR_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1254143359,\"motolkohelp_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1451093794,\"motolko_nbr_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,2110427751,\"MotolkohelpBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5700151833,\"motolko_newsbot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5698230921,\"MotoIko_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1631724675,\"nic_and_mike_bot\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1432039243,\"MotoIko_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5047547433,\"kpd_blr_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5053420704,\"kpd_b_bot\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5660796162,\"MotoIko_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5779625449,\"RuchbelnacBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5135746255,\"TGBelbot\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5130159080,\"MotoIko_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1929789849,\"ruschennie\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6260569674,\"MotoIko_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6143884311,\"Paspalitaje_Rusennie_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6123656477,\"rushenniecz_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5634483218,\"Volnaja_Belaus_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5728606679,\"VolnayBelarus_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1159302697,\"golosovanie_RF\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1766534445,\"insayderyurec\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1730025636,\"cpartisans2020\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5215261203,\"Volnaja_Belaus_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5423658642,\"worldprotest1bot\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1400869810,\"Volnaja_Belaus_bot\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1261378820,\"Volnaja_Belaus_bot\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6125366284,\"Volnaja_Belaus_bot\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,2115172504,\"Volnaja_Belaus_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1864083131,\"nexta\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1248808496,\"lats_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1847224666,\"stsikhanouskaya\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1459405938,\"pulpervoi_official\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1404319831,\"CabinetST\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1260250495,\"naubelarus_bot\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1562636546,\"CabinetST\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1877831257,\"CabinetST\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1956827792,\"ateshua\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5763025616,\"generalchereshnyaBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1972480858,\"gnilayacherexaa\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1699191195,\"bahmut_demon\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1780437970,\"CabinetST\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1810978803,\"zhovtastrichka\")").stepThis().dispose();
        //database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1824347817,\"CabinetST\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1175048525,\"crimean_content\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6247851779,\"partizany_crimea_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6233710456,\"ateshfire_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6273841737,\"zhovta_strichka_ua_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1524487787,\"hochy_zhyt\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5731381213,\"hochu_zhytbot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6057323348,\"krympartizansbot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1812284976,\"gniIayacherexa\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5830427793,\"zhovtastrichka_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5857031037,\"yellowribbon_uabot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5826729656,\"zhovta_strichkabot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1842626385,\"skrinka_pandori_lachen\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1471803186,\"madyar_lachen_pishe\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1976625090,\"nikolaev_vanya\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1805544387,\"vanek_nikolaev1\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5721687279,\"testDs1_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6069541884,\"evorog_gov_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5785216795,\"evorog_ua_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5956713165,\"evorog_anonim_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5440442548,\"evorogina_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5324394535,\"evorog_robot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1719705579,\"hochu_zhyut\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1844665241,\"hochu_zhyt1\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5643974597,\"spasisebya_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5698701277,\"spaslsebyabot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5727271350,\"spastisebyabot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5673236403,\"Hochu_Zhyt_Bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1566061839,\"gdeposjar\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1705987207,\"pojar_piter\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1829232052,\"firemoscowandRussia\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6091365211,\"Kahovskiruhbot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6110012702,\"Kakhovski_ruh_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,1927064019,\"brdprotiv_me\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6221245011,\"brdsprotiv_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6267293532,\"brdprotivBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5734160761,\"brdprotiv_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5760847362,\"Suprotyv_brd_bpa_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6035643336,\"mrplsprotyv_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5399094229,\"peace_da_rusni_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6018488147,\"Rusni_peace_da_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5887363286,\"Rus_ni_peace_daBot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,5857742074,\"Rus_ne_paece_da_bot\")").stepThis().dispose();
        database.executeFast("INSERT INTO verified(storage_id,type,chat_id,username) VALUES (" + storage_id + ",3,6083107910,\"Rusni_peaceda_bot\")").stepThis().dispose();
    }

    private void migrateDatabase(int version) throws SQLiteException {}

    private void createBackup() {
        File filesDir = ApplicationLoader.getFilesDirFixed();
        File backupCacheFile = new File(filesDir, DATABASE_BACKUP_NAME + ".db");
        try {
            AndroidUtilities.copyFile(cacheFile, backupCacheFile);
            FileLog.d("file db backup created " + backupCacheFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean restoreBackup() {
        File filesDir = ApplicationLoader.getFilesDirFixed();
        File backupCacheFile = new File(filesDir, DATABASE_BACKUP_NAME + ".db");
        if (!backupCacheFile.exists()) {
            return false;
        }
        try {
            return AndroidUtilities.copyFile(backupCacheFile, cacheFile);
        } catch (IOException e) {
            FileLog.e(e);
        }
        return false;
    }

    public int getChatType(long chatId) {
        ensureDatabaseCreated();
        if (database == null) {
            return -1;
        }
        SQLiteCursor cursor = null;
        int res = -1;
        try {
            cursor = database.queryFinalized("SELECT type FROM verified WHERE chat_id = " + chatId);
            if (cursor.next()) {
                res = cursor.intValue(0);
            }
        } catch (SQLiteException ignore) {
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return res;
    }

    public void ensureDatabaseCreated() {
        if (!databaseCreated) {
            if (!NativeLoader.loaded()) {
                int tryCount = 0;
                while (!NativeLoader.loaded()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    tryCount++;
                    if (tryCount > 5) {
                        break;
                    }
                }
            }
            createDatabase(0, false);
            databaseCreated = true;
        }
    }

    public DispatchQueue getQueue() {
        ensureQueueExist();
        return dispatchQueue;
    }

    private void ensureQueueExist() {
        if (dispatchQueue == null) {
            synchronized (this) {
                if (dispatchQueue == null) {
                    dispatchQueue = new DispatchQueue("verified_database_queue");
                    dispatchQueue.setPriority(Thread.MAX_PRIORITY);
                }
            }
        }
    }
}
