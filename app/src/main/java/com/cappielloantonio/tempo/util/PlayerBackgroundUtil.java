package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.core.graphics.ColorUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the Apple Music style player background: the album's colour filling the
 * area below the artwork (which the artwork's bottom edge fades into), lightly
 * darkening toward the controls. {@link #backgroundTopColor} is the colour the
 * artwork dissolves into, kept in sync between the background and the art fade.
 */
public final class PlayerBackgroundUtil {

    private PlayerBackgroundUtil() {
    }

    /**
     * Near-black in dark mode (matching YouTube Music), the theme surface in light
     * mode. This is what the glow fades into and what the bars are coloured with.
     */
    public static int baseColor(Context context) {
        boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            return ColorUtils.blendARGB(UIUtil.getPlayerBackgroundColor(context), Color.BLACK, 0.6f);
        }
        return UIUtil.getPlayerBackgroundColor(context);
    }

    /**
     * The rich album colour that fills the area below the artwork and that the
     * artwork's bottom edge fades into, so the two blend seamlessly. Toned very
     * slightly toward the base so text stays legible over it.
     */
    public static int backgroundTopColor(Context context, int dominant) {
        return ColorUtils.blendARGB(dominant, baseColor(context), 0.08f);
    }

    /**
     * Black or white, whichever reads on {@link #backgroundTopColor}. The theme's
     * text colours ignore the album colour, so dark text lands on dark art in light
     * mode (and vice versa); content over the background should use this instead.
     */
    public static int contentColor(Context context, int dominant) {
        return ColorUtils.calculateLuminance(backgroundTopColor(context, dominant)) > 0.5
                ? Color.BLACK : Color.WHITE;
    }

    /**
     * Apple Music style background: the album's colour up top (where the artwork
     * dissolves into it) darkening toward the controls at the bottom. The artwork
     * covers roughly the top half, so the top colour is held solid until midway.
     */
    public static Drawable buildGlow(Context context, int dominant) {
        int top = backgroundTopColor(context, dominant);
        // Keep the album colour rich toward the bottom (only lightly darkened for
        // control contrast) instead of fading almost to black. Held solid down to
        // ~66% so the art (which covers 50-60%) always fades into a matching colour.
        int bottom = ColorUtils.blendARGB(dominant, baseColor(context), 0.40f);
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, top, top, bottom});
    }

    /**
     * The most prominent, reasonably saturated colour in the artwork. Near black,
     * near white and washed-out greys are down-weighted so the result is the
     * colour a person would call the album's colour, not its average mud.
     */
    public static int dominantColor(Bitmap source) {
        if (source == null) return Color.GRAY;

        Bitmap small = Bitmap.createScaledBitmap(source, 24, 24, true);
        int width = small.getWidth();
        int height = small.getHeight();
        int[] pixels = new int[width * height];
        small.getPixels(pixels, 0, width, 0, 0, width, height);

        Map<Integer, float[]> buckets = new HashMap<>();
        float[] hsv = new float[3];

        for (int color : pixels) {
            Color.colorToHSV(color, hsv);
            float saturation = hsv[1];
            float value = hsv[2];
            if (value < 0.15f || value > 0.95f) continue;

            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            float weight = saturation * saturation * value + 0.05f;

            int key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
            float[] acc = buckets.get(key);
            if (acc == null) {
                acc = new float[4];
                buckets.put(key, acc);
            }
            acc[0] += weight;
            acc[1] += r * weight;
            acc[2] += g * weight;
            acc[3] += b * weight;
        }

        float best = -1f;
        int br = 128, bg = 128, bb = 128;
        for (float[] acc : buckets.values()) {
            if (acc[0] > best) {
                best = acc[0];
                br = Math.round(acc[1] / acc[0]);
                bg = Math.round(acc[2] / acc[0]);
                bb = Math.round(acc[3] / acc[0]);
            }
        }

        return Color.rgb(clamp(br), clamp(bg), clamp(bb));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
