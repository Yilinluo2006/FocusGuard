package com.luoyilin.focusguard;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

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
    private final List<AppInfo> allApps;
    private String query = "";
    private boolean limitedOnly;
    private boolean compactCards;
    // 保存需要显示的应用列表

    private OnSelectionChangedListener selectionChangedListener;
    // 保存应用选择状态变化监听器

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);

    }
    // 应用状态变化时通知 Activity，并传递已选择数量

    public AppListAdapter(List<AppInfo> appList) {
        this.allApps = appList;
        this.appList = new java.util.ArrayList<>(appList);
        sortAppList();
    }
    // 创建适配器并对应用列表进行第一次排序

    public void setOnSelectionChangedListener(
            OnSelectionChangedListener listener
    ) {
        this.selectionChangedListener = listener;
    }
    // 设置应用状态变化监听器

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(viewType == 1 ? R.layout.item_app_compact : R.layout.item_app, parent, false);

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
        long usedMillis = Math.max(0L, appInfo.getTodayUsageMillis());
        long usedMinutes = usedMillis / 60000L;
        long limitMillis = Math.max(1L, appInfo.getLimitMinutes()) * 60000L;
        boolean limited = appInfo.isSelected();
        boolean reached = limited && usedMillis >= limitMillis;
        Context context = holder.itemView.getContext();
        holder.tvLimitTime.setText(MetricText.emphasize(limited
                ? (DeviceSyncManager.strictQuota(context)?"共用每日额度":"本机每日额度")+"\n已用 "+usedMinutes + " / " + appInfo.getLimitMinutes()
                    + " 分钟" + (reached && !compactCards ? " · 已用完" : "")
                : usedMinutes + " 分钟 · 不限时", compactCards ? 1f : 1.55f));
        holder.itemView.setContentDescription(appInfo.getAppName() + "，今日已用 " + usedMinutes
                + " 分钟" + (limited ? "，限制 " + appInfo.getLimitMinutes() + " 分钟" : "，未限制")
                + (reached ? "，额度已用完" : "") + "，点按设置时长");
        holder.tvLimitTime.setTextColor(context.getColor(
                reached ? R.color.danger_text : R.color.text_primary));
        holder.usageProgress.setVisibility(limited ? View.VISIBLE : View.GONE);
        holder.usageProgress.setIndicatorColor(context.getColor(
                reached ? R.color.danger_text : R.color.primary_button));
        holder.usageProgress.setProgress((int) (Math.min(usedMillis, limitMillis)
                * 1000L / limitMillis));
        holder.swSelected.setContentDescription(appInfo.getAppName() + "的使用限制");
        holder.swSelected.setChecked(appInfo.isSelected());
        // 把应用信息显示到列表控件中

        holder.itemView.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION) {
                showLimitTimeDialog(v.getContext(), appList.get(currentPosition));
            }
        });

        holder.swSelected.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();

            if (currentPosition == RecyclerView.NO_POSITION) {
                return;
            }
            // 获取开关对应的最新位置

            AppInfo currentApp = appList.get(currentPosition);
            boolean selected = holder.swSelected.isChecked();
            holder.swSelected.setChecked(currentApp.isSelected());
            RestrictionSettings.requestChange(v.getContext(), currentApp,
                    selected, currentApp.getLimitMinutes(), () -> {
            currentApp.setSelected(selected);
            // 将开关状态同步到 AppInfo

            sortAppList();
            notifyDataSetChanged();
            notifySelectionChanged();
            // 重新排序、刷新并通知 Activity 保存
            });
        });

    }

    private void sortAppList() {
        appList.clear();
        for (AppInfo app : allApps) {
            if (limitedOnly && !app.isSelected()) continue;
            if (app.getAppName().toLowerCase(java.util.Locale.ROOT).contains(query)
                    || app.getPackageName().toLowerCase(java.util.Locale.ROOT).contains(query)) appList.add(app);
        }
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
    public void filter(String text) {
        query = text.trim().toLowerCase(java.util.Locale.ROOT);
        sortAppList();
        notifyDataSetChanged();
    }
    void showOnlyLimited(boolean value) {
        limitedOnly = value;
        sortAppList();
        notifyDataSetChanged();
    }
    boolean isOnlyLimited() { return limitedOnly; }
    void setCompactCards(boolean value) {
        if (compactCards == value) return;
        compactCards = value;
        notifyDataSetChanged();
    }
    @Override public int getItemViewType(int position) { return compactCards ? 1 : 0; }
    // 后端数据改变后，重新排序、刷新并通知 Activity

    private void showLimitTimeDialog(
            Context context,
            AppInfo appInfo
    ) {
        EditText etLimitMinutes = new EditText(context);
        // 创建用于输入限制分钟数的输入框

        etLimitMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        // 只允许用户输入数字

        etLimitMinutes.setHint("请输入 1 到 1440 分钟");
        // 提示允许输入的范围

        etLimitMinutes.setTextColor(context.getColor(R.color.input_text));
        etLimitMinutes.setHintTextColor(context.getColor(R.color.input_hint));
        // 与登录、注册等页面保持一致，确保正文和提示文字清晰可见

        etLimitMinutes.setText(String.valueOf(appInfo.getLimitMinutes()));
        // 显示当前已经设置的限制时间

        etLimitMinutes.selectAll();
        // 打开弹窗后选中原来的数字，方便直接修改

        int padding = (int) (24 * context.getResources()
                .getDisplayMetrics().density);
        // 把 24dp 转换成当前设备使用的像素值

        LinearLayout inputContainer = new LinearLayout(context);
        inputContainer.setOrientation(LinearLayout.VERTICAL);
        inputContainer.setPadding(padding, 0, padding, 0);
        inputContainer.addView(
                etLimitMinutes,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );
        // 给输入框添加左右间距，使弹窗布局更整齐
        TextView quotaRule = new TextView(context);
        quotaRule.setText((DeviceSyncManager.strictQuota(context) ? "手机与平板共用每日额度" : "每台设备每天的可用时间，离线也可使用")
                + "\n\n" + RestrictionSettings.USAGE_RULES);
        quotaRule.setTextSize(13);
        quotaRule.setTextColor(context.getColor(R.color.text_secondary));
        quotaRule.setPadding(0, (int) (8 * context.getResources().getDisplayMetrics().density), 0, 0);
        inputContainer.addView(quotaRule);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("设置 " + appInfo.getAppName() + " 的限制时长")
                .setView(inputContainer)
                .setPositiveButton(appInfo.isSelected() ? "保存时长" : "开启限制", null)
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

                                RestrictionSettings.requestChange(context, appInfo,
                                        true, limitMinutes, () -> {
                                appInfo.setSelected(true);
                                appInfo.setLimitMinutes(limitMinutes);
                                // 更新当前应用对象里的限制时间

                                refreshAppList();
                                // 通知 Activity 保存本地数据并同步到后端

                                dialog.dismiss();
                                // 保存成功后关闭弹窗
                                });
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

        for (AppInfo appInfo : allApps) {
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
        com.google.android.material.progressindicator.LinearProgressIndicator usageProgress;
        TextView tvLimitTime;
        Switch swSelected;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);

            ivAppIcon =
                    itemView.findViewById(R.id.ivAppIcon);

            tvAppName =
                    itemView.findViewById(R.id.tvAppName);

            usageProgress =
                    itemView.findViewById(R.id.usageProgress);

            tvLimitTime =
                    itemView.findViewById(R.id.tvLimitTime);

            swSelected =
                    itemView.findViewById(R.id.swSelected);
        }
        // 找到单个列表项中的全部控件
    }
}
