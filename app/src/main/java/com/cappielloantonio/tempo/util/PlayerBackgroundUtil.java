package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;

import androidx.core.graphics.ColorUtils;

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
     * The average colour of the artwork's bottom edge. The art fades into this
     * (see {@link #fadeIntoColor}), so matching the last rows rather than the most
     * vivid colour is what makes art and background read as one image. Transparent
     * pixels (rounded corners) are skipped.
     */
    public static int edgeColor(Bitmap source) {
        if (source == null) return Color.GRAY;

        int width = source.getWidth();
        int height = source.getHeight();
        int top = height - Math.max(1, height / 8);
        long r = 0, g = 0, b = 0, count = 0;
        int[] row = new int[width];
        for (int y = top; y < height; y++) {
            source.getPixels(row, 0, width, 0, y, width, 1);
            for (int color : row) {
                if (Color.alpha(color) < 255) continue;
                r += Color.red(color);
                g += Color.green(color);
                b += Color.blue(color);
                count++;
            }
        }
        if (count == 0) return Color.GRAY;
        return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
    }

    /**
     * Apple Music style art: the lower part of {@code art} is progressively blurred
     * and dissolved into {@code color}, so it melts into a background of that colour
     * with no visible edge. Returns a new bitmap; {@code art} is left untouched.
     */
    public static Bitmap fadeIntoColor(Bitmap art, int color) {
        int width = art.getWidth();
        int height = art.getHeight();
        Bitmap out = art.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);

        // Cheap strong blur: shrink in two steps (to avoid aliasing) and scale back.
        Bitmap small = Bitmap.createScaledBitmap(art, Math.max(1, width / 4), Math.max(1, height / 4), true);
        Bitmap tiny = Bitmap.createScaledBitmap(small, Math.max(1, width / 16), Math.max(1, height / 16), true);
        Bitmap blurred = Bitmap.createScaledBitmap(tiny, width, height, true);

        // Blur ramps in over the lower part, under the colour fade.
        Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blurPaint.setShader(new ComposeShader(
                new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
                new LinearGradient(0, height * 0.50f, 0, height * 0.80f,
                        Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP),
                PorterDuff.Mode.DST_IN));
        canvas.drawRect(0, height * 0.50f, width, height, blurPaint);

        // Eased colour fade (a linear one shows a visible band where it starts).
        int clear = ColorUtils.setAlphaComponent(color, 0);
        Paint fadePaint = new Paint();
        fadePaint.setShader(new LinearGradient(0, height * 0.55f, 0, height,
                new int[]{clear, ColorUtils.setAlphaComponent(color, 90),
                        ColorUtils.setAlphaComponent(color, 200), color, color},
                new float[]{0f, 0.35f, 0.65f, 0.9f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, height * 0.55f, width, height, fadePaint);

        small.recycle();
        tiny.recycle();
        blurred.recycle();
        return out;
    }
}
