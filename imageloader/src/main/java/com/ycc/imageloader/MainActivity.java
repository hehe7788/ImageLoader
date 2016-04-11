package com.ycc.imageloader;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.GridView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ycc.imageloader.bean.FolderBean;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    public static final int MSG_IMG_LOADED = 0x111;
    private static final String TAG = "MainActivity";
    private GridView mGridView;
    private List<String> mImages;

    private RelativeLayout mBottomBar;
    private TextView mDirName;
    private TextView mDirCount;

    private ProgressDialog mProgressDialog;
    private File mCurrentDir;
    private int mMaxCount;

    private List<FolderBean> mFolderBeans = new ArrayList<>();
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_IMG_LOADED) {
                mProgressDialog.dismiss();
                //绑定数据到view中
                dataToView();
            }

        }
    };
    private ImageAdapter mImageAdapter;

    protected void dataToView() {
        if (mCurrentDir == null) {
            Toast.makeText(this, "未扫描到任何图片", Toast.LENGTH_SHORT).show();
            return;
        }

        //数组转为List
        mImages = Arrays.asList(mCurrentDir.list());
        //使用mImages构造gridview的适配器
        mImageAdapter = new ImageAdapter(this , mImages, mCurrentDir.getAbsolutePath());
        mGridView.setAdapter(mImageAdapter);

        mDirCount.setText(mMaxCount + "");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        loadData();
        initEvent();
    }

    /**
     * 初始化控件
     */
    private void initView() {
        mGridView = (GridView) findViewById(R.id.main_gridView);
        mBottomBar = (RelativeLayout) findViewById(R.id.main_bottom_bar);
        mDirName = (TextView) findViewById(R.id.main_dir_name);
        mDirCount = (TextView) findViewById(R.id.main_dir_count);
    }

    /**
     *开启线程，利用content provider，扫描手机中的图片，通过handler通知主线程更新ui
     */
    private void loadData() {
        Log.e(TAG, Environment.getExternalStorageState() + "  " + Environment.getExternalStorageDirectory());
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "当前存储卡不可用", Toast.LENGTH_SHORT).show();
            return;
        }

        mProgressDialog = ProgressDialog.show(this, null, "loading");

        new Thread() {
            @Override
            public void run() {
                Uri mImgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                Log.e(TAG, "mImgUri  " + mImgUri.toString());
                ContentResolver cr = MainActivity.this.getContentResolver();

                Cursor cursor = cr.query(mImgUri,
                        null,
                        MediaStore.Images.Media.MIME_TYPE
                                + " =?or " + MediaStore.Images.Media.MIME_TYPE
                                + " =?",
                        new String[]{"image/jpeg", "image/png"},
                        MediaStore.Images.Media.DATE_MODIFIED);

                Set<String> mDirPaths = new HashSet<>();

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        //图片路径
                        String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                        //图片的父路径
                        File parentFile = new File(path).getParentFile();

                        if (parentFile == null) {
                            continue;
                        }
                        String dirPath = parentFile.getAbsolutePath();

                        FolderBean folderBean;

                        //防止重复扫描
                        if (mDirPaths.contains(dirPath)) {
                            continue;
                        } else {
                            mDirPaths.add(dirPath);
                            folderBean = new FolderBean();
                            folderBean.setDir(dirPath);
                            folderBean.setFirstImgPath(path);
                        }

                        if (parentFile.list() == null) {
                            continue;
                        }

                        int picSize = parentFile.list(new FilenameFilter() {
                            @Override
                            public boolean accept(File dir, String filename) {
                                return filename.endsWith(".jpg")
                                        || filename.endsWith(".jpeg")
                                        || filename.endsWith(".png");
                            }
                        }).length;

                        folderBean.setCount(picSize);
                        mFolderBeans.add(folderBean);

                        if (picSize > mMaxCount) {
                            mMaxCount = picSize;
                            mCurrentDir = parentFile;
                        }



                    }
                    cursor.close();
                    //通知handler图片扫描完成
                    mHandler.sendEmptyMessage(MSG_IMG_LOADED);
                }
            }
        }.start();
    }

    /**
     * 控件的点击事件
     */
    private void initEvent() {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
