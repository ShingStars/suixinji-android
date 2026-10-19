# 随心记 1.1.0 测试报告

验收日期：2026-10-19（Asia/Shanghai）。包名 `cn.suixinji.app`，versionCode 2，最低 Android 10 / API 29，目标 API 36。v1.0.0 历史报告保留在 `TEST_REPORT_1.0.0.md`。

## 自动化结果

| 检查 | 环境 | 结果 |
|---|---|---|
| 日期、统计、计时与备份加密回归 | JDK 17 / JUnit | 原 14 项通过 |
| 导入、旧设置兼容及更新规则 | JDK 17 / JUnit | 新增 8 项通过；共 22 项，0 失败 |
| 界面及设备集成 | Android 10 / API 29 / x86_64 模拟器 | 14 项通过 |
| 界面及设备集成 | Android 13 / API 33 / x86_64 模拟器 | 14 项通过 |
| 界面及设备集成 | Android 16 / API 36 / x86_64 模拟器 | 14 项通过 |
| Android Lint Debug | 固定工具链 | 0 errors，22 warnings |
| Android Lint Release | 固定工具链 | 0 errors，19 warnings |
| 正式 APK 签名 | APK Signature Scheme v2 / RSA 3072 | 通过，与 v1.0.0 相同证书 |

静态检查警告为已有兼容属性、依赖更新提示、Compose 参数风格与 KTX 写法建议；未屏蔽兼容性错误。三个模拟器均为 1080 × 2340、420 dpi，不是真机。

## 新功能覆盖

- 滑动：依次经过五个页面并返回，首页右滑不进入“我的”，末页左滑不进入“今天”；底部点击仍可导航。
- 个人资料：昵称、签名编辑后持久化，头像加入加密备份。移除本机头像并清理后，从备份恢复原头像及资料。旧版设置缺失新增字段时能正常读取。
- 导入：CSV 引号、字段内逗号、UTF-8 模板、单双周、间断周、非法周数/节次；错误预览不静默丢弃。批量追加去重、替换、旧课程进入回收站及恢复。
- 苏大 HTML：重复表、同格多安排、第 20 周单节课程、空教室；非连续节次构造用例不会填补中间空节。用户提供的本地真实课表识别为 7 条上课安排、6 个课程名，未重复导入。原文件不进入源码或公开证据，公开夹具为匿名重建。
- 课表：周一至周日标题同时可见，导入入口及长图预览正常；导出尺寸覆盖全部节次。系统相册保存使用 MediaStore，不申请广泛存储权限。
- 更新：正式版语义版本比较、非正式版本排除、HTTPS 下载域名限制、摘要格式；设备端拒绝将当前版本 APK 当成升级包。

## 正式包人工验收

在 Android 10 模拟器安装原 v1.0.0 正式 APK，创建日程 `UpgradeKeep-110`，使用相同签名的 v1.1.0 直接覆盖安装后，原日程仍完整显示；未清除应用数据。

在升级后的正式版通过系统文件选择器选取匿名 CSV：预览 7 条课程 → 确认追加 → 七列课表展示 → 完整 PNG 预览 → 保存至系统相册 → 打开 Android 系统分享面板，均完成。图片不包含头像、昵称、签名，教师默认隐藏。

实际通过系统选图选择测试图片、打开裁剪预览并保存头像；设置为深色及 130% 系统字体，检查七天列、课程块、时间和纵向滚动。截图在 `docs/screenshots/v110-*.png`，仅含合成演示数据。

“我的 → 检查更新 → 立即检查”实际访问 GitHub API，正常返回当前已是最新正式版的状态。更新下载路径包含 SHA-256、文件大小、包名、递增 versionCode、最低 SDK 和相同签名检查；由用户确认下载和系统确认安装。

API 29、33 的本轮崩溃缓冲检查未发现应用崩溃。首轮失败为旧按钮定位、分页预加载导致同名节点、JUnit 返回类型和异步图片语义定位，均已修正后复测。

## 证据与复现

本地 `qa/artifacts/instrumentation-api29.log`、`instrumentation-api33.log`、`instrumentation-api36.log`，每份均为 `OK (14 tests)`。单元结果在 `app/build/test-results/testDebugUnitTest/`，静态结果在 `app/build/reports/`。公开仓库不含个人原始 HTML、本机运行日志或签名密钥。

```powershell
& ./tools/build.ps1 -Tasks @(':app:testDebugUnitTest', ':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:lintDebug', ':app:assembleRelease', ':app:lintRelease')
& ./tools/emulator.ps1 -Api 33 -Port 5554
& ./tools/run-device-tests.ps1 -Serial emulator-5554 -Api 33
```

私有课表结构测试在设置 `SXJ_PRIVATE_TIMETABLE` 为用户自己的本地 HTML 时执行；公开环境没有该文件时跳过此一项，仍运行其余 21 项。设备脚本仅允许模拟器，清空模拟器应用测试数据；切换正式签名和调试签名时需先移除原测试包。

## 尚未验证

未接入用户真机。苏大实际账号登录、校园网络/WebVPN、验证码及在线读取到导入的完整流程仍需用户学校环境验收；当前不忽略 HTTPS 证书错误，HTTP 使用外部浏览器及保存 HTML 后导入。真实样本没有单双周和非连续节次变体，这些仅有构造测试，不表述为学校真实变体已通过。

GitHub 可达性、请求限流、网络波动和厂商安装权限会影响更新。不会绕过系统安装确认，失败不改变个人记录。超大文件、超大记录量及跨品牌迁移未做压力测试。
