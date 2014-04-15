/*
 * Copyright 2013 Chris Banes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rayboot.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.HorizontalScrollView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.rayboot.ImgProc.R;
import com.rayboot.MyApp;
import com.rayboot.model.Filter;
import com.rayboot.model.PhotoObj;
import com.rayboot.tasks.PhotupThreadRunnable;
import java.util.concurrent.ExecutorService;

public class FiltersRadioGroup extends RadioGroup implements AnimationListener {

    static final class FilterRunnable extends PhotupThreadRunnable
    {

        // TODO Should make these WeakReferences
        private final Context mContext;
        private final RadioButton mButton;

        private final PhotoObj mPhotoFilterObj;
        private final Filter mFilter;

        public FilterRunnable(Context context, PhotoObj photoFilterObj, Filter filter,
                RadioButton button) {
            mContext = context;
            mPhotoFilterObj = photoFilterObj;
            mFilter = filter;
            mButton = button;
        }

        public void runImpl() {
            Bitmap bitmap = mPhotoFilterObj
                    .processBitmapUsingFilter(mPhotoFilterObj.getThumbnailImage(mContext), mFilter, false,
                            true);

            if (isInterrupted()) {
                bitmap.recycle();
                return;
            }

            final Drawable background = createDrawable(mContext.getResources(), bitmap);

            mButton.post(new Runnable() {
                @SuppressWarnings("deprecation")
                public void run() {
                    mButton.setBackgroundDrawable(background);
                }
            });
        }

        private Drawable createDrawable(final Resources resources, final Bitmap bitmap) {
            final StateListDrawable stateListD = new StateListDrawable();
            final Drawable bitmapDrawable = new BitmapDrawable(mContext.getResources(), bitmap);
            final int inset = resources.getDimensionPixelSize(R.dimen.gridview_item_margin);

            Drawable bgDrawable = new ColorDrawable(Color.TRANSPARENT);
            LayerDrawable layer = new LayerDrawable(new Drawable[]{bgDrawable, bitmapDrawable});
            layer.setLayerInset(1, inset, inset, inset, inset);
            stateListD.addState(new int[]{-android.R.attr.state_checked}, layer);

            bgDrawable = resources.getDrawable(R.drawable.photo_gallery_background);
            layer = new LayerDrawable(new Drawable[]{bgDrawable, bitmapDrawable});
            layer.setLayerInset(1, inset, inset, inset, inset);
            stateListD.addState(new int[]{android.R.attr.state_checked}, layer);

            return stateListD;
        }
    };

    private final Animation mSlideInBottomAnim, mSlideOutBottomAnim;
    private final ExecutorService mExecutor;

    public FiltersRadioGroup(Context context, AttributeSet attrs) {
        super(context, attrs);

        mExecutor = MyApp.getApplication(context).getPhotoFilterThreadExecutorService();

        mSlideInBottomAnim = AnimationUtils.loadAnimation(context, R.anim.slide_in_bottom);
        mSlideInBottomAnim.setAnimationListener(this);

        mSlideOutBottomAnim = AnimationUtils.loadAnimation(context, R.anim.slide_out_bottom);
        mSlideOutBottomAnim.setAnimationListener(this);

        addButtons(context);
    }

    private void addButtons(Context context) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        RadioButton button;
        for (Filter filter : Filter.values()) {
            button = (RadioButton) layoutInflater
                    .inflate(R.layout.layout_filters_item, this, false);
            button.setText(filter.getLabelId());
            button.setId(filter.getId());
            addView(button);
        }
    }

    public void setPhotoUpload(PhotoObj upload) {
        for (final Filter filter : Filter.values()) {
            final RadioButton button = (RadioButton) findViewById(filter.getId());
            mExecutor.submit(new FilterRunnable(getContext(), upload, filter, button));
        }

        Filter filter = upload.getFilterUsed();
        final int filterId = filter.getId();
        check(filterId);

        View child = findViewById(filterId);
        final int width = child.getWidth();

        final HorizontalScrollView scrollView = (HorizontalScrollView) getParent();
        final int dx = (filterId * width) - ((scrollView.getWidth() - width) / 2);
        scrollView.smoothScrollTo(dx, 0);
    }

    public void show() {
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
            startAnimation(mSlideInBottomAnim);
        }
    }

    public void hide() {
        if (getVisibility() == View.VISIBLE) {
            startAnimation(mSlideOutBottomAnim);
        }
    }

    public boolean isShowing() {
        return getVisibility() == View.VISIBLE && getAnimation() != mSlideOutBottomAnim;
    }

    public void onAnimationEnd(Animation animation) {
        if (animation == mSlideOutBottomAnim) {
            setVisibility(View.GONE);
        }
    }

    public void onAnimationRepeat(Animation animation) {
        // NO-OP
    }

    public void onAnimationStart(Animation animation) {
        // NO-OP
    }

    @Override
    public void setVisibility(int visibility) {
        View parent = (View) getParent();
        if (null != parent) {
            parent.setVisibility(visibility);
        }

        super.setVisibility(visibility);
    }
}
