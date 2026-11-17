package ba.infotablet.update;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Android 7 and newer install only from content:// addresses. This provider hands the system
 * installer the waiting update and nothing else: it is not exported, and access is granted for
 * the one install request. Android 4.x does not use it.
 */
public final class ApkProvider extends ContentProvider {

    static Uri uri(Context c, String name) {
        return Uri.parse("content://" + c.getPackageName() + ".apk/" + Uri.encode(name));
    }

    private File file(Uri uri) throws FileNotFoundException {
        Context c = getContext();
        File f = c == null ? null : UpdateState.of(c).readyFile(c);
        if (f == null || !f.getName().equals(uri.getLastPathSegment())) throw new FileNotFoundException(String.valueOf(uri));
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return Updater.MIME;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f;
        try {
            f = file(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        MatrixCursor m = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        m.addRow(new Object[] {f.getName(), f.length()});
        return m;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
