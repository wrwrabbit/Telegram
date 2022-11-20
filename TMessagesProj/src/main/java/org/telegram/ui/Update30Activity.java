package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.Update30;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class Update30Activity extends BaseFragment implements Update30.MakeZipDelegate, NotificationCenter.NotificationCenterDelegate {
    enum Step {
        UNINSTALL_OLD_TELEGRAM,
        UNINSTALL_OLD_TELEGRAM_FAILED,

        DOWNLOAD_TELEGRAM,
        DOWNLOAD_TELEGRAM_FAILED,
        DOWNLOAD_TELEGRAM_LOCKED,
        DOWNLOAD_TELEGRAM_COMPLETED,

        INSTALL_TELEGRAM,
        INSTALL_TELEGRAM_FAILED,
        INSTALL_TELEGRAM_LOCKED,

        MAKE_ZIP,
        MAKE_ZIP_FAILED,
        MAKE_ZIP_LOCKED,
        MAKE_ZIP_COMPLETED,

        UNINSTALL_SELF;

        Step simplify() {
            switch (this) {
                case UNINSTALL_OLD_TELEGRAM:
                case UNINSTALL_OLD_TELEGRAM_FAILED:
                    return UNINSTALL_OLD_TELEGRAM;
                case DOWNLOAD_TELEGRAM:
                case DOWNLOAD_TELEGRAM_FAILED:
                case DOWNLOAD_TELEGRAM_LOCKED:
                case DOWNLOAD_TELEGRAM_COMPLETED:
                default:
                    return DOWNLOAD_TELEGRAM;
                case INSTALL_TELEGRAM:
                case INSTALL_TELEGRAM_FAILED:
                case INSTALL_TELEGRAM_LOCKED:
                    return INSTALL_TELEGRAM;
                case MAKE_ZIP:
                case MAKE_ZIP_FAILED:
                case MAKE_ZIP_LOCKED:
                case MAKE_ZIP_COMPLETED:
                    return MAKE_ZIP;
                case UNINSTALL_SELF:
                    return UNINSTALL_SELF;
            }
        }
    }

    RelativeLayout relativeLayout;

    private Step step;

    private MessageObject messageObject;

    private TextView titleTextView;
    private TextView descriptionText;
    private RadialProgressView progressBar;
    private TextView buttonTextView;

    private int telegramDownloadProgress;
    private boolean telegramLoadingStuck;
    private LocalDateTime lastProgressUpdateTime;
    private File zipFile;
    private byte[] passwordBytes;

    private int TAG;
    private boolean destroyed;
    private long spaceSizeNeeded;

    FileDownloadListener downloadListener;

    public Update30Activity() {
        super();
    }

    public Update30Activity(MessageObject messageObject) {
        super();
        this.messageObject = messageObject;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        if (messageObject == null) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.update30MessageLoaded);
        }
        return true;
    }

    @Override
    public View createView(Context context) {
        TAG = getDownloadController().generateObserverTag();

        createActionBar();
        createFragmentView(context);
        createRelativeLayout(context);
        createDescriptionText(context);
        createTitleTextView(context);
        createProgressBar(context);
        createButton(context);

        initStep();

        new Thread(this::checkThread).start();

        return fragmentView;
    }

    private void createActionBar() {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
    }

    private void createFragmentView(Context context) {
        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        actionBar.setTitle(LocaleController.getString(R.string.Updater30ActivityTitle));
        frameLayout.setTag(Theme.key_windowBackgroundGray);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
    }

    private void createRelativeLayout(Context context) {
        relativeLayout = new RelativeLayout(context);
        ((FrameLayout) fragmentView).addView(relativeLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void createProgressBar(Context context) {
        progressBar = new RadialProgressView(context);
        progressBar.setSize(AndroidUtilities.dp(32));
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        relativeParams.addRule(RelativeLayout.BELOW, descriptionText.getId());
        progressBar.setVisibility(View.GONE);
        relativeLayout.addView(progressBar, relativeParams);
    }

    private void createTitleTextView(Context context) {
        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        titleTextView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        relativeParams.addRule(RelativeLayout.ABOVE, descriptionText.getId());
        relativeLayout.addView(titleTextView, relativeParams);
    }

    private void createDescriptionText(Context context) {
        descriptionText = new TextView(context);
        descriptionText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText.setLineSpacing(AndroidUtilities.dp(2), 1);
        descriptionText.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            descriptionText.setId(View.generateViewId());
        } else {
            descriptionText.setId(ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE / 2, Integer.MAX_VALUE));
        }
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        relativeLayout.addView(descriptionText, relativeParams);
    }

    private void createButton(Context context) {
        buttonTextView = new TextView(context) {
            CellFlickerDrawable cellFlickerDrawable;

            @Override
            protected void onDraw(Canvas canvas) {
                if (isEnabled()) {
                    super.onDraw(canvas);
                    if (cellFlickerDrawable == null) {
                        cellFlickerDrawable = new CellFlickerDrawable();
                        cellFlickerDrawable.drawFrame = false;
                        cellFlickerDrawable.repeatProgress = 2f;
                    }
                    cellFlickerDrawable.setParentWidth(getMeasuredWidth());
                    AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(4), null);
                    invalidate();
                } else {
                    super.onDraw(canvas);
                }
            }

            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                Drawable drawable = Theme.createSimpleSelectorRoundRectDrawable(
                        AndroidUtilities.dp(4),
                        enabled ? Theme.getColor(Theme.key_featuredStickers_addButton)
                                : Theme.getColor(Theme.key_picker_disabledButton),
                        Theme.getColor(Theme.key_featuredStickers_addButtonPressed)
                );
                buttonTextView.setBackground(drawable);
            }
        };

        buttonTextView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setEnabled(true);
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        relativeParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        relativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        relativeParams.setMargins(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), AndroidUtilities.dp(32));
        relativeLayout.addView(buttonTextView, relativeParams);
        buttonTextView.setOnClickListener(v -> buttonClicked());
    }

    private void initStep() {
        if (SharedConfig.update30Step == null) {
            if (Update30.isOldStandaloneTelegramInstalled(getParentActivity())) {
                setStep(Step.UNINSTALL_OLD_TELEGRAM);
            } else if (!isTelegramFileDownloaded()) {
                setStep(Step.DOWNLOAD_TELEGRAM);
                downloadTelegramApk();
            } else if (!Update30.isOldStandaloneTelegramInstalled(getParentActivity())
                    && !Update30.isNewStandaloneTelegramInstalled(getParentActivity())) {
                setStep(Step.INSTALL_TELEGRAM);
            } else {
                setStep(Step.MAKE_ZIP);
                makeZip();
            }
        } else {
            setStep(Step.valueOf(SharedConfig.update30Step));
            if (step == Step.MAKE_ZIP) {
                makeZip();
            } else if (step == Step.DOWNLOAD_TELEGRAM) {
                downloadTelegramApk();
            }
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        if (step.simplify() == Step.UNINSTALL_OLD_TELEGRAM && !Update30.isOldStandaloneTelegramInstalled(getParentActivity())) {
            downloadTelegramApk();
        } if (step.simplify() == Step.INSTALL_TELEGRAM && Update30.isNewStandaloneTelegramInstalled(getParentActivity())) {
            setStep(Step.MAKE_ZIP);
            makeZip();
        }
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.update30MessageLoaded);
        destroyed = true;
        super.onFragmentDestroy();
    }

    private synchronized void setStep(Step step) {
        this.step = step;
        SharedConfig.update30Step = step.toString();
        SharedConfig.saveConfig();
        AndroidUtilities.runOnUIThread(this::updateUI);
    }

    private void updateUI() {
        titleTextView.setText(getStepName(step));
        descriptionText.setText(getStepDescription());
        buttonTextView.setText(getButtonName());
        buttonTextView.setEnabled(getStepButtonEnabled(step));
        progressBar.setVisibility(step == Step.MAKE_ZIP ? View.VISIBLE : View.GONE);
    }

    private static String getStepName(Step step) {
        Step simplifiedStep = step.simplify();
        if (simplifiedStep == Step.UNINSTALL_OLD_TELEGRAM) {
            return LocaleController.getString(R.string.StepUninstallTelegram);
        } else if (simplifiedStep == Step.DOWNLOAD_TELEGRAM) {
            return LocaleController.getString(R.string.Step1);
        } else if (simplifiedStep == Step.INSTALL_TELEGRAM) {
            return LocaleController.getString(R.string.Step2);
        } else if (simplifiedStep == Step.MAKE_ZIP) {
            return LocaleController.getString(R.string.Step3);
        } else if (simplifiedStep == Step.UNINSTALL_SELF) {
            return LocaleController.getString(R.string.Step4);
        } else {
            return null;
        }
    }

    private String getStepDescription() {
        switch (step) {
            case UNINSTALL_OLD_TELEGRAM:
            default:
                return LocaleController.getString(R.string.UninstallStandaloneTelegramDescription);
            case UNINSTALL_OLD_TELEGRAM_FAILED:
                return LocaleController.getString(R.string.UninstallTelegramFailedDescription);
            case DOWNLOAD_TELEGRAM:
            {
                String str = String.format(LocaleController.getString(R.string.DownloadTelegramDescription), telegramDownloadProgress);
                if (telegramLoadingStuck) {
                    str += LocaleController.getString(R.string.DownloadTelegramTooLong);
                }
                return str;
            }
            case DOWNLOAD_TELEGRAM_FAILED:
                return LocaleController.getString(R.string.DownloadTelegramFailedDescription);
            case DOWNLOAD_TELEGRAM_COMPLETED:
                return LocaleController.getString(R.string.DownloadTelegramCompleteDescription);
            case INSTALL_TELEGRAM:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    return LocaleController.getString(R.string.InstallNewTelegramNoPermissionDescription);
                } else {
                    return LocaleController.getString(R.string.InstallNewTelegramDescription);
                }
            case INSTALL_TELEGRAM_FAILED:
                return LocaleController.getString(R.string.InstallNewTelegramFailedDescription);
            case MAKE_ZIP:
                return LocaleController.getString(R.string.MakeDataDescription);
            case MAKE_ZIP_FAILED:
                return LocaleController.getString(R.string.MakeDataFailedDescription);
            case MAKE_ZIP_COMPLETED:
                return LocaleController.getString(R.string.MakeDataCompleteDescription);
            case UNINSTALL_SELF:
                return LocaleController.getString(R.string.UninstallSelfDescription);
            case DOWNLOAD_TELEGRAM_LOCKED:
            case INSTALL_TELEGRAM_LOCKED:
            case MAKE_ZIP_LOCKED:
                return String.format(LocaleController.getString(R.string.NoSpaceForStep), (double)spaceSizeNeeded / 1024.0 / 1024.0);
        }
    }

    private String getButtonName() {
        switch (step) {
            case UNINSTALL_OLD_TELEGRAM:
            case UNINSTALL_OLD_TELEGRAM_FAILED:
            default:
                return LocaleController.getString(R.string.UninstallTelegram);
            case DOWNLOAD_TELEGRAM:
            case DOWNLOAD_TELEGRAM_COMPLETED:
            case DOWNLOAD_TELEGRAM_LOCKED:
                return LocaleController.getString(R.string.GoToNextStep);
            case INSTALL_TELEGRAM:
            case INSTALL_TELEGRAM_FAILED:
            case INSTALL_TELEGRAM_LOCKED:
                return LocaleController.getString(R.string.InstallNewTelegram);
            case MAKE_ZIP:
            case MAKE_ZIP_COMPLETED:
            case MAKE_ZIP_LOCKED:
                return LocaleController.getString(R.string.TransferFilesToNewTelegram);
            case UNINSTALL_SELF:
                return LocaleController.getString(R.string.UninstallSelf);
            case DOWNLOAD_TELEGRAM_FAILED:
            case MAKE_ZIP_FAILED:
                return LocaleController.getString(R.string.Retry);
        }
    }

    private static boolean getStepButtonEnabled(Step step) {
        switch (step) {
            case UNINSTALL_OLD_TELEGRAM:
            case UNINSTALL_OLD_TELEGRAM_FAILED:
            case DOWNLOAD_TELEGRAM_FAILED:
            case DOWNLOAD_TELEGRAM_COMPLETED:
            case INSTALL_TELEGRAM:
            case INSTALL_TELEGRAM_FAILED:
            case MAKE_ZIP_FAILED:
            case MAKE_ZIP_COMPLETED:
            case UNINSTALL_SELF:
                return true;
            default:
                return false;
        }
    }

    private synchronized void buttonClicked() {
        if (step == Step.UNINSTALL_OLD_TELEGRAM || step == Step.UNINSTALL_OLD_TELEGRAM_FAILED) {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:org.telegram.messenger.web"));
            getParentActivity().startActivity(intent);
        } else if (step == Step.DOWNLOAD_TELEGRAM_FAILED) {
            downloadTelegramApk();
        } else if (step == Step.DOWNLOAD_TELEGRAM_COMPLETED) {
            setStep(Step.INSTALL_TELEGRAM);
        } else if (step == Step.INSTALL_TELEGRAM || step == Step.INSTALL_TELEGRAM_FAILED) {
            Update30.installStandaloneTelegram(getParentActivity(), getTelegramFile());
            Update30.waitForTelegramInstallation(getParentActivity(), this::makeZip);
        } else if (step == Step.MAKE_ZIP_FAILED) {
            makeZip();
        } else if (step == Step.MAKE_ZIP_COMPLETED) {
            if (Update30.isOldStandaloneTelegramInstalled(getParentActivity())) {
                setStep(Step.UNINSTALL_OLD_TELEGRAM);
            } else if (!Update30.isNewStandaloneTelegramInstalled(getParentActivity())) {
                if (isTelegramFileDownloaded()) {
                    downloadTelegramApk();
                } else {
                    setStep(Step.INSTALL_TELEGRAM);
                }
            } else if (zipFile == null || !zipFile.exists()) {
                makeZip();
            } else {
                Update30.startNewTelegram(getParentActivity(), zipFile, passwordBytes);
            }
        } else if (step == Step.UNINSTALL_SELF) {
            uninstallSelf();
        }
    }

    @Override
    public void makeZipCompleted(File zipFile, byte[] passwordBytes) {
        AndroidUtilities.runOnUIThread(() -> {
            this.zipFile = zipFile;
            this.passwordBytes = passwordBytes;
            setStep(Step.MAKE_ZIP_COMPLETED);
        });
    }

    @Override
    public void makeZipFailed(Update30.MakeZipFailReason reason) {
        AndroidUtilities.runOnUIThread(() -> {
            if (reason == Update30.MakeZipFailReason.NO_TELEGRAM_APK) {
                downloadTelegramApk();
            } else {
                setStep(Step.MAKE_ZIP_FAILED);
            }
        });
    }

    private class FileDownloadListener implements DownloadController.FileDownloadProgressListener {
        @Override
        public void onFailedDownload(String fileName, boolean canceled) {
            setStep(Step.DOWNLOAD_TELEGRAM_FAILED);
            getDownloadController().removeLoadingFileObserver(this);
        }

        @Override
        public void onSuccessDownload(String fileName) {
            telegramApkDownloaded();
            getDownloadController().removeLoadingFileObserver(this);
        }

        @Override
        public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            lastProgressUpdateTime = LocalDateTime.now();
            long downloadedPercent = Math.round(((double)downloadSize / totalSize) * 100);
            telegramDownloadProgress = (int)downloadedPercent;
            telegramLoadingStuck = false;
            AndroidUtilities.runOnUIThread(Update30Activity.this::updateUI);
        }

        @Override
        public void onProgressUpload(String fileName, long downloadSize, long totalSize, boolean isEncrypted) {
        }

        @Override
        public int getObserverTag() {
            return TAG;
        }
    }

    private void downloadTelegramApk() {
        if (messageObject == null) {
            return;
        }
        if (messageObject.getDocument().size > getFreeMemorySize()) {
            spaceSizeNeeded = messageObject.getDocument().size;
            setStep(Step.DOWNLOAD_TELEGRAM_LOCKED);
            return;
        }
        lastProgressUpdateTime = LocalDateTime.now();
        setStep(Step.DOWNLOAD_TELEGRAM);
        telegramDownloadProgress = 0;
        telegramLoadingStuck = false;
        TLRPC.Document document = messageObject.getDocument();
        if (downloadListener != null) {
            getDownloadController().removeLoadingFileObserver(downloadListener);
        }
        downloadListener = new FileDownloadListener();
        getFileLoader().loadFile(document, messageObject, 0, 0);
        getDownloadController().addLoadingFileObserver(FileLoader.getAttachFileName(document), messageObject, downloadListener);
    }

    private void makeZip() {
        long zipSize = calculateZipSize();
        if (zipSize > getFreeMemorySize()) {
            spaceSizeNeeded = zipSize;
            setStep(Step.MAKE_ZIP_LOCKED);
            return;
        }
        setStep(Step.MAKE_ZIP);
        new Thread(() -> Update30.makeZip(getParentActivity(), this)).start();
    }

    private void telegramApkDownloaded() {
        File src = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_FILES), messageObject.getDocument().file_name_fixed);
        File dest = getTelegramFile();
        if (dest.exists()) {
            if (!dest.delete()) {
                setStep(Step.DOWNLOAD_TELEGRAM_FAILED);
                return;
            }
        }
        if (!src.renameTo(dest)) {
            setStep(Step.DOWNLOAD_TELEGRAM_FAILED);
            return;
        }
        setStep(Step.DOWNLOAD_TELEGRAM_COMPLETED);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode == 20202020) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getBooleanExtra("copied", false)) {
                installationFinished();
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.update30MessageLoaded) {
            messageObject = (MessageObject) args[0];
            AndroidUtilities.runOnUIThread(() -> {
                if (step == Step.DOWNLOAD_TELEGRAM) {
                    downloadTelegramApk();
                }
            });
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.dialogsNeedReload);
        }
    }

    private void uninstallSelf() {
        if (getTelegramFile().exists()) {
            getTelegramFile().delete();
        }
        if (zipFile != null && zipFile.exists()) {
            zipFile.delete();
        }

        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + getParentActivity().getPackageName()));
        getParentActivity().startActivity(intent);
    }

    private void checkThread() {
        while (!destroyed) {
            try {
                synchronized (this) {
                    long freeSize = getFreeMemorySize();
                    if (step == Step.DOWNLOAD_TELEGRAM_LOCKED) {
                        if (messageObject != null && messageObject.getDocument().size <= freeSize) {
                            downloadTelegramApk();
                        }
                    } else if (step == Step.DOWNLOAD_TELEGRAM) {
                        if (lastProgressUpdateTime != null) {
                            long progressDuration = ChronoUnit.SECONDS.between(lastProgressUpdateTime, LocalDateTime.now());
                            if (progressDuration > 5 && messageObject != null) {
                                File mediaDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_FILES);
                                String telegramFileName = messageObject.getDocument().file_name_fixed;
                                File telegramFile = new File(mediaDir, telegramFileName);
                                if (telegramFile.exists()) {
                                    if (telegramFile.length() == messageObject.getDocument().size) {
                                        telegramApkDownloaded();
                                    }
                                }
                            }
                            if (step == Step.DOWNLOAD_TELEGRAM && progressDuration > 10) {
                                telegramLoadingStuck = true;
                                AndroidUtilities.runOnUIThread(this::updateUI);
                            }
                        }
                    } else if (step == Step.INSTALL_TELEGRAM || step == Step.INSTALL_TELEGRAM_FAILED) {
                        spaceSizeNeeded = calculateTelegramSize();
                        if (spaceSizeNeeded > freeSize) {
                            setStep(Step.INSTALL_TELEGRAM_LOCKED);
                        }
                    } else if (step == Step.INSTALL_TELEGRAM_LOCKED) {
                        if (calculateTelegramSize() <= freeSize) {
                            setStep(Step.INSTALL_TELEGRAM);
                        }
                    } else if (step == Step.MAKE_ZIP_LOCKED) {
                        if (calculateZipSize() <= freeSize) {
                            makeZip();
                        }
                    } else if (step == Step.MAKE_ZIP_COMPLETED) {
                        if (Build.VERSION.SDK_INT < 24) {
                            if (checkCopiedFile()) {
                                installationFinished();
                            }
                        }
                    } else if (step == Step.UNINSTALL_SELF) {
                        if (Build.VERSION.SDK_INT < 24) {
                            checkCopiedFile();
                        }
                    }
                }
                Thread.sleep(100);
            } catch (Exception e) {
                Log.e("Update30", "copyUpdaterFileFromAssets error ", e);
            }
        }
    }

    private long getFreeMemorySize() {
        File internalStorageFile = getParentActivity().getFilesDir();
        return internalStorageFile.getFreeSpace();
    }

    private long calculateTelegramSize() {
        try {
            File file = getTelegramFile();
            if (file.exists()) {
                return file.length();
            }
        } catch (Exception ignored) {
        }
        return 10 * 1024 * 1024;
    }

    private long calculateZipSize() {
        File filesDir = getParentActivity().getFilesDir();
        long size = calculateDirSize(filesDir)
                + calculateDirSize(new File(filesDir.getParentFile(), "shared_prefs"));
        if (Build.VERSION.SDK_INT >= 24) {
            size *= 2;
            size += getTelegramFile().length();
        }
        return size;
    }

    private void installationFinished() {
        checkCopiedFile();
        Update30.deleteDataZip();
        File telegramFile = getTelegramFile();
        if (telegramFile.exists()) {
            telegramFile.delete();
        }
        if (zipFile != null && zipFile.exists()) {
            zipFile.delete();
        }
        setStep(Step.UNINSTALL_SELF);
    }

    private static long calculateDirSize(File dir) {
        if (!dir.exists()) {
            return 0;
        }
        long result = 0;
        File[] fileList = dir.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isDirectory()) {
                    result += calculateDirSize(file);
                } else {
                    result += file.length();
                }
            }
        }
        return result;
    }

    private File getTelegramFile() {
        return new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), "telegram.apk");
    }

    private boolean isTelegramFileDownloaded() {
        return messageObject != null
                && getTelegramFile().exists()
                && getTelegramFile().length() == messageObject.getDocument().size;
    }

    private boolean checkCopiedFile() {
        File copiedFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), "copied");
        if (copiedFile.exists()) {
            copiedFile.delete();
            return true;
        }
        return false;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        return themeDescriptions;
    }
}
