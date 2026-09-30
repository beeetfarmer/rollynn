package com.cappielloantonio.tempo.glide;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;
import com.cappielloantonio.tempo.util.PlayerBackgroundUtil;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

/**
 * Crops the art to the view, then blurs and dissolves its bottom into {@code color}
 * (see {@link PlayerBackgroundUtil#fadeIntoColor}). Cropping first puts the fade on
 * the edge that is actually visible.
 */
public class ArtFadeTransformation extends BitmapTransformation {
    // Bump the version whenever the fade's look changes so cached art is redone.
    private static final String ID = "com.cappielloantonio.tempo.glide.ArtFadeTransformation.v3";

    private final int color;
    private final float start;
    // Pixels at the bottom of the view that lie beyond the art (see fadeIntoColor).
    private final int extend;

    public ArtFadeTransformation(int color, float start, int extend) {
        this.color = color;
        this.start = start;
        this.extend = extend;
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        int ext = Math.max(0, Math.min(extend, outHeight - 1));
        return PlayerBackgroundUtil.fadeIntoColor(
                TransformationUtils.centerCrop(pool, toTransform, outWidth, outHeight - ext), color, start, ext);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ArtFadeTransformation && ((ArtFadeTransformation) o).color == color
                && ((ArtFadeTransformation) o).start == start
                && ((ArtFadeTransformation) o).extend == extend;
    }

    @Override
    public int hashCode() {
        return ((ID.hashCode() * 31 + color) * 31 + Float.hashCode(start)) * 31 + extend;
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID.getBytes(CHARSET));
        messageDigest.update(ByteBuffer.allocate(12).putInt(color).putFloat(start).putInt(extend).array());
    }
}
