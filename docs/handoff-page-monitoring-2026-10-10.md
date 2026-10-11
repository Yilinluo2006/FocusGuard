# 奶蛙 ToDo 项目交接（2026-10-10）

> 用户后续要求取消微信每日额度，已仅取消 com.tencent.mm 本机额度及账号对应记录，其他限制保留；未改页面规则和睡前计划。见 [cancel-wechat-limit-2026-10-11.md](cancel-wechat-limit-2026-10-11.md)。

> 后端连接补充：重装后手机无法连接是 127.0.0.1:8080 的 USB 转发缺失，已恢复 adb reverse，手机健康接口返回 ok；连接仍依赖 USB。见 [server-usb-connection-2026-10-11.md](server-usb-connection-2026-10-11.md)。未恢复卸载前应用数据。

> 最新真实状态：用户利用限制层触发延迟仍成功卸载正式应用，不能视为可靠防卸载。按用户“先重装”要求，已重新安装相同最终 APK 并启动；旧任务/计划/独立授权/页面规则文件不存在，数据恢复尚未完成。保留响应延迟问题，见 [page-layer-lifetime-2026-10-11.md](page-layer-lifetime-2026-10-11.md) 后续记录。

> 2026-10-11 最新收尾：用户报告限制层半秒消失，已实机定位为后台卸载组件迟到事件误触发退出，加入前台窗口元数据校验。缺陷回归先失败后通过，原系统卸载入口实机和用户确认可用，无诊断日志最终包已安装；用户规则/授权与 23 项原数据保留，探针已清理。见 [page-layer-lifetime-2026-10-11.md](page-layer-lifetime-2026-10-11.md)。

> 2026-10-11 最新：用户按参考视频要求直接到页面录入、自动摘要确认，新系统页面也限制进入。第 4 版已安装，6 项 JVM/30 项 Android 检查通过，原数据与旧授权/规则文件保留，探针已清理；用户已在应用内同意第 4 版并自行保存一条系统卸载页规则，必须保留；正式微信及规则再次进入命中尚待实测。见 [direct-page-recording-2026-10-11.md](direct-page-recording-2026-10-11.md)。本条覆盖下方旧版处理约定。

> 2026-10-11 最新接续：用户已恢复代码修改和安装。保存与管理修复已安装、26 项测试通过，23 项原数据及独立授权/规则摘要一致，临时规则和探针已清理。微信视频页识别及系统详情页窗口不一致仍未解决，见 [recorded-pages-fix-2026-10-11.md](recorded-pages-fix-2026-10-11.md)。用户要求控制剩余额度，后续避免扩大无关测试。

> 历史暂停指令（已被 2026-10-11 用户恢复授权取代）：真实使用发现录入兼容性、普通/系统页保存及记录管理问题。现在只记录问题与分析方案，**不修改或生成代码、不构建安装、不操作手机**；等待用户明确恢复开发。问题记录见 [recorded-pages-issues-2026-10-10.md](recorded-pages-issues-2026-10-10.md)。不要根据下面历史验证记录宣称当前功能已普遍可用。

> 最新接续：用户实测后改为“现场录入目标页面、普通应用限制进入、系统页面只提醒”，并要求小浮窗。第 3 版已安装、用户已主动授权；真实无障碍详情/桌面卸载确认和隔离普通页均完成录入与再进入验证，三个临时规则已删除，两个探针包已卸载，23 项原数据一致。见 [recorded-pages-2026-10-10.md](recorded-pages-2026-10-10.md)。当前允许控件读取，仅用于授权后的录入及已录入页面匹配；不要按下面历史第一版的 false 配置回退。总开关开启但规则为空，用户需自行录入。以下保留历史交接。

> 接续完成：独立授权与系统退出提醒第一版已于 2026-10-10 完成测试并覆盖安装，正式授权保持默认关闭，23 项原数据摘要一致，两个探针包已卸载。最终记录见 [page-monitoring-v1-2026-10-10.md](page-monitoring-v1-2026-10-10.md)。以下保留原交接内容，未完成状态以最终记录为准。

## 用户当前指令

