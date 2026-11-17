package ba.infotablet.alarm;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

/**
 * The alarm tone on the alarm stream at full volume, rising over the first seconds. The previous
 * alarm volume is restored afterwards. Falls back to a generated beep if no tone can be played.
 */
final class AlarmSound {

    private static final long RAMP_MS = 12_000L;
    private static final long STEP_MS = 400L;

    private final Context ctx;
    private final AudioManager audio;
    private final Handler handler = new Handler();
    private MediaPlayer player;
    private ToneGenerator tone;
    private int savedVolume = -1;
    private long startedAt;

    AlarmSound(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    boolean playing() {
        return player != null || tone != null;
    }

    void start() {
        if (playing()) return;
        savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM);
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        startedAt = System.currentTimeMillis();
        if (!startPlayer(RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM))
                && !startPlayer(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))) {
            tone = new ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME);
            handler.post(beep);
        }
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // already stopped
            }
            player.release();
            player = null;
        }
        if (tone != null) {
            tone.release();
            tone = null;
        }
        if (savedVolume >= 0) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0);
            savedVolume = -1;
        }
    }

    private boolean startPlayer(Uri uri) {
        if (uri == null) return false;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(ctx, uri);
            mp.setAudioStreamType(AudioManager.STREAM_ALARM);
            mp.setLooping(true);
            mp.prepare();
            mp.setVolume(0.3f, 0.3f);
            mp.start();
            player = mp;
            handler.postDelayed(ramp, STEP_MS);
            return true;
        } catch (Exception e) {
            Log.w("InfoTablet", "alarm: tone failed: " + e);
            mp.release();
            return false;
        }
    }

    private final Runnable ramp = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            float f = Math.min(1f, 0.3f + 0.7f * (System.currentTimeMillis() - startedAt) / RAMP_MS);
            player.setVolume(f, f);
            if (f < 1f) handler.postDelayed(this, STEP_MS);
        }
    };

    private final Runnable beep = new Runnable() {
        @Override public void run() {
            if (tone == null) return;
            tone.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 700);
            handler.postDelayed(this, 1500);
        }
    };
}
