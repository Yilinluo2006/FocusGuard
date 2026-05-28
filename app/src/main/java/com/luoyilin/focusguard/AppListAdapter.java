//接收 List<AppInfo>
//加载 item_app.xml 作为每一行布局
//把应用图标、名称、包名显示到每一行
//处理 Switch 开关点击
//统计已选择应用数量
//通过回调通知 AppManageActivity

package com.luoyilin.focusguard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {
    private List<AppInfo> appList;
    private OnSelectionChangedListener selectionChangedListener;
    // 保存选择状态变化的监听器，用来通知 Activity 更新数量

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
        // 当已选择应用数量变化时调用
    }

    public AppListAdapter(List<AppInfo> appList) {
        this.appList = appList;
        // 创建适配器时接收外部传入的应用列表
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
        // 设置选择状态变化监听器
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        // 加载 item_app.xml，创建一行应用列表界面

        return new AppViewHolder(view);
        // 创建 ViewHolder，用来保存这一行里的控件
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

        holder.swSelected.setChecked(appInfo.isSelected());
        // 根据 AppInfo 的 selected 状态设置 Switch 是否开启

        holder.itemView.setOnClickListener(v -> {
            appInfo.setSelected(!appInfo.isSelected());
            // 点击整行时，切换当前应用是否被选中

            notifyItemChanged(position);
            // 通知 RecyclerView 刷新当前这一行

            notifySelectionChanged();
            // 通知 Activity 已选择数量发生变化
        });

        holder.swSelected.setOnClickListener(v -> {
            appInfo.setSelected(holder.swSelected.isChecked());
            // 点击 Switch 时，把开关状态同步到 AppInfo 数据里

            notifySelectionChanged();
            // 通知 Activity 已选择数量发生变化
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
        // 返回应用列表数量，决定 RecyclerView 显示多少行
    }

    private int getSelectedCount() {
        int count = 0;
        // 创建计数器，用来统计已选择应用数量

        for (AppInfo appInfo : appList) {
            if (appInfo.isSelected()) {
                count++;
            }
        }
        // 遍历应用列表，统计 selected 为 true 的应用数量

        return count;
        // 返回最终统计结果
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(getSelectedCount());
        }
        // 如果 Activity 设置了监听器，就把最新选择数量通知出去
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvPackageName;
        Switch swSelected;
        // 保存一行中的图标、文字和开关控件

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);

            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            // 找到列表项里的应用图标控件

            tvAppName = itemView.findViewById(R.id.tvAppName);
            // 找到列表项里的应用名称控件

            tvPackageName = itemView.findViewById(R.id.tvPackageName);
            // 找到列表项里的应用包名控件

            swSelected = itemView.findViewById(R.id.swSelected);
            // 找到列表项里的 Switch 开关控件
        }
    }
}