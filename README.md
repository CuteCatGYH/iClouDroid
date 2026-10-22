# iClouDroid

> **Android 原生 iCloud 照片客户端与自动备份工具**  
> 基于 **Kotlin (Jetpack Compose)** + **Go (gomobile .aar)** 混合架构，旨在解决 Android 与 Apple 跨生态下的照片浏览、双向/单向增量自动同步与动态照片（Motion Photo / Live Photo）互转痛点。

---

## 🌟 核心特性

- 🔐 **混合安全鉴权**：通过 Android WebView 安全完成 Apple ID 密码输入与 2FA 双重认证，自动拦截并加密存储 `TOKEN`、`HSA-TRUST`、`PCS-CloudKit` 三大关键凭证。
- 🌏 **双区动态路由**：支持**中国大陆（云上贵州 `icloud.com.cn`）**与**国际区（`icloud.com`）**自动解析分配的 CloudKit (`ckdatabasews`) 专属接入节点。
- ⚡ **Go 语言高性能协议底座**：复用 `rclone` 网络层，逆向 CloudKit 私有 API，实现会话保活与数据高并发传输。
- 📱 **现代 Compose UI**：基于 Material 3 Design，自适应折叠屏与大屏布局。

---

## 📐 架构设计

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Android (Kotlin + Compose)                      │
│                                                                        │
│  [AuthScreen] ──► [ICloudWebView] (官方登录页: .com.cn / .com)          │
│                          │                                             │
│                          ▼ (完成密码 + 2FA 认证)                        │
│                  [CookieManager] (提取 Cookie 字符串)                    │
│                          │                                             │
│                          ▼                                             │
│                  [SessionManager] (DataStore / EncryptedSharedPref)    │
│                          │                                             │
│                          ▼ (传输凭证 + 节点标识)                         │
│                    [AuthViewModel]                                     │
└──────────────────────────┬─────────────────────────────────────────────┘
                           │ JNI (Icloudcore.initSession)
                           ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Go 核心层 (go-core)                              │
│                                                                        │
│  [InitSession] ──► [HTTP Client & CookieJar] (注入 Header & Origin)    │
│                           │                                            │
│                           ▼                                            │
│   POST https://setup.icloud.com[.cn]/setup/ws/1/accountLogin            │
│                           │                                            │
│                           ▼                                            │
│     解析 webservices 字典 ──► 提取 ckdatabasews.url & dsid               │
│                           │                                            │
│                           ▼                                            │
│      返回 JSON 给 Kotlin ──► 渲染“登录成功 & 节点路由”状态                │
└──────────────────────────┴─────────────────────────────────────────────┘
```

---

## 🚦 当前进度与 Roadmap

- [x] **Phase 0: 基础工程与 gomobile 交叉编译**
  - [x] Go 核心层模块 (`icloudcore`) 初始化与 `gomobile` 环境打通。
  - [x] 自动构建脚本 `scripts/build_aar.sh` 实现一键输出 `icloudcore.aar`。
  - [x] Jetpack Compose + Material 3 + 协程整合，模拟器实机调用 JNI 连通性测试通过。
- [x] **Phase 1: 混合鉴权与动态路由 (已完成)**
  - [x] WebView 登录拦截器与 Cookie 强校验（`TOKEN` / `HSA-TRUST` / `PCS-CloudKit`）。
  - [x] Go 核心层 `/validate` 动态解析 `ckdatabasews.url` 与 `dsid`。
  - [x] 本地加密 Session 存储与状态流展示。
  - [x] 真机验证：18 项 Cookie 全部捕获，三要素齐全，`dsid=20118126497`，路由 `https://p203-ckdatabasews.icloud.com.cn:443`，冷启动可静默恢复。
  - [ ] 遗留项：Token 静默刷新回调、跨子域 Cookie 实测验证（详见 [TODO.md](TODO.md) Phase 1 遗留项）。

> 📖 已修复问题案例、根因分析与验证记录见 **[CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md)**。
- [x] **Phase 2: 图库拉取、全库月扫描与月分组本地缓存 (只读预览)**
- [ ] **Phase 3: Android 相册自动备份 (增量上传与秒传去重)**
- [ ] **Phase 4: 跨生态深度适配 (Motion Photo / Live Photo 互转)**

