package com.mitype.blur.core;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 按键音：wav 以 Base64 内嵌（EmbeddedSounds），解码到缓存后 SoundPool 播放。
 * 不依赖模块 APK 路径；播成功才允许调用方 skip 原生音。
 */
public final class KeySoundPlayer {

    public static final int FX_KEYPRESS = 0;
    public static final int FX_RETURN = 2;
    public static final int FX_TEXT_DELETE = 5;

    private static final Object LOCK = new Object();
    private static volatile boolean sReady;
    private static volatile boolean sFailed;
    private static SoundPool sPool;
    private static int sClick;
    private static int sDelete;
    private static int sAction;

    private KeySoundPlayer() {
    }

    /** 返回 true=已接管播放；false=未就绪/失败，应保留原生音。 */
    public static boolean playEffect(int effect, AudioManager am) {
        if (!ensureReady()) return false;
        if (effect == FX_TEXT_DELETE) return playId(sDelete);
        if (effect == FX_RETURN) return playId(sAction);
        return playId(sClick);
    }

    /** 按 t9.a 枚举名选音。 */
    public static boolean playEvent(String enumName, AudioManager am) {
        if (!ensureReady()) return false;
        String n = enumName == null ? "" : enumName;
        if (n.contains("ERROR")) return playId(sDelete);
        if (n.contains("SUCCESS") || n.contains("BUTTON")) return playId(sAction);
        return playId(sClick);
    }

    private static boolean playId(int id) {
        if (id <= 0 || sPool == null) return false;
        try {
            return sPool.play(id, 1f, 1f, 1, 0, 1f) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean ensureReady() {
        if (sReady) return true;
        if (sFailed) return false;
        synchronized (LOCK) {
            if (sReady) return true;
            if (sFailed) return false;
            try {
                File dir = new File(System.getProperty("java.io.tmpdir", "/data/local/tmp"),
                        "mitype_key_sound");
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    sFailed = true;
                    return false;
                }
                CountDownLatch latch = new CountDownLatch(3);
                SoundPool pool = new SoundPool.Builder()
                        .setMaxStreams(3)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .build();
                pool.setOnLoadCompleteListener((sp, sampleId, status) -> latch.countDown());
                sPool = pool;
                sClick = loadB64(pool, dir, "click.wav", EmbeddedSounds.CLICK);
                sDelete = loadB64(pool, dir, "delete.wav", EmbeddedSounds.DELETE);
                sAction = loadB64(pool, dir, "action.wav", EmbeddedSounds.ACTION);
                if (sClick <= 0) {
                    sFailed = true;
                    return false;
                }
                latch.await(800, TimeUnit.MILLISECONDS);
                sReady = true;
                return true;
            } catch (Throwable t) {
                sFailed = true;
                return false;
            }
        }
    }

    private static int loadB64(SoundPool pool, File dir, String name, String b64) {
        try {
            File out = new File(dir, name);
            if (!out.exists() || out.length() == 0) {
                byte[] data = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
                try (OutputStream os = new FileOutputStream(out)) {
                    os.write(data);
                }
            }
            if (!out.exists() || out.length() == 0) return 0;
            return pool.load(out.getAbsolutePath(), 1);
        } catch (Throwable t) {
            return 0;
        }
    }
}
