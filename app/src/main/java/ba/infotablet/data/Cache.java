package ba.infotablet.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Read and write {@link SourceState} rows. Safe to call from any thread. */
public final class Cache {

    private Cache() {}

    public static SourceState get(Context ctx, String id) {
        SQLiteDatabase db = Db.get(ctx).getReadableDatabase();
        Cursor c = db.query("source_state",
                new String[] {"payload", "ok_at", "attempt_at", "fail_count", "first_fail_at", "error"},
                "id = ?", new String[] {id}, null, null, null);
        try {
            if (!c.moveToFirst()) return SourceState.empty(id);
            return new SourceState(id, c.getString(0), c.getLong(1), c.getLong(2), c.getInt(3), c.getLong(4), c.getString(5));
        } finally {
            c.close();
        }
    }

    public static void putOk(Context ctx, String id, String payload, long now) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("payload", payload);
        v.put("ok_at", now);
        v.put("attempt_at", now);
        v.put("fail_count", 0);
        v.put("first_fail_at", 0);
        v.putNull("error");
        Db.get(ctx).getWritableDatabase().insertWithOnConflict("source_state", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Records a failure and keeps the last good payload. */
    public static void putFail(Context ctx, String id, String error, long now) {
        SourceState old = get(ctx, id);
        ContentValues v = new ContentValues();
        v.put("id", id);
        if (old.payload != null) v.put("payload", old.payload); else v.putNull("payload");
        v.put("ok_at", old.okAt);
        v.put("attempt_at", now);
        v.put("fail_count", old.failCount + 1);
        v.put("first_fail_at", old.failCount == 0 ? now : old.firstFailAt);
        v.put("error", error == null ? "?" : (error.length() > 300 ? error.substring(0, 300) : error));
        Db.get(ctx).getWritableDatabase().insertWithOnConflict("source_state", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
