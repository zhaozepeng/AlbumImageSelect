package com.zhao.album;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.zzp.albumimageselect.R;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * @author: zzp
 * @since: 2015-06-10
 * Description: 仿微信选取手机所有图片，或者拍照界面
 */
public class PickOrTakeImageActivity extends Activity implements View.OnClickListener, AdapterView.OnItemClickListener,
        AbsListView.OnScrollListener, View.OnTouchListener{

    /** 按时间排序的所有图片list */
    private ArrayList<SingleImageModel> allImages;
    /** 按目录排序的所有图片list */
    private ArrayList<SingleImageDirectories> imageDirectories;
    /** 选中图片的信息 */
    ArrayList<String> picklist = new ArrayList<String>();

    private MyHandler handler;

    /** 当前显示的文件夹路径，全部-- -1 */
    private int currentShowPosition;

    private GridView gridView = null;
    private int firstVisibleItem = 0;
    private int currentState = SCROLL_STATE_IDLE;
    private int currentTouchState = MotionEvent.ACTION_UP;

    private TextView tv_choose_image_directory;
    private View v_line;
    private TextView tv_preview;
    private RelativeLayout rl_bottom;
    private RelativeLayout rl_date;
    private TextView tv_date;

    private Button btn_choose_finish;

    private LayoutInflater inflater = null;
    private GridViewAdapter adapter;

    //拍照的文件的文件名
    String tempPath = null;

    /** 选择文件夹的弹出框 */
//    private PopupWindow window;
    private RelativeLayout rl_choose_directory;
    private ListView listView;
    private ListviewAdapter listviewAdapter;

    private ObjectAnimator animation;
    private ObjectAnimator reverseanimation;

    private Animation alphaAnimation = new AlphaAnimation(1.0f, 0.0f);
//    private TranslateAnimation animation;
//    private TranslateAnimation reverseanimation;

    /** 每张图片需要显示的高度和宽度 */
    private int perWidth;

    /** 选择图片的数量总数，默认为9 */
    private int picNums = 9;
    /** 当前选中的图片数量 */
    private int currentPicNums = 0;

    /** 最新一张图片的时间 */
    private long lastPicTime = 0;

    public static final String EXTRA_NUMS = "extra_nums";
    public static final int CODE_FOR_PIC_BIG = 1;
    public static final int CODE_FOR_PIC_BIG_PREVIEW = 2;
    public static final int CODE_FOR_TAKE_PIC = 3;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_or_take_image_activity);
        initView();
        initData();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected void initView() {
        gridView = (GridView) findViewById(R.id.gv_content);
        gridView.setOnTouchListener(this);

        tv_choose_image_directory = (TextView) findViewById(R.id.tv_choose_image_directory);
        tv_preview = (TextView) findViewById(R.id.tv_preview);
        v_line = findViewById(R.id.v_line);
        rl_bottom = (RelativeLayout) findViewById(R.id.rl_bottom);
        rl_bottom.setOnClickListener(this);

        rl_date = (RelativeLayout) findViewById(R.id.rl_date);
        tv_date = (TextView) findViewById(R.id.tv_date);

//        listView = new ListView(this);

        rl_choose_directory = (RelativeLayout) findViewById(R.id.rl_choose_directory);
        listView = (ListView) findViewById(R.id.lv_directories);
        listView.setOnItemClickListener(this);
        listviewAdapter = new ListviewAdapter();

//        animation = new TranslateAnimation(0, 0, 0, -CommonUtil.dip2px(this, 300));
//        reverseanimation = new TranslateAnimation(0, 0, -CommonUtil.dip2px(this, 300), 0);
        if (Build.VERSION.SDK_INT < 11){
            // no animation cause low SDK version
        }else{
            animation = ObjectAnimator.ofInt(listView, "bottomMargin", -CommonUtil.dip2px(this, 400), 0);
            animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int value = (Integer) valueAnimator.getAnimatedValue();
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) listView.getLayoutParams();
                    rl_choose_directory.setAlpha(1 - Math.abs(value / CommonUtil.dip2px(PickOrTakeImageActivity.this, 400)));
                    params.bottomMargin = value;
                    listView.setLayoutParams(params);
                    listView.invalidate();
                    rl_choose_directory.invalidate();
                }
            });
            animation.setDuration(500);

            reverseanimation = ObjectAnimator.ofInt(listView, "bottomMargin", 0, -CommonUtil.dip2px(this, 400));
            reverseanimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int value = (Integer) valueAnimator.getAnimatedValue();
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) listView.getLayoutParams();
                    params.bottomMargin = value;
                    listView.setLayoutParams(params);
                    rl_choose_directory.setAlpha(1 - Math.abs(value / CommonUtil.dip2px(PickOrTakeImageActivity.this, 400)));
                    if (value <= -CommonUtil.dip2px(PickOrTakeImageActivity.this, 300)) {
                        rl_choose_directory.setVisibility(View.GONE);
                    }
                    listView.invalidate();
                    rl_choose_directory.invalidate();
                }
            });
            reverseanimation.setDuration(500);
        }

        alphaAnimation.setDuration(1000);
        alphaAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                rl_date.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

