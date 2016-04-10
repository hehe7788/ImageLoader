package com.ycc.imageloader.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Created by L.Y.C on 2016/4/10.
 * lru cache
 */
public class ImageLoader {
    private static final String TAG = "ImageLoader";
    private  static ImageLoader mInstance;
    /**
     * 图片缓存对象，管理所有图片占据的内存
     */
    private LruCache<String, Bitmap> mLruCache;

    /**
     * 线程池
     */
    private ExecutorService mThreadPool;
    private static final  int DEAFULT_THREAD_COUNT = 1;

    /**
     * 队列调度方式
     */
    private Type mType = Type.LIFO;

    /**
     * 任务队列
     */
    private LinkedList<Runnable> mTaskQueue;

    /**
     * 后台轮询线程
     */
    private  Thread mPoolThread;
    private Handler mPoolThreadHandler;

    /**
     * UI线程中的Handler
     */
    private Handler mUIHandler;
    /**
     * 信号量
     * mSemaphorePoolThreadHandler防止mPoolThreadHandler空指针
     * mSemaphoreThreadPool使得线程池中的任务不超过DEAFULT_THREAD_COUNT
     */
    private Semaphore mSemaphorePoolThreadHandler = new Semaphore(0);
    private Semaphore mSemaphoreThreadPool;

    public enum Type {
        LIFO , FIFO
    }

    private ImageLoader(int threadCount, Type type) {
        init(threadCount, type);
    }

