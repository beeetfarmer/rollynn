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
     * The main colour of the artwork's bottom edge. The art fades into this (see
     * {@link #fadeIntoColor}), so matching the last rows is what makes art and
     * background read as one image. It is the most common colour there, leaning
     * toward saturated ones, rather than the mean: averaging two distinct colours
     * (red and white, say) gives a washed-out one that is in neither. Transparent
     * pixels (rounded corners) are skipped.
     */
    public static int edgeColor(Bitmap source) {
        if (source == null) return Color.GRAY;
        int width = source.getWidth();
        int height = source.getHeight();
        int rows = Math.max(1, height / 8);
        int[] pixels = new int[width * rows];
        source.getPixels(pixels, 0, width, 0, height - rows, width, rows);

        // 3 bits per channel: coarse enough to group the shades of one colour.
        float[] weight = new float[512];
        long[] r = new long[512], g = new long[512], b = new long[512];
        int[] count = new int[512];
        for (int color : pixels) {
            if (Color.alpha(color) < 255) continue;
            int cr = Color.red(color), cg = Color.green(color), cb = Color.blue(color);
            int max = Math.max(cr, Math.max(cg, cb));
            int min = Math.min(cr, Math.min(cg, cb));
            int bucket = (cr >> 5) << 6 | (cg >> 5) << 3 | (cb >> 5);
            weight[bucket] += 0.4f + (max == 0 ? 0f : (max - min) / (float) max);
            r[bucket] += cr;
            g[bucket] += cg;
            b[bucket] += cb;
            count[bucket]++;
        }
        int best = 0;
        for (int i = 1; i < 512; i++) if (weight[i] > weight[best]) best = i;
        if (count[best] == 0) return Color.GRAY;
        return Color.rgb((int) (r[best] / count[best]), (int) (g[best] / count[best]), (int) (b[best] / count[best]));
    }

    /** The average colour of the whole artwork (transparent pixels skipped). */
    public static int averageColor(Bitmap source) {
        if (source == null) return Color.GRAY;
        return averageColor(source, 0);
    }

    private static int averageColor(Bitmap source, int fromRow) {
        int width = source.getWidth();
        int height = source.getHeight();
        long r = 0, g = 0, b = 0, count = 0;
        int[] row = new int[width];
        for (int y = fromRow; y < height; y++) {
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
     * with no visible edge. {@code start} is the fraction of the height left sharp
     * before the blur sets in. With {@code extend} > 0 the result is that many pixels
     * taller: the art is mirrored into the extra strip, the blur straddles the art's
     * bottom edge and the colour fade finishes below it, so content laid over the
     * strip sits on blurred art. Returns a new bitmap; {@code art} is left untouched.
     */
    public static Bitmap fadeIntoColor(Bitmap art, int color, float start, int extend) {
        int width = art.getWidth();
        int height = art.getHeight();
        int outHeight = height + extend;
        Bitmap out = Bitmap.createBitmap(width, outHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint artPaint = new Paint();
        artPaint.setShader(new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.MIRROR));
        canvas.drawRect(0, 0, width, outHeight, artPaint);

        Bitmap blurred = blur(art, 6, 5);

        // Blur ramps in over the lower part, under the colour fade.
        float blurTop = height * start;
        float blurFull = extend > 0 ? height + extend * 0.25f : height * (start + (1 - start) * 0.6f);
        float fadeTop = extend > 0 ? blurTop : height * (start + (1 - start) * 0.1f);
        Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blurPaint.setShader(new ComposeShader(
                new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.MIRROR),
                new LinearGradient(0, blurTop, 0, blurFull,
                        Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP),
                PorterDuff.Mode.DST_IN));
        canvas.drawRect(0, blurTop, width, outHeight, blurPaint);
        blurred.recycle();

        // In the strip a second, much heavier blur takes over, so shapes in the art
        // melt into soft colour before the fade instead of lingering as blobs.
        if (extend > 0) {
            Bitmap heavy = blur(art, 24, 4);
            float heavyTop = (blurTop + height) / 2f;
            blurPaint.setShader(new ComposeShader(
                    new BitmapShader(heavy, Shader.TileMode.CLAMP, Shader.TileMode.MIRROR),
                    new LinearGradient(0, heavyTop, 0, height + extend * 0.3f,
                            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP),
                    PorterDuff.Mode.DST_IN));
            canvas.drawRect(0, heavyTop, width, outHeight, blurPaint);
            heavy.recycle();
        }

        // Eased colour fade (a linear one shows a visible band where it starts).
        int clear = ColorUtils.setAlphaComponent(color, 0);
        Paint fadePaint = new Paint();
        fadePaint.setShader(new LinearGradient(0, fadeTop, 0, outHeight,
                new int[]{clear, ColorUtils.setAlphaComponent(color, extend > 0 ? 110 : 90),
                        ColorUtils.setAlphaComponent(color, 200), color, color},
                extend > 0 ? new float[]{0f, 0.25f, 0.55f, 0.85f, 1f} : new float[]{0f, 0.35f, 0.65f, 0.9f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, fadeTop, width, outHeight, fadePaint);

        return out;
    }

    /**
     * A smoothly blurred copy of {@code art} at its own size: box-blur a 1/{@code div}
     * size copy (three passes approximate a Gaussian), then scale it back up. Scaling
     * alone leaves visible blocks.
     */
    private static Bitmap blur(Bitmap art, int div, int radius) {
        int sw = Math.max(1, art.getWidth() / div);
        int sh = Math.max(1, art.getHeight() / div);
        Bitmap small = Bitmap.createScaledBitmap(art, sw, sh, true);
        int[] pixels = new int[sw * sh];
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh);
        int[] scratch = new int[pixels.length];
        for (int pass = 0; pass < 3; pass++) {
            boxBlurTransposed(pixels, scratch, sw, sh, radius);
            boxBlurTransposed(scratch, pixels, sh, sw, radius);
        }
        if (small != art) small.recycle();
        small = Bitmap.createBitmap(pixels, sw, sh, Bitmap.Config.ARGB_8888);
        Bitmap blurred = Bitmap.createScaledBitmap(small, art.getWidth(), art.getHeight(), true);
        if (small != blurred) small.recycle();
        return blurred;
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
