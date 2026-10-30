package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** The theme folders copied to the TV and the overlay one of them leaves behind. */
public class ThemeStoreTest {

    private static File dir(String prefix) throws IOException {
        return Files.createTempDirectory(prefix).toFile();
    }

    private static File file(File parent, String name, String text) throws IOException {
        File f = new File(parent, name);
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static List<String> names(List<File> files) {
        List<String> out = new ArrayList<>();
        for (File f : files) out.add(f.getName());
        return out;
    }

    private static List<String> listed(File folder) {
        String[] names = folder.list();
        List<String> out = names == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(names));
        java.util.Collections.sort(out);
        return out;
    }

    @Test
    public void themesAreTheFoldersWithSomethingToApply() throws IOException {
        File ext = dir("store");
        File themes = new File(ext, ThemeStore.DIR_NAME);
        file(new File(themes, "aurora"), "config.json", "{\"columns\":5}");
        file(new File(themes, "blocky"), "wallpaper.jpg", "jpg");
        file(new File(themes, "scenes"), "wallpapers/dusk.jpg", "jpg");
        file(new File(themes, "empty"), "README.md", "no payload");
        new File(themes, "empty2").mkdirs();
        file(new File(themes, "default"), "config.json", "{}");
        file(themes, "notes.txt", "not a folder");
        assertEquals(Arrays.asList("aurora", "blocky", "scenes"), names(ThemeStore.themes(ext)));
        assertTrue(ThemeStore.themes(null).isEmpty());
        assertTrue(ThemeStore.themes(dir("nothemes")).isEmpty());
    }

    @Test
    public void applyReplacesTheOverlayAndKeepsOnlyWhatTheLauncherReads() throws IOException {
        File ext = dir("apply");
        file(ext, "config.json", "old");
        file(ext, "wallpaper.jpg", "old-jpg");
        file(ext, "wallpapers/old.jpg", "old");
        file(ext, "wallpapers/captions.txt", "old");
        file(ext, "theme_revision", "1");
        file(ext, "theme_name", "old");
        File theme = new File(ext, ThemeStore.DIR_NAME + "/aurora");
        file(theme, "config.json", "new");
        file(theme, "wallpaper.jpg", "new-jpg");
        file(theme, "wallpapers/dusk.jpg", "dusk");
        file(theme, "wallpapers/captions.txt", "dusk caption");
        file(theme, "wallpapers/notes.txt", "not an image");
        file(theme, "screenshot.jpg", "preview");

        assertTrue(ThemeStore.apply(ext, theme));

        assertEquals("new", read(new File(ext, "config.json")));
        assertEquals("new-jpg", read(new File(ext, "wallpaper.jpg")));
        assertEquals(Arrays.asList("captions.txt", "dusk.jpg"), listed(new File(ext, "wallpapers")));
        assertFalse(new File(ext, "screenshot.jpg").exists());
        assertNotEquals("1", read(new File(ext, "theme_revision")));
        assertEquals("aurora", read(new File(ext, "theme_name")));
        assertEquals("aurora", ThemeStore.current(ext));
    }

    @Test
    public void resetRemovesTheOverlay() throws IOException {
        File ext = dir("reset");
        file(ext, "config.json", "old");
        file(ext, "wallpaper.jpg", "old-jpg");
        file(ext, "wallpapers/old.jpg", "old");
        file(ext, "wallpapers/captions.txt", "old");
        file(ext, "theme_revision", "1");
        file(ext, "theme_name", "old");

        assertTrue(ThemeStore.reset(ext));

        assertFalse(new File(ext, "config.json").exists());
        assertFalse(new File(ext, "wallpaper.jpg").exists());
        assertTrue(listed(new File(ext, "wallpapers")).isEmpty());
        assertFalse(new File(ext, "theme_revision").exists());
        assertFalse(new File(ext, "theme_name").exists());
        assertNull(ThemeStore.current(ext));
    }

    @Test
    public void currentFollowsTheConfigOnTheTV() throws IOException {
        File ext = dir("current");
        File aurora = new File(ext, ThemeStore.DIR_NAME + "/aurora");
        File blocky = new File(ext, ThemeStore.DIR_NAME + "/blocky");
        file(aurora, "config.json", "aurora");
        file(blocky, "config.json", "blocky");
        file(ext, "config.json", "aurora");
        assertEquals("aurora", ThemeStore.current(ext));

        // A theme.sh push replaces the file without touching the picker's marker.
        file(ext, "config.json", "blocky");
        assertEquals("blocky", ThemeStore.current(ext));

        // A hand-edited config matches no theme, so the row shows the built-in look.
        file(ext, "config.json", "edited by hand");
        assertNull(ThemeStore.current(ext));

        new File(ext, "config.json").delete();
        assertNull(ThemeStore.current(ext));
        assertNull(ThemeStore.current(null));
    }

    @Test
    public void equalConfigsAreToldApartByTheMarker() throws IOException {
        File ext = dir("equal");
        File aurora = new File(ext, ThemeStore.DIR_NAME + "/aurora");
        File blocky = new File(ext, ThemeStore.DIR_NAME + "/blocky");
        file(aurora, "config.json", "same");
        file(blocky, "config.json", "same");
        assertTrue(ThemeStore.apply(ext, blocky));
        assertEquals("blocky", ThemeStore.current(ext));
    }

    @Test
    public void aThemeWithOnlyAPictureStillApplies() throws IOException {
        File ext = dir("picture");
        file(ext, "config.json", "old");
        File theme = new File(ext, ThemeStore.DIR_NAME + "/picture");
        file(theme, "wallpaper.jpg", "jpg");

        assertTrue(ThemeStore.apply(ext, theme));

        assertFalse(new File(ext, "config.json").exists());
        assertEquals("jpg", read(new File(ext, "wallpaper.jpg")));
        assertNull(ThemeStore.current(ext));
    }

    @Test
    public void nothingToWriteIsAFailure() throws IOException {
        File ext = dir("missing");
        assertFalse(ThemeStore.apply(null, new File(ext, "themes/aurora")));
        assertFalse(ThemeStore.apply(ext, null));
        assertFalse(ThemeStore.apply(ext, new File(ext, "themes/gone")));
        assertFalse(ThemeStore.reset(null));
    }
}
