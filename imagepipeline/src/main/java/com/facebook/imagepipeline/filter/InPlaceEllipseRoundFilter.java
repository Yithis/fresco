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
     * @param h Height of ellipse
     * @param w Width of ellipse
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

    /**
     * An implementation for rounding each corner of a given bitmap to an ellipse shape with given width and height.
     *
     * @param bitmap The input {@link Bitmap}
     * @param topLeftHeight Height of ellipse in top left corner
     * @param topLeftWidth Width of ellipse in top left corner
     * @param topRightHeight Height of ellipse in top right corner
     * @param topRightWidth Width of ellipse in top tight corner
     * @param bottomRightHeight Height of ellipse in bottom right corner
     * @param bottomRightWidth Width of ellipse in bottom right corner
     * @param bottomLeftHeight Height of ellipse in bottom left corner
     * @param bottomLeftWidth Width of ellipse in bottom left corner
     */
    public static void roundCornersEllipseBitmapInPlace(Bitmap bitmap, int topLeftHeight, int topLeftWidth, int topRightHeight, int topRightWidth, int bottomRightHeight, int bottomRightWidth, int bottomLeftHeight, int bottomLeftWidth) {
        Preconditions.checkNotNull(bitmap);
        final int imageWidth = bitmap.getWidth();
        final int imageHeight = bitmap.getHeight();

        Preconditions.checkArgument(topLeftWidth <= imageWidth);
        Preconditions.checkArgument(topLeftHeight <= imageHeight);
        Preconditions.checkArgument(topRightWidth <= imageWidth);
        Preconditions.checkArgument(topRightHeight <= imageHeight);
        Preconditions.checkArgument(bottomRightWidth <= imageWidth);
        Preconditions.checkArgument(bottomRightHeight <= imageHeight);
        Preconditions.checkArgument(bottomLeftWidth <= imageWidth);
        Preconditions.checkArgument(bottomLeftHeight <= imageHeight);

        final int[] transparentColor = new int[imageWidth];

        final int[] pixels = new int[imageWidth * imageHeight];
        bitmap.getPixels(pixels, 0, imageWidth, 0, 0, imageWidth, imageHeight);

        for (int y = -1*topLeftHeight/2; y < 0; y++) {
            int x = -1*topLeftWidth/2;
            while ( ((float) 4*x*x)/(topLeftWidth*topLeftWidth) + ((float) 4*y*y)/(topLeftHeight*topLeftHeight) > 1 && x < 0 ) {
                x++;
            }
            System.arraycopy(transparentColor, 0, pixels, (y + topLeftHeight / 2) * imageWidth, Math.max(0, (x + topLeftWidth / 2) - 1));
        }

        for (int y = -1*topRightHeight/2; y < 0; y++) {
            int x = topRightWidth/2;
            while ( ((float) 4*x*x)/(topRightWidth*topRightWidth) + ((float) 4*y*y)/(topRightHeight*topRightHeight) > 1 && x > 0) {
                x--;
            }
            System.arraycopy(transparentColor, 0, pixels, (y + topRightHeight / 2) * imageWidth + imageWidth - topRightWidth/2 + x - 1, Math.max(0, topRightWidth - (x + topRightWidth / 2) + 1));
        }

        for (int y = 0; y < bottomLeftHeight/2; y++) {
            int x = -1*bottomLeftWidth/2;
            while ( ((float) 4*x*x)/(bottomLeftWidth*bottomLeftWidth) + ((float) 4*y*y)/(bottomLeftHeight*bottomLeftHeight) > 1 && x < 0 ) {
                x++;
            }
            System.arraycopy(transparentColor, 0, pixels, (imageHeight - bottomLeftHeight / 2 + y) * imageWidth, Math.max(0, (x + bottomLeftWidth / 2) - 1));
        }

        for (int y = 0; y < bottomRightHeight/2; y++) {
            int x = bottomRightWidth/2;
            while ( ((float) 4*x*x)/(bottomRightWidth*bottomRightWidth) + ((float) 4*y*y)/(bottomRightHeight*bottomRightHeight) > 1 && x > 0) {
                x--;
            }
            System.arraycopy(transparentColor, 0, pixels, (imageHeight - bottomRightHeight / 2 + y) * imageWidth + imageWidth - bottomRightWidth/2 + x - 1, Math.max(0, bottomRightWidth - (x + bottomRightWidth  / 2) + 1));
        }


        bitmap.setPixels(pixels, 0, imageWidth, 0, 0, imageWidth, imageHeight);
    }
}

