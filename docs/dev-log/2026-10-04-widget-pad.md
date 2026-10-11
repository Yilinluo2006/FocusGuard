# OPPO Pad 5 桌面小组件

## 复现与原因

- 平板 `OPD2506` 的系统 AppWidgetManager 正常登记 `StreakWidget`，没有 zombie 状态。
- OPPO 的「卡片中心」原生卡片目录不直接展示标准 Android 小组件；真实入口为：桌面长按空白处 → 卡片 → 全部卡片 → 最底部「插件」。在插件列表找到「奶蛙ToDo 连续坚持」，规格 4×2。
- App 内「桌面小组件」命令在 `WidgetPin.show` 的 RemoteViews.apply 崩溃：AppCompatImageView 的 setImageResource 不能被 RemoteViews 调用。
- 同一 populated RemoteViews 在隔离 AppCompat Activity 中复现相同异常。将 apply 的 context 改为 application context 后通过，支持控件替换是根因；布局/资源本身可以正常渲染。
- 平板 requestPinAppWidget 返回接受请求，但没有系统确认界面或回调；8 秒后显示未确认状态。不能把接受请求当成添加完成。

## 修改

- WidgetPin.inflatePreview 使用 Application context，让 RemoteViews 使用系统控件。
- 固定预览高度 160dp，避免占满对话框。
- OPPO 设备展示已验证的「插件」入口路径，其他设备保留通用说明。
- 打开预览不再自动请求添加，用户点击「添加到桌面」后才发起系统请求。

## 验证

- 隔离项目 `build/setup-probe-project`，`WidgetPreviewTest`：修改前 1 个失败，修改后 `OK (1 test)`，断言文字、图片、5 个日期格均已渲染。
- 实际手机与平板覆盖安装成功，打开 App 内预览正常；平板点击系统添加按钮无确认界面，手动入口有效。
- 从平板标准插件列表点击实际产品组件，系统分配 widget id 4，provider 为实际产品包，RemoteViews 非空。完成桌面编辑后实际组件内容正常显示。
- 已移除设备上的隔离测试应用；用户数据保留。两端无障碍服务 bound、没有 crashed services。
- 未修改后端或睡前/额度规则。
