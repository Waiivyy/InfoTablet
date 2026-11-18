package ba.infotablet.reminders;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import ba.infotablet.alarm.AlarmScheduler;
import ba.infotablet.data.Db;
import ba.infotablet.sync.Scheduler;

/**
 * Reminder rows in SQLite (kept across reboots and app updates). Every write reschedules the alarm
 * and refreshes the visible screen. Safe to call from any thread.
 */
public final class ReminderStore {

    private static final String[] COLUMNS = {"id", "text", "year", "month", "day", "hour", "minute", "repeat", "category",
            "handled_until", "snooze_for", "snooze_until", "rings", "created_at", "updated_at", "ring", "all_day"};

    /** Bumped on every change, so screens can tell when to rebuild. */
    private static volatile int version;

    private ReminderStore() {}

    public static int version() {
        return version;
    }

    public static List<Reminder> all(Context ctx) {
        List<Reminder> out = new ArrayList<Reminder>();
        Cursor c = Db.get(ctx).getReadableDatabase().query("reminders", COLUMNS, null, null, null, null, "id");
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public static Reminder get(Context ctx, long id) {
        Cursor c = Db.get(ctx).getReadableDatabase().query("reminders", COLUMNS, "id = ?", new String[] {String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    /**
     * Inserts or updates a reminder from the editor. Saving counts as handling everything up to now,
     * so an edited reminder never rings for a time that has already passed.
     */
    public static long save(Context ctx, Reminder r, long now) {
        r.handledUntil = now;
        r.snoozeFor = 0;
        r.snoozeUntil = 0;
        r.rings = 0;
        r.updatedAt = now;
        SQLiteDatabase db = Db.get(ctx).getWritableDatabase();
        if (r.id == 0) {
            r.createdAt = now;
            r.id = db.insertOrThrow("reminders", null, values(r));
        } else {
            db.update("reminders", values(r), "id = ?", new String[] {String.valueOf(r.id)});
        }
        changed(ctx);
        return r.id;
    }

    /** Puts back a reminder exactly as given (import), keeping its state. */
    public static void restore(Context ctx, Reminder r) {
        ContentValues v = values(r);
        v.remove("id");
        r.id = Db.get(ctx).getWritableDatabase().insertOrThrow("reminders", null, v);
        changed(ctx);
    }

    public static void delete(Context ctx, long id) {
        Db.get(ctx).getWritableDatabase().delete("reminders", "id = ?", new String[] {String.valueOf(id)});
        changed(ctx);
    }

    /** "Gotovo": everything up to now is handled. */
    public static void done(Context ctx, long id, long now) {
        Reminder r = get(ctx, id);
        if (r != null) {
            long due = Agenda.due(r, now);
            if (due != Agenda.NONE) log(ctx, r, due, History.DONE, now);
        }
        ContentValues v = new ContentValues();
        v.put("handled_until", now);
        v.put("snooze_for", 0);
        v.put("snooze_until", 0);
        v.put("rings", 0);
        update(ctx, id, v);
    }

    /** "Odgodi 10 min" pressed: rings again at until. */
    public static void snooze(Context ctx, long id, long dueAt, long until) {
        Reminder r = get(ctx, id);
        if (r != null) log(ctx, r, dueAt, History.SNOOZE, ba.infotablet.util.Clock.now());
        ContentValues v = new ContentValues();
        v.put("snooze_for", dueAt);
        v.put("snooze_until", until);
        v.put("rings", 0);
        update(ctx, id, v);
    }

    /** A ring ended without an answer: snooze, or go quiet after MAX_RINGS (then it shows as missed). */
    public static void unanswered(Context ctx, long id, long dueAt, long now) {
        Reminder r = get(ctx, id);
        if (r == null) return;
        int rings = (r.snoozeFor == dueAt ? r.rings : 0) + 1;
        ContentValues v = new ContentValues();
        v.put("snooze_for", dueAt);
        v.put("snooze_until", rings >= Agenda.MAX_RINGS ? Long.MAX_VALUE : now + Agenda.SNOOZE_MS);
        v.put("rings", rings);
        update(ctx, id, v);
        if (rings >= Agenda.MAX_RINGS) log(ctx, r, dueAt, History.MISSED, now);
    }

    /** Drops one-time reminders that finished more than 30 days ago. Returns how many were removed. */
    public static int cleanup(Context ctx, long now) {
        int n = 0;
        for (Reminder r : all(ctx)) {
            if (Agenda.finished(r, now) && now - Recurrence.occurrence(r, 0) > Agenda.KEEP_FINISHED_MS) {
                Db.get(ctx).getWritableDatabase().delete("reminders", "id = ?", new String[] {String.valueOf(r.id)});
                n++;
            }
        }
        if (n > 0) version++;
        Db.get(ctx).getWritableDatabase().delete("reminder_log", "at < ?", new String[] {String.valueOf(now - History.KEEP_MS)});
        return n;
    }

    // ---- history ----

    private static void log(Context ctx, Reminder r, long dueAt, String kind, long at) {
        ContentValues v = new ContentValues();
        v.put("reminder_id", r.id);
        v.put("due_at", dueAt);
        v.put("text", r.text);
        v.put("category", r.category);
        v.put("kind", kind);
        v.put("at", at);
        Db.get(ctx).getWritableDatabase().insert("reminder_log", null, v);
    }

    /** Rings due since the given time, newest first. */
    public static List<History.Row> history(Context ctx, long since) {
        List<History.Event> events = new ArrayList<History.Event>();
        Cursor c = Db.get(ctx).getReadableDatabase().query("reminder_log",
                new String[] {"reminder_id", "due_at", "text", "category", "kind", "at"},
                "due_at >= ?", new String[] {String.valueOf(since)}, null, null, "at");
        try {
            while (c.moveToNext()) {
                events.add(new History.Event(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5)));
            }
        } finally {
            c.close();
        }
        return History.rows(events);
    }

    private static void update(Context ctx, long id, ContentValues v) {
        Db.get(ctx).getWritableDatabase().update("reminders", v, "id = ?", new String[] {String.valueOf(id)});
        changed(ctx);
    }

    private static void changed(Context ctx) {
        version++;
        AlarmScheduler.reschedule(ctx);
        Scheduler.get(ctx).notifyChanged();
    }

    private static ContentValues values(Reminder r) {
        ContentValues v = new ContentValues();
        if (r.id != 0) v.put("id", r.id);
        v.put("text", r.text);
        v.put("year", r.year);
        v.put("month", r.month);
        v.put("day", r.day);
        v.put("hour", r.hour);
        v.put("minute", r.minute);
        v.put("repeat", r.repeat);
        v.put("category", r.category);
        v.put("handled_until", r.handledUntil);
        v.put("snooze_for", r.snoozeFor);
        v.put("snooze_until", r.snoozeUntil);
        v.put("rings", r.rings);
        v.put("created_at", r.createdAt);
        v.put("updated_at", r.updatedAt);
        v.put("ring", r.ring ? 1 : 0);
        v.put("all_day", r.allDay ? 1 : 0);
        return v;
    }

    private static Reminder read(Cursor c) {
        Reminder r = new Reminder();
        r.id = c.getLong(0);
        r.text = c.getString(1);
        r.year = c.getInt(2);
        r.month = c.getInt(3);
        r.day = c.getInt(4);
        r.hour = c.getInt(5);
        r.minute = c.getInt(6);
        r.repeat = c.getInt(7);
        r.category = c.getString(8);
        r.handledUntil = c.getLong(9);
        r.snoozeFor = c.getLong(10);
        r.snoozeUntil = c.getLong(11);
        r.rings = c.getInt(12);
        r.createdAt = c.getLong(13);
        r.updatedAt = c.getLong(14);
        r.ring = c.getInt(15) != 0;
        r.allDay = c.getInt(16) != 0;
        return r;
    }
}
