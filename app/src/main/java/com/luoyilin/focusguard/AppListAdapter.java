package com.luoyilin.focusguard;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AppListAdapter
        extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    private final List<AppInfo> appList;
    private OnSelectionChangedListener selectionChangedListener;
    // 保存应用列表和状态变化监听器

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
        // 应用状态变化时通知 Activity
    }

    public AppListAdapter(List<AppInfo> appList) {
        this.appList = appList;
        // 接收需要显示的应用列表
    }

    public void setOnSelectionChangedListener(
            OnSelectionChangedListener listener
    ) {
        this.selectionChangedListener = listener;
        // 设置状态变化监听器
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        // 加载单个应用的列表项布局

        return new AppViewHolder(view);
        // 创建并返回 ViewHolder
    }

    @Override
    public void onBindViewHolder(
            @NonNull AppViewHolder holder,
            int position
    ) {
        AppInfo appInfo = appList.get(position);
        // 获取当前位置对应的应用数据

        holder.ivAppIcon.setImageDrawable(appInfo.getAppIcon());
        holder.tvAppName.setText(appInfo.getAppName());
        holder.tvPackageName.setText(appInfo.getPackageName());
        holder.tvLimitTime.setText(appInfo.getLimitText());
        holder.swSelected.setChecked(appInfo.isSelected());
        // 将应用数据绑定到列表控件

        holder.itemView.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            // 获取用户点击时最新的列表位置

            if (currentPosition == RecyclerView.NO_POSITION) {
                return;
            }
            // 如果列表位置已经失效，就停止处理

            AppInfo currentApp = appList.get(currentPosition);
            currentApp.setSelected(!currentApp.isSelected());
            // 切换当前应用的选择状态

            notifyItemChanged(currentPosition);
            // 刷新当前这一行

            notifySelectionChanged();
            // 通知 Activity 更新数量并保存
        });

        holder.swSelected.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            // 获取开关对应的最新列表位置

            if (currentPosition == RecyclerView.NO_POSITION) {
                return;
            }

            AppInfo currentApp = appList.get(currentPosition);
            currentApp.setSelected(holder.swSelected.isChecked());
            // 将开关状态同步到应用数据

            notifyItemChanged(currentPosition);
            notifySelectionChanged();
            // 刷新界面并通知 Activity
        });

        holder.tvLimitTime.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            // 获取当前应用最新的列表位置

            if (currentPosition == RecyclerView.NO_POSITION) {
                return;
            }

            AppInfo currentApp = appList.get(currentPosition);

            if (!currentApp.isSelected()) {
                Toast.makeText(
                        v.getContext(),
                        "请先开启该应用的限制",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            // 未开启限制时，不允许设置时间

            showLimitTimeDialog(
                    v.getContext(),
                    currentApp,
                    currentPosition
            );
            // 显示限制时间选择弹窗
        });
    }

    private void showLimitTimeDialog(
            Context context,
            AppInfo appInfo,
            int position
    ) {
        String[] timeOptions = {
                "15 分钟",
                "30 分钟",
                "60 分钟",
                "120 分钟"
        };
        // 弹窗显示的时间选项

        int[] timeValues = {15, 30, 60, 120};
        // 每个选项对应的分钟数

        new AlertDialog.Builder(context)
                .setTitle("设置 " + appInfo.getAppName() + " 的每日限制")
                .setItems(timeOptions, (dialog, which) -> {
                    appInfo.setLimitMinutes(timeValues[which]);
                    // 保存用户选择的分钟数

                    notifyItemChanged(position);
                    // 更新当前列表项

                    notifySelectionChanged();
                    // 通知 Activity 保存最新状态
                })
                .setNegativeButton("取消", null)
                .show();
        // 创建并显示时间选择弹窗
    }

    @Override
    public int getItemCount() {
        return appList.size();
        // 返回应用总数
    }

    private int getSelectedCount() {
        int count = 0;

        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected()) {
                count++;
            }
        }
        // 统计已选择的应用数量

        return count;
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(getSelectedCount());
        }
        // 通知 Activity 当前已选择数量
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvPackageName;
        TextView tvLimitTime;
        Switch swSelected;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);

            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvPackageName = itemView.findViewById(R.id.tvPackageName);
            tvLimitTime = itemView.findViewById(R.id.tvLimitTime);
            swSelected = itemView.findViewById(R.id.swSelected);
            // 找到单个列表项中的所有控件
        }
    }
}