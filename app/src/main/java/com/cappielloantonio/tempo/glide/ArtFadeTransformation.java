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
    private static final String ID = "com.cappielloantonio.tempo.glide.ArtFadeTransformation";

    private final int color;

    public ArtFadeTransformation(int color) {
        this.color = color;
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        return PlayerBackgroundUtil.fadeIntoColor(
                TransformationUtils.centerCrop(pool, toTransform, outWidth, outHeight), color);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ArtFadeTransformation && ((ArtFadeTransformation) o).color == color;
    }

    @Override
    public int hashCode() {
        return ID.hashCode() * 31 + color;
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID.getBytes(CHARSET));
        messageDigest.update(ByteBuffer.allocate(4).putInt(color).array());
    }
}
