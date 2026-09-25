package com.mitype.blur.core;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 按键音：从模块 APK 解出 wav，SoundPool 播放。
 * 仅在播放成功时让调用方 skip 原生 playSoundEffect；失败则回落系统音。
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
        if (!ensureReady(am)) return false;
        int id;
        if (effect == FX_TEXT_DELETE) {
            id = sDelete;
        } else if (effect == FX_RETURN) {
            id = sAction;
        } else {
            id = sClick;
        }
        if (id <= 0 || sPool == null) return false;
        try {
            int stream = sPool.play(id, 1f, 1f, 1, 0, 1f);
            return stream != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean ensureReady(AudioManager am) {
        if (sReady) return true;
        if (sFailed) return false;
        synchronized (LOCK) {
            if (sReady) return true;
            if (sFailed) return false;
            try {
                Context ctx = audioContext(am);
                File dir;
                if (ctx != null) {
                    dir = new File(ctx.getCacheDir(), "mitype_key_sound");
                } else {
                    dir = new File(System.getProperty("java.io.tmpdir", "/data/local/tmp"),
                            "mitype_key_sound");
                }
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
                pool.setOnLoadCompleteListener((soundPool, sampleId, status) -> latch.countDown());

                sPool = pool;
                sClick = loadOne(dir, "key_press_click.wav", pool);
                sDelete = loadOne(dir, "key_press_delete.wav", pool);
                sAction = loadOne(dir, "key_press_action.wav", pool);

                if (sClick <= 0) {
                    sFailed = true;
                    return false;
                }
                // 等加载完成（最多 800ms）；即使超时也先允许 play，避免永久无声
                latch.await(800, TimeUnit.MILLISECONDS);
                sReady = true;
                return true;
            } catch (Throwable t) {
                sFailed = true;
                return false;
            }
        }
    }

    private static int loadOne(File dir, String name, SoundPool pool) {
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
            if (!out.exists() || out.length() == 0) return 0;
            return pool.load(out.getAbsolutePath(), 1);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Context audioContext(AudioManager am) {
        if (am == null) return null;
        try {
            // AOSP: AudioManager.mContext
            Field f = AudioManager.class.getDeclaredField("mContext");
            f.setAccessible(true);
            Object o = f.get(am);
            if (o instanceof Context) return (Context) o;
        } catch (Throwable ignored) {
        }
        try {
            Field f = AudioManager.class.getDeclaredField("mContext");
            f.setAccessible(true);
            Object o = f.get(am);
            if (o instanceof Context) return (Context) o;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 从本模块 APK（代码源 zip）读条目；多种 CodeSource 形态兜底。 */
    private static byte[] readFromModuleApk(String entry) {
        for (String path : moduleApkCandidates()) {
            if (path == null || path.isEmpty()) continue;
            try {
                String p = path;
                if (p.startsWith("jar:")) {
                    // jar:file:/x.apk!/ → file:/x.apk
                    int i = p.indexOf("file:");
                    int j = p.indexOf("!/");
                    if (i >= 0 && j > i) p = p.substring(i, j);
                }
                if (p.startsWith("file:")) {
                    p = new File(URI.create(p)).getAbsolutePath();
                }
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(p)) {
                    java.util.zip.ZipEntry e = zip.getEntry(entry);
                    if (e == null) continue;
                    try (InputStream in = zip.getInputStream(e)) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                        if (bos.size() > 0) return bos.toByteArray();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String[] moduleApkCandidates() {
        String[] out = new String[4];
        int n = 0;
        try {
            java.security.CodeSource cs =
                    KeySoundPlayer.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                URI uri = cs.getLocation().toURI();
                if (uri.getScheme() == null || "file".equalsIgnoreCase(uri.getScheme())) {
                    out[n++] = new File(uri).getAbsolutePath();
                } else {
                    out[n++] = uri.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            java.net.URL url = KeySoundPlayer.class.getResource("/assets/sound/key_press_click.wav");
            if (url != null) out[n++] = url.getPath();
        } catch (Throwable ignored) {
        }
        try {
            ClassLoader cl = KeySoundPlayer.class.getClassLoader();
            if (cl != null) {
                // LSPosed/PathClassLoader.path 多为非 public，用 getDeclaredField
                try {
                    Field path = cl.getClass().getDeclaredField("path");
                    path.setAccessible(true);
                    Object v = path.get(cl);
                    if (v instanceof String && ((String) v).contains(".apk")) {
                        out[n++] = (String) v;
                    }
                } catch (Throwable ignored) {
                }
                try {
                    Field path = ClassLoader.class.getDeclaredField("path");
                    path.setAccessible(true);
                    Object v = path.get(cl);
                    if (v instanceof String && ((String) v).contains(".apk")) {
                        out[n++] = (String) v;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        String[] r = new String[n];
        System.arraycopy(out, 0, r, 0, n);
        return r;
    }
}
