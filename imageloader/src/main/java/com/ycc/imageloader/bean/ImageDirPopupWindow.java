package com.ycc.imageloader.bean;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.ycc.imageloader.R;
import com.ycc.imageloader.util.ImageLoader;

import java.util.List;

/**
 * Created by feng on 2016/4/12.
 */
public class ImageDirPopupWindow extends PopupWindow {
    private int mWidth;
    private int mHeight;
    private View mConvertView;
    private ListView mListView;
    private List<FolderBean> mFolderBeans;

    /**
     * 为了使ImageDirPopupWindow和别的activity解耦
     * 使用接口 在activity里面实现回调
     */
    public interface onDirSelectedListener {
        void onSelected(FolderBean folderBean);
    }

    public onDirSelectedListener mListener;

    public void setOnDirSelectedListener(onDirSelectedListener listener) {
        this.mListener = listener;
    }

    public ImageDirPopupWindow(Context context, List<FolderBean> folderBeans) {
        calWindowSize(context);
        //以下是PopupWindow常用设置
        mConvertView = LayoutInflater.from(context).inflate(R.layout.popup, null);
        mFolderBeans = folderBeans;

        setContentView(mConvertView);
        setWidth(mWidth);
        setHeight(mHeight);

        setFocusable(true);
        setTouchable(true);
        //外部区域可点击 用来退出popupwindow
        setOutsideTouchable(true);
        setBackgroundDrawable(new BitmapDrawable());

        setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    dismiss();
                    return true;
                }
                return false;
            }
        });
        //以上

        initView(context);
        initEvent();
    }


    private void initView(Context context) {
        mListView = (ListView) mConvertView.findViewById(R.id.popup_listView);
        mListView.setAdapter(new ListDirAdapter(context, R.layout.item_popup, mFolderBeans));

    }

    private void initEvent() {
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mListener != null) {
                    mListener.onSelected(mFolderBeans.get(position));
                }
            }
        });
    }

    private class ListDirAdapter extends ArrayAdapter<FolderBean> {

        private LayoutInflater inflater;
        private int resourceId;

        public ListDirAdapter(Context context, int resource, List<FolderBean> objects) {
            super(context, 0, objects);
            inflater = LayoutInflater.from(context);
            resourceId = resource;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                Log.e("convertView", "null");
                holder = new ViewHolder();
                convertView = inflater.inflate(resourceId, parent, false);
                holder.image = (ImageView) convertView.findViewById(R.id.item_listView_image);
                holder.dirName = (TextView) convertView.findViewById(R.id.item_dir_name);
                holder.dirCount = (TextView) convertView.findViewById(R.id.item_dir_file_count);

                convertView.setTag(holder);
            } else {
                Log.e("convertView", "not null");

                holder = (ViewHolder) convertView.getTag();
            }

            FolderBean bean = getItem(position);
            //重置状态
            Log.e("holder.image:", holder.toString());
            holder.image.setImageResource(R.drawable.null_picture);

            //再加载
            ImageLoader.getInstance().loadImage(bean.getFirstImgPath(), holder.image);

            holder.dirName.setText(bean.getName());
            holder.dirCount.setText(bean.getCount()+"");
            return convertView;
        }

        private class ViewHolder {
            ImageView image;
            TextView dirName;
            TextView dirCount;

            @Override
            public String toString() {
                return "ViewHolder{" +
                        "image=" + image +
                        ", dirName=" + dirName.getId() +
                        ", dirCount=" + dirCount.getId() +
                        '}';
            }
        }
    }
    /**
     * 计算宽高
     */
    private void calWindowSize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        DisplayMetrics outMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(outMetrics);

        mWidth = outMetrics.widthPixels;
        mHeight = (int) (outMetrics.heightPixels * 0.7);
    }
}
