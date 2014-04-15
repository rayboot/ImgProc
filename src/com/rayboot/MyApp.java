package com.rayboot;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import com.rayboot.tasks.PhotupThreadFactory;
import com.rayboot.util.Flags;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import uk.co.senab.bitmapcache.BitmapLruCache;

/**
 * @author rayboot
 * @from 14-4-15 10:44
 * @TODO
 */
public class MyApp extends Application
{
    private ExecutorService  mMultiThreadExecutor,mSingleThreadExecutor;
    static final float EXECUTOR_POOL_SIZE_PER_CORE = 1.5f;
    public static final String THREAD_FILTERS = "filters_thread";
    private BitmapLruCache mImageCache;

    public static MyApp getApplication(Context context) {
        return (MyApp) context.getApplicationContext();
    }

    public ExecutorService getPhotoFilterThreadExecutorService() {
        if (null == mSingleThreadExecutor || mSingleThreadExecutor.isShutdown()) {
            mSingleThreadExecutor = Executors
                    .newSingleThreadExecutor(
                            new PhotupThreadFactory(THREAD_FILTERS));
        }
        return mSingleThreadExecutor;
    }

    public ExecutorService getMultiThreadExecutorService() {
        if (null == mMultiThreadExecutor || mMultiThreadExecutor.isShutdown()) {
            final int numThreads = Math.round(Runtime.getRuntime().availableProcessors()
                    * EXECUTOR_POOL_SIZE_PER_CORE);
            mMultiThreadExecutor = Executors
                    .newFixedThreadPool(numThreads, new PhotupThreadFactory());

            if (Flags.DEBUG) {
                Log.d("MyApp", "MultiThreadExecutor created with "
                        + numThreads
                        + " threads");
            }
        }
        return mMultiThreadExecutor;
    }

    public BitmapLruCache getImageCache() {
        if (null == mImageCache) {
            mImageCache = new BitmapLruCache(this, Constants.IMAGE_CACHE_HEAP_PERCENTAGE);
        }
        return mImageCache;
    }

    @SuppressWarnings("deprecation")
    public int getSmallestScreenDimension() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        return Math.min(display.getHeight(), display.getWidth());
    }
}
