package com.ycc.imageloader;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.ycc.imageloader.util.ImageLoader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by feng on 2016/4/11.
 */
public class ImageAdapter extends BaseAdapter {

    private String mDirPath;
    private List<String> mFileNames;
    private static Set<String> mSelectFileNames = new HashSet<>();
    private LayoutInflater mInflater;

    /**
     * @param context   上下文
     * @param fileNames 文件名的集合，不存路径的集合是为了节省空间
     * @param dirPath   父路径
     */
    public ImageAdapter(Context context, List<String> fileNames, String dirPath) {
        this.mDirPath = dirPath;
        this.mFileNames = fileNames;
        this.mInflater = LayoutInflater.from(context);
    }


    @Override
    public int getCount() {
        return mFileNames.size();
    }

    @Override
    public Object getItem(int position) {
        return mFileNames.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ViewHolder viewHolder;
        //filePath使用全路径，防止不同文件夹下文件同名
        final String filePath = mDirPath + "/" + mFileNames.get(position);
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_main_grid_view, parent, false);

            viewHolder = new ViewHolder();
            viewHolder.imageView = (ImageView) convertView.findViewById(R.id.item_image);
            viewHolder.imageButton = (ImageButton) convertView.findViewById(R.id.item_select);

            //
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        //重置状态！！由于converview是复用的，必须要做重置。
        // 否则，滑动后若图片尚未加载，会显示上一屏的图片，加载完成后突变为实际图片
        viewHolder.imageView.setImageResource(R.drawable.null_picture);
        viewHolder.imageButton.setImageResource(R.drawable.btn_check_off);
        viewHolder.imageView.setColorFilter(null);

        if (mSelectFileNames.contains(filePath)) {
            viewHolder.imageView.setColorFilter(Color.parseColor("#77000000"));
            viewHolder.imageButton.setImageResource(R.drawable.btn_check_on);
        }

        viewHolder.imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (mSelectFileNames.contains(filePath)) {
                    mSelectFileNames.remove(filePath);
                    viewHolder.imageButton.setImageResource(R.drawable.btn_check_off);
                    viewHolder.imageView.setColorFilter(null);
                } else {
                    mSelectFileNames.add(filePath);
                    viewHolder.imageView.setColorFilter(Color.parseColor("#77000000"));
                    viewHolder.imageButton.setImageResource(R.drawable.btn_check_on);
                }
                //notifyDataSetChanged();会闪屏 不用
            }
        });

        //载入图片
        ImageLoader.getInstance(ImageLoader.DEFAULT_THREAD_COUNT, ImageLoader.Type.FIFO)
                .loadImage(mDirPath + "/" + mFileNames.get(position), viewHolder.imageView);


        return convertView;
    }

    private class ViewHolder {
        ImageView imageView;
        ImageButton imageButton;
    }
}
