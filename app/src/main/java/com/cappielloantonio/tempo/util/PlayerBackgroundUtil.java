package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the YouTube Music style player background: a mostly dark surface with a
 * soft glow of the album's colour radiating out from behind the artwork and
 * fading to the base colour toward the top and bottom edges. The activity paints
 * the system bars with matching edge colours so they blend in.
 */
public final class PlayerBackgroundUtil {

    private static final int OUT_WIDTH = 200;
    private static final int OUT_HEIGHT = 400;
    // The glow sits behind the artwork, which occupies the upper part of the player,
    // and stays tight so most of the screen falls back to the near-black base.
    private static final float GLOW_CENTER_Y = 0.30f;
    private static final float GLOW_RADIUS = 0.42f;

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

    public static Drawable buildGlow(Context context, int dominant) {
        int base = baseColor(context);

        Bitmap out = Bitmap.createBitmap(OUT_WIDTH, OUT_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(base);

        float cx = OUT_WIDTH * 0.5f;
        float cy = OUT_HEIGHT * GLOW_CENTER_Y;
        float radius = OUT_HEIGHT * GLOW_RADIUS;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{
                        ColorUtils.setAlphaComponent(dominant, 205),
                        ColorUtils.setAlphaComponent(dominant, 70),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, OUT_WIDTH, OUT_HEIGHT, paint);

        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), out);
        drawable.setFilterBitmap(true);
        return drawable;
    }

    /** Status bar colour: the base only lightly tinted by the album colour. */
    public static int statusBarColor(Context context, int dominant) {
        return ColorUtils.blendARGB(dominant, baseColor(context), 0.85f);
    }

    /** Navigation bar colour: the base, matching the dark bottom of the player. */
    public static int navBarColor(Context context) {
        return baseColor(context);
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
