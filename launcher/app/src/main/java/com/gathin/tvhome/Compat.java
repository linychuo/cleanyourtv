package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.os.UserManager;
import android.view.View;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * API 21 fallbacks for calls the launcher makes directly on Android 8+ TVs.
 *
 * Local Android 5.0 build only: upstream supports API 26 and newer and does not carry these. Every
 * method here is a no-op or a simpler equivalent below the API level that introduced the call, so
 * the same source still runs unchanged on a modern TV.
 */
final class Compat {
    private Compat() {
    }

    /** Context.getSystemService(Class) arrived in Android 6.0; fall back to the string service name. */
    static <T> T service(Context context, Class<T> type, String name) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return context.getSystemService(type);
        Object found = context.getSystemService(name);
        return type.isInstance(found) ? type.cast(found) : null;
    }

    /** Direct boot arrived in Android 7.0; before it the one user is always unlocked. */
    static boolean isUserUnlocked(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true;
        UserManager users = context.getSystemService(UserManager.class);
        return users == null || users.isUserUnlocked();
    }

    /** Runtime permissions arrived in Android 6.0; before it every manifest permission is granted. */
    static boolean hasPermission(Context context, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkPermission(permission, Process.myPid(), Process.myUid())
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Context.getColorStateList arrived in Android 6.0. */
    static ColorStateList colorStateList(Context context, int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return context.getColorStateList(id);
        return context.getResources().getColorStateList(id);
    }

    /** Configuration.getLocales arrived in Android 7.0. */
    static Locale locale(Configuration config) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return config.getLocales().get(0);
        return config.locale;
    }

    /** TextView.setCompoundDrawableTintList arrived in Android 6.0; tint the drawables themselves before it. */
    static void setCompoundDrawableTintList(TextView view, ColorStateList tint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.setCompoundDrawableTintList(tint);
            return;
        }
        for (Drawable drawable : view.getCompoundDrawablesRelative()) {
            if (drawable == null) continue;
            drawable.mutate().setTintList(tint);
        }
    }

    static ColorStateList getCompoundDrawableTintList(TextView view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return view.getCompoundDrawableTintList();
        // Drawable's tint getter is not public API; below Android 6.0 the pill keeps its own colour.
        return null;
    }

    /** View.forceHasOverlappingRendering arrived in Android 7.0; without it the default stands. */
    static void forceHasOverlappingRendering(View view, boolean value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) view.forceHasOverlappingRendering(value);
    }

    /** TvInputInfo.isHidden arrived in Android 7.0; before it no input is hidden. */
    static boolean isHidden(TvInputInfo info, Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.isHidden(context);
    }

    /** TvInputInfo.loadCustomLabel arrived in Android 7.0; before it the set has no per-input names. */
    static CharSequence loadCustomLabel(TvInputInfo info, Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? info.loadCustomLabel(context) : null;
    }

    /**
     * A TvInputManager whose tv_input service is absent, as on some vendor sets (a Letv X3-55 on
     * Android 5.0.1), throws a NullPointerException from inside every call rather than returning null.
     * These wrappers treat that service as an empty input list.
     */
    static List<TvInputInfo> tvInputs(TvInputManager tim) {
        if (tim == null) return Collections.emptyList();
        try {
            return tim.getTvInputList();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    static int tvInputState(TvInputManager tim, String inputId) {
        if (tim == null) return -1;
        try {
            return tim.getInputState(inputId);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static void registerTvInputCallback(TvInputManager tim, TvInputManager.TvInputCallback callback,
                                        Handler handler) {
        if (tim == null) return;
        try {
            tim.registerCallback(callback, handler);
        } catch (RuntimeException ignored) {
            // No tv_input service: Home has no input list to follow.
        }
    }

    static void unregisterTvInputCallback(TvInputManager tim, TvInputManager.TvInputCallback callback) {
        if (tim == null) return;
        try {
            tim.unregisterCallback(callback);
        } catch (RuntimeException ignored) {
            // The callback was never registered.
        }
    }

    /** registerDefaultNetworkCallback arrived in Android 7.0; the internet request is the closest equivalent. */
    static void registerDefaultNetworkCallback(ConnectivityManager cm,
                                               ConnectivityManager.NetworkCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(callback);
            return;
        }
        cm.registerNetworkCallback(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), callback);
    }
}