//        LinearLayout parent = new LinearLayout(this);
//        parent.addView(listView);

        //不使用popupwindow方案
//        window = new PopupWindow();
//        window.setWidth(AppContext.getInstance().getScreenWidth());
//        window.setHeight(CommonUtil.dip2px(this, 300));
//        window.setContentView(parent);
//        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
//        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
//        window.setBackgroundDrawable(getResources().getDrawable(R.color.white));

        findViewById(R.id.iv_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ((TextView)findViewById(R.id.tv_title)).setText("选择图片");

        btn_choose_finish = (Button) findViewById(R.id.btn_choose_finish);
        btn_choose_finish.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                returnDataAndClose();
            }
        });
    }

    protected void initData() {
        inflater = LayoutInflater.from(this);

        allImages = new ArrayList<SingleImageModel>();
        imageDirectories = new ArrayList<SingleImageDirectories>();

        handler = new MyHandler(this);
        //默认显示全部图片
        currentShowPosition = -1;
        adapter = new GridViewAdapter();
        getAllImages();
        tv_choose_image_directory.setText(getString(R.string.all_pic));
        tv_preview.setText(getString(R.string.preview_without_num));

        rl_choose_directory.setOnClickListener(this);
        tv_choose_image_directory.setOnClickListener(this);
        tv_preview.setOnClickListener(this);
        //计算每张图片应该显示的宽度
        perWidth = (((WindowManager) (AppContext.getInstance().getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay().getWidth() - CommonUtil.dip2px(this, 4))/3;

        picNums = getIntent().getIntExtra(EXTRA_NUMS, 9);

        if (picNums == 1){
            tv_preview.setVisibility(View.GONE);
            v_line.setVisibility(View.GONE);
            btn_choose_finish.setVisibility(View.GONE);
        }else{
            btn_choose_finish.setText("完成");
        }
    }


    /**
     * 从手机中获取所有的手机图片
     */
    private void getAllImages(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver contentResolver = getContentResolver();
                //获取jpeg和png格式的文件，并且按照时间进行倒序
                Cursor cursor = contentResolver.query(uri, null, MediaStore.Images.Media.MIME_TYPE + "=\"image/jpeg\" or " +
                        MediaStore.Images.Media.MIME_TYPE + "=\"image/png\"", null, MediaStore.Images.Media.DATE_MODIFIED+" desc");
                if (cursor != null){
                    allImages.clear();
                    while (cursor.moveToNext()){
                        SingleImageModel singleImageModel = new SingleImageModel();
                        singleImageModel.path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                        try {
                            singleImageModel.date = Long.parseLong(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)));
                        }catch (NumberFormatException e){
                            singleImageModel.date  = System.currentTimeMillis();
                        }
                        try {
                            singleImageModel.id = Long.parseLong(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)));
                        }catch (NumberFormatException e){
                            singleImageModel.id = 0;
                        }
                        allImages.add(singleImageModel);

                        //存入按照目录分配的list
                        String path = singleImageModel.path;
                        String parentPath = new File(path).getParent();
                        putImageToParentDirectories(parentPath, path, singleImageModel.date, singleImageModel.id);
                    }
                    handler.sendEmptyMessage(0);
                }
            }
        }).start();
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.tv_choose_image_directory) {//                window.showAsDropDown(tv_choose_image_directory);
            if (Build.VERSION.SDK_INT < 11) {
                if (rl_choose_directory.getVisibility() == View.VISIBLE) {
                    rl_choose_directory.setVisibility(View.GONE);
                } else {
                    rl_choose_directory.setVisibility(View.VISIBLE);
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) listView.getLayoutParams();
                    params.bottomMargin = 0;
                    listView.setLayoutParams(params);
                    ((ViewGroup) (listView.getParent())).invalidate();
                }
            } else {
                if (rl_choose_directory.getVisibility() == View.VISIBLE) {
                    reverseanimation.start();
                } else {
                    rl_choose_directory.setVisibility(View.VISIBLE);
                    animation.start();
                }
            }

        } else if (i == R.id.tv_preview) {
            if (currentPicNums > 0) {
                Intent intent = new Intent();
                intent.setClass(PickOrTakeImageActivity.this, PickBigImagesActivity.class);
                intent.putExtra(PickBigImagesActivity.EXTRA_DATA, getChoosePicFromList());
                intent.putExtra(PickBigImagesActivity.EXTRA_ALL_PICK_DATA, picklist);
                intent.putExtra(PickBigImagesActivity.EXTRA_CURRENT_PIC, 0);
                intent.putExtra(PickBigImagesActivity.EXTRA_LAST_PIC, picNums - currentPicNums);
                intent.putExtra(PickBigImagesActivity.EXTRA_TOTAL_PIC, picNums);
                startActivityForResult(intent, CODE_FOR_PIC_BIG_PREVIEW);
                AlbumBitmapCacheHelper.getInstance().releaseHalfSizeCache();
            }

        } else if (i == R.id.rl_choose_directory) {
            if (Build.VERSION.SDK_INT < 11) {
                if (rl_choose_directory.getVisibility() == View.VISIBLE) {
                    rl_choose_directory.setVisibility(View.GONE);
                } else {
                    rl_choose_directory.setVisibility(View.VISIBLE);
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) listView.getLayoutParams();
                    params.bottomMargin = 0;
                    listView.setLayoutParams(params);
                    ((ViewGroup) (listView.getParent())).invalidate();
                }
            } else {
                if (rl_choose_directory.getVisibility() == View.VISIBLE) {
                    reverseanimation.start();
                } else {
                    rl_choose_directory.setVisibility(View.VISIBLE);
                    animation.start();
                }
            }

        } else {
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        AlbumBitmapCacheHelper.getInstance().removeAllThreads();
        //点击的全部图片
        if(!(currentShowPosition == i-1)){
            currentShowPosition = i-1;
            reloadDataByChooseDirectory();
        }
    }

    /**
     * 重新加载当前页面数据
     */
    private void reloadDataByChooseDirectory(){
        if(currentShowPosition == -1){
            tv_choose_image_directory.setText(getString(R.string.all_pic));
        }else {
            tv_choose_image_directory.setText(new File(imageDirectories.get(currentShowPosition).directoryPath).getName());
        }
        //去除当前正在加载的所有图片，重新开始
        AlbumBitmapCacheHelper.getInstance().removeAllThreads();
        gridView.setAdapter(adapter);
        gridView.smoothScrollToPosition(0);
        View v = listView.findViewWithTag("picked");
        if (v != null) {
            v.setVisibility(View.GONE);
            v.setTag(null);
        }
        v = (View) listView.findViewWithTag(currentShowPosition + 1).getParent().getParent();
        if (v != null) {
            v.findViewById(R.id.iv_directory_check).setVisibility(View.VISIBLE);
            v.findViewById(R.id.iv_directory_check).setTag("picked");
        }
        if (Build.VERSION.SDK_INT < 11){
            rl_choose_directory.setVisibility(View.GONE);
        }else {
            reverseanimation.start();
        }
    }

    @Override
    public void onBackPressed() {
        if(rl_choose_directory.getVisibility() == View.VISIBLE){
            if (Build.VERSION.SDK_INT < 11){
                rl_choose_directory.setVisibility(View.GONE);
            }else {
                reverseanimation.start();
            }
        }else {
            //离开此页面之后记住要清空cache内存
            AlbumBitmapCacheHelper.getInstance().clearCache();
            super.onBackPressed();
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView absListView, int i) {
        currentState = i;
        if (currentState == SCROLL_STATE_IDLE){
            rl_date.setAnimation(alphaAnimation);
            alphaAnimation.startNow();
        }
        if (currentTouchState == MotionEvent.ACTION_UP && currentState != SCROLL_STATE_FLING){
            rl_date.setAnimation(alphaAnimation);
            alphaAnimation.startNow();
        }
    }

    @Override
    public void onScroll(AbsListView absListView, int i, int i1, int i2) {
        firstVisibleItem = i;
        //保证当选择全部文件夹的时候，显示的时间为第一个图片，排除第一个拍照图片
        if (currentShowPosition == -1 && firstVisibleItem > 0)
            firstVisibleItem --;
        if(lastPicTime != getImageDirectoryModelDateFromMapById(firstVisibleItem)) {
            lastPicTime = getImageDirectoryModelDateFromMapById(firstVisibleItem);
        }
        if (currentState == SCROLL_STATE_TOUCH_SCROLL){
            showTimeLine(lastPicTime);
        }
        if (currentTouchState == MotionEvent.ACTION_UP && currentState == SCROLL_STATE_FLING){
            showTimeLine(lastPicTime);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()){
            case MotionEvent.ACTION_DOWN:
                currentTouchState = MotionEvent.ACTION_DOWN;
                break;
            case MotionEvent.ACTION_MOVE:
                currentTouchState = MotionEvent.ACTION_MOVE;
                break;
            case MotionEvent.ACTION_UP:
                currentTouchState = MotionEvent.ACTION_UP;
                break;
        }
        return false;
    }

    /**
     * leak memory
     */
    private static class MyHandler extends Handler{

        WeakReference<PickOrTakeImageActivity> activity = null;

        public MyHandler(PickOrTakeImageActivity context){
            activity = new WeakReference<PickOrTakeImageActivity>(context);
        }

        @Override
        public void handleMessage(Message msg) {
            if (activity.get() == null)
                return;
            if (activity.get().gridView.getAdapter() == null){
                activity.get().gridView.setAdapter(activity.get().adapter);
            }else
                activity.get().adapter.notifyDataSetChanged();
            activity.get().listView.setAdapter(activity.get().listviewAdapter);
            activity.get().gridView.setOnScrollListener(activity.get());
            super.handleMessage(msg);
        }
    }

    /**
     * gridview适配器
     */
    private class GridViewAdapter extends BaseAdapter{

        @Override
        public int getCount() {
            int size = 0;
            //如果显示全部图片,则第一项为
            if(currentShowPosition == -1){
                size = allImages.size() + 1;
            }else{
                size = imageDirectories.get(currentShowPosition).images.getImageCounts();
            }
            return size;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            //第一个要显示拍摄照片图片
            if (currentShowPosition == -1 && i==0){
                view = new ImageView(PickOrTakeImageActivity.this);
                ((ImageView)view).setBackgroundResource(R.drawable.take_pic);
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        takePic();
                    }
                });
                view.setLayoutParams(new GridView.LayoutParams(perWidth, perWidth));
                return view;
            }
            //在此处直接进行处理最好，能够省去其他部分的处理，其他部分直接可以使用原来的数据结构
            if (currentShowPosition == -1)
                i--;
            //其他部分的处理
            final String path = getImageDirectoryModelUrlFromMapById(i);
            lastPicTime = getImageDirectoryModelDateFromMapById(i);
            if(view==null || view.getTag()==null){
                view = inflater.inflate(R.layout.item_pick_up_image, null);
                GridViewHolder holder = new GridViewHolder();
                holder.iv_content = (ImageView) view.findViewById(R.id.iv_content);
                holder.v_gray_masking = view.findViewById(R.id.v_gray_masking);
                holder.iv_pick_or_not = (ImageView) view.findViewById(R.id.iv_pick_or_not);
                if(picNums == 1){
                    holder.iv_pick_or_not.setVisibility(View.GONE);
                }

                OnclickListenerWithHolder listener = new OnclickListenerWithHolder(holder);
                holder.iv_content.setOnClickListener(listener);
                holder.iv_pick_or_not.setOnClickListener(listener);
                view.setTag(holder);
                //要在这进行设置，在外面设置会导致第一个项点击效果异常
                view.setLayoutParams(new GridView.LayoutParams(perWidth, perWidth));
            }
            final GridViewHolder holder = (GridViewHolder) view.getTag();
            //一定不要忘记更新position
            holder.position = i;
            //如果该图片被选中，则讲状态变为选中状态
            if (getImageDirectoryModelStateFromMapById(i)){
                holder.v_gray_masking.setVisibility(View.VISIBLE);
                holder.iv_pick_or_not.setImageResource(R.drawable.image_choose);
            }else{
                holder.v_gray_masking.setVisibility(View.GONE);
                holder.iv_pick_or_not.setImageResource(R.drawable.image_not_chose);
            }
            //优化显示效果
            if(holder.iv_content.getTag() != null) {
                String remove = (String) holder.iv_content.getTag();
                AlbumBitmapCacheHelper.getInstance().removePathFromShowlist(remove);
            }
            AlbumBitmapCacheHelper.getInstance().addPathToShowlist(path);
            holder.iv_content.setTag(path);
            Bitmap bitmap = AlbumBitmapCacheHelper.getInstance().getBitmap(path, perWidth, perWidth, new AlbumBitmapCacheHelper.ILoadImageCallback() {
                @Override
                public void onLoadImageCallBack(Bitmap bitmap, String path1, Object... objects) {
                    if (bitmap == null) {
                        return;
                    }
                    BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap);
                    View v = gridView.findViewWithTag(path1);
                    if (v != null)
                        v.setBackgroundDrawable(bd);
                }
            }, i);
            if (bitmap != null){
                BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap);
                holder.iv_content.setBackgroundDrawable(bd);
            }else{
                holder.iv_content.setBackgroundResource(R.drawable.ic_product_9);
            }
            return view;
        }
    }

    private class GridViewHolder{
        public ImageView iv_content;
        public View v_gray_masking;
        public ImageView iv_pick_or_not;
        public int position;
    }

    /**
     * 调用系统相机进行拍照
     */
    private void takePic(){
        String name = "temp";
        if (!new File(CommonUtil.getDataPath()).exists())
            new File(CommonUtil.getDataPath()).mkdirs();
        tempPath = CommonUtil.getDataPath() + name + ".jpg";
        File file = new File(tempPath);
        try {
            if (file.exists())
                file.delete();
            file.createNewFile();
        }catch (IOException e){
            e.printStackTrace();
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));
        startActivityForResult(intent, CODE_FOR_TAKE_PIC);
    }

    /**
     * 展示顶部的时间
     */
    private void showTimeLine(long date){
        alphaAnimation.cancel();
        rl_date.setVisibility(View.VISIBLE);
        tv_date.setText(calculateShowTime(date*1000));
    }

    /**
     * 计算照片的具体时间
     * @param time
     * @return
     */
    private String calculateShowTime(long time){

        final Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        int mDayWeek = c.get(Calendar.DAY_OF_WEEK);
        mDayWeek -- ;
        //习惯性的还是定周一为第一天
        if (mDayWeek == 0)
            mDayWeek = 7;
        int mWeek = c.get(Calendar.WEEK_OF_MONTH);
        int mHour = c.get(Calendar.HOUR_OF_DAY);
        int mMinute = c.get(Calendar.MINUTE);

        if((System.currentTimeMillis()-time) < (mHour*60 + mMinute)*60*1000){
            return "今天";
        }else if((System.currentTimeMillis()-time) < (mDayWeek)*24*60*60*1000){
            return "本周";
        }else if((System.currentTimeMillis()-time) < ((long)((mWeek-1)*7+mDayWeek))*24*60*60*1000){
            return "这个月";
        }else{
            SimpleDateFormat format = new SimpleDateFormat("yyyy/MM", java.util.Locale.getDefault());
            return format.format(time);
        }
    }

    private class ListviewAdapter extends BaseAdapter{

        @Override
        public int getCount() {
            if (imageDirectories.size() == 0)
                return 0;
            else
                return imageDirectories.size() + 1;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getItemViewType(int position) {
            if(position == 0) {
                return 0;
            }
            return 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if(view==null || view.getTag()==null){
                view = inflater.inflate(R.layout.item_list_view_album_directory, null);
                ListViewHolder holder = new ListViewHolder();
                holder.iv_directory_pic = (ImageView) view.findViewById(R.id.iv_directory_pic);
                holder.tv_directory_name = (TextView) view.findViewById(R.id.tv_directory_name);
                holder.iv_directory_check = (ImageView) view.findViewById(R.id.iv_directory_check);
                holder.tv_directory_nums = (TextView) view.findViewById(R.id.tv_directory_nums);
                view.setTag(holder);
            }
            final ListViewHolder holder = (ListViewHolder) view.getTag();
            holder.position = i;
            holder.tv_directory_name.setTag(i);
            String path = null;
            //全部图片
            if(getItemViewType(i) == 0){
                holder.tv_directory_name.setText(getString(R.string.all_pic) + "   ");
                int size = 0;
                for (SingleImageDirectories directories : imageDirectories)
                    size += directories.images.getImageCounts();
                holder.tv_directory_nums.setText(size +"张");
                //获取第0个位置的图片，即第一张图片展示
                path = imageDirectories.get(0).images.getImagePath(0);
                if(currentShowPosition == -1){
                    holder.iv_directory_check.setTag("picked");
                    holder.iv_directory_check.setVisibility(View.VISIBLE);
                }else{
                    holder.iv_directory_check.setTag(null);
                    holder.iv_directory_check.setVisibility(View.INVISIBLE);
                }
            }else{
                holder.tv_directory_nums.setText(imageDirectories.get(i-1).images.getImageCounts() +"张");
                if(currentShowPosition == i-1){
                    holder.iv_directory_check.setTag("picked");
                    holder.iv_directory_check.setVisibility(View.VISIBLE);
                }else{
                    holder.iv_directory_check.setTag(null);
                    holder.iv_directory_check.setVisibility(View.INVISIBLE);
                }
                holder.tv_directory_name.setText(new File(imageDirectories.get(i-1).directoryPath).getName()+"   ");
                //获取第0个位置的图片，即第一张图片展示
                path = imageDirectories.get(i-1).images.getImagePath(0);
            }
            if(path == null)
                return null;
            if (holder.iv_directory_pic.getTag() != null) {
                AlbumBitmapCacheHelper.getInstance().removePathFromShowlist((String) (holder.iv_directory_pic.getTag()));
            }
            AlbumBitmapCacheHelper.getInstance().addPathToShowlist(path);
            if(getItemViewType(i) == 0) {
                holder.iv_directory_pic.setTag(path + "all");
            }
            else
                holder.iv_directory_pic.setTag(path);
            Bitmap bitmap = AlbumBitmapCacheHelper.getInstance().getBitmap(path, 225, 225, new AlbumBitmapCacheHelper.ILoadImageCallback() {
                @Override
                public void onLoadImageCallBack(Bitmap bitmap, String path, Object... objects) {
                    if (bitmap == null) return;
                    BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap);
                    View v = null;
                    if (objects[0].toString().equals("0")) {
                        v = listView.findViewWithTag(path + "all");
                    } else {
                        v = listView.findViewWithTag(path);
                    }
                    if (v != null) v.setBackgroundDrawable(bd);
                }
            }, getItemViewType(i));
            if (bitmap != null){
                BitmapDrawable bd = new BitmapDrawable(getResources(), bitmap);
                holder.iv_directory_pic.setBackgroundDrawable(bd);
            }else{
                holder.iv_directory_pic.setBackgroundResource(R.drawable.ic_product_9);
            }
            return view;
        }
    }

    private class ListViewHolder{
        public ImageView iv_directory_pic;
        public TextView tv_directory_name;
        public ImageView iv_directory_check;
        public TextView tv_directory_nums;
        public int position;
    }

    /**
     * 根据id获取map中相对应的图片路径
     */
    private String getImageDirectoryModelUrlFromMapById(int position){
        //如果是选择的全部图片
        if(currentShowPosition == -1){
            return allImages.get(position).path;
        }else{
            return imageDirectories.get(currentShowPosition).images.getImagePath(position);
        }
    }

    /**
     * 根据id获取map中相对应的图片时间
     */
    private long getImageDirectoryModelDateFromMapById(int position){
        if (allImages.size() ==0){
            return System.currentTimeMillis();
        }
        //如果是选择的全部图片
        if(currentShowPosition == -1){
            return allImages.get(position).date;
        }else{
            return imageDirectories.get(currentShowPosition).images.getImages().get(position).date;
        }
    }

    /**
     * 根据id获取map中相对应的图片选中状态
     */
    private boolean getImageDirectoryModelStateFromMapById(int position){
        //如果是选择的全部图片
        if(currentShowPosition == -1){
            return allImages.get(position).isPicked;
        }else{
            return imageDirectories.get(currentShowPosition).images.getImagePickOrNot(position);
        }
    }

    /**
     * 转变该位置图片的选中状态
     * @param position
     */
    private void toggleImageDirectoryModelStateFromMapById(int position){
        //如果是选择的全部图片
        if(currentShowPosition == -1){
            allImages.get(position).isPicked = !allImages.get(position).isPicked;
            for (SingleImageDirectories directories : imageDirectories){
                directories.images.toggleSetImage(allImages.get(position).path);
            }
        }else{
            imageDirectories.get(currentShowPosition).images.toggleSetImage(position);
            for (SingleImageModel model : allImages){
                if (model.path.equalsIgnoreCase(imageDirectories.get(currentShowPosition).images.getImagePath(position)))
                    model.isPicked = !model.isPicked;
            }
        }
    }

    /**
     * 带holder的监听器
     */
    private class OnclickListenerWithHolder implements View.OnClickListener{
        GridViewHolder holder;

        public OnclickListenerWithHolder(GridViewHolder holder){
            this.holder = holder;
        }

        @Override
        public void onClick(View view) {
            int position = holder.position;
            int i = view.getId();
            if (i == R.id.iv_content) {
                if (picNums > 1) {
                    Intent intent = new Intent();
                    intent.setClass(PickOrTakeImageActivity.this, PickBigImagesActivity.class);
                    //TODO 这里由于涉及到intent传递的数据不能太大的问题，所以如果需要，这里需要进行另外的处理，写入到内存或者写入到文件中
                    intent.putExtra(PickBigImagesActivity.EXTRA_DATA, getAllImagesFromCurrentDirectory());
                    intent.putExtra(PickBigImagesActivity.EXTRA_ALL_PICK_DATA, picklist);
                    intent.putExtra(PickBigImagesActivity.EXTRA_CURRENT_PIC, position);
                    intent.putExtra(PickBigImagesActivity.EXTRA_LAST_PIC, picNums - currentPicNums);
                    intent.putExtra(PickBigImagesActivity.EXTRA_TOTAL_PIC, picNums);
                    startActivityForResult(intent, CODE_FOR_PIC_BIG);
                    AlbumBitmapCacheHelper.getInstance().releaseHalfSizeCache();
                }else{
                    picklist.add(getImageDirectoryModelUrlFromMapById(holder.position));
                    currentPicNums++;
                    returnDataAndClose();
                }

            } else if (i == R.id.iv_pick_or_not) {
                toggleImageDirectoryModelStateFromMapById(position);
                if (getImageDirectoryModelStateFromMapById(position)) {
                    if (currentPicNums == picNums) {
                        toggleImageDirectoryModelStateFromMapById(position);
                        Toast.makeText(PickOrTakeImageActivity.this, String.format(getString(R.string.choose_pic_num_out_of_index), picNums), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    picklist.add(getImageDirectoryModelUrlFromMapById(holder.position));
                    holder.iv_pick_or_not.setImageResource(R.drawable.image_choose);
                    holder.v_gray_masking.setVisibility(View.VISIBLE);
                    currentPicNums++;
                    if (currentPicNums == 1) {
                        btn_choose_finish.setTextColor(getResources().getColor(R.color.white));
                    }
                    tv_preview.setText(String.format(getString(R.string.preview_with_num), currentPicNums));
                    btn_choose_finish.setText(String.format(getString(R.string.choose_pic_finish_with_num), currentPicNums, picNums));
                } else {
                    picklist.remove(getImageDirectoryModelUrlFromMapById(holder.position));
                    holder.iv_pick_or_not.setImageResource(R.drawable.image_not_chose);
                    holder.v_gray_masking.setVisibility(View.GONE);
                    currentPicNums--;
                    if (currentPicNums == 0) {
                        btn_choose_finish.setTextColor(getResources().getColor(R.color.found_description_color));
                        btn_choose_finish.setText(getString(R.string.choose_pic_finish));
                        tv_preview.setText(getString(R.string.preview_without_num));
                    } else {
                        tv_preview.setText(String.format(getString(R.string.preview_with_num), currentPicNums));
                        btn_choose_finish.setText(String.format(getString(R.string.choose_pic_finish_with_num), currentPicNums, picNums));
                    }
                }
//                    adapter.notifyDataSetChanged();

            } else {
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case CODE_FOR_PIC_BIG:
            case CODE_FOR_PIC_BIG_PREVIEW:
                AlbumBitmapCacheHelper.getInstance().resizeCache();
                if(resultCode == RESULT_OK && data != null) {
                    ArrayList<String> temp = (ArrayList<String>) data.getSerializableExtra("pick_data");
                    //如果返回的list中含该path，但是picklist不含有该path，选中
                    for (String path : temp){
                        if (!picklist.contains(path)){
                            View v = gridView.findViewWithTag(path);
                            if(v != null) {
                                ((ViewGroup) (v.getParent())).findViewById(R.id.v_gray_masking).setVisibility(View.VISIBLE);
                                ((ImageView) ((ViewGroup) (v.getParent())).findViewById(R.id.iv_pick_or_not)).setImageResource(R.drawable.image_choose);
                            }
                            setPickStateFromHashMap(path, true);
                            currentPicNums ++;
                        }
                    }
                    //如果返回的list中不含该path，但是picklist含有该path,不选中
                    for (String path : picklist){
                        if (!temp.contains(path)){
                            View v = gridView.findViewWithTag(path);
                            if(v != null) {
                                ((ViewGroup) (v.getParent())).findViewById(R.id.v_gray_masking).setVisibility(View.GONE);
                                ((ImageView) ((ViewGroup) (v.getParent())).findViewById(R.id.iv_pick_or_not)).setImageResource(R.drawable.image_not_chose);
                            }
                            currentPicNums --;
                            setPickStateFromHashMap(path, false);
                        }
                    }
                    picklist = temp;
                    if (currentPicNums == 0) {
                        tv_preview.setText(getString(R.string.preview_without_num));
                        btn_choose_finish.setTextColor(getResources().getColor(R.color.found_description_color));
                        btn_choose_finish.setText(getString(R.string.choose_pic_finish));
                    }
                    else {
                        btn_choose_finish.setText(String.format(getString(R.string.choose_pic_finish_with_num), currentPicNums, picNums));
                        btn_choose_finish.setTextColor(getResources().getColor(R.color.white));
                        tv_preview.setText(String.format(getString(R.string.preview_with_num), currentPicNums));
                    }
                    boolean isFinish = data.getBooleanExtra("isFinish", false);
                    if (isFinish){
                        returnDataAndClose();
                    }
                }
                break;
            case CODE_FOR_TAKE_PIC:
                if (resultCode == RESULT_OK){
                    //临时文件的文件名
                    Toast.makeText(this, "拍照的图片 "+tempPath, Toast.LENGTH_LONG).show();

                    //扫描最新的图片进相册
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    Uri uri = Uri.fromFile(new File(tempPath));
                    intent.setData(uri);
                    sendBroadcast(intent);

                    //重新拉取最新数据库文件
                    getAllImages();
                }
                break;
            default:
                break;
        }
    }

    /**
     * 点击完成按钮之后将图片的地址返回到上一个页面
     */
    private void returnDataAndClose(){
        AlbumBitmapCacheHelper.getInstance().clearCache();
        if (currentPicNums == 0){
            Toast.makeText(this, getString(R.string.not_choose_any_pick), Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String model : picklist){
            sb.append(model+"\n");
        }
        TextView textview = new TextView(this);
        textview.setText(sb);
        Dialog dialog = new Dialog(this);
        dialog.setTitle("结果");
        dialog.setContentView(textview);
        dialog.show();
        if (picNums == 1)
            picklist.clear();
//        Intent data = new Intent();
//        data.putExtra("data", list);
//        setResult(RESULT_OK, data);
//        finish();
    }

    /**
     * 设置该图片的选中状态
     */
    private void setPickStateFromHashMap(String path, boolean isPick){
        for (SingleImageDirectories directories : imageDirectories){
            if(isPick)
                directories.images.setImage(path);
            else
                directories.images.unsetImage(path);
        }
        for (SingleImageModel model : allImages){
            if (model.path.equalsIgnoreCase(path))
                model.isPicked = isPick;
        }
    }

    /**
     * 获取所有的选中图片
     */
    private ArrayList<SingleImageModel> getChoosePicFromList(){
        ArrayList<SingleImageModel> list = new ArrayList<SingleImageModel>();
        for (String path : picklist){
            SingleImageModel model = new SingleImageModel(path, true, 0, 0);
            list.add(model);
        }
        return list;
    }

    /**
     * 获取当前选中文件夹中给的所有图片
     */
    private ArrayList<SingleImageModel> getAllImagesFromCurrentDirectory(){
        ArrayList<SingleImageModel> list = new ArrayList<SingleImageModel>();
        if(currentShowPosition == -1) {
            for (SingleImageModel model : allImages) {
                list.add(model);
            }
        }else{
            for (SingleImageModel model : imageDirectories.get(currentShowPosition).images.getImages()) {
                list.add(model);
            }
        }
        return list;
    }

    /**
     * 将图片插入到对应parentPath路径的文件夹中
     */
    private void putImageToParentDirectories(String parentPath, String path, long date, long id){
        ImageDirectoryModel model = getModelFromKey(parentPath);
        if (model == null){
            model = new ImageDirectoryModel();
            SingleImageDirectories directories = new SingleImageDirectories();
            directories.images = model;
            directories.directoryPath = parentPath;
            imageDirectories.add(directories);
        }
        model.addImage(path, date, id);
    }

    private ImageDirectoryModel getModelFromKey(String path){
        for (SingleImageDirectories directories : imageDirectories){
            if(directories.directoryPath.equalsIgnoreCase(path)){
                return directories.images;
            }
        }
        return null;
    }

    /**
     * 一个文件夹中的图片数据实体
     */
    private class SingleImageDirectories{
        /** 父目录的路径 */
        public String directoryPath;
        /** 目录下的所有图片实体 */
        public ImageDirectoryModel images;
    }
}
