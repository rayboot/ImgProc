package com.rayboot.model;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.FaceDetector;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import com.rayboot.Constants;
import com.rayboot.ImgProc.R;
import com.rayboot.MyApp;
import com.rayboot.listeners.OnFaceDetectionListener;
import com.rayboot.listeners.OnPhotoTagsChangedListener;
import com.rayboot.util.Flags;
import com.rayboot.util.PhotoProcessing;
import com.rayboot.util.Utils;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * @author rayboot
 * @from 14-4-15 16:02
 * @TODO
 */
public class PhotoObj
{
    private static final HashMap<Uri, PhotoObj> SELECTION_CACHE
            = new HashMap<Uri, PhotoObj>();

    private Uri mFullUri;
    private String mFullUriString;
    static final float CROP_THRESHOLD = 0.01f; // 1%
    static final int MINI_THUMBNAIL_SIZE = 300;
    static final int MICRO_THUMBNAIL_SIZE = 96;
    static final float MIN_CROP_VALUE = 0.0f;
    static final float MAX_CROP_VALUE = 1.0f;

    private float mCropLeft;
    private float mCropTop;
    private float mCropRight;
    private float mCropBottom;

    private int mUserRotation;
    private Filter mFilter;
    static final String LOG_TAG = "PhotoUpload";
    private boolean mCompletedDetection;
    private HashSet<PhotoTag> mTags;

    /**
     * Listeners
     */
    private WeakReference<OnFaceDetectionListener> mFaceDetectListener;
    private WeakReference<OnPhotoTagsChangedListener> mTagChangedListener;

    public static PhotoObj getSelection(Uri uri) {
        // Check whether we've already got a Selection cached
        PhotoObj item = SELECTION_CACHE.get(uri);

        if (null == item) {
            item = new PhotoObj(uri);
            SELECTION_CACHE.put(uri, item);
        }

        return item;
    }

    private PhotoObj(Uri uri) {
        mFullUri = uri;
        mFullUriString = uri.toString();
        reset();
    }
    public void reset() {
        mUserRotation = 0;
        mCropLeft = mCropTop = MIN_CROP_VALUE;
        mCropRight = mCropBottom = MAX_CROP_VALUE;
        mFilter = null;
        mCompletedDetection = false;
    }

    public Filter getFilterUsed() {
        if (null == mFilter) {
            mFilter = Filter.ORIGINAL;
        }
        return mFilter;
    }

    public void setFilterUsed(Filter filter) {
        mFilter = filter;
    }

    public boolean beenFiltered() {
        return null != mFilter && mFilter != Filter.ORIGINAL;
    }


    public boolean beenCropped() {
        return checkCropValues(mCropLeft, mCropTop, mCropRight, mCropBottom);
    }

    public RectF getCropValues() {
        return new RectF(mCropLeft, mCropTop, mCropRight, mCropBottom);
    }

    public RectF getCropValues(final int width, final int height) {
        return new RectF(mCropLeft * width, mCropTop * height, mCropRight * width,
                mCropBottom * height);
    }
    public void rotateClockwise() {
        mUserRotation += 90;
    }
    public int getUserRotation() {
        return mUserRotation % 360;
    }

    private static boolean checkCropValues(float left, float top, float right, float bottom) {
        return Math.max(left, top) >= (MIN_CROP_VALUE + CROP_THRESHOLD)
                || Math.min(right, bottom) <= (MAX_CROP_VALUE - CROP_THRESHOLD);
    }

    public Uri getOriginalPhotoUri() {
        if (null == mFullUri && !TextUtils.isEmpty(mFullUriString)) {
            mFullUri = Uri.parse(mFullUriString);
        }
        return mFullUri;
    }

