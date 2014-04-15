package com.rayboot.ImgProc;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import com.rayboot.model.Filter;
import com.rayboot.model.PhotoObj;
import com.rayboot.views.FiltersRadioGroup;
import com.rayboot.views.MultiTouchImageView;
import com.rayboot.views.PhotoTagItemLayout;

/**
 * @author rayboot
 * @from 14-4-15 15:37
 * @TODO
 */
public class PhotoEditActivity extends Activity
        implements RadioGroup.OnCheckedChangeListener,ViewPager.OnPageChangeListener
{
    private FiltersRadioGroup mFilterGroup;
    private ViewGroup mContentView;
    private ViewPager mViewPager;
    private PagerAdapter mAdapter;
    private boolean mIgnoreFilterCheckCallback = false;

    @Override protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_viewer);
        mContentView = (ViewGroup) findViewById(R.id.fl_root);

        mViewPager = (ViewPager) findViewById(R.id.vp_photos);
        mViewPager.setOffscreenPageLimit(2);
        mViewPager.setPageMargin(
                getResources().getDimensionPixelSize(R.dimen.viewpager_margin));
        mViewPager.setOnPageChangeListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_photo_viewer, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {

        switch (item.getItemId())
        {
        case android.R.id.home:
            finish();
            return true;
        case R.id.menu_filters:
            showFiltersView();
            return true;
        case R.id.menu_caption:
            return true;
        case R.id.menu_rotate:
            rotateCurrentPhoto();
            return true;
        case R.id.menu_place:
            //startPlaceFragment();
            return true;
        case R.id.menu_crop:
            //CropImageActivity.CROP_SELECTION = getCurrentUpload();
            //startActivityForResult(new Intent(this, CropImageActivity.class),
            //        REQUEST_CROP_PHOTO);
            return true;
        case R.id.menu_reset:
            resetCurrentPhoto();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showFiltersView()
    {
        ActionBar ab = getActionBar();
        if (ab.isShowing())
        {
            ab.hide();
        }

        if (null == mFilterGroup)
        {
            View view = getLayoutInflater().inflate(R.layout.layout_filters,
                    mContentView);
            mFilterGroup =
                    (FiltersRadioGroup) view.findViewById(R.id.rg_filters);
            mFilterGroup.setOnCheckedChangeListener(this);
        }

        mFilterGroup.show();
        updateFiltersView();
    }

    private void updateFiltersView() {
        mIgnoreFilterCheckCallback = true;
        mFilterGroup.setPhotoUpload(getCurrentUpload());
        mIgnoreFilterCheckCallback = false;
    }

    private void rotateCurrentPhoto() {
        PhotoTagItemLayout currentView = getCurrentView();
        PhotoObj upload = currentView.getPhotoSelection();
        upload.rotateClockwise();
        reloadView(currentView);
    }

    private void resetCurrentPhoto() {
        PhotoTagItemLayout currentView = getCurrentView();
        PhotoObj upload = currentView.getPhotoSelection();

        upload.reset();
        reloadView(currentView);
    }

    @Override public void onCheckedChanged(RadioGroup group, int checkedId)
    {
        if (!mIgnoreFilterCheckCallback) {
            Filter filter = checkedId != -1 ? Filter.mapFromId(checkedId) : null;
            PhotoTagItemLayout currentView = getCurrentView();
            PhotoObj upload = currentView.getPhotoSelection();

            upload.setFilterUsed(filter);
            reloadView(currentView);
        }
    }

    @Override public void onPageScrolled(int i, float v, int i2)
    {

    }

    @Override public void onPageSelected(int i)
    {
        PhotoTagItemLayout currentView = getCurrentView();

        if (null != currentView) {
            PhotoObj upload = currentView.getPhotoSelection();

            if (null != upload) {
                getActionBar().setTitle(upload.toString());

                // Request Face Detection
                currentView.getImageView().postFaceDetection(upload);

                if (null != mFilterGroup && mFilterGroup.getVisibility() == View.VISIBLE) {
                    updateFiltersView();
                }
            }
        }
    }

    @Override public void onPageScrollStateChanged(int state)
    {
        if (state != ViewPager.SCROLL_STATE_IDLE) {
            clearFaceDetectionPasses();
        }
    }

    private void clearFaceDetectionPasses() {
        for (int i = 0, z = mViewPager.getChildCount(); i < z; i++) {
            PhotoTagItemLayout child = (PhotoTagItemLayout) mViewPager.getChildAt(i);
            if (null != child) {
                child.getImageView().clearFaceDetection();
            }
        }
    }

    private PhotoObj getCurrentUpload() {
        PhotoTagItemLayout view = getCurrentView();
        if (null != view) {
            return view.getPhotoSelection();
        }
        return null;
    }

    private PhotoTagItemLayout getCurrentView() {
        final int currentPos = mViewPager.getCurrentItem();

        for (int i = 0, z = mViewPager.getChildCount(); i < z; i++) {
            PhotoTagItemLayout child = (PhotoTagItemLayout) mViewPager.getChildAt(i);
            if (null != child && child.getPosition() == currentPos) {
                return child;
            }
        }

        return null;
    }

    private void reloadView(PhotoTagItemLayout currentView) {
        if (null != currentView) {
            MultiTouchImageView imageView = currentView.getImageView();
            PhotoObj selection = currentView.getPhotoSelection();
            imageView.requestFullSize(selection, true, false, null);
        }
    }
}
