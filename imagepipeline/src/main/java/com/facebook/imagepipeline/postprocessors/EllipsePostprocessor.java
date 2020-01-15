/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.imagepipeline.postprocessors;

import android.graphics.Bitmap;
import com.facebook.cache.common.CacheKey;
import com.facebook.cache.common.SimpleCacheKey;
import com.facebook.imagepipeline.filter.InPlaceEllipseRoundFilter;
import com.facebook.imagepipeline.request.BasePostprocessor;
import javax.annotation.Nullable;

/** Postprocessor that rounds a given image as a ellipse using non-native code. */
public class EllipsePostprocessor extends BasePostprocessor {

    private @Nullable CacheKey mCacheKey;
    private int imageHeight = -1;
    private int imageWidth = -1;
    private @Nullable int[] cornersHeight;
    private @Nullable int[] cornersWidth;

    public EllipsePostprocessor() {;}

    public EllipsePostprocessor(int h, int w) {
        imageHeight = h;
        imageWidth = w;
    }

    public EllipsePostprocessor(int[] heights, int[] widths) {
        cornersHeight = heights;
        cornersWidth = widths;
    }

    @Override
    public void process(Bitmap bitmap) {
        if (imageHeight == -1 && imageWidth == -1 && cornersWidth == null && cornersHeight == null) {
            InPlaceEllipseRoundFilter.roundEllipseBitmapInPlace(bitmap);
        }
        else if (imageHeight >= 0 && imageWidth >= 0) {
            InPlaceEllipseRoundFilter.roundEllipseBitmapInPlace(bitmap, imageHeight, imageWidth);
        }
        else if (cornersWidth != null && cornersHeight != null) {
            InPlaceEllipseRoundFilter.roundCornersEllipseBitmapInPlace(bitmap,
                    cornersHeight[0], cornersWidth[0], cornersHeight[1], cornersWidth[1],
                    cornersHeight[2], cornersWidth[2], cornersHeight[3], cornersWidth[3]);
        }
    }

    public void process(Bitmap bitmap, int h, int w) {
        InPlaceEllipseRoundFilter.roundEllipseBitmapInPlace(bitmap, h, w);
    }

    @Nullable
    @Override
    public CacheKey getPostprocessorCacheKey() {
        return null;
    }
}
