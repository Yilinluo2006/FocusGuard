# 重启后无障碍授权丢失：实机调查

手机 RMX3888，Android 16（SDK 36），厂商系统版本 V16.1.0。

## 复现结果

- 重启前 boot_count=94，奶蛙在 enabled_accessibility_services 中，服务已绑定，无崩溃服务。
- 用户手动重启、解锁后 boot_count=95，奶蛙组件从授权列表消失，accessibility_enabled=0，绑定、正在绑定和崩溃服务列表均为空。
- 安装包仍启用且 stopped=false；主进程和 guard 进程仍存在。
- 应用退出记录仅见先前 APK 更新，没有本次启动的新崩溃证据。
- dumpsys settings 显示 enabled_accessibility_services 被写为空、accessibility_enabled 为 0，写入归属 pkg:android。
- RUN_ANY_IN_BACKGROUND、START_FOREGROUND、ACCESS_ACCESSIBILITY 的 app-op 当前均为 allow。
- 系统无障碍详情页没有发现“重启后保持开启”选项，说明文字明确指出开启服务即获得自启动、不受自启动管理页面影响。
- 项目没有 disableSelf 或写入 enabled_accessibility_services 的调用。

因此，已经确认的是系统层授权被清除，不是单纯的服务断连。现有记录尚不足以确定系统内部具体是哪条策略触发清除。

## 权限边界与后续选择

当前开机广播只能在无障碍授权仍开启时启动普通保护服务；增加相同的后台拉起不能恢复已消失的授权。

Android 文档说明无障碍生命周期由系统管理。普通安装不能自动取得 WRITE_SECURE_SETTINGS。
同系列 ColorOS 16 的另一个开发者在自己的设备上记录了第三方无障碍授权被重启清除，并通过一次性 ADB 授予 WRITE_SECURE_SETTINGS 后在开机恢复自己的服务项：
https://github.com/Nielk74/coloros-power-button-launcher

这是单独的开发设备兼容方案，需要明确的额外系统权限授权和本机实测，不能当作普通用户安装即获得的能力。
若以后验证此方案，应只恢复用户此前明确开启且未主动关闭的奶蛙组件，并保留其他服务，不覆盖整个授权列表。

本轮仅采集和调查，没有授予额外系统权限，也没有自动重新启用无障碍。诊断结束时手机无障碍仍关闭，需要用户从当前系统页面恢复。
