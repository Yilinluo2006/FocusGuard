# 2026-10-02 专注热力图

## 实现

专注趋势的近七天、月、年柱状图改为每日圆角格子热力图。
星期一至星期日为七行，每列一周；默认显示截至所选统计日的近十二周，
更早有记录时扩展内容宽度，可横向滑动查看。初次布局滚动到最近日期。
月份显示于顶部，尚未开始的日期不显示可点击格子；所选日有细轮廓。
点击任意已到日期格子显示日期、已保存的专注时长。添加虚拟无障碍节点支持逐格朗读与点击。

固定五档颜色：0 灰色、正时长且不足 25 分钟、25–59 分钟、60–119 分钟、至少 120 分钟。
暗色使用逐渐明亮的金色；浅色使用逐渐浓郁的金色。零数据仍显示灰色格子，不伪造活跃记录。

FocusHeatmapData 在已有后台执行器中汇总历史。继续遵守凌晨 04:00 分界，
跨天拆分实际专注时长，排除番茄钟休息，仅汇总已结束且保存的记录。
旧 Activity 状态的 statisticsPeriod 不再使用；日期与应用排行独立日期仍恢复。
当日专注时段柱状图继续保留。

## 验证

- 离线 assembleDebug 和单元测试编译成功。
- 96 项 JUnit 全部通过，新增三个汇总测试验证跨 04:00、任务合计、番茄钟休息、截止时间及短时长颜色。
- 手机 470e575c 重新确认在线且无活动普通专注、番茄钟或睡前窗口，覆盖安装返回 Success。
- 未启动手机计时测试；真机热力图视觉、滑动、点击和其他机型表现待用户反馈。

## 首次滑入空白修复

用户报告首次滚到热力图时空白，点击后才出现。原因是 onDraw 使用
getLocalVisibleRect 判断是否绘制：视图在 ScrollView 视口下方时首次记录得到空绘制，
硬件绘制缓存被滚动复用；点击触发 invalidate 后重新记录才显示。
将绘制裁剪改为 Canvas.getClipBounds，保证屏幕下方的首次绘制仍包含格子。
无障碍节点仍按实际可见区域查询；未改动时长数据、颜色或计时逻辑。

新增 Android 仪器测试 FocusHeatmapViewTest，在真实 ScrollView/HorizontalScrollView 层级下，
分别测试可见与视口下方的首次绘制，不依赖触摸。修复前可见场景通过，
视口下方场景失败，painted pixels=0；修复后两项全部通过（0.344 秒）。
命令：adb -s 470e575c shell am instrument -w -r -e class
com.luoyilin.focusguard.FocusHeatmapViewTest
com.luoyilin.focusguard.test/androidx.test.runner.AndroidJUnitRunner。

离线 assembleDebug 与 assembleDebugAndroidTest 成功，确认无活动计时后覆盖安装成功。
测试结束移除临时测试 APK，保留回归测试源码。未启动专注或睡眠测试，未切换手机前台应用。
