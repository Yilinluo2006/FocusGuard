package com.luoyilin.focusguard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import android.widget.ImageView;
// 引入 ImageView 控件，用来显示应用图标

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import android.widget.ImageButton; //引入ImageView 控件，用来显示应用图标。


public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {
    private List<AppInfo> appList;

    public AppListAdapter(List<AppInfo> appList) {
        this.appList = appList;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo appInfo = appList.get(position);
        // 根据当前位置获取对应的应用数据

        holder.ivAppIcon.setImageDrawable(appInfo.getAppIcon());
        // 把应用图标显示到 ImageView 上

        holder.tvAppName.setText(appInfo.getAppName());
        // 把应用名称显示到 TextView 上

        holder.tvPackageName.setText(appInfo.getPackageName());
        // 把应用包名显示到 TextView 上
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvPackageName;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            // 找到列表项里的应用图标控件

            tvAppName = itemView.findViewById(R.id.tvAppName);
            // 找到列表项里的应用名称控件

            tvPackageName = itemView.findViewById(R.id.tvPackageName);
            // 找到列表项里的应用包名控件
        }
    }
}