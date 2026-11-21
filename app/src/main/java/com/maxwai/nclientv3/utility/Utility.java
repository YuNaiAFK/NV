package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.maxwai.nclientv3.R;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.maxwai.nclientv3.settings.Global;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Random;

public class Utility {
    public static final Random RANDOM = new Random(System.nanoTime());
    public static final String ORIGINAL_URL = "nhentai.net";

    public static String getBaseUrl() {
        return "https://" + Utility.getHost() + "/";
    }

    public static String getApiBaseUrl() {
        return getBaseUrl() + "api/v2/";
    }

    public static String getHost() {
        return Global.getMirror();
    }

    private static void parseEscapedCharacter(Reader reader, Writer writer) throws IOException {
        int toCreate, read;
        switch (read = reader.read()) {
            case 'u':
                toCreate = 0;
                for (int i = 0; i < 4; i++) {
                    toCreate *= 16;
                    toCreate += Character.digit(reader.read(), 16);
                }
                writer.write(toCreate);
                break;
            case 'n':
                writer.write('\n');
                break;
            case 't':
                writer.write('\t');
                break;
            default:
                writer.write('\\');
                writer.write(read);
                break;
        }
    }

    @NonNull
    public static String unescapeUnicodeString(@Nullable String scriptHtml) {
        if (scriptHtml == null) return "";
        StringReader reader = new StringReader(scriptHtml);
        StringWriter writer = new StringWriter();
        int actualChar;
        try {
            while ((actualChar = reader.read()) != -1) {
                if (actualChar != '\\') writer.write(actualChar);
                else parseEscapedCharacter(reader, writer);
            }
        } catch (IOException ignore) {
            return "";
        }
        return writer.toString();
    }

    public static void threadSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            LogUtility.d("Unimportant sleep interrupted", e);
        }
    }

    public static void tintMenu(Context context, Menu menu) {
        int x = menu.size();
        for (int i = 0; i < x; i++) {
            MenuItem item = menu.getItem(i);
            Global.setTint(context, item.getIcon());
        }
    }

    @Nullable
    private static Bitmap drawableToBitmap(Drawable dra) {
        if (!(dra instanceof BitmapDrawable)) return null;
        return ((BitmapDrawable) dra).getBitmap();
    }

    public static boolean canSavePage(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || Global.hasStoragePermission(context);
    }

    public static void savePage(Context context, Drawable drawable, String name) {
        Bitmap bitmap = drawable instanceof GifDrawable gif ? gif.getFirstFrame() : drawableToBitmap(drawable);
        if (bitmap == null) {
            Toast.makeText(context, R.string.page_image_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(context, bitmap, name);
            } else {
                File folder = Global.SCREENFOLDER;
                if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create image directory");
                File file = new File(folder, name);
                try (OutputStream output = new FileOutputStream(file)) {
                    writeJpeg(bitmap, output);
                }
                MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()},
                    new String[]{"image/jpeg"}, null);
            }
            Toast.makeText(context, R.string.page_image_saved, Toast.LENGTH_SHORT).show();
        } catch (IOException | RuntimeException e) {
            LogUtility.e("Cannot save page image", e);
            Toast.makeText(context, R.string.page_image_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private static void saveToMediaStore(Context context, Bitmap bitmap, String name) throws IOException {
        var resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NVbook");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Cannot create MediaStore image");
        boolean saved = false;
        try {
            try (OutputStream output = resolver.openOutputStream(uri)) {
                if (output == null) throw new IOException("Cannot open MediaStore image");
                writeJpeg(bitmap, output);
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) throw new IOException("Cannot publish image");
            saved = true;
        } finally {
            if (!saved) resolver.delete(uri, null, null);
        }
    }

    private static void writeJpeg(Bitmap bitmap, OutputStream output) throws IOException {
        Bitmap software = bitmap.getConfig() == Bitmap.Config.HARDWARE
            ? bitmap.copy(Bitmap.Config.ARGB_8888, false) : bitmap;
        try {
            if (software == null || !software.compress(Bitmap.CompressFormat.JPEG, 95, output))
                throw new IOException("JPEG encoding failed");
        } finally {
            if (software != null && software != bitmap) software.recycle();
        }
    }

    private static void saveImage(@NonNull Bitmap bitmap, @NonNull File output) {
        try {
            if (!output.exists())
                //noinspection ResultOfMethodCallIgnored
                output.createNewFile();
            try (FileOutputStream ostream = new FileOutputStream(output)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, ostream);
                ostream.flush();
            }
        } catch (IOException e) {
            LogUtility.e(e.getLocalizedMessage(), e);
        }
    }

    public static long writeStreamToFile(InputStream inputStream, File filePath) throws IOException {
        try (inputStream;
             FileOutputStream outputStream = new FileOutputStream(filePath)) {
            int read;
            long totalByte = 0;
            byte[] bytes = new byte[1024];
            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
                totalByte += read;
            }
            outputStream.flush();
            return totalByte;
        }
    }

    public static void sendImage(Context context, Drawable drawable, String text) {
        context = context.getApplicationContext();
        try {
            File tempFile = File.createTempFile("toSend", ".jpg");
            tempFile.deleteOnExit();
            Bitmap image = drawableToBitmap(drawable);
            if (image == null) return;
            saveImage(image, tempFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            if (text != null) shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            Uri x = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", tempFile);
            shareIntent.putExtra(Intent.EXTRA_STREAM, x);
            shareIntent.setType("image/jpeg");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, x, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            shareIntent = Intent.createChooser(shareIntent, context.getString(R.string.share_with));
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(shareIntent);
        } catch (IOException e) {
            LogUtility.e("Error creating temp file", e);
            Toast.makeText(context, R.string.send_image_error, Toast.LENGTH_SHORT).show();
        }

    }

}
