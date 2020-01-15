/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.fresco.samples.showcase.imagepipeline;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.fresco.samples.showcase.BaseShowcaseFragment;
import com.facebook.fresco.samples.showcase.R;
import com.facebook.fresco.samples.showcase.misc.ImageUriProvider;
import com.facebook.fresco.samples.showcase.postprocessor.BenchmarkPostprocessorForDuplicatedBitmapInPlace;
import com.facebook.imagepipeline.postprocessors.EllipsePostprocessor;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.imagepipeline.request.Postprocessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Fragment that illustrates how to use the image pipeline directly in order to create
 * notifications.
 */
public class ImagePipelineEllipseRoundingFragment extends BaseShowcaseFragment
    implements DurationCallback {

  private List<Entry> mSpinnerEntries = new ArrayList<>();

  private Button mButton;
  private SimpleDraweeView mDraweeMain;
  private Spinner mSpinner;
  private Uri mUri;

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_imagepipeline_postprocessor, container, false);
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    mSpinnerEntries = getSpinnerItems();
    mUri = sampleUris().createSampleUri(ImageUriProvider.ImageSize.L);

    mButton = (Button) view.findViewById(R.id.button);
    mDraweeMain = (SimpleDraweeView) view.findViewById(R.id.drawee_view);
    mSpinner = (Spinner) view.findViewById(R.id.spinner);

    mSpinner.setAdapter(new SimplePostprocessorAdapter());
    mSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            final Entry spinnerEntry = mSpinnerEntries.get(position);
            setPostprocessor(spinnerEntry.postprocessor);
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
    mSpinner.setSelection(0);

    mButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            final Entry spinnerEntry = mSpinnerEntries.get(mSpinner.getSelectedItemPosition());
            setPostprocessor(spinnerEntry.postprocessor);
          }
        });
  }

  @Override
  public int getTitleId() {
    return R.string.imagepipeline_ellipse_rounding_title;
  }

  @Override
  public void showDuration(long startNs) {
    final float deltaMs = startNs / 1e6f;
    final String message = String.format((Locale) null, "Duration: %.1f ms", deltaMs);
    getActivity()
        .runOnUiThread(
            new Runnable() {
              @Override
              public void run() {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
              }
            });
  }

  private void setPostprocessor(Postprocessor postprocessor) {
    final ImageRequest imageRequest =
        ImageRequestBuilder.newBuilderWithSource(mUri).setPostprocessor(postprocessor).build();

    final DraweeController draweeController =
        Fresco.newDraweeControllerBuilder()
            .setOldController(mDraweeMain.getController())
            .setImageRequest(imageRequest)
            .build();

    mDraweeMain.setController(draweeController);
  }

  private class SimplePostprocessorAdapter extends BaseAdapter {

    @Override
    public int getCount() {
      return mSpinnerEntries.size();
    }

    @Override
    public Object getItem(int position) {
      return mSpinnerEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      final LayoutInflater layoutInflater = getLayoutInflater(null);

      final View view =
          convertView != null
              ? convertView
              : layoutInflater.inflate(
                  android.R.layout.simple_spinner_dropdown_item, parent, false);

      final TextView textView = (TextView) view.findViewById(android.R.id.text1);
      textView.setText(mSpinnerEntries.get(position).descriptionId);

      return view;
    }
  }

  private List<Entry> getSpinnerItems() {
    int[] heights = {150, 250, 400, 450};
    int[] widths = {425, 600, 800, 1275};
    return Arrays.asList(
        new Entry(
            R.string.imagepipeline_ellipse_rounding_set_default,
            new BenchmarkPostprocessorForDuplicatedBitmapInPlace(
               this, new EllipsePostprocessor())),
        new Entry(
            R.string.imagepipeline_ellipse_rounding_set_custom,
            new BenchmarkPostprocessorForDuplicatedBitmapInPlace(
               this, new EllipsePostprocessor(400, 800))),
        new Entry(
             R.string.imagepipeline_ellipse_rounding_set_corners,
             new BenchmarkPostprocessorForDuplicatedBitmapInPlace(
               this, new EllipsePostprocessor(heights, widths))));
  }

  private static class Entry {

    final int descriptionId;
    final Postprocessor postprocessor;

    Entry(int descriptionId, Postprocessor postprocessor) {
      this.descriptionId = descriptionId;
      this.postprocessor = postprocessor;
    }
  }
}
