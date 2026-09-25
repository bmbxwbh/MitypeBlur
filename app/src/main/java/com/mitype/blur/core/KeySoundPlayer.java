package com.mitype.blur.core;

import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 按键音：从模块 APK 解出 assets/sound/*.wav，用 SoundPool 播放。
 * 钩在 IME 进程 AudioManager.playSoundEffect 上，替换系统按键音。
 */
public final class KeySoundPlayer {

    /** Android SoundEffectConstants。 */
    public static final int FX_KEYPRESS = 0;
    public static final int FX_RETURN = 2;
    public static final int FX_TEXT_DELETE = 5;

    private static final AtomicBoolean sLoading = new AtomicBoolean(false);
    private static volatile boolean sReady;
    private static volatile boolean sFailed;
    private static SoundPool sPool;
    private static int sClick;
    private static int sDelete;
    private static int sAction;

    private KeySoundPlayer() {
    }

    public static void playEffect(int effect) {
        if (!ensureReady()) return;
        int id;
        if (effect == FX_TEXT_DELETE) {
            id = sDelete;
        } else if (effect == FX_RETURN) {
            id = sAction;
        } else {
            id = sClick;
        }
        if (id <= 0 || sPool == null) return;
        try {
            sPool.play(id, 1f, 1f, 1, 0, 1f);
        } catch (Throwable ignored) {
        }
    }

    private static synchronized boolean ensureReady() {
        if (sReady) return true;
        if (sFailed) return false;
        try {
            File dir = new File(System.getProperty("java.io.tmpdir", "/data/local/tmp"),
                    "mitype_key_sound");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                sFailed = true;
                return false;
            }
            if (sPool == null) {
                sPool = new SoundPool.Builder()
                        .setMaxStreams(3)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .build();
            }
            sClick = loadOne(dir, "key_press_click.wav");
            sDelete = loadOne(dir, "key_press_delete.wav");
            sAction = loadOne(dir, "key_press_action.wav");
            if (sClick <= 0) {
                sFailed = true;
                return false;
            }
            sReady = true;
            return true;
        } catch (Throwable t) {
            sFailed = true;
            return false;
        }
    }

    private static int loadOne(File dir, String name) {
        try {
            File out = new File(dir, name);
            if (!out.exists() || out.length() == 0) {
                byte[] data = readFromModuleApk("assets/sound/" + name);
                if (data == null) data = readFromModuleApk("sound/" + name);
                if (data == null || data.length == 0) return 0;
                try (OutputStream os = new FileOutputStream(out)) {
                    os.write(data);
                }
            }
            // SoundPool 无 load(File,int)；用 path 字符串 API
            return sPool.load(out.getAbsolutePath(), 1);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 从本模块 APK（代码源 zip）读条目。 */
    private static byte[] readFromModuleApk(String entry) {
        try {
            URI uri = KeySoundPlayer.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            String path;
            if (uri.getScheme() == null || "file".equalsIgnoreCase(uri.getScheme())) {
                path = new File(uri).getAbsolutePath();
            } else {
                path = uri.toString();
            }
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(path)) {
                java.util.zip.ZipEntry e = zip.getEntry(entry);
                if (e == null) return null;
                try (InputStream in = zip.getInputStream(e)) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    return bos.toByteArray();
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