用户要求结束旧对话，新建对话继续项目。本会话没有 create_thread / set_thread_archived 工具，不能直接创建或归档。旧对话已停止开发，下面用于新对话接续。

## 项目与设备

- Android 项目：E:\FocusGuard
- ADB：E:\AndroidSDK\platform-tools\adb.exe
- 手机序列号：470e575c
- 仓库有大量既有修改和未跟踪文件，必须保留。不要 git reset/clean，不要清正式应用数据，不要为测试消耗正式临时次数或更改真实计划。
- 当前日期/时区：2026-10-10，Asia/Shanghai。

## 已交付，勿重做

1. 设置页删除“恢复保护与检查记录”“查看保护详情与修复”两个重复入口；置顶金色“必要加固”，突出必要设置和壁纸守护，正式手机已安装。
2. 睡前计划修改与应用额度共用现有窗口，本机为 07:00–08:00。其他时间可加强，睡眠进行中不能修改；使用可信时间、单调时钟与固定管控时区，防改日期/时间/时区。覆盖白名单、同步 pending 的 CAS 与取消、备份补缺校验。44 项纯 Java、39 项 Android 测试通过并已安装。原计划每天 23:30，240 分钟保持。
3. 睡眠临时使用浮窗：桌面和应用切换时显示剩余分秒，可拖动贴边，读取授权真实截止点，不重置/消耗额外次数；到期隐藏与恢复管控。7 项纯 Java、9 项 Android 检查通过，23 项用户数据摘要前后相同，已安装。文档 docs/sleep-temporary-timer-2026-10-10.md。

用户暂停锁屏耗电专项；真实睡前管控验收等晚上由用户安排，不要自动启动睡眠/熄屏/改系统时间测试。

## 当前未完成：第一版独立授权 + 系统退出提醒

用户原本想自由指定受限页面（微信内嵌小视频、卸载、无障碍关闭等）。经过讨论，用户同意第一版先只完成“独立授权 + 系统退出提醒”。自定义普通应用页面限制尚未实现，不得宣称已有。

约定：默认关闭，独立说明识别范围、用途、数据处理、可能误判和撤回方式，未勾选不能启用。系统提醒只提示影响，有“返回”和“继续操作”，不会阻止卸载或关闭系统服务。用户随时可以撤回，不能套用 07:00–08:00 或睡眠锁定去阻止退出授权。

### 已写入代码

新增：
- app/src/main/java/com/luoyilin/focusguard/PageMonitoringActivity.java：独立告知、未勾选复选框、同意启用、提醒开关、随时撤回。
- PageMonitoringStore.java：跨进程锁 + no_backup AtomicFile，当前授权版本 1，默认关闭，不参与云备份/设备同步。失效/读失败时关闭。
- SystemExitPagePolicy.java：仅系统窗口事件的包名、界面类型、提示标题，识别本应用卸载或无障碍详情；不读取节点树或聊天。
- SystemExitReminderController.java：非模态顶部提醒，“返回”仅在用户点击时执行系统返回，“继续操作”仅隐藏，一次系统设置访问只提醒一次，15 秒自动移除，撤回广播立即隐藏，解绑关闭清理。
- app/src/main/res/values/page_monitoring_ids.xml
- app/src/test/java/com/luoyilin/focusguard/SystemExitPagePolicyTest.java（7 项通过）
- app/src/androidTest/java/com/luoyilin/focusguard/PageMonitoringTest.java（5 项，3 项已通过；2 个窗口测试尚未完成）

修改：
- HomeNavigation.java：设置页“可选功能”里的“页面监控与退出提醒”入口。
- AndroidManifest.xml：非导出 PageMonitoringActivity。
- FocusAccessibilityService.java：连接/窗口事件/解绑/销毁接入提醒；可选模块异常不影响原保护服务。
- FocusModeController.java：授权管理页不被睡前遮挡，系统安装器等退出操作保留访问。

重要：accessibility_service_config.xml 的 canRetrieveWindowContent 仍为 false；没有扩大内容读取权限。仅凭窗口元数据可能无法识别部分厂商入口，须诚实说明，不能声称所有卸载/关闭页面已实机适配。

### 验证与 APK 状态

