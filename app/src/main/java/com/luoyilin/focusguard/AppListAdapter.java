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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class AppListAdapter
        extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    private final List<AppInfo> appList;
    // 保存需要显示的应用列表

    private OnSelectionChangedListener selectionChangedListener;
    // 保存应用选择状态变化监听器

    private OnDeleteRequestListener deleteRequestListener;
    // 保存长按删除请求监听器

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);

    }
    // 应用状态变化时通知 Activity，并传递已选择数量

    public interface OnDeleteRequestListener {
        void onDeleteRequested(AppInfo appInfo);
    }
    // 用户长按应用时，将对应 AppInfo 传给 Activity

    public AppListAdapter(List<AppInfo> appList) {
        this.appList = appList;
        sortAppList();
    }
    // 创建适配器并对应用列表进行第一次排序

    public void setOnSelectionChangedListener(
            OnSelectionChangedListener listener
    ) {
        this.selectionChangedListener = listener;
    }
    // 设置应用状态变化监听器

    public void setOnDeleteRequestListener(
            OnDeleteRequestListener listener
    ) {
        this.deleteRequestListener = listener;
    }
    // 设置长按删除请求监听器

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);

        return new AppViewHolder(view);
    }
    // 加载单个应用布局并创建 ViewHolder

    @Override
    public void onBindViewHolder(
            @NonNull AppViewHolder holder,
            int position
    ) {
        AppInfo appInfo = appList.get(position);
        // 获取当前位置对应的应用

        holder.ivAppIcon.setImageDrawable(appInfo.getAppIcon());
        holder.tvAppName.setText(appInfo.getAppName());
        holder.tvPackageName.setText(appInfo.getPackageName());
        holder.tvLimitTime.setText(appInfo.getUsageAndLimitText());
        holder.swSelected.setChecked(appInfo.isSelected());
        // 把应用信息显示到列表控件中

        holder.itemView.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();

            if (currentPosition == RecyclerView.NO_POSITION) {
                return;
            }
            // 列表位置失效时停止处理

            AppInfo currentApp = appList.get(currentPosition);
            currentApp.setSelected(!currentApp.isSelected());
            // 点击整行时切换应用限制状态

            sortAppList();
            notifyDataSetChanged();
            notifySelectionChanged();
            // 重新排序、刷新并通知 Activity 保存
        });

        holder.itemView.setOnLongClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();

            if (currentPosition == RecyclerView.NO_POSITION) {
                return false;
            }
            // 长按位置失效时停止处理

            AppInfo currentApp = appList.get(currentPosition);
            // 获取被长按的应用

            if (deleteRequestListener != null) {
                deleteRequestListener.onDeleteRequested(currentApp);
            }
            // 将删除请求交给 Activity

            return true;
            // 消费长按事件，避免继续触发普通点击
        });

        holder.swSelected.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();

            if (currentPosition == RecyclerView.NO_POSITION) {
                return;
            }
            // 获取开关对应的最新位置

            AppInfo currentApp = appList.get(currentPosition);
            currentApp.setSelected(holder.swSelected.isChecked());
            // 将开关状态同步到 AppInfo

            sortAppList();
            notifyDataSetChanged();
            notifySelectionChanged();
            // 重新排序、刷新并通知 Activity 保存
        });

        holder.tvLimitTime.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();

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
            // 未开启限制时不允许设置时间

            showLimitTimeDialog(
                    v.getContext(),
                    currentApp,
                    currentPosition
            );
        });
    }

    private void sortAppList() {
        Collections.sort(appList, new Comparator<AppInfo>() {
            @Override
            public int compare(
                    AppInfo firstApp,
                    AppInfo secondApp
            ) {
                if (firstApp.isSelected()
                        && !secondApp.isSelected()) {
                    return -1;
                }
                // 已选择应用排在前面

                if (!firstApp.isSelected()
                        && secondApp.isSelected()) {
                    return 1;
                }
                // 未选择应用排在后面

                return Long.compare(
                        secondApp.getTodayUsageMillis(),
                        firstApp.getTodayUsageMillis()
                );
                // 同组应用按照今日使用时间降序排列
            }
        });
    }

    public void refreshAppList() {
        sortAppList();
        notifyDataSetChanged();
        notifySelectionChanged();
    }
    // 后端数据改变后，重新排序、刷新并通知 Activity

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
        // 时间选项对应的分钟数

        new AlertDialog.Builder(context)
                .setTitle(
                        "设置 "
                                + appInfo.getAppName()
                                + " 的每日限制"
                )
                .setItems(timeOptions, (dialog, which) -> {
                    appInfo.setLimitMinutes(timeValues[which]);
                    // 保存用户选择的时间

                    notifyItemChanged(position);
                    notifySelectionChanged();
                    // 刷新当前应用并通知 Activity 保存
                })
                .setNegativeButton("取消", null)
                .show();
    }
    // 创建并显示限制时间选择弹窗

    @Override
    public int getItemCount() {
        return appList.size();
    }
    // 返回应用列表总数

    private int getSelectedCount() {
        int count = 0;

        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected()) {
                count++;
            }
        }

        return count;
    }
    // 统计当前已选择的应用数量

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(
                    getSelectedCount()
            );
        }
    }
    // 把最新选择数量通知给 Activity

    static class AppViewHolder
            extends RecyclerView.ViewHolder {

        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvPackageName;
        TextView tvLimitTime;
        Switch swSelected;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);

            ivAppIcon =
                    itemView.findViewById(R.id.ivAppIcon);

            tvAppName =
                    itemView.findViewById(R.id.tvAppName);

            tvPackageName =
                    itemView.findViewById(R.id.tvPackageName);

            tvLimitTime =
                    itemView.findViewById(R.id.tvLimitTime);

            swSelected =
                    itemView.findViewById(R.id.swSelected);
        }
        // 找到单个列表项中的全部控件
    }
}