    public Bitmap getThumbnailImage(Context context) {
        if (ContentResolver.SCHEME_CONTENT.equals(getOriginalPhotoUri().getScheme())) {
            return getThumbnailImageFromMediaStore(context);
        }

        final Resources res = context.getResources();
        int size = res.getBoolean(R.bool.load_mini_thumbnails) ? MINI_THUMBNAIL_SIZE
                : MICRO_THUMBNAIL_SIZE;
        if (size == MINI_THUMBNAIL_SIZE && res.getBoolean(R.bool.sample_mini_thumbnails)) {
            size /= 2;
        }

        try {
            Bitmap bitmap = Utils
                    .decodeImage(context.getContentResolver(),
                            getOriginalPhotoUri(), size);
            bitmap = Utils.rotate(bitmap, getExifRotation(context));
            return bitmap;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap getThumbnailImageFromMediaStore(Context context) {
        Resources res = context.getResources();

        final int kind = res.getBoolean(R.bool.load_mini_thumbnails) ? MediaStore.Images.Thumbnails.MINI_KIND
                : MediaStore.Images.Thumbnails.MICRO_KIND;

        BitmapFactory.Options opts = null;
        if (kind == MediaStore.Images.Thumbnails.MINI_KIND && res.getBoolean(R.bool.sample_mini_thumbnails)) {
            opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
        }

        try {
            final long id = Long.parseLong(getOriginalPhotoUri().getLastPathSegment());

            Bitmap bitmap = MediaStore.Images.Thumbnails.getThumbnail(
                    context.getContentResolver(), id, kind, opts);
            bitmap = Utils.rotate(bitmap, getExifRotation(context));
            return bitmap;
        } catch (Exception e) {
            if (Flags.DEBUG) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public int getExifRotation(Context context) {
        return Utils
                .getOrientationFromContentUri(context.getContentResolver(), getOriginalPhotoUri());
    }


    public Bitmap processBitmapUsingFilter(final Bitmap bitmap, final Filter filter,
            final boolean fullSize,
            final boolean modifyOriginal) {
        Utils.checkPhotoProcessingThread();

        PhotoProcessing.sendBitmapToNative(bitmap);
        if (modifyOriginal) {
            bitmap.recycle();
        }

        if (fullSize && beenCropped()) {
            RectF rect = getCropValues();
            PhotoProcessing.nativeCrop(rect.left, rect.top, rect.right, rect.bottom);
        }

        if (null != filter) {
            PhotoProcessing.filterPhoto(filter.getId());
        }

        switch (getUserRotation()) {
        case 90:
            PhotoProcessing.nativeRotate90();
            break;
        case 180:
            PhotoProcessing.nativeRotate180();
            break;
        case 270:
            PhotoProcessing.nativeRotate180();
            PhotoProcessing.nativeRotate90();
            break;
        }

        return PhotoProcessing.getBitmapFromNative(null);
    }

    public Bitmap processBitmap(Bitmap bitmap, final boolean fullSize,
            final boolean modifyOriginal) {
        if (requiresProcessing(fullSize)) {
            return processBitmapUsingFilter(bitmap, mFilter, fullSize, modifyOriginal);
        } else {
            return bitmap;
        }
    }

    public boolean requiresProcessing(final boolean fullSize) {
        return getUserRotation() != 0 || beenFiltered() || (fullSize && beenCropped());
    }

    public void setTagChangedListener(OnPhotoTagsChangedListener tagChangedListener) {
        mTagChangedListener = new WeakReference<OnPhotoTagsChangedListener>(tagChangedListener);
    }

    private void notifyTagListener(PhotoTag tag, boolean added) {
        if (null != mTagChangedListener) {
            OnPhotoTagsChangedListener listener = mTagChangedListener.get();
            if (null != listener) {
                listener.onPhotoTagsChanged(tag, added);
            }
        }
    }

    public void detectPhotoTags(final Bitmap originalBitmap) {
        // If we've already done Face detection, don't do it again...
        if (mCompletedDetection) {
            return;
        }

        final OnFaceDetectionListener listener = mFaceDetectListener.get();
        if (null != listener) {
            listener.onFaceDetectionStarted(this);
        }

        final int bitmapWidth = originalBitmap.getWidth();
        final int bitmapHeight = originalBitmap.getHeight();

        Bitmap bitmap = originalBitmap;

        // The Face detector only accepts 565 bitmaps, so create one if needed
        if (Bitmap.Config.RGB_565 != bitmap.getConfig()) {
            bitmap = originalBitmap.copy(Bitmap.Config.RGB_565, false);
        }

        final FaceDetector detector = new FaceDetector(bitmapWidth, bitmapHeight,
                Constants.FACE_DETECTOR_MAX_FACES);
        final FaceDetector.Face[] faces = new FaceDetector.Face[Constants.FACE_DETECTOR_MAX_FACES];
        final int detectedFaces = detector.findFaces(bitmap, faces);

        // We must have created a converted 565 bitmap
        if (bitmap != originalBitmap) {
            bitmap.recycle();
            bitmap = null;
        }

        if (Flags.DEBUG) {
            Log.d(LOG_TAG, "Detected Faces: " + detectedFaces);
        }

        FaceDetector.Face face;
        final PointF point = new PointF();
        for (int i = 0, z = faces.length; i < z; i++) {
            face = faces[i];
            if (null != face) {
                if (Flags.DEBUG) {
                    Log.d(LOG_TAG, "Detected Face with confidence: " + face.confidence());
                }
                face.getMidPoint(point);
                addPhotoTag(new PhotoTag(point.x, point.y, bitmapWidth, bitmapWidth));
            }
        }

        if (null != listener) {
            listener.onFaceDetectionFinished(this);
        }
        mFaceDetectListener = null;

        mCompletedDetection = true;
    }

    public void setFaceDetectionListener(OnFaceDetectionListener listener) {
        // No point keeping listener if we've already done a pass
        if (!mCompletedDetection) {
            mFaceDetectListener = new WeakReference<OnFaceDetectionListener>(listener);
        }
    }

    public boolean requiresFaceDetectPass() {
        return !mCompletedDetection;
    }

    public void addPhotoTag(PhotoTag tag) {
        if (null == mTags) {
            mTags = new HashSet<PhotoTag>();
        }
        mTags.add(tag);
        notifyTagListener(tag, true);
    }

    public List<PhotoTag> getPhotoTags() {
        if (null != mTags) {
            return new ArrayList<PhotoTag>(mTags);
        }
        return null;
    }

    public int getPhotoTagsCount() {
        return null != mTags ? mTags.size() : 0;
    }
    public int getFriendPhotoTagsCount() {
        int count = 0;
        if (getPhotoTagsCount() > 0) {
            for (PhotoTag tag : mTags) {
                if (!TextUtils.isEmpty(tag.getFriend())) {
                    count++;
                }
            }
        }
        return count;
    }

    public void removePhotoTag(PhotoTag tag) {
        if (null != mTags) {
            mTags.remove(tag);
            notifyTagListener(tag, false);

            if (mTags.isEmpty()) {
                mTags = null;
            }
        }
    }

    public String getDisplayImageKey() {
        return "dsply_" + getOriginalPhotoUri();
    }

    public String getThumbnailImageKey() {
        return "thumb_" + getOriginalPhotoUri();
    }

    public Bitmap getDisplayImage(Context context) {
        try {
            final int size = MyApp.getApplication(context).getSmallestScreenDimension();
            Bitmap bitmap = Utils
                    .decodeImage(context.getContentResolver(), getOriginalPhotoUri(), size);
            bitmap = Utils.rotate(bitmap, getExifRotation(context));
            return bitmap;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}