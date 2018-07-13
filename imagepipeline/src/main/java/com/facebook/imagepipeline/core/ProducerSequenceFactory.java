/*
 * Copyright (c) 2015-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.imagepipeline.core;

import static com.facebook.imagepipeline.common.SourceUriType.SOURCE_TYPE_DATA;
import static com.facebook.imagepipeline.common.SourceUriType.SOURCE_TYPE_LOCAL_ASSET;
import static com.facebook.imagepipeline.common.SourceUriType.SOURCE_TYPE_LOCAL_CONTENT;
import static com.facebook.imagepipeline.common.SourceUriType.SOURCE_TYPE_LOCAL_IMAGE_FILE;
import static com.facebook.imagepipeline.common.SourceUriType.SOURCE_TYPE_LOCAL_RESOURCE;
import static com.facebook.imagepipeline.common.SourceUriType.SOURCE_TYPE_LOCAL_VIDEO_FILE;
import static com.facebook.imagepipeline.common.SourceUriType.SOURCE_TYPE_NETWORK;
import static com.facebook.imagepipeline.common.SourceUriType.SOURCE_TYPE_QUALIFIED_RESOURCE;

import android.content.ContentResolver;
import android.net.Uri;
import com.facebook.common.internal.Preconditions;
import com.facebook.common.internal.VisibleForTesting;
import com.facebook.common.media.MediaUtils;
import com.facebook.common.memory.PooledByteBuffer;
import com.facebook.common.references.CloseableReference;
import com.facebook.common.webp.WebpSupportStatus;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.image.EncodedImage;
import com.facebook.imagepipeline.producers.BitmapMemoryCacheKeyMultiplexProducer;
import com.facebook.imagepipeline.producers.BitmapMemoryCacheProducer;
import com.facebook.imagepipeline.producers.DecodeProducer;
import com.facebook.imagepipeline.producers.EncodedMemoryCacheProducer;
import com.facebook.imagepipeline.producers.LocalAssetFetchProducer;
import com.facebook.imagepipeline.producers.LocalContentUriFetchProducer;
import com.facebook.imagepipeline.producers.LocalFileFetchProducer;
import com.facebook.imagepipeline.producers.LocalResourceFetchProducer;
import com.facebook.imagepipeline.producers.LocalVideoThumbnailProducer;
import com.facebook.imagepipeline.producers.NetworkFetcher;
import com.facebook.imagepipeline.producers.PostprocessedBitmapMemoryCacheProducer;
import com.facebook.imagepipeline.producers.PostprocessorProducer;
import com.facebook.imagepipeline.producers.Producer;
import com.facebook.imagepipeline.producers.QualifiedResourceFetchProducer;
import com.facebook.imagepipeline.producers.RemoveImageTransformMetaDataProducer;
import com.facebook.imagepipeline.producers.SwallowResultProducer;
import com.facebook.imagepipeline.producers.ThreadHandoffProducer;
import com.facebook.imagepipeline.producers.ThreadHandoffProducerQueue;
import com.facebook.imagepipeline.producers.ThrottlingProducer;
import com.facebook.imagepipeline.producers.ThumbnailBranchProducer;
import com.facebook.imagepipeline.producers.ThumbnailProducer;
import com.facebook.imagepipeline.request.ImageRequest;
import java.util.HashMap;
import java.util.Map;

public class ProducerSequenceFactory {

  private final ContentResolver mContentResolver;
  private final ProducerFactory mProducerFactory;
  private final NetworkFetcher mNetworkFetcher;
  private final boolean mResizeAndRotateEnabledForNetwork;
  private final boolean mWebpSupportEnabled;
  private final boolean mPartialImageCachingEnabled;
  private final ThreadHandoffProducerQueue mThreadHandoffProducerQueue;
  private final boolean mUseDownsamplingRatio;
  private final boolean mUseBitmapPrepareToDraw;
  private final boolean mDiskCacheEnabled;

  // Saved sequences
  @VisibleForTesting Producer<CloseableReference<CloseableImage>> mNetworkFetchSequence;
  @VisibleForTesting Producer<EncodedImage> mBackgroundLocalFileFetchToEncodedMemorySequence;
  @VisibleForTesting Producer<EncodedImage> mBackgroundNetworkFetchToEncodedMemorySequence;
  @VisibleForTesting Producer<CloseableReference<PooledByteBuffer>>
      mLocalFileEncodedImageProducerSequence;
  @VisibleForTesting Producer<CloseableReference<PooledByteBuffer>>
      mNetworkEncodedImageProducerSequence;
  @VisibleForTesting Producer<Void> mLocalFileFetchToEncodedMemoryPrefetchSequence;
  @VisibleForTesting Producer<Void> mNetworkFetchToEncodedMemoryPrefetchSequence;
  private Producer<EncodedImage> mCommonNetworkFetchToEncodedMemorySequence;
  @VisibleForTesting Producer<CloseableReference<CloseableImage>> mLocalImageFileFetchSequence;
  @VisibleForTesting Producer<CloseableReference<CloseableImage>> mLocalVideoFileFetchSequence;
  @VisibleForTesting Producer<CloseableReference<CloseableImage>> mLocalContentUriFetchSequence;
  @VisibleForTesting Producer<CloseableReference<CloseableImage>> mLocalResourceFetchSequence;
  @VisibleForTesting Producer<CloseableReference<CloseableImage>> mLocalAssetFetchSequence;
  @VisibleForTesting Producer<CloseableReference<CloseableImage>> mDataFetchSequence;
  @VisibleForTesting Producer<CloseableReference<CloseableImage>> mQualifiedResourceFetchSequence;
  @VisibleForTesting Map<
      Producer<CloseableReference<CloseableImage>>,
      Producer<CloseableReference<CloseableImage>>>
      mPostprocessorSequences;
  @VisibleForTesting Map<Producer<CloseableReference<CloseableImage>>, Producer<Void>>
      mCloseableImagePrefetchSequences;
  @VisibleForTesting Map<
      Producer<CloseableReference<CloseableImage>>,
      Producer<CloseableReference<CloseableImage>>>
      mBitmapPrepareSequences;

  public ProducerSequenceFactory(
      ContentResolver contentResolver,
      ProducerFactory producerFactory,
      NetworkFetcher networkFetcher,
      boolean resizeAndRotateEnabledForNetwork,
      boolean webpSupportEnabled,
      ThreadHandoffProducerQueue threadHandoffProducerQueue,
      boolean useDownsamplingRatio,
      boolean useBitmapPrepareToDraw,
      boolean partialImageCachingEnabled,
      boolean diskCacheEnabled) {
    mContentResolver = contentResolver;
    mProducerFactory = producerFactory;
    mNetworkFetcher = networkFetcher;
    mResizeAndRotateEnabledForNetwork = resizeAndRotateEnabledForNetwork;
    mWebpSupportEnabled = webpSupportEnabled;
    mPostprocessorSequences = new HashMap<>();
    mCloseableImagePrefetchSequences = new HashMap<>();
    mBitmapPrepareSequences = new HashMap<>();
    mThreadHandoffProducerQueue = threadHandoffProducerQueue;
    mUseDownsamplingRatio = useDownsamplingRatio;
    mUseBitmapPrepareToDraw = useBitmapPrepareToDraw;
    mPartialImageCachingEnabled = partialImageCachingEnabled;
    mDiskCacheEnabled = diskCacheEnabled;
  }

  /**
   * Returns a sequence that can be used for a request for an encoded image from either network or
   * local files.
   *
   * @param imageRequest the request that will be submitted
   * @return the sequence that should be used to process the request
   */
  public Producer<CloseableReference<PooledByteBuffer>> getEncodedImageProducerSequence(
      ImageRequest imageRequest) {
    validateEncodedImageRequest(imageRequest);
    final Uri uri = imageRequest.getSourceUri();

    switch (imageRequest.getSourceUriType()) {
      case SOURCE_TYPE_NETWORK:
        return getNetworkFetchEncodedImageProducerSequence();
      case SOURCE_TYPE_LOCAL_VIDEO_FILE:
      case SOURCE_TYPE_LOCAL_IMAGE_FILE:
        return getLocalFileFetchEncodedImageProducerSequence();
      default:
        throw new IllegalArgumentException(
            "Unsupported uri scheme for encoded image fetch! Uri is: "
                + getShortenedUriString(uri));
    }
  }

  /**
   * Returns a sequence that can be used for a request for an encoded image from network.
   */
  public Producer<CloseableReference<PooledByteBuffer>>
  getNetworkFetchEncodedImageProducerSequence() {
    synchronized (this) {
      if (mNetworkEncodedImageProducerSequence == null) {
        mNetworkEncodedImageProducerSequence = new RemoveImageTransformMetaDataProducer(
            getBackgroundNetworkFetchToEncodedMemorySequence());
      }
    }
    return mNetworkEncodedImageProducerSequence;
  }

  /**
   * Returns a sequence that can be used for a request for an encoded image from a local file.
   */
  public Producer<CloseableReference<PooledByteBuffer>>
  getLocalFileFetchEncodedImageProducerSequence() {
    synchronized (this) {
      if (mLocalFileEncodedImageProducerSequence == null) {
        mLocalFileEncodedImageProducerSequence = new RemoveImageTransformMetaDataProducer(
            getBackgroundLocalFileFetchToEncodeMemorySequence());
      }
    }
    return mLocalFileEncodedImageProducerSequence;
  }

  /**
   * Returns a sequence that can be used for a prefetch request for an encoded image.
   *
   * <p>Guaranteed to return the same sequence as
   * {@code getEncodedImageProducerSequence(request)}, except that it is pre-pended with a
   * {@link SwallowResultProducer}.
   * @param imageRequest the request that will be submitted
   * @return the sequence that should be used to process the request
   */
  public Producer<Void> getEncodedImagePrefetchProducerSequence(ImageRequest imageRequest) {
    validateEncodedImageRequest(imageRequest);

    switch (imageRequest.getSourceUriType()) {
      case SOURCE_TYPE_NETWORK:
        return getNetworkFetchToEncodedMemoryPrefetchSequence();
      case SOURCE_TYPE_LOCAL_VIDEO_FILE:
      case SOURCE_TYPE_LOCAL_IMAGE_FILE:
        return getLocalFileFetchToEncodedMemoryPrefetchSequence();
      default:
        final Uri uri = imageRequest.getSourceUri();
        throw new IllegalArgumentException(
            "Unsupported uri scheme for encoded image fetch! Uri is: "
                + getShortenedUriString(uri));
    }
  }

  private static void validateEncodedImageRequest(ImageRequest imageRequest) {
    Preconditions.checkNotNull(imageRequest);
    Preconditions.checkArgument(
        imageRequest.getLowestPermittedRequestLevel().getValue() <=
            ImageRequest.RequestLevel.ENCODED_MEMORY_CACHE.getValue());
  }

  /**
   * Returns a sequence that can be used for a request for a decoded image.
   *
   * @param imageRequest the request that will be submitted
   * @return the sequence that should be used to process the request
   */
  public Producer<CloseableReference<CloseableImage>> getDecodedImageProducerSequence(
      ImageRequest imageRequest) {
    Producer<CloseableReference<CloseableImage>> pipelineSequence =
        getBasicDecodedImageSequence(imageRequest);

    if (imageRequest.getPostprocessor() != null) {
      pipelineSequence = getPostprocessorSequence(pipelineSequence);
    }

    if (mUseBitmapPrepareToDraw) {
      pipelineSequence = getBitmapPrepareSequence(pipelineSequence);
    }

    return pipelineSequence;
  }

  /**
   * Returns a sequence that can be used for a prefetch request for a decoded image.
   *
   * @param imageRequest the request that will be submitted
   * @return the sequence that should be used to process the request
   */
  public Producer<Void> getDecodedImagePrefetchProducerSequence(
      ImageRequest imageRequest) {
    Producer<CloseableReference<CloseableImage>> inputProducer =
        getBasicDecodedImageSequence(imageRequest);

    if (mUseBitmapPrepareToDraw) {
      inputProducer = getBitmapPrepareSequence(inputProducer);
    }

    return getDecodedImagePrefetchSequence(inputProducer);
  }

  private Producer<CloseableReference<CloseableImage>> getBasicDecodedImageSequence(
      ImageRequest imageRequest) {
    Preconditions.checkNotNull(imageRequest);

    Uri uri = imageRequest.getSourceUri();
    Preconditions.checkNotNull(uri, "Uri is null.");

    switch (imageRequest.getSourceUriType()) {
      case SOURCE_TYPE_NETWORK:
        return getNetworkFetchSequence();
      case SOURCE_TYPE_LOCAL_VIDEO_FILE:
        return getLocalVideoFileFetchSequence();
      case SOURCE_TYPE_LOCAL_IMAGE_FILE:
        return getLocalImageFileFetchSequence();
      case SOURCE_TYPE_LOCAL_CONTENT:
        if (MediaUtils.isVideo(mContentResolver.getType(uri))) {
          return getLocalVideoFileFetchSequence();
        }
        return getLocalContentUriFetchSequence();
      case SOURCE_TYPE_LOCAL_ASSET:
        return getLocalAssetFetchSequence();
      case SOURCE_TYPE_LOCAL_RESOURCE:
        return getLocalResourceFetchSequence();
      case SOURCE_TYPE_QUALIFIED_RESOURCE:
        return getQualifiedResourceFetchSequence();
      case SOURCE_TYPE_DATA:
        return getDataFetchSequence();
      default:
        throw new IllegalArgumentException(
            "Unsupported uri scheme! Uri is: " + getShortenedUriString(uri));
    }
  }

  /**
   * swallow result if prefetch -> bitmap cache get ->
   * background thread hand-off -> multiplex -> bitmap cache -> decode -> multiplex ->
   * encoded cache -> disk cache -> (webp transcode) -> network fetch.
   */
  private synchronized Producer<CloseableReference<CloseableImage>> getNetworkFetchSequence() {
    if (mNetworkFetchSequence == null) {
      mNetworkFetchSequence =
          newBitmapCacheGetToDecodeSequence(getCommonNetworkFetchToEncodedMemorySequence());
    }
    return mNetworkFetchSequence;
  }

  /**
   * background-thread hand-off -> multiplex -> encoded cache ->
   * disk cache -> (webp transcode) -> network fetch.
   */
  private synchronized Producer<EncodedImage> getBackgroundNetworkFetchToEncodedMemorySequence() {
    if (mBackgroundNetworkFetchToEncodedMemorySequence == null) {
      // Use hand-off producer to ensure that we don't do any unnecessary work on the UI thread.
      mBackgroundNetworkFetchToEncodedMemorySequence =
          mProducerFactory.newBackgroundThreadHandoffProducer(
              getCommonNetworkFetchToEncodedMemorySequence(),
              mThreadHandoffProducerQueue);
    }
    return mBackgroundNetworkFetchToEncodedMemorySequence;
  }

  /**
   * swallow-result -> background-thread hand-off -> multiplex -> encoded cache ->
   * disk cache -> (webp transcode) -> network fetch.
   */
  private synchronized Producer<Void> getNetworkFetchToEncodedMemoryPrefetchSequence() {
    if (mNetworkFetchToEncodedMemoryPrefetchSequence == null) {
      mNetworkFetchToEncodedMemoryPrefetchSequence =
          ProducerFactory.newSwallowResultProducer(
              getBackgroundNetworkFetchToEncodedMemorySequence());
    }
    return mNetworkFetchToEncodedMemoryPrefetchSequence;
  }

  /**
   * multiplex -> encoded cache -> disk cache -> (webp transcode) -> network fetch.
   */
  private synchronized Producer<EncodedImage> getCommonNetworkFetchToEncodedMemorySequence() {
    if (mCommonNetworkFetchToEncodedMemorySequence == null) {
      Producer<EncodedImage> inputProducer =
          newEncodedCacheMultiplexToTranscodeSequence(
              mProducerFactory.newNetworkFetchProducer(mNetworkFetcher));
      mCommonNetworkFetchToEncodedMemorySequence =
          ProducerFactory.newAddImageTransformMetaDataProducer(inputProducer);

      mCommonNetworkFetchToEncodedMemorySequence =
          mProducerFactory.newResizeAndRotateProducer(
              mCommonNetworkFetchToEncodedMemorySequence,
              mResizeAndRotateEnabledForNetwork,
              mUseDownsamplingRatio);
    }
    return mCommonNetworkFetchToEncodedMemorySequence;
  }

  /**
   * swallow-result -> background-thread hand-off -> multiplex -> encoded cache ->
   * disk cache -> (webp transcode) -> local file fetch.
   */
  private synchronized Producer<Void> getLocalFileFetchToEncodedMemoryPrefetchSequence() {
    if (mLocalFileFetchToEncodedMemoryPrefetchSequence == null) {
      mLocalFileFetchToEncodedMemoryPrefetchSequence =
          ProducerFactory.newSwallowResultProducer(
              getBackgroundLocalFileFetchToEncodeMemorySequence());
    }
    return mLocalFileFetchToEncodedMemoryPrefetchSequence;
  }

  /**
   * background-thread hand-off -> multiplex -> encoded cache ->
   * disk cache -> (webp transcode) -> local file fetch
   */
  private synchronized Producer<EncodedImage> getBackgroundLocalFileFetchToEncodeMemorySequence() {
    if (mBackgroundLocalFileFetchToEncodedMemorySequence == null) {
      final LocalFileFetchProducer localFileFetchProducer =
          mProducerFactory.newLocalFileFetchProducer();

      final Producer<EncodedImage> toEncodedMultiplexProducer =
          newEncodedCacheMultiplexToTranscodeSequence(localFileFetchProducer);

      mBackgroundLocalFileFetchToEncodedMemorySequence =
          mProducerFactory.newBackgroundThreadHandoffProducer(
              toEncodedMultiplexProducer,
              mThreadHandoffProducerQueue);
    }
    return mBackgroundLocalFileFetchToEncodedMemorySequence;
  }

  /**
   * bitmap cache get ->
   * background thread hand-off -> multiplex -> bitmap cache -> decode ->
   * branch on separate images
   *   -> exif resize and rotate -> exif thumbnail creation
   *   -> local image resize and rotate -> add meta data producer -> multiplex -> encoded cache ->
   *   (webp transcode) -> local file fetch.
   */
  private synchronized Producer<CloseableReference<CloseableImage>>
  getLocalImageFileFetchSequence() {
    if (mLocalImageFileFetchSequence == null) {
      LocalFileFetchProducer localFileFetchProducer =
          mProducerFactory.newLocalFileFetchProducer();
      mLocalImageFileFetchSequence =
          newBitmapCacheGetToLocalTransformSequence(localFileFetchProducer);
    }
    return mLocalImageFileFetchSequence;
  }

  /**
   * Bitmap cache get -> thread hand off -> multiplex -> bitmap cache ->
   * local video thumbnail
   */
  private synchronized Producer<CloseableReference<CloseableImage>>
  getLocalVideoFileFetchSequence() {
    if (mLocalVideoFileFetchSequence == null) {
      LocalVideoThumbnailProducer localVideoThumbnailProducer =
          mProducerFactory.newLocalVideoThumbnailProducer();
      mLocalVideoFileFetchSequence =
          newBitmapCacheGetToBitmapCacheSequence(localVideoThumbnailProducer);
    }
    return mLocalVideoFileFetchSequence;
  }

  /**
   * bitmap cache get ->
   * background thread hand-off -> multiplex -> bitmap cache -> decode ->
   * branch on separate images
   *   -> thumbnail resize and rotate -> thumbnail branch
   *     -> local content thumbnail creation
   *     -> exif thumbnail creation
   *   -> local image resize and rotate -> add meta data producer -> multiplex -> encoded cache ->
   *   (webp transcode) -> local content uri fetch.
   */
  private synchronized Producer<CloseableReference<CloseableImage>>
  getLocalContentUriFetchSequence() {
    if (mLocalContentUriFetchSequence == null) {
      LocalContentUriFetchProducer localContentUriFetchProducer =
          mProducerFactory.newLocalContentUriFetchProducer();

      ThumbnailProducer<EncodedImage>[] thumbnailProducers = new ThumbnailProducer[2];
      thumbnailProducers[0] = mProducerFactory.newLocalContentUriThumbnailFetchProducer();
      thumbnailProducers[1] = mProducerFactory.newLocalExifThumbnailProducer();

      mLocalContentUriFetchSequence = newBitmapCacheGetToLocalTransformSequence(
          localContentUriFetchProducer,
          thumbnailProducers);
    }
    return mLocalContentUriFetchSequence;
  }

  /**
   * bitmap cache get ->
   * background thread hand-off -> multiplex -> bitmap cache -> decode ->
   * branch on separate images
   *   -> exif resize and rotate -> exif thumbnail creation
   *   -> local image resize and rotate -> add meta data producer -> multiplex -> encoded cache ->
   *   (webp transcode) -> qualified resource fetch.
   */
  private synchronized Producer<CloseableReference<CloseableImage>>
  getQualifiedResourceFetchSequence() {
    if (mQualifiedResourceFetchSequence == null) {
      QualifiedResourceFetchProducer qualifiedResourceFetchProducer =
          mProducerFactory.newQualifiedResourceFetchProducer();
      mQualifiedResourceFetchSequence =
          newBitmapCacheGetToLocalTransformSequence(qualifiedResourceFetchProducer);
    }
    return mQualifiedResourceFetchSequence;
  }

  /**
   * bitmap cache get ->
   * background thread hand-off -> multiplex -> bitmap cache -> decode ->
   * branch on separate images
   *   -> exif resize and rotate -> exif thumbnail creation
   *   -> local image resize and rotate -> add meta data producer -> multiplex -> encoded cache ->
   *   (webp transcode) -> local resource fetch.
   */
  private synchronized Producer<CloseableReference<CloseableImage>>
  getLocalResourceFetchSequence() {
    if (mLocalResourceFetchSequence == null) {
      LocalResourceFetchProducer localResourceFetchProducer =
          mProducerFactory.newLocalResourceFetchProducer();
      mLocalResourceFetchSequence =
          newBitmapCacheGetToLocalTransformSequence(localResourceFetchProducer);
    }
    return mLocalResourceFetchSequence;
  }

  /**
   * bitmap cache get ->
   * background thread hand-off -> multiplex -> bitmap cache -> decode ->
   * branch on separate images
   *   -> exif resize and rotate -> exif thumbnail creation
   *   -> local image resize and rotate -> add meta data producer -> multiplex -> encoded cache ->
   *   (webp transcode) -> local asset fetch.
   */
  private synchronized Producer<CloseableReference<CloseableImage>> getLocalAssetFetchSequence() {
    if (mLocalAssetFetchSequence == null) {
      LocalAssetFetchProducer localAssetFetchProducer =
          mProducerFactory.newLocalAssetFetchProducer();
      mLocalAssetFetchSequence =
          newBitmapCacheGetToLocalTransformSequence(localAssetFetchProducer);
    }
    return mLocalAssetFetchSequence;
  }

  /**
   * bitmap cache get ->
   * background thread hand-off -> bitmap cache -> decode -> resize and rotate -> (webp transcode)
   * -> data fetch.
   */
  private synchronized Producer<CloseableReference<CloseableImage>> getDataFetchSequence() {
    if (mDataFetchSequence == null) {
      Producer<EncodedImage> inputProducer = mProducerFactory.newDataFetchProducer();
      if (WebpSupportStatus.sIsWebpSupportRequired &&
          (!mWebpSupportEnabled || WebpSupportStatus.sWebpBitmapFactory == null)) {
        inputProducer = mProducerFactory.newWebpTranscodeProducer(inputProducer);
      }
      inputProducer = mProducerFactory.newAddImageTransformMetaDataProducer(inputProducer);
      inputProducer = mProducerFactory.newResizeAndRotateProducer(
          inputProducer,
          true,
          mUseDownsamplingRatio);
      mDataFetchSequence = newBitmapCacheGetToDecodeSequence(inputProducer);
    }
    return mDataFetchSequence;
  }

  /**
   * Creates a new fetch sequence that just needs the source producer.
   * @param inputProducer the source producer
   * @return the new sequence
   */
  private Producer<CloseableReference<CloseableImage>> newBitmapCacheGetToLocalTransformSequence(
      Producer<EncodedImage> inputProducer) {
    ThumbnailProducer<EncodedImage>[] defaultThumbnailProducers = new ThumbnailProducer[1];
    defaultThumbnailProducers[0] = mProducerFactory.newLocalExifThumbnailProducer();
    return newBitmapCacheGetToLocalTransformSequence(inputProducer, defaultThumbnailProducers);
  }

  /**
   * Creates a new fetch sequence that just needs the source producer.
   * @param inputProducer the source producer
   * @param thumbnailProducers the thumbnail producers from which to request the image before
   * falling back to the full image producer sequence
   * @return the new sequence
   */
  private Producer<CloseableReference<CloseableImage>> newBitmapCacheGetToLocalTransformSequence(
      Producer<EncodedImage> inputProducer,
      ThumbnailProducer<EncodedImage>[] thumbnailProducers) {
    inputProducer = newEncodedCacheMultiplexToTranscodeSequence(inputProducer);
    Producer<EncodedImage> inputProducerAfterDecode =
        newLocalTransformationsSequence(inputProducer, thumbnailProducers);
    return newBitmapCacheGetToDecodeSequence(inputProducerAfterDecode);
  }

  /**
   * Same as {@code newBitmapCacheGetToBitmapCacheSequence} but with an extra DecodeProducer.
   * @param inputProducer producer providing the input to the decode
   * @return bitmap cache get to decode sequence
   */
  private Producer<CloseableReference<CloseableImage>> newBitmapCacheGetToDecodeSequence(
      Producer<EncodedImage> inputProducer) {
    DecodeProducer decodeProducer = mProducerFactory.newDecodeProducer(inputProducer);
    return newBitmapCacheGetToBitmapCacheSequence(decodeProducer);
  }

  /**
   * encoded cache multiplex -> encoded cache -> (disk cache) -> (webp transcode)
   * @param inputProducer producer providing the input to the transcode
   * @return encoded cache multiplex to webp transcode sequence
   */
  private Producer<EncodedImage> newEncodedCacheMultiplexToTranscodeSequence(
      Producer<EncodedImage> inputProducer) {
    if (WebpSupportStatus.sIsWebpSupportRequired &&
        (!mWebpSupportEnabled || WebpSupportStatus.sWebpBitmapFactory == null)) {
      inputProducer = mProducerFactory.newWebpTranscodeProducer(inputProducer);
    }
    if (mDiskCacheEnabled) {
      inputProducer = newDiskCacheSequence(inputProducer);
    }
    EncodedMemoryCacheProducer encodedMemoryCacheProducer =
        mProducerFactory.newEncodedMemoryCacheProducer(inputProducer);
    return mProducerFactory.newEncodedCacheKeyMultiplexProducer(encodedMemoryCacheProducer);
  }

  private Producer<EncodedImage> newDiskCacheSequence(Producer<EncodedImage> inputProducer) {
    Producer<EncodedImage> cacheWriteProducer;
    if (mPartialImageCachingEnabled) {
      Producer<EncodedImage> partialDiskCacheProducer =
          mProducerFactory.newPartialDiskCacheProducer(inputProducer);
      cacheWriteProducer = mProducerFactory.newDiskCacheWriteProducer(partialDiskCacheProducer);
    } else {
      cacheWriteProducer = mProducerFactory.newDiskCacheWriteProducer(inputProducer);
    }
    return mProducerFactory.newDiskCacheReadProducer(cacheWriteProducer);
  }

  /**
   * Bitmap cache get -> thread hand off -> multiplex -> bitmap cache
   * @param inputProducer producer providing the input to the bitmap cache
   * @return bitmap cache get to bitmap cache sequence
   */
  private Producer<CloseableReference<CloseableImage>> newBitmapCacheGetToBitmapCacheSequence(
      Producer<CloseableReference<CloseableImage>> inputProducer) {
    BitmapMemoryCacheProducer bitmapMemoryCacheProducer =
        mProducerFactory.newBitmapMemoryCacheProducer(inputProducer);
    BitmapMemoryCacheKeyMultiplexProducer bitmapKeyMultiplexProducer =
        mProducerFactory.newBitmapMemoryCacheKeyMultiplexProducer(bitmapMemoryCacheProducer);
    ThreadHandoffProducer<CloseableReference<CloseableImage>> threadHandoffProducer =
        mProducerFactory.newBackgroundThreadHandoffProducer(
            bitmapKeyMultiplexProducer,
            mThreadHandoffProducerQueue);
    return mProducerFactory.newBitmapMemoryCacheGetProducer(threadHandoffProducer);
  }

  /**
   * Branch on separate images
   *   -> thumbnail resize and rotate -> thumbnail producers as provided
   *   -> local image resize and rotate -> add meta data producer
   * @param inputProducer producer providing the input to add meta data producer
   * @param thumbnailProducers the thumbnail producers from which to request the image before
   * falling back to the full image producer sequence
   * @return local transformations sequence
   */
  private Producer<EncodedImage> newLocalTransformationsSequence(
      Producer<EncodedImage> inputProducer,
      ThumbnailProducer<EncodedImage>[] thumbnailProducers) {
    Producer<EncodedImage> localImageProducer =
        ProducerFactory.newAddImageTransformMetaDataProducer(inputProducer);
    localImageProducer =
        mProducerFactory.newResizeAndRotateProducer(
            localImageProducer,
            true,
            mUseDownsamplingRatio);
    ThrottlingProducer<EncodedImage>
        localImageThrottlingProducer =
        mProducerFactory.newThrottlingProducer(localImageProducer);
    return mProducerFactory.newBranchOnSeparateImagesProducer(
        newLocalThumbnailProducer(thumbnailProducers),
        localImageThrottlingProducer);
  }

  private Producer<EncodedImage> newLocalThumbnailProducer(
      ThumbnailProducer<EncodedImage>[] thumbnailProducers) {
    ThumbnailBranchProducer thumbnailBranchProducer =
        mProducerFactory.newThumbnailBranchProducer(thumbnailProducers);

    return mProducerFactory.newResizeAndRotateProducer(
        thumbnailBranchProducer,
        true,
        mUseDownsamplingRatio);
  }

  /**
   * post-processor producer -> copy producer -> inputProducer
   */
  private synchronized Producer<CloseableReference<CloseableImage>> getPostprocessorSequence(
      Producer<CloseableReference<CloseableImage>> inputProducer) {
    if (!mPostprocessorSequences.containsKey(inputProducer)) {
      PostprocessorProducer postprocessorProducer =
          mProducerFactory.newPostprocessorProducer(inputProducer);
      PostprocessedBitmapMemoryCacheProducer postprocessedBitmapMemoryCacheProducer =
          mProducerFactory.newPostprocessorBitmapMemoryCacheProducer(postprocessorProducer);
      mPostprocessorSequences.put(inputProducer, postprocessedBitmapMemoryCacheProducer);
    }
    return mPostprocessorSequences.get(inputProducer);
  }

  /**
   * swallow result producer -> inputProducer
   */
  private synchronized Producer<Void> getDecodedImagePrefetchSequence(
      Producer<CloseableReference<CloseableImage>> inputProducer) {
    if (!mCloseableImagePrefetchSequences.containsKey(inputProducer)) {
      SwallowResultProducer<CloseableReference<CloseableImage>> swallowResultProducer =
          mProducerFactory.newSwallowResultProducer(inputProducer);
      mCloseableImagePrefetchSequences.put(inputProducer, swallowResultProducer);
    }
    return mCloseableImagePrefetchSequences.get(inputProducer);
  }

  /**
   * bitmap prepare producer -> inputProducer
   */
  private synchronized Producer<CloseableReference<CloseableImage>> getBitmapPrepareSequence(
      Producer<CloseableReference<CloseableImage>> inputProducer) {
    Producer<CloseableReference<CloseableImage>> bitmapPrepareProducer =
        mBitmapPrepareSequences.get(inputProducer);

    if (bitmapPrepareProducer == null) {
      bitmapPrepareProducer = mProducerFactory.newBitmapPrepareProducer(inputProducer);
      mBitmapPrepareSequences.put(inputProducer, bitmapPrepareProducer);
    }

    return bitmapPrepareProducer;
  }

  private static String getShortenedUriString(Uri uri) {
    final String uriString = String.valueOf(uri);
    return uriString.length() > 30 ? uriString.substring(0, 30) + "..." : uriString;
  }
}
