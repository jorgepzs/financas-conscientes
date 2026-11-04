package br.com.financasconscientes.reminderexperiment;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.util.Locale;

/** Reads only share MIME type and a temporary display name; never opens or stores shared content. */
final class ReceiptClassifier {
    private ReceiptClassifier() { }

    static boolean isCsv(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return false;
        String mimeType = normalize(intent.getType());
        if (mimeType.equals("text/csv")
                || mimeType.equals("text/comma-separated-values")
                || mimeType.equals("application/csv")
                || mimeType.equals("application/vnd.ms-excel")) {
            return true;
        }

        Uri sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        if (sharedUri == null) {
            ClipData clipData = intent.getClipData();
            if (clipData != null && clipData.getItemCount() > 0) {
                sharedUri = clipData.getItemAt(0).getUri();
            }
        }
        String displayName = queryDisplayName(context.getContentResolver(), sharedUri);
        return displayName != null && displayName.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private static String normalize(String mimeType) {
        return mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        if (uri == null) return null;
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                return index >= 0 ? cursor.getString(index) : null;
            }
        } catch (RuntimeException ignored) {
            // Some share providers expose no metadata; no shared bytes are opened as a fallback.
        }
        return null;
    }
}
