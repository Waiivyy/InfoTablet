package ba.infotablet.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Local SQLite database. Schema changes are additive only (never drop user tables in
 * onUpgrade), so reminders and settings survive app updates.
 */
public final class Db extends SQLiteOpenHelper {

    private static final String NAME = "infotablet.db";
    private static final int VERSION = 2;

    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) instance = new Db(ctx.getApplicationContext());
        return instance;
    }

    private Db(Context ctx) {
        super(ctx, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE source_state ("
                + "id TEXT PRIMARY KEY,"
                + "payload TEXT,"
                + "ok_at INTEGER NOT NULL DEFAULT 0,"
                + "attempt_at INTEGER NOT NULL DEFAULT 0,"
                + "fail_count INTEGER NOT NULL DEFAULT 0,"
                + "first_fail_at INTEGER NOT NULL DEFAULT 0,"
                + "error TEXT)");
        createReminders(db);
    }

    private static void createReminders(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE reminders ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "text TEXT NOT NULL,"
                + "year INTEGER NOT NULL, month INTEGER NOT NULL, day INTEGER NOT NULL,"
                + "hour INTEGER NOT NULL, minute INTEGER NOT NULL,"
                + "repeat INTEGER NOT NULL DEFAULT 0,"
                + "category TEXT NOT NULL DEFAULT '',"
                + "handled_until INTEGER NOT NULL DEFAULT 0,"
                + "snooze_for INTEGER NOT NULL DEFAULT 0,"
                + "snooze_until INTEGER NOT NULL DEFAULT 0,"
                + "rings INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Each step only adds tables or columns; existing data is always kept.
        if (oldVersion < 2) createReminders(db);
    }
}