- build/diagnostics/page-monitoring-v1/build.log：正式 assembleDebug 曾成功，但随后有小修（模块异常隔离、开关状态刷新、控制器 close 等），最终正式包需要重建。
- probe-build.log：独立探针构建成功，但最新小修也要同步后重建。
- junit.log：OK (7 tests)。
- consent-tests.log：OK (3 tests)：默认关闭/未确认不能开启、授权与撤回不改睡眠计划、复选框默认未勾选且需勾选才能同意。
- 两个横跨系统窗口的提醒测试尚未运行通过：continue 无系统操作且不重复、撤回后关闭已显示的提醒。
- 本功能尚未覆盖安装正式手机，不得报“已交付”。
- 没有遗留 adb 子进程（交接只读检查时未发现）。ADB 可连接，接续时重新确认。

### 测试包与权限待处理

独立项目 build/setup-probe-project，applicationId=com.luoyilin.focusguard.setupprobe，标签“奶蛙倒计时测试”。此测试包及 .setupprobe.test 当前已安装。

探针悬浮窗权限当前未确认：adb appops get SYSTEM_ALERT_WINDOW 返回 default/no operations；手机禁止 adb appops set（MANAGE_APP_OPS_MODES）。旧对话已打开系统悬浮窗列表，找到唯一测试包，但无法自动识别对应开关，没有冒险操作正式应用。可让用户只给“奶蛙倒计时测试”开悬浮窗，然后运行全 PageMonitoringTest。新对话不要假定旧权限还在；之前的倒计时测试包已卸载，权限不继承。

独立测试服务用 attachBaseContext 的 ProbeService，未启用探针无障碍，测试假系统窗口事件，不关闭真实无障碍、不卸载正式应用。请检查 PageMonitoringTest 最新源与探针副本同步。

### 继续步骤

1. 读取当前代码、此次 before 快照，确认中断没有部分变更或未结束构建，继续而非重启项目。
2. 改进/验证系统窗口识别的保守边界，不能靠字幕“免责声明”绕过用户退出授权。
3. 同步最新源到探针；正式与探针重建；完成剩余窗口测试及授权页深浅色视觉核对。
4. 必要时在用户配合下只读观察本应用系统设置入口，不实际卸载/关闭正式服务；如果元数据不足，明确支持范围，保留 canRetrieveWindowContent=false 的第一版边界。
5. 原数据摘要前后比较后 install --no-streaming -r 更新正式应用，默认授权仍关闭；不代用户同意。
6. 卸载两探针包，恢复所有临时构建配置，写最终报告并交付。

## 构建环境

本机 C:\Users\罗乙淋\.jdks\openjdk-24。Gradle daemon criteria 文件默认 toolchainVersion=21，会尝试坏的远程 JDK 下载。以 try/finally 保存文件原始字节，临时替换为 24，设置 JAVA_HOME，传 -Porg.gradle.java.installations.paths=C:\Users\罗乙淋\.jdks\openjdk-24，构建后原样恢复。不永久改用户 Gradle 配置。

普通 Gradle Test Executor 有中文用户目录 classpath 问题；可用已编译类与 build/diagnostics/junit-compliance.jar、hamcrest-compliance.jar 运行 JUnitCore。此次纯策略 javac 输出在 build/diagnostics/page-monitoring-v1/jvm。

ADB 安装优先 --no-streaming -r。权限/功能测试必须硬门禁 .setupprobe；正式数据不要 clear。各次 before 快照与日志已在 build/diagnostics/page-monitoring-v1/ 保存。

## 政策背景（已查官方，避免反复争论）

Google Play 普通应用不能通过无障碍阻止用户停用/卸载，用户勾选免责协议不解除该平台限制；普通应用需独立醒目披露与主动同意。基于同意处理个人信息需有便捷撤回。第一版为非强制提醒，无隐蔽页面读取或自动改设置。

参考：https://support.google.com/googleplay/android-developer/answer/16558241?hl=en
https://support.google.com/googleplay/android-developer/answer/10964491?hl=zh-Hans
https://www.cac.gov.cn/2021-08/20/c_1631050028355286.htm
