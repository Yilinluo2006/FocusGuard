package com.luoyilin.focusguard;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/** A dedicated opt-in disclosure; declining does not change existing protection settings. */
public class PageMonitoringActivity extends AppCompatActivity {
    static final String DISCLOSURE = "第 4 版：开启录入浮窗，直接进入想限制的页面，点击浮窗后自动识别当前应用、界面标识及稳定特征。无需预先选择应用或逐项勾选特征。"
            + "\n\n使用已开启的无障碍服务，仅在你点击录入时提取当前前台页面的可见控件标识及短文字，生成摘要。摘要可能含个人信息；请检查后点击确认添加，未确认不保存。不会截图、录屏或保存整个控件树，不上传，不参与备份或设备同步。"
            + "\n\n有稳定特征时，监控仅在录入的应用和界面查找摘要中的特征。没有稳定特征时，普通应用可按界面标识限制整个界面，其中多个标签或弹层也可能一起受限，摘要会明确说明。系统的通用界面需要稳定特征；无法区分时不保存。"
            + "\n\n普通页面和你新录入的系统设置、卸载页面命中后显示限制层，须返回上一页、桌面或进入管理页暂停规则。可能干扰系统操作，应先验证目标页与相邻页面。拨号、键盘和系统通知界面保留使用；不自动修改系统设置或权限。"
            + "\n\n你可以随时进入奶蛙的管理页暂停、删除规则，关闭总开关或撤回授权；这些入口不受睡前计划或额度修改时段限制。旧版系统提醒规则保留原行为，不自动升级为限制进入。"
            + "\n\n应用改版、共用界面或无障碍未提供内容时，可能无法识别或误判。不授权仍可使用原有任务、额度和睡前功能。";

