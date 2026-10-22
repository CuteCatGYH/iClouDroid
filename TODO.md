# Android iCloud Photos Sync - 开发路线图 (TODO.md)

> **项目定位**：基于 `Go (gomobile .aar)` + `Kotlin (Jetpack Compose)` 的 Android 原生 iCloud 照片客户端与后台自动备份工具。
> **核心目标**：解决"Android 主力机 + 多台 Apple 设备"跨生态照片浏览与自动同步痛点。
> **核心架构**：Android WebView 混合鉴权 + Go 核心层（复用 `rclone` iCloud Session 底座 + 逆向 CloudKit `ckdatabasews` 协议）+ Android 原生系统接口（`MediaStore` / `WorkManager` / `Room`）。

## 📚 文档索引

| 文档 | 内容 |
| --- | --- |
| [README.md](README.md) | 项目介绍、架构图、构建步骤 |
| [AGENTS.md](AGENTS.md) | AI Agent 协作规范、编译调试约定 |
| [CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md) | **已修复问题案例库**（根因分析、修复方案、验证记录） |
| [docs/REFERENCE.md](docs/REFERENCE.md) | 核心参考开源项目与源码对照表 |
| [docs/PITFALLS.md](docs/PITFALLS.md) | 关键工程避坑指南（必读） |
| [docs/ARCHIVE_PHASE1.md](docs/ARCHIVE_PHASE1.md) | Phase 1 详细过程记录与修复方向存档 |

---

## 分阶段开发任务清单

### Phase 0: 跨语言基础工程与编译流水线搭建 ✅

- [x] **工程目录初始化**（Kotlin + Compose + Material 3 主工程、`/go-core` Go Module）
- [x] **打通 `gomobile` 编译链路**（`scripts/build_aar.sh` 一键构建、JNI 异常捕获验证）

### Phase 1: 混合鉴权与国区/国际区动态路由 (MVP 底座) ✅

- [x] **Android 端：WebView 登录拦截与凭证提取器**
  - [x] 国区 / 国际区节点切换开关
  - [x] 加载官方登录页，用户完成密码 + 2FA（提示勾选"信任此浏览器"）
  - [x] 监听跳转后调用 `CookieManager.getCookie()` 提取完整 Cookie
  - [x] **强校验三要素**：`X-APPLE-WEBAUTH-TOKEN`、`X-APPLE-WEBAUTH-HSA-TRUST`、`X-APPLE-WEBAUTH-PCS-CloudKit`
  - [x] `EncryptedSharedPreferences` 加密持久化 Cookie 与节点类型
- [x] **Go 核心层：Session 接管与动态路由表解析**
  - [x] `InitSession(cookieStr, isChinaMainland)` JNI 接口
  - [x] `POST /setup/ws/1/validate` 完成会话握手
  - [x] 解析 `webservices` 提取 `ckdatabasews.url` 与 `pcsRequired`
  - [x] Cookie 解析剥离双引号、跨子域 `CookieMap` 手动注入

**实机验证结论**（emulator-5554 / API 37）

| 项目 | 结果 |
| --- | --- |
| Cookie 捕获 | ✅ 18 项，三要素齐全（TOKEN 154 / HSA-TRUST 265 / PCS-CloudKit 68 字符） |
| 区域路由 | ✅ 国区 `https://www.icloud.com.cn/` |
| 动态路由解析 | ✅ `dsid=20118126497`，`https://p203-ckdatabasews.icloud.com.cn:443` |
| 冷启动恢复 | ✅ 从加密存储静默恢复，无需重新登录 |

> [!NOTE]
> Phase 1 与 Phase 2 的所有已修复问题及案例闭环记录（ISS-001 ~ ISS-014）已完整收录并归档至 [CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md)。

### Phase 2: 照片库拉取、万级图库分页与本地缓存 (只读预览) ✅

