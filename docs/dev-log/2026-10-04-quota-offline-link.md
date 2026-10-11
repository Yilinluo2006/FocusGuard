# 2026-10-04 离线倒计时两分钟排查

用户截图显示小黑盒“离线可用 02:00”。真实手机读取验证：限制仍为 30 分钟，已确认合计用时约 15.4 分钟，但最后成功同步已过去约 42 分钟，手机 USB 重连后缺少 `tcp:8080` 转发。平板转发和同步均正常。不是限制配置被改成两分钟。

## 反馈循环与修复

`python build/diagnostics/check-quota-link.py 470e575c` 只读真实共享额度文件、同步状态、开机编号和服务端限制，凭证仅留内存，不保存或输出。修复前返回失败：`reverse8080=False`、`timer_online=False`。只恢复手机 ADB reverse，未重启服务、未重装 APK、未改使用记录或额度。服务下一次轮询恢复成功，原命令返回通过，两端均在线。

根因是本地后端联调的连接丢失。增加 `build/diagnostics/keep-dev-quota-link.ps1`，仅针对当前手机和平板恢复缺失的 8080 转发；`-Watch` 每五秒检查，默认最多四小时后退出。脚本不启动后端、不分配额度，也不修改其他设备和端口。

```powershell
./build/diagnostics/keep-dev-quota-link.ps1
./build/diagnostics/keep-dev-quota-link.ps1 -Watch -DurationMinutes 240
```

## 范围

严格共享模式仍遵守用户选定规则：未连上后端时只可用已领取的两分钟预留额度，不能为了显示正常而把未领取额度放开。电脑端自动恢复只适用于 USB 开发联调；拔掉 USB 或电脑关闭后不能继续联网续领。任意网络下持续同步仍需可由两端访问的后端，当前未部署云端。
