package com.facebook.imagepipeline.filter;

import android.graphics.Bitmap;
import com.facebook.common.internal.Preconditions;
import com.facebook.imageutils.BitmapUtil;

/**
 * Filter for rounding to ellipse.
 */
public final class InPlaceEllipseRoundFilter {

    private InPlaceEllipseRoundFilter() {}

    /**
     * An implementation for rounding a given bitmap to an ellipse shape.
     *
     * @param bitmap The input {@link Bitmap}
     */
    public static void roundEllipseBitmapInPlace(Bitmap bitmap) {
        Preconditions.checkNotNull(bitmap);
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();

        final int[] transparentColor = new int[w];

        final int[] pixels = new int[w*h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        for (int y = -1*h/2; y < h/2; y++) {
            int x = -1*w/2;
            while ( ((float) 4*x*x)/(w*w) + ((float) 4*y*y)/(h*h) > 1 ) {
                x++;
            }

            int pixelsToHide = Math.max(0, x + w/2 - 1);

            System.arraycopy(transparentColor, 0, pixels, (y + h/2)*w, pixelsToHide);

            System.arraycopy(transparentColor, 0, pixels, (y + h/2)*w + w - pixelsToHide, pixelsToHide);
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    /**
     * An implementation for rounding a given bitmap to an ellipse shape with given width and height.
     *
     * @param bitmap The input {@link Bitmap}
     */
    public static void roundEllipseBitmapInPlace(Bitmap bitmap, int h, int w) {
        Preconditions.checkNotNull(bitmap);
        final int imageWidth = bitmap.getWidth();
        final int imageHeight = bitmap.getHeight();

        Preconditions.checkArgument(w <= imageWidth);
        Preconditions.checkArgument(h <= imageHeight);

        final int[] transparentColor = new int[imageWidth];

        final int[] pixels = new int[imageWidth * imageHeight];
        bitmap.getPixels(pixels, 0, imageWidth, 0, 0, imageWidth, imageHeight);

        for (int y = -1*imageHeight/2; y < imageHeight/2; y++) {
            int x = -1*imageWidth/2;
            while ( ((float) 4*x*x)/(w*w) + ((float) 4*y*y)/(h*h) > 1 && x < 0 ) {
                x++;
            }
            if (x < 0) {
                int pixelsToHide = Math.max(0, x + imageWidth/2 - 1);

                System.arraycopy(transparentColor, 0, pixels, (y + imageHeight/2)*imageWidth, pixelsToHide);

                System.arraycopy(transparentColor, 0, pixels, (y + imageHeight/2)*imageWidth + imageWidth - pixelsToHide, pixelsToHide);
            }
            else {
                System.arraycopy(transparentColor, 0, pixels, (y + imageHeight / 2) * imageWidth, imageWidth);
            }
        }

        bitmap.setPixels(pixels, 0, imageWidth, 0, 0, imageWidth, imageHeight);
    }
}