    private boolean recordsMode;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        recordsMode = state == null ? getIntent().getBooleanExtra("showRecords", false) : state.getBoolean("recordsMode");
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        render();
    }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent); setIntent(intent); recordsMode = intent.getBooleanExtra("showRecords", false); render();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putBoolean("recordsMode", recordsMode); super.onSaveInstanceState(out); }
    private void back() { if (recordsMode) { recordsMode = false; render(); } else finish(); }
    @Override protected void onResume() { super.onResume(); render(); }
    private void render() {
        if (recordsMode) { renderRecords(); return; }
        PageMonitoringStore.State state = PageMonitoringStore.read(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int dp = PageUi.dp(this, 20); body.setPadding(dp, dp, dp, dp);
        LinearLayout status = PageUi.card(this);
        status.addView(PageUi.heading(this, state.authorized ? "已独立授权" : "尚未授权 · 默认关闭", 20));
        status.addView(note(state.enabled ? "自定义页面监控已开启" : "自定义页面监控已关闭"));
        status.addView(note(FocusAccessibilityService.isRunning(this) ? "无障碍服务已连接"
                : "无障碍未连接，无法在系统界面显示提醒；已有授权仍可在本页撤回。"));
        status.addView(button("已录入页面 · 查看与管理", () -> { recordsMode = true; render(); }));
        PageUi.addCard(body, status);
        LinearLayout disclosure = PageUi.card(this);
        disclosure.addView(PageUi.heading(this, "功能告知与授权 · 第 " + PageMonitoringStore.VERSION + " 版", 18));
        disclosure.addView(note(DISCLOSURE));
        if (!state.authorized) {
            CheckBox understood = new CheckBox(this);
            understood.setText("我已了解现场录入与页面监控的读取范围、数据处理和退出方式，自愿开启");
            understood.setTextColor(getColor(R.color.text_primary));
            understood.setId(R.id.pageMonitoringConsent); understood.setChecked(false);
            disclosure.addView(understood);
            Button accept = button("同意并开启", () -> {
                if (PageMonitoringStore.authorize(this, understood.isChecked())) render(); else failure();
            });
            accept.setId(R.id.pageMonitoringAuthorize); accept.setEnabled(false);
            understood.setOnCheckedChangeListener((v, checked) -> accept.setEnabled(checked));
            disclosure.addView(accept);
            disclosure.addView(button("暂不开启", this::finish));
        } else {
            Switch enabled = new Switch(this);
            enabled.setText("页面监控总开关"); enabled.setTextColor(getColor(R.color.text_primary));
            enabled.setMinHeight(PageUi.dp(this, 48)); enabled.setChecked(state.enabled);
            enabled.setId(R.id.systemExitReminderEnabled);
            enabled.setOnCheckedChangeListener((v, checked) -> {
                if (!PageMonitoringStore.enable(this, checked)) failure();
                render();
            });
            disclosure.addView(enabled);
            Button revoke = button("撤回授权并关闭页面监控", () -> {
                if (PageMonitoringStore.revoke(this)) render(); else failure();
            });
            revoke.setId(R.id.pageMonitoringRevoke); disclosure.addView(revoke);
        }
        PageUi.addCard(body, disclosure);
        LinearLayout recorded = PageUi.card(this);
        recorded.addView(PageUi.heading(this, "我录入的页面", 18));
        recorded.addView(note("开启小浮窗，直接进入想限制的页面，点击录入即可识别当前应用。无需先选应用。"));
        Button capture = button("开启页面录入浮窗", () -> beginCapture("@current"));
        capture.setEnabled(state.enabled && FocusAccessibilityService.isRunning(this)); recorded.addView(capture);
        recorded.addView(note("自动展示识别摘要，确认添加后限制该页面；系统通用页面需要可区分的特征。"));
        if (state.authorized) {
            LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
            spacing.topMargin = PageUi.dp(this, 16); body.addView(recorded, 1, spacing);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(body);
        SecondaryPage.show(this, scroll, "页面监控与退出提醒", this::finish);
    }
    private void renderRecords() {
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int padding = PageUi.dp(this, 20); body.setPadding(padding, padding, padding, padding);
        body.addView(note("已保存的规则保留在本机。保存成功不代表已经验证识别，请分别测试目标页与相邻页。"));
        PageMonitoringStore.State state = PageMonitoringStore.read(this);
        body.addView(note(state.enabled ? "总开关已开启" : "总开关已关闭，以下规则暂不监控"));
        body.addView(button("开启页面录入浮窗", () -> beginCapture("@current")));
        RecordedPageStore.ReadResult result = RecordedPageStore.readResult(this);
        if (result.error != null) {
            body.addView(note(result.error)); body.addView(button("重新读取", this::render));
        } else if (result.rules.isEmpty()) body.addView(note("还没有已保存的页面。点击上方按钮开始录入。"));
        body.addView(note("已保存 " + (result.error == null ? result.rules.size() : "—") + " / 24 条"));
        java.util.List<RecordedPageRule> rules = new java.util.ArrayList<>(result.rules);
        rules.sort(java.util.Comparator.comparing((RecordedPageRule r) -> appName(r.pkg)).thenComparing(r -> r.name));
        for (RecordedPageRule rule : rules) {
            LinearLayout card = PageUi.card(this);
            card.addView(PageUi.heading(this, rule.name, 18));
            card.addView(note(appName(rule.pkg) + (rule.system && !rule.restrictSystem ? " · 系统页面只提醒" : " · 限制进入")));
            Switch toggle = new Switch(this); toggle.setText("启用这条规则");
            toggle.setTextColor(getColor(R.color.text_primary)); toggle.setMinHeight(PageUi.dp(this, 48)); toggle.setChecked(rule.enabled);
            toggle.setOnCheckedChangeListener((v, checked) -> { if (!RecordedPageStore.enable(this, rule.id, checked)) failure(); render(); });
            card.addView(toggle);
            card.addView(button("查看特征与编辑", () -> showDetails(rule)));
            PageUi.addCard(body, card);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(body);
        SecondaryPage.show(this, scroll, "已录入页面", this::back);
    }
    private String appName(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception unavailable) { return pkg; }
    }
    private void showDetails(RecordedPageRule rule) {
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int padding = PageUi.dp(this, 20); body.setPadding(padding, padding, padding, padding);
        EditText name = new EditText(this); name.setSingleLine(true); name.setText(rule.name); name.setHint("页面名称");
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)}); body.addView(name);
        body.addView(note(appName(rule.pkg) + "\n" + (rule.system && !rule.restrictSystem ? "系统页面只提醒，可继续操作" : "命中后限制进入，可随时管理或撤回")
                + "\n已保存；请到目标页和相邻页验证识别"));
        body.addView(note(rule.classOnly ? "识别范围：整个界面（同一界面内的标签也可能受限）" : "识别范围：界面标识与全部特征同时匹配"));
        body.addView(note("页面标识：" + rule.type));
        for (RecordedPageRule.Anchor a : rule.anchors) body.addView(note("• " + a.text + (a.selected ? "（须处于选中状态）" : "")
                + (a.text.matches(".*[0-9０-９].*") ? "\n含数字，变化后需要重新录入" : "")));
        body.addView(button("保存名称", () -> {
            if (RecordedPageStore.rename(this, rule.id, name.getText().toString())) {
                Toast.makeText(this, "名称已保存", Toast.LENGTH_SHORT).show();
            } else name.setError("名称不能为空；若仍失败请重新读取记录");
        }));
        body.addView(button("重新录入此页面", () -> beginCapture(rule.pkg, rule.id)));
        body.addView(note("重新录入成功后替换特征，保留名称和开关；取消录入会保留原记录。"));
        body.addView(button("删除此记录", () -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("删除这条页面规则？").setMessage("删除后不再识别此页面，本机保存的所选特征也会删除。")
                .setPositiveButton("删除", (d, which) -> { if (!RecordedPageStore.delete(this, rule.id)) failure(); render(); })
                .setNegativeButton("取消", null).show()));
        ScrollView scroll = new ScrollView(this); scroll.addView(body);
        SecondaryPage.show(this, scroll, "页面规则详情", this::render);
    }
    private void beginCapture(String target) { beginCapture(target, null); }
    private void beginCapture(String target, String replacingId) {
        if (!PageMonitoringStore.read(this).enabled || !FocusAccessibilityService.isRunning(this)) {
            Toast.makeText(this, "请先独立授权、开启总开关并连接无障碍", Toast.LENGTH_LONG).show(); return;
        }
        sendBroadcast(new android.content.Intent(RecordedPageStore.captureAction(this)).setPackage(getPackageName()).putExtra("target", target).putExtra("replaceId", replacingId));
        moveTaskToBack(true);
    }
    private TextView note(String text) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(14);
        view.setTextColor(getColor(R.color.text_secondary));
        view.setPadding(0, PageUi.dp(this, 10), 0, PageUi.dp(this, 10)); return view;
    }
    private Button button(String text, Runnable action) {
        Button view = new Button(this); view.setText(text); PageUi.secondary(view);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = PageUi.dp(this, 12); view.setLayoutParams(params);
        view.setOnClickListener(v -> action.run()); return view;
    }
    private void failure() { Toast.makeText(this, "保存失败，请重试；本次未自动启用", Toast.LENGTH_LONG).show(); }
}