- [x] **🔴 P0 阻塞 — 冷启动恢复跳过 Go 会话重建** ✅（已通过 `PhotoRepository.ensureGoSession()` 解决，详见 [ISS-012](CHANGELOG_ISSUES.md#iss-012-冷启动恢复跳过-go-会话重建导致照片接口全局报-no-active-session)）
- [x] **🟠 P1 — 会话守卫未下沉到 `PhotoRepository`** ✅（已将 `ensureGoSession(force)` 统一封装至 `PhotoRepository` 的 `checkZoneReady` / `fetchInitialPage` / `fetchNextPage` / `syncIncremental` / `refreshAssetUrls` 全部入口，支持 401/421 自动静默重建与 Token 自动续期）
- [x] **🟡 P2 — 跨子域 Cookie 首次真实验证** ✅（已通过 `Session.NewRequest` 手动注入 CookieMap 验证通过）

- [x] **Go 核心层：CloudKit (`ckdatabasews`) 查询接口封装**
  - [x] 验证工作区状态：`{ckdatabasews.url}/database/1/com.apple.photos.cloud/production/private/zones/list`，确认 `PrimarySync` 分区就绪
  - [x] `FetchPhotoPage(continuationMarker string, limit int) (jsonResult string, err error)`
    - 路径：`POST {ckdatabasews.url}/database/1/com.apple.photos.cloud/production/private/records/query`
    - 解析 `CPLAsset`：`assetDate`、`masterRef`、`isFavorite`、`isHidden`、`duration`
    - 解析 `CPLMaster`：`recordName`、`itemType`、`resJPEGThumbRes`、`resJPEGMedRes`、`resOriginalRes`、`resOriginalVidComplRes`
- [x] **Go 核心层：增量同步 (Delta Sync)**
  - [x] 对接 `/records/changes`，保存并传入 `syncToken`，仅拉取增量变更
  - [x] 对接 `/records/lookup`，实现 `RefreshAssetUrls` 解决 1 小时直链过期失效
- [x] **Android 端：本地数据库索引与流畅瀑布流**
  - [x] `Room` 数据库（`PhotoAssetEntity`、`SyncStateEntity`、`PhotoDao`、`AppDatabase`）
  - [x] `Paging 3` + `RemoteMediator` + `LazyVerticalGrid` 瀑布流（首屏秒开、动态 3/4/5 列缩放切换）
  - [x] 自定义 `Coil` `Interceptor` 与 `Keyer`（缓存 Key 绑定 `recordName`，按需检测直链过期自动刷新）
  - [x] `Media3 (ExoPlayer)` 云端视频流式播放 + 一键流式下载写入 `MediaStore`（规避 JNI OOM）

- [x] **🔴 P1 阻塞 — 每次分页都触发一次完整会话重建** ✅（修正 `isAuthError` 解析 JSON 错误码匹配避免误判数据，固化 Go 侧 `ClientID` 全生命周期复用，详见 [ISS-013](CHANGELOG_ISSUES.md#iss-013-isautherror-误判原始-json-字符串导致每次分页触发重复鉴权)）
- [x] **🔴 P1 阻塞 — 原图/视频详情页加载失败（HTTP 410）** ✅（`ICloudPhotoInterceptor` 和 `MediaStoreDownloader` 增加 HTTP 410/403 捕获与自动重试换链，详见 [ISS-014](CHANGELOG_ISSUES.md#iss-014-直链失效捕获重试与-clientid-游标关联断开)）
- [x] **🟠 P2 — `ICloudPhotoCoil` 无任何日志** ✅（已在 `ICloudPhotoInterceptor` 中增加结构化日志输出）
- [x] **🟠 P2 — 视频详情页时长显示 00:00** ✅（已在 `VideoPlayerView` 添加 `Player.Listener` 错误监听并向用户展示清晰引导）
- [x] **🟡 P3 — 分页日志刷新刷屏** ✅（已降级无害频繁日志至 `Log.d`）

#### 第三轮真机联调收尾（三星 SM-F9760）

- [x] **`append` 指示器作为网格项导致跳顶** ✅（footer 移出网格 + 网格常驻组合树，详见 [ISS-019](CHANGELOG_ISSUES.md#iss-019-append-指示器作为网格项导致列表跳回顶部)）
- [x] **分页期间会话重建回归（17 秒 4 次）** ✅（`continuationMarker` 失效路径作废，详见 [ISS-020](CHANGELOG_ISSUES.md#iss-020-continuationmarker-失效后仍被重放引发-401--重建死循环)）
- [x] **冷启动主线程阻塞 ~3.3 秒** ✅（`AuthUiState.Checking` 初始态避免 WebView 提前构造，详见 [ISS-022](CHANGELOG_ISSUES.md#iss-022-冷启动时-login-webview-被提前构造阻塞主线程-33-秒)）
- [x] **HDR gain map HEIC 解码失败 + 解码失败误触发刷新** ✅（`ImageDecoderDecoder` + `force` 刷新通道，详见 [ISS-023](CHANGELOG_ISSUES.md#iss-023-hdr-gain-map-heic-解码失败--解码失败误触发-url-刷新)）
- [x] **Go 层 `time.Now()` 作排序键兜底** ✅（改为稳定 `0`，避免照片位置漂移）
- [x] **排序键不稳定** ✅（`ORDER BY assetDate DESC, id ASC` + 复合索引）
- [x] **退出登录不清洗数据层（P2-018）** ✅（`logout()` 四阶段清洗：阻断 → Room → Coil → Cookie/Vault，并修复 `removeAllCookies` 未等回调就 `flush()` 的缺陷，详见 [ISS-025](CHANGELOG_ISSUES.md#iss-025)）
- [x] **Cookie 顺序抖动 + 错误失效假设（P2-019）** ✅（Go 侧 `BuildCookieHeader` 排序 + Kotlin 侧集合比较；实测推翻「会话重建即失效」，URL 真实寿命 ~15-18 分钟而非 1-2 小时，`URL_MAX_AGE_MS` 45min→12min，代际不再由 Cookie 变化触发 bump。详见 [ISS-026](CHANGELOG_ISSUES.md#iss-026)）
- [ ] **⚠️ 深翻页跳顶（P2-016）** — `RemoteMediator` 每次 APPEND 必 invalidate，属 Paging 3 架构性行为。当前为缓解态（`initialLoadSize=1200`），**根治待 UI 重构时一并解决**（详见 [ISS-021](CHANGELOG_ISSUES.md#iss-021-remotemediator-每次-append-必-invalidate导致深翻页跳顶)）

#### 架构重构：多功能 iCloud 客户端演进 (全功能 App 骨架) ✅

- [x] **手写 AppContainer 代替分散单例** ✅（创建 `AppContainer` 与 `IClouDroidApp`，统一注入 `SessionManager`、`PhotoDatabase` 与 `PhotoRepository`）
- [x] **模块化包结构重构** ✅（拆分出 `feature/photos/` 自包含包，将数据/数据库/UI/Coil代码完全内聚）
- [x] **数据库隔离设计** ✅（`AppDatabase` 重命名为 `PhotoDatabase`，专库专用，避免未来扩展 Drive/Notes 拖累照片表）
- [x] **AppShell 壳层与多 tab 导航** ✅（新增 `AppShell` 底部导航，预留 Drive 与备忘录占位入口 `AppDestination`）
- [x] **Context 容器定位自愈** ✅（`Context.appContainer()` 优先使用 `applicationContext`，解决 Compose 上下文层级查找崩溃）

**Phase 2 收尾验证指标**（模拟器 Medium Phone API 37）：

| 指标 | 结果 |
| --- | --- |
| 冷启动会话重建 | **0 次** |
| `auth error` | **0 次** |
| WebView 冷启动初始化 | **0 次** |
| `Choreographer: Skipped` | **无** |
| 滚动中 `itemCount` 塌陷 | **0 次**（index 平滑至 336，itemCount 稳定 1200） |
| `go test ./...` | **ok** |
| `testDebugUnitTest` | **26 passed, 0 failed**（P2-019 新增 15 项） |

**P2-019 修复后补充验证**（物理机 SM-F9760）：

| 指标 | 结果 |
| --- | --- |
| 冷启动 + 强制重建的 generation bump | **0 次**（修复前每次 +1） |
| 首屏 + 滚动 3 屏的 Coil 加载失败 | **0 次** |
| 42 分钟老 URL | 主动 `refreshed` 换链，无需等 410 |

### Phase 3: Android 本地相册自动备份 (增量上传)

- [ ] **本地相册监听与云端去重**
  - [ ] `MediaStore` 扫描用户勾选目录（`DCIM/Camera`、`Pictures/Screenshots`）
  - [ ] Go 层实现与 CloudKit 一致的文件指纹计算，上传前 `/records/lookup` 批量查重
- [ ] **Go 核心层：CloudKit 照片流式上传链路**
  - [ ] 步骤 1：`/assets/upload` 申请上传凭证，获取临时 `uploadURL`
  - [ ] 步骤 2：Go 层通过本地文件路径 `os.File` 流式上传至 MMCS，提取 `singleFile` 签名
  - [ ] 步骤 3：`/records/modify` 提交事务（`CPLMaster` + `CPLAsset`），写入 EXIF 时间戳与 GPS
- [ ] **Android 端：后台任务调度 (`WorkManager`)**
  - [ ] 周期性任务 + `ContentObserver` 触发器（仅 Wi-Fi / 充电时 / 电量充足）
  - [ ] `ForegroundService (dataSync)` 通知栏展示进度与异常重试

### Phase 4: 跨生态深度适配 (安卓 + 苹果痛点打磨)

- [ ] **Motion Photo ↔ Live Photo 互转**
  - [ ] **上传（安卓 → iCloud）**：检测 `MotionPhoto_Data` / `MicroVideoOffset` XMP 标签，剥离尾部 MP4 → `MediaMuxer`/`FFmpeg` 转 `.MOV`，写入 `com.apple.quicktime.content.identifier` UUID，JPEG 注入匹配 `MakerApple` ContentIdentifier
  - [ ] **下载（iCloud → 安卓）**：`CPLMaster` 含 `resOriginalVidComplRes` 时同时下载图+视频，按三星/Google Motion Photo 规范拼接单文件并写 XMP 偏移量
- [ ] **折叠屏与大屏自适应布局**
  - [ ] `WindowSizeClass` 适配：外屏单栏底部导航 / 内屏与平板 `NavigationRail` + 双栏详情

---

> 参考资料、避坑指南与 Phase 1 详细过程记录已拆分至 [docs/](docs/) 目录，详见上方文档索引。