    private void init(int threadCount, Type type) {
        //后台轮询线程，及其handler
        mPoolThread = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mPoolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        //todo 从线程池中取出一个任务去执行
                        mThreadPool.execute(getTask());

                        //使用信号量阻塞线程，使线程数最多为threadCount
                        try {
                            mSemaphoreThreadPool.acquire();
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.toString());
                        }

                    }
                };
                //释放一个信号量
                mSemaphorePoolThreadHandler.release();
                Looper.loop();
            }
        };

        mPoolThread.start();

        //获取应用最大可用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheMemory = maxMemory / 8;

        mLruCache = new LruCache<String, Bitmap>(cacheMemory) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                //get每个bitmap占据的内存
                return value.getRowBytes() * value.getHeight();
            }
        };

        //线程池
        mThreadPool = Executors.newFixedThreadPool(threadCount);

        mTaskQueue = new LinkedList<>();
        mType = type;

        //限定mSemaphoreThreadPool的数量
        mSemaphoreThreadPool = new Semaphore(threadCount);
    }

    /**
     * 根据类型，从任务队列取一个任务
     * @return
     */
    private Runnable getTask() {
        if (mType == Type.FIFO) {
            return mTaskQueue.removeFirst();
        } else if (mType == Type.LIFO) {
            return mTaskQueue.removeLast();
        }
        return null;
    }

    public static ImageLoader getInstance(int threadCount, Type type) {
        //单例模式双重判断，
        // 如果直接将方法写成同步的，可能效率低，第一个if提高效率
        if (mInstance == null) {
            //此处可能有多个线程竞争资源，所以第二个if也是必须的
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader(threadCount, type);
                }
            }
        }

        return mInstance;
    }

    /**
     * 核心载入图片的方法，运行在UI线程
     * @param path 图片路径
     * @param imageView
     */
    public void loadImage(final String path, final ImageView imageView) {
        //在异步加载完成时比较标识与当前行Item的标识是否一致，一致则显示
        imageView.setTag(path);
        if (mUIHandler == null) {
            mUIHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    //todo 获取图片，为imageview回调设置图片 没看懂
                    //此处不能用msg.obj来取bitmap，msg.obj取到的bitmaphe和loadImage的参数path, imageview很可能不对应
                    ImageBeanHolder holder = (ImageBeanHolder) msg.obj;
                    Bitmap bitmap = holder.bitmap;
                    ImageView imageView = holder.imageView;
                    String path = holder.path;

                    if (imageView.getTag().toString().equals(path)) {
                        imageView.setImageBitmap(bitmap);
                    }
                }
            };
        }

        //从缓存中获取bitmap
        Bitmap bitmap = getBitmapFromLruCache(path);

        if (bitmap != null) {
            refreshBitmap(bitmap, path, imageView);
        } else {
            //没有从缓存中获取
            addTask(new Runnable(){
                @Override
                public void run() {
                    //todo 加载图片 图片的压缩
                    //1.获得图片显示大小
                    ImageSize imageSize = getImageViewSize(imageView);
                    //2.压缩图片
                    Bitmap bm = decodeSampleBitmapFromPath(path, imageSize.width, imageSize.height);
                    //3.把图片加入到缓存
                    addBitmapToLruCache(path, bm);

                    refreshBitmap(bm, path, imageView);

                    //执行完一个任务释放一个信号量
                    mSemaphoreThreadPool.release();
                }
            });
        }
    }

    private void refreshBitmap(Bitmap bitmap, String path, ImageView imageView) {
        Message message = Message.obtain();

        ImageBeanHolder holder = new ImageBeanHolder();
        holder.bitmap = bitmap;
        holder.path = path;
        holder.imageView = imageView;

        message.obj = holder;
        mUIHandler.sendMessage(message);
    }

    /**
     * 将图片加入LruCache
     * @param path
     * @param bm
     */
    private void addBitmapToLruCache(String path, Bitmap bm) {
        if (getBitmapFromLruCache(path) == null) {
            if (bm != null) {
                mLruCache.put(path, bm);
            }
        }
    }

    /**
     * 根据需要显示的宽高压缩图片
     * @param path
     * @param width
     * @param height
     * @return
     */
    private Bitmap decodeSampleBitmapFromPath(String path, int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        //获取图片宽高但不把图片加载到内存中
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, width, height);

        //已获取压缩比，再次解析图片，把图片加载到内存
        options.inJustDecodeBounds= false;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);

        return bitmap;
    }

    /**
     * 根据需求和实际宽高计算压缩比
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int width = options.outWidth;
        int height = options.outHeight;

        int inSampleSize = 1;
        if (width > reqWidth || height > reqHeight) {
            int widthRadio = Math.round(width * 1.0f/ reqWidth);
            int heightRadio = Math.round(height * 1.0f / reqHeight);

            inSampleSize = Math.max(widthRadio, heightRadio);
        }
        return 0;
    }

    /**
     * 获取适当压缩的宽高
     * todo 低于sdk16则使用反射获取maxwidth
     */
    private ImageSize getImageViewSize(ImageView imageView) {
        ImageSize imageSize = new ImageSize();
        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();

        //获取imageview的实际宽度
        int width = imageView.getWidth();
        //如果view是第一次生成，拿不到宽度，则取layout中声明的宽度（只能取到明确宽度）
        if (width <= 0) width = lp.width;
        //如果layout中声明的match_parent（拿到-2） wrap_content（拿到-1），则获取最大值（如果设置了maxWidth）
        if (width <= 0) width = getImageViewFieldValue(imageView, "mMaxWidth");
        //如果没有设置maxWidth，取屏幕宽度
        if (width <= 0) width = displayMetrics.widthPixels;

        int height = imageView.getHeight();
        if (height <= 0) height = lp.height;
        if (height <= 0) height = getImageViewFieldValue(imageView, "mMaxHeight");
        if (height <= 0) height = displayMetrics.heightPixels;

        imageSize.width = width;
        imageSize.height = height;
        return imageSize;
    }

    /**
     * 通过反射获取某个对象的某个属性值
     */
    private static int getImageViewFieldValue(Object object, String fieldName) {
        int value = 0;
        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);

            int fieldValue = field.getInt(object);
            if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE) {
                value = fieldValue;
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return value;
    }

    /**
     * 创建一个task且发送一个通知去提醒后台轮询线程
     * 由于mPoolThreadHandler是在另一个线程中初始化的，在此使用时无法保证其已经初始化
     * 如果没有初始化，信号量acquire阻塞，直到另一个mSemaphore.release()，acquire方法继续执行
     *
     * @param runnable
     */
    private synchronized void addTask(Runnable runnable) {
        mTaskQueue.add(runnable);

        //需要等待mPoolThreadHandler初始化完成后
        try {
            if (mPoolThreadHandler == null) {
                mSemaphorePoolThreadHandler.acquire();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
        mPoolThreadHandler.sendEmptyMessage(0x110);

    }

    /**
     * 从lrucache缓存中获取bitmap
     * @param key
     * @return
     */
    private Bitmap getBitmapFromLruCache(String key) {
        return mLruCache.get(key);
    }

    /**
     * 持有bitmap，imageview，path
     * 避免错乱
     */
    private class ImageBeanHolder {
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }


    private class ImageSize {
        int width;
        int height;
    }
}
