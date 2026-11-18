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
    private static final int VERSION = 3;

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
        createReminderLog(db);
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
                + "updated_at INTEGER NOT NULL DEFAULT 0,"
                + "ring INTEGER NOT NULL DEFAULT 1,"
                + "all_day INTEGER NOT NULL DEFAULT 0)");
    }

    /** What happened to each ring: done, snoozed or missed (kept 60 days, for the history view). */
    private static void createReminderLog(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE reminder_log ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "reminder_id INTEGER NOT NULL,"
                + "due_at INTEGER NOT NULL,"
                + "text TEXT NOT NULL,"
                + "category TEXT NOT NULL DEFAULT '',"
                + "kind TEXT NOT NULL,"
                + "at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX reminder_log_due ON reminder_log (due_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Each step only adds tables or columns; existing data is always kept.
        if (oldVersion < 2) createReminders(db);
        if (oldVersion == 2) {
            // Plans: entries that do not ring, and whole-day entries (version 3).
            db.execSQL("ALTER TABLE reminders ADD COLUMN ring INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE reminders ADD COLUMN all_day INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) createReminderLog(db);
    }
}
