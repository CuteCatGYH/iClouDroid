# iClouDroid

> **在 Android 手机上直接看你的 iCloud 照片。** 不需要 iPhone，不需要 iPad，也不需要开电脑。

如果你手上是 Android 主力机，但照片都存在 iCloud 里（家里有 iPhone、iPad 或 Mac 在拍），
这个 App 让你在 Android 上直接浏览那批照片 —— 就像用系统相册一样。

---

## 它能做什么

**📷 浏览整个 iCloud 照片库**

登录你的 Apple ID，就能在 Android 上看到 iCloud 里的全部照片和视频。
按月份分组，滚动流畅，可以切换每行显示 3 / 4 / 5 张。

**⚡ 边扫边看，不用等**

第一次登录后会整理整个图库。你不需要盯着进度条等它跑完 —— 最快的那部分照片会先出现，
你可以马上开始查看，剩下的可在后台继续整理。

**🔒 登录信息加密保存在手机上**

用 Apple 官方的登录页面输入密码和验证码（和你在浏览器里登录 iCloud 一模一样），
App 不会看到也不会保存你的密码。登录状态加密存放在手机本地。

**🌏 国内和海外账号都支持**

登录前请选择中国区（云上贵州）或国际区的 iCloud，登录成功后请点击***提取 cookie***按钮。

**🖼️ 原图、视频、下载都在**

点开可以看大图；支持视频播放；可以把照片保存到手机相册。

---

## 怎么用

1. **安装 App**，打开后选择你的账号区域（中国大陆 / 国际）
2. **点击下方按钮**会打开 iCloud 官方登录页，正常输入 Apple ID 和密码
3. **收到验证码时输入**；如果看到「信任此浏览器」的选项，**建议勾选** —— 这样之后一段时间不用重复登录
4. 登录成功后会自动开始整理照片库，稍等片刻就能看到照片

> **关于「信任此浏览器」**
> 勾选后，iCloud 会记住这台设备 30~60 天。这段时间里你重新打开 App 不需要再登录。
> 不勾选也能用，只是登录状态会更频繁地过期。

---

## 常见问题

**Q：我的密码会被上传到哪里吗？**

不会。登录完全通过 Apple 官方的网页完成，和你在浏览器里登录 iCloud 是同一个页面。
App 只保存登录成功后 iCloud 发给它的"凭证"，且加密存放在手机本地。

**Q：为什么第一次打开要等一会儿？**

iCloud 上万个文件需要逐个读取。App 会按时间从新到旧整理，
所以你看到的照片从最近开始 —— 马上就能用，不用等全部完成。

**Q：可以关掉 App 吗？会不会中断？**

可以。切到后台后会在通知栏显示进度并继续工作。
如果中途被杀掉或断网，下次打开会**接着上次的地方继续**，不会从头再来。

**Q：退出登录后，本地缓存会留下吗？**

不会。退出时会完整清除这个账号在本机保存的照片索引和图片缓存。
换账号或换区域时直接退出重登即可，如果你不放心的话也可以去系统设置里清除 App 数据。

**Q：现在支持哪些功能？**

目前仅可以**浏览** iCloud 照片、播放视频、下载到手机相册。
「把手机本地照片自动备份到 iCloud」还在开发中，目前 Drive 和备忘录也是占位页面。

---

## 目前的进度

| 功能 | 状态 |
| --- | --- |
| iCloud 登录（含两步验证） | ✅ 可用 |
| 浏览照片与视频 | ✅ 可用 |
| 月分组视图 + 年月快速跳转 | ✅ 可用 |
| 视频播放 | ✅ 可用 |
| 保存到手机相册 | ✅ 可用 |
| 本地相册自动备份到 iCloud | 🚧 开发中 |
| 动态照片（Live Photo）互转 | 🚧 计划中 |
| iCloud Drive / 备忘录 | 🚧 计划中 |

---

## 需要知道的事

**照片加载有时会慢一点**

iCloud 不会把照片直接给你，而是给一个限时的下载链接，链接大约 15~18 分钟后就失效。
App 会自动处理换链，但如果图片加载失败，点一下重试通常就好了。

**扫描大图库需要时间**

如果 iCloud 里有上万张照片，完整整理可能需要几分钟。这是苹果接口的速度限制，
不是 App 卡住了。

***这是一次性工作，不是实时同步***

App 浏览的是你已经上传到 iCloud 的照片。你在 iPhone 上新拍的照片，
需要等它上传到 iCloud 刷新后才能看到。

---

## 安装

目前还没有发布到应用商店，需要通过 Android Studio 自行编译安装。

如果你只是想用，请找我要安装包，在手机上直接安装即可

<details>
<summary>给开发者：如何自行构建</summary>

### 环境要求

- **Go** 1.21+（推荐 1.27）
- **JDK** 17+（建议使用 Android Studio 随附的 JBR）
- **Android SDK** API 35+，**NDK** 26+

### 构建步骤

1. 先打包 Go 核心层（首次和每次改动 Go 代码后都要做）：

   ```bash
   ./scripts/build_aar.sh
   ```

   脚本会自动检测 `JAVA_HOME`、`ANDROID_HOME` 和 `ANDROID_NDK_HOME`，
   在 `app/libs/` 下生成 `icloudcore.aar`。

2. 编译 APK：

   ```bash
   ./gradlew :app:assembleDebug
   ```

   产物在 `app/build/outputs/apk/debug/` 下。

### 技术架构

```
Android (Kotlin + Jetpack Compose)
    │
    │  WebView 官方登录页 → 提取 Cookie → 加密存储
    │
    ▼  JNI (gomobile)
Go 核心层 (go-core)
    │
    │  CloudKit 私有协议 (ckdatabasews)
    ▼
iCloud 照片服务
```

- **UI 层**：Jetpack Compose + Material 3，按月分组的 `LazyColumn`
- **数据层**：Room 本地索引 + Coil 图片加载（缓存 Key 绑 `recordName`）
- **协议层**：Go 实现 CloudKit `/records/query` 游标分页、`/records/changes` 增量同步
- **后台任务**：WorkManager + 前台服务，扫描可断点续扫

### 文档

| 文档 | 内容 |
| --- | --- |
| [docs/AGENTS.md](docs/AGENTS.md) | 开发规范（**新接手必读** 第 3、4、9 节） |
| [docs/CHANGELOG_ISSUES.md](docs/CHANGELOG_ISSUES.md) | 已修复问题案例库（ISS-001 ~ ISS-029） |
| [docs/PITFALLS.md](docs/PITFALLS.md) | 工程避坑指南 |
| [docs/REFERENCE.md](docs/REFERENCE.md) | CloudKit 字段字典（实测）与开源参考 |
| [docs/TODO.md](docs/TODO.md) | 分阶段路线图 |
| [docs/list.md](docs/list.md) | 待修复问题清单 |

</details>

---

## 致谢

本项目在协议实现上参考了以下开源项目：

- [rclone/rclone](https://github.com/rclone/rclone) —— HTTP 会话与网络底座
- [icloud-photos-downloader/icloud_photos_downloader](https://github.com/icloud-photos-downloader/icloud_photos_downloader) —— CloudKit 协议与字段结构参考

---

> **免责声明**：本项目为个人开发的第三方客户端，与 Apple Inc. 无关联。下载后请在 24 小时内删除。
> 使用前请确保你了解 iCloud 的使用条款，并自行承担账号安全风险。
