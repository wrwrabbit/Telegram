package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BadPasscodeAttempt;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BadPasscodeCell extends FrameLayout {

    private TextView typeView;
    private TextView fakePasscodeView;
    private TextView dateView;
    private ImageView frontPhoto;
    private ImageView backPhoto;
    private LinearLayout layout;

    public BadPasscodeCell(Context context) {
        super(context);

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        typeView = createTextView(context);
        typeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        layout.addView(typeView, lp);

        fakePasscodeView = createTextView(context);
        fakePasscodeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        layout.addView(fakePasscodeView, lp);

        dateView = createTextView(context);
        dateView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        layout.addView(dateView, lp);

        layout.addView(createPhotoLayout(context));

        addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 2, 21, 2));
    }

    private static TextView createTextView(Context context) {
        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        return textView;
    }

    private LinearLayout createPhotoLayout(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(200));
        lp.leftMargin = AndroidUtilities.dp(5);
        lp.rightMargin = AndroidUtilities.dp(5);

        frontPhoto = createImageView(context);
        layout.addView(frontPhoto, lp);

        backPhoto = createImageView(context);
        layout.addView(backPhoto, lp);
        return layout;
    }

    private static ImageView createImageView(Context context) {
        ImageView image = new ImageView(context);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return image;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50));
        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
        layout.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec( getMeasuredHeight(), MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), layout.getMeasuredHeight() + AndroidUtilities.dp(10));
    }

    public void setBadPasscodeAttempt(BadPasscodeAttempt badPasscodeAttempt) {
        typeView.setVisibility(VISIBLE);
        typeView.setText(badPasscodeAttempt.getTypeString());
        if (badPasscodeAttempt.isFakePasscode) {
            fakePasscodeView.setVisibility(VISIBLE);
            fakePasscodeView.setText(LocaleController.getString("FakePasscode", R.string.FakePasscode));
        } else {
            fakePasscodeView.setVisibility(GONE);
        }
        dateView.setVisibility(VISIBLE);
        dateView.setText(badPasscodeAttempt.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        bindPhoto(frontPhoto, badPasscodeAttempt.frontPhotoPath);
        bindPhoto(backPhoto, badPasscodeAttempt.backPhotoPath);
        setWillNotDraw(false);
    }

    private void bindPhoto(ImageView image, String path) {
        if (path != null) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                Bitmap bitmap = lessResolution(path);
                AndroidUtilities.runOnUIThread(() -> image.setImageBitmap(bitmap));
            });
            image.setVisibility(VISIBLE);
            image.setOnLongClickListener(view -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("SaveToGallery", R.string.SaveToGallery) + "?");
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                    saveFile(path);
                });
                builder.create().show();
                return true;
            });
        } else {
            image.setVisibility(GONE);
        }
    }

    public static Bitmap lessResolution(String filePath) {
        int reqHeight = 320;
        int reqWidth = 320;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = Math.min(heightRatio, widthRatio);
        }
        return inSampleSize;
    }

    public static void saveFile(String fullPath) {
        if (fullPath == null) {
            return;
        }

        File file = null;
        if (!TextUtils.isEmpty(fullPath)) {
            file = new File(fullPath);
            if (!file.exists()) {
                file = null;
            }
        }

        if (file == null) {
            return;
        }

        final File sourceFile = file;
        if (sourceFile.exists()) {
            new Thread(() -> {
                try {
                    Uri uriToInsert = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    File dirDest = new File(Environment.DIRECTORY_PICTURES, "Telegram");
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, dirDest + File.separator);
                    contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, sourceFile.getName());
                    String extension = MimeTypeMap.getFileExtensionFromUrl(sourceFile.getAbsolutePath());
                    String mimeType = null;
                    if (extension != null) {
                        mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    }
                    contentValues.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                    Uri dstUri = ApplicationLoader.applicationContext.getContentResolver().insert(uriToInsert, contentValues);
                    if (dstUri != null) {
                        FileInputStream fileInputStream = new FileInputStream(sourceFile);
                        OutputStream outputStream = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(dstUri);
                        AndroidUtilities.copyFile(fileInputStream, outputStream);
                        fileInputStream.close();
                        AndroidUtilities.addMediaToGallery(dstUri.getPath());
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }).start();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(isEnabled());
    }
}