---

## ⚠️ 关键注意事项与风险规避

1. **国区（云上贵州）与国际区 Header 隔离**：
   - 向苹果发送的所有请求必须严格包含匹配的 `Origin` 与 `Referer`（国区：`https://www.icloud.com.cn`，国际区：`https://www.icloud.com`）。混用会导致 `421 Misdirected Request`。
2. **2FA 信任凭证 (HSA-TRUST)**：
   - 登录时用户须勾选“信任此浏览器”，以获得有效期为 30~60 天的 `X-APPLE-WEBAUTH-HSA-TRUST` 凭证用于静默刷新 Token。
3. **图片下载直链有效期（已实测，见 [ISS-026](CHANGELOG_ISSUES.md#iss-026)）**：
   - CloudKit 临时下载 URL 的实测寿命为 **~15~18 分钟**（882s 仍 `200`，1065s 变 `410`），
     **不是** 早前文档所称的 1~2 小时。
   - **会话重建不会使已签发 URL 失效** —— 实测重建后旧 URL 仍返回 `200`。
     因此主动换链只看**年龄**（阈值 12 分钟），不靠会话代际。
   - 加载缓存时仍需以永久静态 `recordName` 作为主键。
4. **退出登录自动清洗数据层**（已修复，见 [ISS-025](CHANGELOG_ISSUES.md#iss-025)）：
   - `logout()` 现在会按「阻断 → Room → Coil → Cookie/Vault」顺序完整清洗账号作用域数据。
   - 因此**不必**再手动去系统设置「清除数据」；切换国区/国际区或更换 Apple ID 后可直接退出重登。
   - 修复前的症状是：旧账号照片残留、`dsid` 不匹配、直链签名失效导致图片全部加载失败。

> **背景说明**：照片数据库与图片缓存均以 `recordName` 为主键，这些 ID 属于特定账号。跨账号复用会拿到不属于当前账号的记录，而旧直链又因会话代际变化而失效，最终表现为“列表有内容但图片全部加载失败”。

5. **换号未走退出登录也会自动清库**：
   - `ensureGoSession()` 检测到 `dsid` 变化时主动 `wipeAllData()`，见 [ISS-027](CHANGELOG_ISSUES.md#iss-027-paging-3-remotemediator-导致深翻页跳顶改用月分组本地分页根治)。
   - 修复前的症状：直接登录另一个 Apple ID 后，界面仍是旧账号照片，新照片全部不可见。
6. **画廊按「月」分组，扫描是分阶段完成的**：
   - 首次登录后会全库扫描（`MonthScanEngine`），**边扫边看**；扫描完成后月份标题不再带「· 扫描中」。
   - 切到后台时由 `MonthScanWorker` 接管，通知栏显示常驻进度。
   - 若扫到一半中断（网络/切后台），下次启动会自动续扫 —— 游标已持久化，不会重头开始。

---

## 🛠️ 环境要求与构建步骤

### 开发环境要求
- **Go**: 1.21+ (推荐 1.27)
- **Java/JDK**: JDK 17+ (建议使用 Android Studio 随附的 JBR)
- **Android SDK**: API Level 35+, NDK 26+

### 编译构建流程

1. **打包 Go 核心层 AAR**：
   ```bash
   ./scripts/build_aar.sh
   ```
   *该脚本会自动检测 JAVA_HOME、ANDROID_HOME 及 ANDROID_NDK_HOME，并在 `app/libs/` 下生成 `icloudcore.aar`。*

2. **编译并部署 Android App**：
   ```bash
   ./gradlew :app:assembleDebug
   ```

---

## 📜 许可与参考

本项目开发参考了以下成熟开源实现：
- [rclone/rclone](https://github.com/rclone/rclone) - Session 与 HTTP 网络底座
- [icloud-photos-downloader/icloud_photos_downloader](https://github.com/icloud-photos-downloader/icloud_photos_downloader) - CloudKit `ckdatabasews` 协议与 API 结构字典
