# 页面滑动与保护状态

## 滑动卡顿

昨日为缩短主题切换时间，将应用页改为首次进入时创建，但创建仍同步查询已安装应用、加载图标、读取使用记录，并重复刷新用量。切页回调同时重建不可见的首页任务卡片。

- AppManagePage 将安装应用、图标、使用时长和临时使用次数读取移到后台，合并未完成的刷新。
- HomeNavigation 在滑动结束后刷新当前页，停止切页时重建其他页。后台结果在滑动期间等待，到停稳后显示。
- 应用列表初始化期间显示加载状态，完成后按已有设置选择“已限制”或“全部应用”。保存搜索及用户选择，读取完成时使用最新限制设置，避免覆盖读取期间的编辑。
- 页面销毁时取消读取和界面回调。
- 保留昨日 PixelCopy 主题截图与圆形扩散方案，移除临时 ThemePerf/SwipePerf 探针。昨日试验用的 180ms 主题测量脚本归档到 build/diagnostics，不作为当前已接受的主题效果的检查门槛。

### 手机验证

HomeSwipePerformanceTest 通过真实 ViewPager 拖动路径采集切页回调与 Choreographer 帧间隔，同时确认应用列表最终加载。

| 路径 | 修复前回调 | 修复后回调 | 修复后最长帧间隔 |
| --- | ---: | ---: | ---: |
| 首次应用页 | 1034ms | 4ms | 25ms |
| 应用到计划 | 17ms | 3ms | 41ms |
| 返回首页 | 54ms | 13ms | 24ms |
| 再次应用页 | 106ms | 21ms | 49ms |
| 再次计划页 | 41ms | 15ms | 16ms |

测量来自 RMX3888；不代表其他机型的帧率保证。最终搜索/筛选保存修正通过编译，未重新运行会强停保护进程的 instrumentation。assembleDebug 成功，最终 APK 校验 SHA256 后覆盖安装成功；安装前后任务与应用限制配置 SHA256 一致。

## 无障碍开关开启但服务未绑定

系统 dumpsys accessibility 显示开关在 Enabled services，Bound services 为空、Crashed services 含本应用。进程退出记录明确标为 `due to start instr` / `due to finished inst` 的 FORCE STOP。这次断开由 Android instrumentation 停止整个包（含 :guard）触发，没有看到支持“此次是后台省电杀死”的证据。

- ProtectionStateStore 在新保护进程启动时清除旧进程遗留的无障碍连接与心跳；新进程不会继承系统绑定。
- 应用保护页面只在短暂等待阶段显示“正在连接”；超出 2 秒仍未恢复则显示“已断开”，提示关闭再开启无障碍，提供系统详情页入口和通用设置后备入口。
- 常驻通知根据连接状态显示“正在保护”或“保护已断开”，断开时点按进入恢复页面。
- 应用保护页面仅在可见时每秒刷新，离开时取消回调。

最终安装后确认 Bound services 含奶蛙ToDo、Crashed services 为空；保护状态文件 accessibilityConnected 和 foregroundRunning 均为 true，记录的 PID 与实际 :guard PID 一致。没有人为开始专注、睡前会话或修改限制来测试阻断。

上述修正提供准确状态和恢复入口；没有承诺可自动恢复被系统强制断开的无障碍，也未据此宣称所有后台存活问题已经解决。后续 UI 检查优先使用不强停应用的方式。

滑动修复前相关源码及 APK 保存于 build/before-home-swipe-2026-10-04/。
