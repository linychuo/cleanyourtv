package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * The typeface behind one of the font_* strings in res/values/fonts.xml, for views built in code. The string
 * points at a bundled font, or, in locales Outfit can't draw (values-vi), names a system family (#833).
 */
final class UiFont {
    private static Typeface bundled;

    private UiFont() {}

    static Typeface get(Context context, int fontString) {
        Resources res = context.getResources();
        TypedValue value = new TypedValue();
        res.getValue(fontString, value, true);
        String name = String.valueOf(value.string);
        if (name.startsWith("res/")) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                return res.getFont(value.resourceId);
            }
            return bundled(context);
        }
        return Typeface.create(name, Typeface.NORMAL);
    }

    /** res/font arrived in Android 8.0; before it, load the bundled TTF from the packaged file once. */
    @SuppressLint("ResourceType")   // R.font.outfit is a file resource; openRawResource reads its bytes.
    private static Typeface bundled(Context context) {
        if (bundled != null) return bundled;
        File file = new File(context.getCacheDir(), "outfit.ttf");
        try {
            if (!file.isFile() || file.length() == 0) {
                try (InputStream in = context.getResources().openRawResource(R.font.outfit);
                     FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                }
            }
            bundled = Typeface.createFromFile(file);
        } catch (Exception e) {
            bundled = Typeface.SANS_SERIF;
        }
        return bundled;
    }
}
