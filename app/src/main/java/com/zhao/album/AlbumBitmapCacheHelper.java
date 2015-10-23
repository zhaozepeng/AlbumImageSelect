package com.zhao.album;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author: zzp
 * @since: 2015-06-10
 * Description: 相册页加载图片，省内存，防止oom
 */
public class AlbumBitmapCacheHelper {
    //线程安全的单例模式
    private volatile static AlbumBitmapCacheHelper instance = null;
    private LruCache<String, Bitmap> cache;
    /**
     * 用来优化图片的展示效果，保存当前显示的图片path
     */
    private ArrayList<String> currentShowString;
//    private ContentResolver cr;

    private AlbumBitmapCacheHelper() {
        //分配1/4的运行时内存给图片显示
        final int memory = (int) (Runtime.getRuntime().maxMemory() / 1024 / 4);

        cache = new LruCache<String, Bitmap>(memory) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                //获取每张bitmap大小
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };

        currentShowString = new ArrayList<String>();
//        cr = AppContext.getInstance().getContentResolver();
    }

    /**
     * 释放所有的内存
     */
    public void releaseAllSizeCache(){
        cache.evictAll();
        cache.resize(1);
    }

    public void releaseHalfSizeCache() {
        cache.resize((int) (Runtime.getRuntime().maxMemory() / 1024 / 8));
    }

    public void resizeCache() {
        cache.resize((int) (Runtime.getRuntime().maxMemory() / 1024 / 4));
    }

    /**
     * 选择完毕，直接释放缓存所占的内存
     */
    public void clearCache() {
        cache.evictAll();
        cache = null;
        instance = null;
    }

    public static AlbumBitmapCacheHelper getInstance() {
        if (instance == null) {
            synchronized (AlbumBitmapCacheHelper.class) {
                if (instance == null) {
                    instance = new AlbumBitmapCacheHelper();
                }
            }
        }
        return instance;
    }

    /**
     * 通过图片的path回调该图片的bitmap
     *
     * @param path     图片地址
     * @param width    需要显示图片的宽度，0代表显示完整图片
     * @param height   需要显示图片的高度，0代表显示完整图片
     * @param callback 加载bitmap成功回调
     * @param objects  用来直接返回标识
     */
    public Bitmap getBitmap(final String path, int width, int height, final ILoadImageCallback callback, Object... objects){
        Bitmap bitmap = getBitmapFromCache(path, width, height);
        //如果能够从缓存中获取符合要求的图片，则直接回调
        if (bitmap != null) {
            Log.e("zhao", "get bitmap from cache");
        } else {
            decodeBitmapFromPath(path, width, height, callback, objects);
        }
        return bitmap;
    }

    //try another size to get better display
    ThreadPoolExecutor tpe = new ThreadPoolExecutor(2, 5, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
//    ExecutorService tpe = Executors.newFixedThreadPool(1);

    /**
     * 通过path获取图片bitmap
     */
    private void decodeBitmapFromPath(final String path, final int width, final int height, final ILoadImageCallback callback, final Object... objects) throws OutOfMemoryError {
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                callback.onLoadImageCallBack((Bitmap) msg.obj, path, objects);
            }
        };
        //防止主线程卡顿
        tpe.execute(new Runnable() {
            @Override
            public void run() {
                if (!currentShowString.contains(path)||cache==null) {
                    return;
                }
                Bitmap bitmap = null;
                //返回大图,屏幕宽度为准
                if (width == 0 || height == 0) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(path, options);
                    options.inSampleSize = computeScale(options, ((WindowManager) (AppContext.getInstance().getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay().getWidth(), ((WindowManager) (AppContext.getInstance().getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay().getWidth());
                    options.inJustDecodeBounds = false;
                    try {
                        bitmap = BitmapFactory.decodeFile(path, options);
                    } catch (OutOfMemoryError e) {
                        releaseAllSizeCache();
                        bitmap = BitmapFactory.decodeFile(path, options);
                    }
                } else {
                    //返回小图，第一步，从temp目录下取该图片指定大小的缓存，如果取不到，
                    // 第二步，计算samplesize,如果samplesize > 4,
                    // 第三步则将压缩后的图片存入temp目录下，以便下次快速取出
                    String hash = CommonUtil.md5(path+"_"+width+"_"+height);
                    File file = new File(CommonUtil.getDataPath());
                    if (!file.exists())
                        file.mkdirs();
                    //临时文件的文件名
                    String tempPath = CommonUtil.getDataPath() + hash + ".temp";
                    File picFile = new File(path);
                    File tempFile = new File(tempPath);
                    //如果该文件存在,并且temp文件的创建时间要原文件之后
                    if (tempFile.exists() && (picFile.lastModified() <= tempFile.lastModified()))
                        bitmap = BitmapFactory.decodeFile(tempPath);
                    //无法在临时文件的缩略图目录找到该图片，于是执行第二步
                    if (bitmap == null) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(path, options);
                        options.inSampleSize = computeScale(options, width, height);
                        options.inJustDecodeBounds = false;
                        //获取手机自带缩略图,速度依旧很慢，所以该方案放弃
//                    if(objects.length != 0){
//                        long start = System.currentTimeMillis();
//                        bitmap = MediaStore.Images.Thumbnails.getThumbnail(cr, Long.parseLong(objects[0].toString()),
//                                MediaStore.Video.Thumbnails.MINI_KIND, options);
//                    }else{
                        try {
                            bitmap = BitmapFactory.decodeFile(path, options);
                        }catch (OutOfMemoryError error){
                            bitmap = null;
                        }
                        if (bitmap != null && cache!=null) {
                            bitmap = centerSquareScaleBitmap(bitmap, ((bitmap.getWidth() > bitmap.getHeight()) ? bitmap.getHeight() : bitmap.getWidth()));
                        }
                        //第三步,如果缩放比例大于4，该图的加载会非常慢，所以将该图保存到临时目录下以便下次的快速加载
                        if (options.inSampleSize>=4 && bitmap!=null) {
                            try {
                                file = new File(tempPath);
                                if (!file.exists())
                                    file.createNewFile();
                                else {
                                    file.delete();
                                    file.createNewFile();
                                }
                                FileOutputStream fos = new FileOutputStream(file);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                                fos.write(baos.toByteArray());
                                fos.flush();
                                fos.close();
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
//                    }
                    }else{
                        //从temp目录加载出来的图片也要放入到cache中
                        if (bitmap != null && cache!=null) {
                            bitmap = centerSquareScaleBitmap(bitmap, ((bitmap.getWidth() > bitmap.getHeight()) ? bitmap.getHeight() : bitmap.getWidth()));
                        }
                    }
                }
                if (bitmap != null && cache!=null)
                    cache.put(path +"_"+ width +"_"+height, bitmap);
                Message msg = Message.obtain();
                msg.obj = bitmap;
                handler.sendMessage(msg);
            }
        });
    }

    /**
     * @param bitmap     原图
     * @param edgeLength 希望得到的正方形部分的边长
     * @return 缩放截取正中部分后的位图。
     */
    public static Bitmap centerSquareScaleBitmap(Bitmap bitmap, int edgeLength) {
        if (null == bitmap || edgeLength <= 0) {
            return null;
        }
        Bitmap result = bitmap;
        int widthOrg = bitmap.getWidth();
        int heightOrg = bitmap.getHeight();

        //从图中截取正中间的正方形部分。
        int xTopLeft = (widthOrg - edgeLength) / 2;
        int yTopLeft = (heightOrg - edgeLength) / 2;

        if (xTopLeft == 0 && yTopLeft == 0) return result;

        try {
            result = Bitmap.createBitmap(bitmap, xTopLeft, yTopLeft, edgeLength, edgeLength);
            bitmap.recycle();
        } catch (OutOfMemoryError e) {
            return result;
        }

        return result;
    }

    /**
     * 计算缩放比例
     */
    private int computeScale(BitmapFactory.Options options, int width, int height) {
        if (options == null) return 1;
        int widthScale = (int)((float) options.outWidth / (float) width);
        int heightScale = (int)((float) options.outHeight / (float) height);
        //选择缩放比例较大的那个
        int scale = (widthScale > heightScale ? widthScale : heightScale);
        if (scale < 1) scale = 1;
        return scale;
    }

    /**
     * 获取lrucache中的图片，如果该图片的宽度和长度无法和需要的相符，则返回null
     *
     * @param path   图片地址,key
     * @param width  需要的图片宽度
     * @param height 需要的图片长度
     * @return 图片value
     */
    private Bitmap getBitmapFromCache(final String path, int width, int height) {
        return cache.get(path +"_"+ width +"_"+height);
    }

    /**
     * 将要展示的path加入到list
     */
    public void addPathToShowlist(String path) {
        currentShowString.add(path);
    }

    /**
     * 从展示list中删除该path
     */
    public void removePathFromShowlist(String path) {
        currentShowString.remove(path);
    }

    /**
     * 加载图片成功的接口回调
     */
    public interface ILoadImageCallback {
        void onLoadImageCallBack(Bitmap bitmap, String path, Object... objects);
    }

    /**
     * 移出该threads中的所有线程
     */
    public void removeAllThreads() {
        currentShowString.clear();
        for (Runnable runnable : tpe.getQueue()) {
            tpe.remove(runnable);
        }
    }

}
