package com.etherealnymph.courseflow;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public final class AttachmentStore {

    private AttachmentStore() {}

    public static File dir(Context context) {
        File dir = new File(context.getFilesDir(), "attachments");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public static Store.Attachment copyImage(Context context, Uri uri) throws Exception {
        String mime = context.getContentResolver().getType(uri);
        if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";
        String name = displayName(context, uri);
        if (name == null || name.isEmpty()) name = "图片";
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (ext == null) ext = "jpg";
        File dest = new File(dir(context), "img_" + System.currentTimeMillis() + "." + ext);
        copy(context, uri, dest);
        Store.Attachment a = new Store.Attachment();
        a.id = Store.newId("att");
        a.type = "image";
        a.name = name;
        a.path = dest.getAbsolutePath();
        a.mime = mime;
        a.size = dest.length();
        return a;
    }

    public static Store.Attachment fileShortcut(Context context, Uri uri) {
        Store.Attachment a = new Store.Attachment();
        a.id = Store.newId("att");
        a.type = "file";
        String name = displayName(context, uri);
        a.name = name == null || name.isEmpty() ? "文件" : name;
        a.uri = uri.toString();
        a.mime = context.getContentResolver().getType(uri);
        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        return a;
    }

    public static Bitmap decodeImage(String path, int maxWidth, int maxHeight) {
        if (path == null || path.isEmpty()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxWidth
                && bounds.outHeight / (sample * 2) >= maxHeight) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, options);
    }

    public static void openFile(Context context, Store.Attachment attachment) {
        if (attachment == null || attachment.uri.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(attachment.uri), attachment.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(context, "没有可以打开这个文件的应用", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void copy(Context context, Uri uri, File dest) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = resolver.openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new Exception("无法读取图片");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        }
    }

    public static String typeLabel(Store.Attachment a) {
        if (a == null) return "";
        if ("image".equals(a.type)) return "图片";
        if ("audio".equals(a.type)) return "录音";
        String mime = a.mime == null ? "" : a.mime.toLowerCase(Locale.ROOT);
        if (mime.startsWith("video/")) return "视频";
        if (mime.startsWith("audio/")) return "音频";
        return "文件";
    }
}
