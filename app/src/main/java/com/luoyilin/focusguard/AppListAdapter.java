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

import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;


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
        EditText etLimitMinutes = new EditText(context);
        // 创建用于输入限制分钟数的输入框

        etLimitMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        // 只允许用户输入数字

        etLimitMinutes.setHint("请输入 1 到 1440 分钟");
        // 提示允许输入的范围

        etLimitMinutes.setText(String.valueOf(appInfo.getLimitMinutes()));
        // 显示当前已经设置的限制时间

        etLimitMinutes.selectAll();
        // 打开弹窗后选中原来的数字，方便直接修改

        int padding = (int) (24 * context.getResources()
                .getDisplayMetrics().density);
        // 把 24dp 转换成当前设备使用的像素值

        LinearLayout inputContainer = new LinearLayout(context);
        inputContainer.setPadding(padding, 0, padding, 0);
        inputContainer.addView(
                etLimitMinutes,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );
        // 给输入框添加左右间距，使弹窗布局更整齐

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("设置 " + appInfo.getAppName() + " 的每日限制")
                .setView(inputContainer)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        // 创建限制时间输入弹窗

        dialog.setOnShowListener(unused ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            String inputText = etLimitMinutes
                                    .getText()
                                    .toString()
                                    .trim();
                            // 获取并整理用户输入的内容

                            if (inputText.isEmpty()) {
                                etLimitMinutes.setError("请输入限制时间");
                                return;
                            }
                            // 阻止保存空内容

                            try {
                                int limitMinutes = Integer.parseInt(inputText);
                                // 把字符串转换成整数分钟数

                                if (limitMinutes < 1 || limitMinutes > 1440) {
                                    etLimitMinutes.setError(
                                            "限制时间必须在 1 到 1440 分钟之间"
                                    );
                                    return;
                                }
                                // 检查时间是否处于合理范围

                                appInfo.setLimitMinutes(limitMinutes);
                                // 更新当前应用对象里的限制时间

                                notifyItemChanged(position);
                                // 刷新当前应用在 RecyclerView 中的显示

                                notifySelectionChanged();
                                // 通知 Activity 保存本地数据并同步到后端

                                dialog.dismiss();
                                // 保存成功后关闭弹窗
                            } catch (NumberFormatException exception) {
                                etLimitMinutes.setError("请输入有效的整数");
                            }
                            // 防止输入内容无法转换成整数
                        })
        );
        dialog.show();
        // 显示弹窗
    }

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