package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;

import androidx.core.graphics.ColorUtils;

/**
 * Builds the Apple Music style player background: the album's colour filling the
 * area below the artwork, which the artwork's bottom edge fades into (used by the
 * player, album and artist pages). {@link #backgroundTopColor} is the colour the
 * artwork dissolves into, kept in sync between the background and the art fade.
 */
public final class PlayerBackgroundUtil {

    private PlayerBackgroundUtil() {
    }

    /**
     * Near-black in dark mode (matching YouTube Music), the theme surface in light
     * mode. The album colour is toned slightly toward it.
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

        // Strong, smooth blur: box-blur a 1/6 size copy (three passes approximate a
        // Gaussian), then scale it back up. Scaling alone leaves visible blocks.
        int sw = Math.max(1, width / 6);
        int sh = Math.max(1, height / 6);
        Bitmap small = Bitmap.createScaledBitmap(art, sw, sh, true);
        int[] pixels = new int[sw * sh];
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh);
        int[] scratch = new int[pixels.length];
        for (int pass = 0; pass < 3; pass++) {
            boxBlurTransposed(pixels, scratch, sw, sh, 5);
            boxBlurTransposed(scratch, pixels, sh, sw, 5);
        }
        small.recycle();
        small = Bitmap.createBitmap(pixels, sw, sh, Bitmap.Config.ARGB_8888);
        Bitmap blurred = Bitmap.createScaledBitmap(small, width, height, true);

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
        blurred.recycle();
        return out;
    }

    /**
     * One horizontal box-blur pass of radius {@code r} over {@code src} (w x h),
     * written transposed into {@code dst} (h x w), so calling it twice blurs both
     * axes and restores the orientation. Output is opaque.
     */
    private static void boxBlurTransposed(int[] src, int[] dst, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int rs = 0, gs = 0, bs = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[row + Math.min(w - 1, Math.max(0, i))];
                rs += (c >> 16) & 0xFF;
                gs += (c >> 8) & 0xFF;
                bs += c & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                dst[x * h + y] = 0xFF000000 | ((rs / div) << 16) | ((gs / div) << 8) | (bs / div);
                int in = src[row + Math.min(w - 1, x + r + 1)];
                int out = src[row + Math.max(0, x - r)];
                rs += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                gs += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                bs += (in & 0xFF) - (out & 0xFF);
            }
        }
    }
}
