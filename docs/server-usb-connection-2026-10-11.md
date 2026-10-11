# 手机连接本机后端恢复（2026-10-11）

用户反馈后端启动成功但应用无法连接。

确认已安装构建的 API_BASE_URL 来自 local.properties，为 http://127.0.0.1:8080/。电脑 java 进程监听 8080，电脑请求 /api/health 返回 status=ok。手机处于移动网络/VPN状态；手机地址 127.0.0.1 代表手机自身，不直接代表电脑。

ADB reverse --list 原本为空。手机原请求 /api/health 失败（curl 7，连接拒绝），运行以下已有设备的可逆调试配置后，同一手机请求成功返回 FocusGuardServer / status=ok：

    E:/AndroidSDK/platform-tools/adb.exe -s 470e575c reverse tcp:8080 tcp:8080

未修改代码、后端设置、账号、权限或应用数据，未发登录/注册/邮件请求。当前连接依赖 USB ADB 转发；USB 断连、设备或 ADB 重启后需核查转发状态，不能仅依据后端启动日志认为手机已能连接。
