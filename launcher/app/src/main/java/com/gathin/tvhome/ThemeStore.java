package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The theme folders copied to the TV under Config.dir/themes, and applying one of them to the files Home reads.
 *
 * The folders are ordinary copies of themes/<name> from the repository: config.json, wallpaper.jpg and a
 * wallpapers/ collection. tools/theme.sh still pushes a single theme; this store only lists what is there and
 * copies one folder over the live overlay, so a TV with the folder copied once switches themes with the remote
 * and no ADB. Nothing here is read by Config: the overlay it writes is the same one theme.sh writes.
 */
final class ThemeStore {
    static final String DIR_NAME = "themes";
    /** The picker's memory of the last folder it applied; theme.sh does not touch it. */
    private static final String NAME_MARKER = "theme_name";
    /** Written with a new value on every apply, so Config clears the on-TV layout choices. */
    private static final String REVISION = "theme_revision";
    /** themes/default is the built-in look written out as JSON, offered as the "Built-in default" row instead. */
    private static final String TEMPLATE = "default";

    private ThemeStore() {
    }

    /** The theme folders on the TV that have something to apply, by name; empty when none were copied. */
    static List<File> themes(File externalDir) {
        File folder = externalDir == null ? null : new File(externalDir, DIR_NAME);
        File[] entries = folder == null ? null : folder.listFiles();
        List<File> out = new ArrayList<>();
        if (entries == null) return out;
        for (File entry : entries) {
            if (entry.isDirectory() && !entry.getName().startsWith(".") && !TEMPLATE.equals(entry.getName())
                    && hasPayload(entry)) {
                out.add(entry);
            }
        }
        Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    /** The theme whose config.json is the one on the TV, or null for the built-in look (or an unknown file). */
    static String current(File externalDir) {
        File config = externalDir == null ? null : new File(externalDir, Config.FILE_NAME);
        if (config == null || !config.isFile()) return null;
        String marker = read(new File(externalDir, NAME_MARKER));
        String first = null;
        for (File theme : themes(externalDir)) {
            if (!same(config, new File(theme, Config.FILE_NAME))) continue;
            if (theme.getName().equals(marker)) return marker;
            if (first == null) first = theme.getName();
        }
        return first;
    }

    /** Copies one theme folder over the live overlay; false when a file could not be written. */
    static boolean apply(File externalDir, File theme) {
        if (externalDir == null || theme == null || !theme.isDirectory()) return false;
        if (!externalDir.isDirectory() && !externalDir.mkdirs()) return false;
        File wallpapers = new File(externalDir, ThemeFiles.WALLPAPERS);
        if (!clear(wallpapers)) return false;
        File source = new File(theme, ThemeFiles.WALLPAPERS);
        File[] images = source.listFiles();
        if (images != null) {
            for (File image : images) {
                if (!image.isFile()) continue;
                String name = image.getName();
                if (!ThemeFiles.collectionImage(name) && !WallpaperCaptions.FILE_NAME.equals(name)) continue;
                if (!copy(image, new File(wallpapers, name))) return false;
            }
        }
        File single = new File(externalDir, Config.WALLPAPER_NAME);
        if (!delete(single)) return false;
        File sourceSingle = new File(theme, Config.WALLPAPER_NAME);
        if (sourceSingle.isFile() && !copy(sourceSingle, single)) return false;
        // The config goes last, through a rename: a copy that fails halfway leaves the old theme in place.
        File config = new File(externalDir, Config.FILE_NAME);
        File sourceConfig = new File(theme, Config.FILE_NAME);
        if (sourceConfig.isFile()) {
            if (!replace(sourceConfig, config)) return false;
        } else if (!delete(config)) {
            return false;
        }
        if (!write(new File(externalDir, REVISION), Long.toString(System.currentTimeMillis()))) return false;
        return write(new File(externalDir, NAME_MARKER), theme.getName());
    }

    /** Removes the overlay, back to the built-in config.xml; false when a file could not be deleted. */
    static boolean reset(File externalDir) {
        if (externalDir == null) return false;
        boolean ok = delete(new File(externalDir, Config.FILE_NAME));
        ok &= delete(new File(externalDir, Config.WALLPAPER_NAME));
        ok &= clear(new File(externalDir, ThemeFiles.WALLPAPERS));
        ok &= delete(new File(externalDir, REVISION));
        ok &= delete(new File(externalDir, NAME_MARKER));
        return ok;
    }

    private static boolean hasPayload(File theme) {
        if (new File(theme, Config.FILE_NAME).isFile()) return true;
        if (new File(theme, Config.WALLPAPER_NAME).isFile()) return true;
        File[] images = new File(theme, ThemeFiles.WALLPAPERS).listFiles();
        if (images == null) return false;
        for (File image : images) {
            if (image.isFile() && ThemeFiles.collectionImage(image.getName())) return true;
        }
        return false;
    }

    /** True when both files hold the same bytes; config.json is small enough to compare whole. */
    private static boolean same(File a, File b) {
        if (!a.isFile() || !b.isFile() || a.length() != b.length() || a.length() > 64 * 1024) return false;
        try {
            return Arrays.equals(bytes(a), bytes(b));
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] bytes(File file) throws IOException {
        byte[] buffer = new byte[(int) file.length()];
        try (InputStream in = new FileInputStream(file)) {
            int read = 0, n;
            while (read < buffer.length && (n = in.read(buffer, read, buffer.length - read)) > 0) read += n;
            return read == buffer.length ? buffer : Arrays.copyOf(buffer, read);
        }
    }

    private static boolean copy(File source, File dest) {
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Writes the file beside its destination, then renames it over: a half-written config never goes live. */
    private static boolean replace(File source, File dest) {
        File temp = new File(dest.getParentFile(), dest.getName() + ".new");
        if (!copy(source, temp)) {
            delete(temp);
            return false;
        }
        if (!delete(dest)) {
            delete(temp);
            return false;
        }
        if (!temp.renameTo(dest)) {
            delete(temp);
            return false;
        }
        return true;
    }

    /** Deletes the files directly inside a folder (the wallpapers collection), leaving the folder. */
    private static boolean clear(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return true;
        boolean ok = true;
        for (File file : files) {
            if (file.isFile() && !delete(file)) ok = false;
        }
        return ok;
    }

    private static boolean delete(File file) {
        return !file.exists() || file.delete();
    }

    private static boolean write(File file, String text) {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String read(File file) {
        if (file == null || !file.isFile() || file.length() > 128) return null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = 0, n;
            while (read < buffer.length && (n = in.read(buffer, read, buffer.length - read)) > 0) read += n;
            return new String(buffer, 0, read, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }
}
