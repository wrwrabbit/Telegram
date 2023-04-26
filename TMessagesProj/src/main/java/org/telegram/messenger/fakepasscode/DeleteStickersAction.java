package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.StickersActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.telegram.messenger.MediaDataController.TYPE_IMAGE;

@FakePasscodeSerializer.ToggleSerialization
public class DeleteStickersAction extends AccountAction implements NotificationCenter.NotificationCenterDelegate {
    @JsonIgnore
    private Set<Integer> loadedStickerTypes = new HashSet<>();
    @JsonIgnore
    private Set<Long> deletedStickerSets = new HashSet<>();
    @JsonIgnore
    private Set<Long> archivedStickerSets = new HashSet<>();

    private boolean preventBulletin = false;

    @Override
    public void execute(FakePasscode fakePasscode) {
        loadedStickerTypes.clear();
        deletedStickerSets.clear();
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.stickersDidLoad);
        preventBulletin = true;
        MediaDataController.getInstance(accountNum).loadStickers(TYPE_IMAGE, true, false, false, s -> {
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.stickersDidLoad);
            preventBulletin = false;
        });
        //delete recent emoji
        Emoji.clearRecentEmoji();
        // delete recent gif
        for (TLRPC.Document document : MediaDataController.getInstance(accountNum).getRecentGifs()) {
            MediaDataController.getInstance(accountNum).removeRecentGif(document);
        }
        deleteArchivedStickers();
    }

    private void deleteArchivedStickers() {
        TLRPC.TL_messages_getArchivedStickers req = new TLRPC.TL_messages_getArchivedStickers();
        req.offset_id = 0;
        req.limit = 100;
        req.masks = false;
        ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                processArchivedStickersResponse((TLRPC.TL_messages_archivedStickers) response);
            }
        }));
    }


    private void processArchivedStickersResponse(TLRPC.TL_messages_archivedStickers res) {
        MediaDataController controller = MediaDataController.getInstance(accountNum);
        for (TLRPC.StickerSetCovered set : res.sets) {
            controller.toggleStickerSet(null, set, 2, null, false, false);
        }
        synchronized (this) {
            archivedStickerSets = res.sets.stream().map(s -> s.set.id).collect(Collectors.toSet());
        }
    }

    @Override
    public synchronized void didReceivedNotification(int id, int account, Object... args) {
        if (account != accountNum) {
            return;
        }
        int type = (int) args[0];
        MediaDataController controller = MediaDataController.getInstance(accountNum);
        List<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>(controller.getStickerSets(type));
        if (!loadedStickerTypes.contains(type)) {
            loadedStickerTypes.add(type);
            for (TLRPC.TL_messages_stickerSet stickerSet : stickerSets) {
                if (!deletedStickerSets.contains(stickerSet.set.id)) {
                    deletedStickerSets.add(stickerSet.set.id);
                    controller.toggleStickerSet(null, stickerSet, 0, null, false, false);
                    synchronized (this) {
                        archivedStickerSets.remove(stickerSet.set.id);
                    }
                }
            }
        } else {
            for (TLRPC.TL_messages_stickerSet stickerSet : stickerSets) {
                synchronized (this) {
                    if (!archivedStickerSets.contains(stickerSet.set.id)) {
                        continue;
                    }
                }
                if (!deletedStickerSets.contains(stickerSet.set.id)) {
                    deletedStickerSets.add(stickerSet.set.id);
                    controller.toggleStickerSet(null, stickerSet, 0, null, false, false);
                }
            }
        }
        for (int recent_sticker_type = 0; recent_sticker_type < 8; recent_sticker_type++) {
            for (TLRPC.Document document : controller.getRecentStickers(recent_sticker_type)) {
                controller.addRecentSticker(recent_sticker_type, null, document, 0, true, false);
            }
        }
        controller.clearRecentStickers();
    }

    public boolean isPreventBulletin() {
        return preventBulletin;
    }
}
