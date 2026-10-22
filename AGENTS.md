# AGENTS.md - AI Agent 协助开发规范指南

本文档为参与 **iClouDroid** 项目协同开发的 AI Agent 提供架构导引、编码约定与编译调试规范。

> [!IMPORTANT]
> **新接手请先阅读第 5、6、7、9 节**（会话生命周期 / URL 生命周期 / 错误判定 / 验证纪律）。
> 这四节集中了本项目踩过的所有重大坑，违反任意一条都会导致难排查的诡异问题。

## 目录

| 章节 | 内容 |
| --- | --- |
| [1](#1-工程目录结构说明) | 工程目录结构 |
| [2](#2-编译与构建工作流) | 编译与构建工作流 |
| [3](#3-go-跨语言交互-gomobile-编写规范) | Go 跨语言交互 (gomobile) |
| [4](#4-icloud-协议开发红线) | iCloud 协议开发红线 |
| [5](#5-会话生命周期session-lifecycle) | 会话生命周期 |
| [6](#6-url-生命周期与刷新规范) | URL 生命周期与刷新 |
| [7](#7-错误判定规范) | 错误判定规范 |
| [8](#8-日志与可观测性规范) | 日志与可观测性 |
| [9](#9-验证纪律verification-discipline) | 验证纪律 |
| [10](#10-代码结构导引android-层) | 代码结构导引 |
| [11](#11-问题归档与文档维护) | 问题归档与文档维护 |
| [12](#12-当前进度与下一步) | 当前进度与下一步 |

---

## 1. 工程目录结构说明

```
/Users/syh/iClouDroid/
├── TODO.md                 # 分阶段功能路线图
├── README.md               # 项目公开说明与快速上手
├── AGENTS.md               # AI Agent 指导规范 (本文件)
├── CHANGELOG_ISSUES.md     # 已修复问题案例库 (ISS-001 ~)
├── list.md                 # 待修复问题清单（含推荐方案）
├── scripts/
│   └── build_aar.sh        # Go gomobile 交叉编译构建脚本 (关键!)
├── docs/
│   ├── PITFALLS.md         # 工程避坑指南
│   ├── REFERENCE.md        # 参考开源项目源码对照
│   └── ARCHIVE_PHASE1.md   # Phase 1 过程存档
├── go-core/                # Go 核心协议层 (go mod init icloudcore)
│   ├── go.mod
│   ├── core.go             # gomobile 导出入口、会话摘要
│   ├── session.go          # Session、Cookie 解析与请求封装
│   ├── account.go          # 账户登录、路由表解析、会话自愈
│   ├── photos.go           # CloudKit 照片：zones/list、records/query|changes|lookup
│   └── *_test.go           # 单测（可用 httptest 模拟 CloudKit）
└── app/                    # Android 主工程 (Kotlin + Compose)
    ├── libs/
    │   └── icloudcore.aar  # 由 scripts/build_aar.sh 自动生成
    └── src/main/java/com/getup/iClouDroid/
        ├── MainActivity.kt
        ├── data/           # db / repository / coil / media / session
        └── ui/             # auth / photos
```

Android 层细分见第 10 节。

---

## 2. 编译与构建工作流

在修改 Go 代码或 Android 代码后，请严格遵照以下步骤：

1. **若修改了 `/go-core`**：
   **必须**优先运行 `./scripts/build_aar.sh`。不要直接手动调用裸 `gomobile bind` 命令，因为该脚本会自动配置 `JAVA_HOME` 和 `ANDROID_NDK_HOME`。
2. **若修改了 Android 代码**：
   可以使用 `gradle_build` 工具调用 `:app:assembleDebug` 或 `:app:compileDebugKotlin`。
3. **禁止在 Shell 中使用文本替换工具**：
   **切勿**使用 `sed`、`awk` 或 `echo` 修改代码文件，请始终使用 IDE 提供的内置文件写入/替换工具。

---

## 3. Go 跨语言交互 (gomobile) 编写规范

1. **类型限制**：
   `gomobile bind` 仅支持 Go 基本数据类型（`string`, `int64`, `bool`, `[]byte`）及简单 Struct。
2. **数据传递首选 JSON**：
   复杂数据结构（如账号信息、照片列表等）请在 Go 端序列化为 **JSON 字符串**，在 Kotlin 端解析。
3. **异常处理机制**：
   Go 函数返回 `(result, error)` 时，`gomobile` 会将其转化为 Java/Kotlin 中的 `throws Exception`。请在 Kotlin 协程体中总是使用 `try-catch` 包裹调用。
4. **大文件传输禁忌**：
   **绝对不要**用 `[]byte` 传递照片或视频内容，会触发 Android JNI OOM！必须在 Kotlin 传递文件路径，由 Go 直接读写本地文件流。

---

## 4. iCloud 协议开发红线

1. **请求头 (Headers) 强校验**：
   所有发往 Apple 接口的请求，必须携带有正确的 `User-Agent`，以及与节点匹配的 `Origin` 和 `Referer`：
   - 国区：`Origin: https://www.icloud.com.cn`，`Referer: https://www.icloud.com.cn/`
   - 国际区：`Origin: https://www.icloud.com`，`Referer: https://www.icloud.com/`
2. **三要素 Cookie**：
   任何 Session 的建立必须包含：
   - `X-APPLE-WEBAUTH-TOKEN`
   - `X-APPLE-WEBAUTH-HSA-TRUST`
   - `X-APPLE-WEBAUTH-PCS-CloudKit`
3. **Cookie 跨子域手动注入**：
   不要依赖 `cookiejar` 自动匹配。`golang.org/x/net/publicsuffix` 会把 `com.cn` 视为公共后缀，**拒绝**向父域下发 Cookie；且 CloudKit 节点 (`pXX-ckdatabasews.icloud.com.cn`) 与对象存储 (`cvws.icloud-content.com.cn`) 属不同子域。必须由 `Session.CookieMap` 在 `NewRequest` 中显式拼装 `Cookie` Header。
4. **下载直链的寿命由时间决定，与代际无关（已实测）**：
   `cvws.icloud-content.com[.cn]` 返回的 URL 实测寿命约 **15~18 分钟**（物理机实测：
   882s 仍 200，1065s 变 410），**不是** “固定的 1~2 小时”。

   > [!CAUTION]
   > **「会话重建导致 URL 失效」是已被证伪的假设。** 实测：gen=6 签发的 URL 在会话重建到
   > gen=7 之后仍然返回 `200`。CDN 签名绑定的是 record，不是签发它的会话。
   > 详见 [ISS-026](CHANGELOG_ISSUES.md#iss-026) 与
   > [url_lifetime_experiment.md](.artifacts/url_lifetime_experiment.md)。

   因此 URL 刷新只由**年龄**和**真实失败（403/410）**驱动，不靠代际。详见第 6 节。

---

## 5. 会话生命周期（Session Lifecycle）

> 踩坑记录：ISS-012（冷启动丢会话）、ISS-016（generation 洪泛）。

### 5.1 Go 侧 `currentSession` 是进程级全局指针

App 进程冷启动后，Go 层的 `currentSession` 必为 `nil`。**切勿**把本地缓存的 `dsid` / `ckDatabaseUrl` 当作「会话已就绪」的依据 —— 它们只是服务端路由快照，不代表 `*Session` 指针存在。

所有需要 Go 会话的操作，必须先经过 [PhotoRepository.ensureGoSession()](file:///Users/syh/iClouDroid/app/src/main/java/com/getup/iClouDroid/data/repository/PhotoRepository.kt) 守卫：

```kotlin
// 判定依据：Go 层自报的 Session 摘要，而非本地缓存
val summaryStr = Icloudcore.getSessionStateSummary()
val hasActiveSession = summaryJson.optBoolean("hasAuthToken", false) &&
    summaryJson.optString("ckDatabaseURL", "").isNotBlank()
```

### 5.2 会话重建是「昂贵操作」

一次重建 = 一次 `/validate` 网络往返（1~2s）+ **全部已签发 URL 立即作废** + 一次 generation 递增。

> [!WARNING]
> 任何触发重建的路径都必须极度保守。宁可提示用户「网络错误，请重试」，也不要贸然 `ensureGoSession(force = true)`。

### 5.3 `bumpSessionGeneration()` 只能调一次

**只能在 `initSession` 真正成功后**递增。绝不可以在「会话已就绪」的快速路径上调用 —— 该路径每次 API 请求都会经过，会让 generation 每几十毫秒 +1，导致每一格缩略图都变成「代际过期」并各发一次 `/records/lookup`（即 URL 刷新洪泛）。

> [!NOTE]
> **已修复（[ISS-026](CHANGELOG_ISSUES.md#iss-026)）**：此前 `applyRefreshedCookies()` 用**字符串相等**
> 判断凭证是否轮转，而 Go 的 `BuildCookieHeader()` 遍历 `map` 拼接 Cookie（顺序随机），
> 导致每次冷启动都 bump 一次 generation。现已 Go 侧按 key 排序 + Kotlin 侧改集合比较。
>
> 更重要的是：**代际 bump 不再由 Cookie 变化触发。** 实测发现苹果每次 `/validate` 都会轮换
> `X-APPLE-WEBAUTH-TOKEN` / `X-APPLE-WEBAUTH-VALIDATE`，而这两个令牌与已签发 URL 的有效性无关。
> 现在只有 `force = true`（401/421 恢复路径）才会 bump。

### 5.4 会话代际机制

| 角色 | 字段 | 说明 |
| --- | --- | --- |
| `SessionManager` | `session_generation` | 持久化的单调递增计数器 |
| `PhotoAssetEntity` | `sessionGeneration` | 记录入库时打戳 |

判定：`data.sessionGeneration != sessionManager.getSessionGeneration()` → 强制刷新。

> [!CAUTION]
> 刷新成功后**必须把当前 generation 回写到该行**，否则它永远停留在 stale 状态，每次加载都重复刷新。

### 5.5 换区 / 换账号必须清空数据层

会话层清理（Cookie / WebStorage / `EncryptedSharedPreferences`）**远不够**。
Room 照片表与 Coil 缓存都以 `recordName` 为主键，而这些 ID 属于特定账号：

| 数据 | 位置 | 是否账号相关 |
| --- | --- | --- |
| `photo_assets` | `PhotoDatabase` | ✅ CloudKit 记录 ID |
| `sync_state` | `PhotoDatabase` | ✅ `syncToken` / `continuationMarker` |
| 图片字节 | Coil `DiskLruCache` | ✅ 来自旧账号的 `cvws` 直链 |

新账号登录后会 `bumpSessionGeneration()`，旧行全部 stale → 首屏批量刷新，
但用的是**新账号 Cookie** 去查**旧账号 recordName**，必然失败，表现为
「列表有内容但图片全部加载失败」。

**当前状态**（已修复，详见 [ISS-025](CHANGELOG_ISSUES.md#iss-025)）：

`logout()` 已改为完整四阶段清洗，顺序固定不可调换：

1. **阻断** `PhotoRepository.beginWipe()` —— 置 `isWiping = true`，所有网络入口
   （`checkZoneReady` / `fetchInitialPage` / `fetchNextPage` / `syncIncremental` /
   `refreshUrlsForMaster(s)`）与 `PhotoRemoteMediator.load()` 立即拒绝工作
2. **数据层** `wipeAllData()` —— `sync_state` + `photo_assets` 全清，并清理刷新去重缓存
3. **图片层** `ICloudImageLoaderFactory.clearCaches()` —— Coil 内存 + 磁盘
4. **会话层** `clearWebViewCookies()`（桥接回调后 `flush()`）+ `sessionManager.clearSession()`
   最后 `endWipe()` 释放守卫。

修改 `logout()` 时注意：

1. **先阻断再清库** —— 否则正在飞行中的 `RemoteMediator` APPEND 会 `upsertAll()`
   把已删的行写回。
2. **不能以 `isWiping` 之外的任何方式抑制同步** —— 用同步布尔哨兵位，
   不用 Flow/State（异步会竞态，见 [docs/PITFALLS.md](docs/PITFALLS.md) §7）。
3. **顺序：阻断 → 数据层 → Coil → 会话层** —— 先清 Cookie 会让中途的自动刷新
   拿空 Cookie 打 Apple 接口；先清数据层再阻断则会被写回。
4. **`removeAllCookies` 必须等回调** —— 传 `null` 只是放弃回调，并不代表已同步完成，
   紧接着的 `flush()` 可能把删除前的 jar 落盘。
5. **`AuthViewModel.isLoggingOut` 必须从 `logout()` 第一行就开始生效** ——
   `ICloudWebView` 的 Cookie 探针会在 `removeAllCookies` 完成前读到旧 Cookie
   并回调 `onCookiesExtracted()`，把刚销毁的会话重新持久化。
   `AuthUiState` **挡不住**这个竞态（`logout()` 第一时间就置为 `NotLoggedIn`，
   反而放行了回调），必须用 `@Volatile` 布尔位，并在清理完成后再保留
   `COOKIE_CAPTURE_GRACE_MS`（800ms）等登录页彻底重载。见
   [ISS-025](CHANGELOG_ISSUES.md#iss-025) 的“竞态”小节。

> [!CAUTION]
> **新增任何「退出登录时需要清理的状态」，同时必须检查它能不能被异步回调写回。**
> 本项目已验证的写回路径有两条：`PhotoRemoteMediator.load()` → `upsertAll()`，
> 以及 `ICloudWebView` 的 Cookie 探针 → `onCookiesExtracted()` → `saveSession()`。

---

## 6. URL 生命周期与刷新规范

### 6.1 所有消耗 URL 的路径必须统一走刷新

| 路径 | 事前检查 | 失败后重试 | 重试上限 |
| --- | --- | --- | --- |
| Coil 缩略图 | **年龄 12min**（+ 代际仅作提示） | `ErrorResult` → 刷新重请 | 1 |
| 图片详情 | — | `onError` → 刷新 → `reloadToken++` | 2 |
| 视频播放 | — | 重建 `ExoPlayer` + `seekTo(lastPosition)` | 2 |
| 相册下载 | 年龄 | HTTP 403/410 捕获后换链 | 1 |

### 6.1.1 阈值必须来自实测，不要猜

| 常量 | 值 | 依据 |
| --- | --- | --- |
| `URL_MAX_AGE_MS` | **12 分钟** | 实测 882s(14.7m) 仍 206，1065s(17.8m) 变 410 |

> [!CAUTION]
> **不要把这个值调回 45 分钟。** 旧值基于「1~2 小时」的错误假设，留下约 30 分钟的盲区：
> URL 早已 410，系统却仍以为有效，只能靠失败重试兜底。
> 若苹果将来改变寿命，用 [实测脚本](.artifacts/url_lifetime_experiment.md) 重新标定，别拍脑袋。

**代际（`sessionGeneration`）的定位已降级**：它不再是刷新触发器。
实测已证 URL 跨会话重建仍有效，因此在拦截器里只有「代际不符 **且** 年龄过半」才提前刷新 ——
这是为换账号场景（[P2-018](CHANGELOG_ISSUES.md#iss-025)）留的加速信号，不是常规路径。

### 6.2 刷新必须走 `PhotoRepository`，禁止直调 Go

`refreshUrlsForMaster(masterId)` 内建三层去重：

1. 同一 masterId 的 in-flight 请求合并（`Deferred` 共享）
2. 5 分钟窗口内成功刷过的直接读 DB
3. 会话刚重建时加 20~120ms 随机抖动，摊平突发

批量场景用 `refreshUrlsForMasters(List<String>)`，一次 `/records/lookup` 搞定多个。

> [!WARNING]
> 若在拦截器 / UI 里绕过 `PhotoRepository` 直调 `Icloudcore.refreshAssetUrls()`，网格 60+ item 同时失败时就会瞬间发出几十个 `/records/lookup`，极易触发 Apple 限流。

### 6.3 Coil 缓存 Key 必须绑定 `recordName`

内存与磁盘缓存 Key 使用 `icloud_{recordName}_{quality}`，**绝不能**绑定会变化的 `downloadURL`，否则签名一换缓存全废。

---

## 7. 错误判定规范

> 踩坑记录：ISS-001（Cookie 静默丢弃）、ISS-015（base64 误伤）。

### 7.1 禁止对原始 JSON 做子串匹配

```kotlin
// ❌ 绝对禁止：CloudKit 的 base64 continuationMarker 会随机命中 "401"/"421"
jsonStr.contains("401")
```

必须结构化解析，按优先级：

1. Go 层结构化 `errorCode == "AUTH_EXPIRED"`
2. `success == false` 才继续
3. `httpStatus in {401, 421}`
4. 仅对 `errorMessage` 字段用 `\b` 词边界正则匹配 `HTTP 401/421`
5. 解析异常一律返回 `false`（失败即放过，绝不误触发昂贵重建）

### 7.2 HTTP 状态码从异常对象读，不从文本猜

```kotlin
val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
```

### 7.3 Go 层 `net/http` 对非法 Cookie 是静默丢弃

不含 error 返回，只打一行警告。跨语言传 Cookie 必须 `strings.Trim(v, `"`)` 剥引号，并打印解析成功数量。

---

## 8. 日志与可观测性规范

### 8.1 Logcat Tag 速查

| Tag | 来源 | 用途 |
| --- | --- | --- |
| `ICloudAuth` | Android 鉴权探针 | Cookie 捕获、三要素校验、JNI 调用、区域判定 |
| `PhotoRepository` | Android 数据层 | 会话重建、分页拉取、URL 刷新去重 |
| `ICloudPhotoCoil` | Coil 拦截器 | 单图 load / refresh / ok / fail 全链路 |
| `PhotoDetailViewer` | 详情页 | 播放器错误、图片重试 |
| `MediaStoreDownloader` | 下载器 | 下载进度与换链 |
| `GoLog` | Go 侧 `net/http` | Cookie 丢弃告警、TLS/HTTP 错误 |
| `chromium` | WebView 内核 | 页面 JS 报错、CORS 噪音 |

### 8.2 日志分级约定

- **`Log.i`**：关键业务流程节点（会话重建开始、同步完成、下载成功）
- **`Log.d`**：高频分页/刷新细节、Cookie 落盘对比
- **`Log.w`**：可恢复异常（鉴权判定命中、刷新失败、重试）
- **`Log.e`**：不可恢复异常

> [!CAUTION]
> **绝不打印完整签名 URL** —— 里面含一次性签名参数，可能被日志采集泄露。只打印 `URL.host`。

### 8.3 已知无害噪音（排查时直接忽略）

- `gatewayws.icloud.com.cn/.../batchRegisterToken` 的 CORS 报错
- `wallpaper.svg was preloaded using link preload but not used`
- `MediaCodec / Codec2 / c2.goldfish.*` 系列（模拟器软解）
- `E/MESA: Failed to open rendernode`

---

## 9. 验证纪律（Verification Discipline）

### 9.1 实机验证优先于单测绿

**不要编译通过 + 单测绿就宣布完成。** 本项目多次出现过「编译成功、单测全过」但实机崩溃的案例（ISS-013、ISS-016 均如此）。

修改完毕后必须：
1. `./scripts/build_aar.sh`（如改了 Go）
2. `gradle_build(":app:assembleDebug testDebugUnitTest")`
3. **部署到 `emulator-5554` 并实际点击/滚动验证**
4. **拉 logcat 确认无异常、无预期外的重复请求**

### 9.2 可用工具

| 工具 | 用途 |
| --- | --- |
| `ui_state` | 查看当前屏幕 UI 层级 + 截图 |
| `adb_shell_input` | tap / swipe / keyevent 操作设备 |
| `read_logcat` | 按 `applicationId` 过滤拉取日志 |
| `deploy` | 构建并部署到设备 |

### 9.3 性能验证基线

| 指标 | 期望值 |
| --- | --- |
| 冷启动后 `Rebuilding Go core session` | ≤ 1 次 |
| 连续滚动 3 页的额外重建次数 | 0 次 |
| 同一 masterId 5 分钟内 `/records/lookup` | 1 次 |
| 缩略图 `genStale=true` | 刷新后必须收敛为 `false` |

---

## 10. 代码结构导引（Android 层）

```
app/src/main/java/com/getup/iClouDroid/
├── IClouDroidApp.kt                   # Application，持有 AppContainer（已在 Manifest 注册）
├── MainActivity.kt                    # 顶层状态路由：Checking → spinner；LoggedIn → AppShell；否则 AuthScreen
├── di/
│   ├── AppContainer.kt                # 手写 DI：所有 App 级依赖的唯一创建点
│   └── LocalAppContainer.kt           # Context.appContainer() / rememberAppContainer()
├── data/
│   └── session/SessionManager.kt      # 加密存储 + session_generation 计数器（跨功能共享，留在顶层）
├── feature/
│   └── photos/                        # 照片功能（自包含）
│       ├── data/
│       │   ├── PhotoDatabase.kt        # 照片专属 Room 库（icloud_photos.db）
│       │   ├── PhotoRepository.kt      # 会话守卫 + 三层刷新去重 + 所有 Go 调用入口
│       │   ├── PhotoRemoteMediator.kt  # Paging 3 远端中介器
│       │   ├── MediaStoreDownloader.kt # 流式写入 MediaStore，规避 JNI OOM
│       │   ├── db/                     # PhotoAssetEntity(含 sessionGeneration)、SyncStateEntity、Dao
│       │   └── image/ICloudPhotoCoil.kt # Keyer 绑 recordName + 拦截器代际判定 + 失败重试 + 日志
│       └── ui/                         # PhotosScreen(gridState 保位)、PhotoGridItem、PhotoDetailViewer、PhotosViewModel
└── ui/
    ├── AppShell.kt                    # 登录后的壳：底部导航 + 各功能入口
    ├── PlaceholderScreen.kt           # 未实现功能的占位页
    ├── navigation/Destinations.kt     # AppDestination 枚举（新增功能在此登记）
    └── auth/                          # AuthScreen、AuthViewModel、ICloudWebView、AccountInfoDialog
```

**分层约定**：
- UI 层**不得**直接调 `Icloudcore.*`
- 所有 Go 调用收敛到 `PhotoRepository`
- 所有耗时读写在 `withContext(Dispatchers.IO)` 中执行
- 依赖一律从 `AppContainer` 取，**不得**再新增 `getInstance()` 单例
- 新增 iCloud 功能（Drive / 备忘录）= 在 `feature/` 下建自包含包 + 在 `AppDestination` 与
  `AppShell` 登记 + 在 `AppContainer` 加一个 `lazy` 字段；**不**需改动认证逻辑或其他功能

---

## 11. 问题归档与文档维护

| 文档 | 职责 |
| --- | --- |
| [CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md) | 已修复问题案例库（ISS-001 ~），含现象/日志证据/根因/方案/验证 |
| [list.md](list.md) | 待修复问题清单（含推荐方案），修完迁往 CHANGELOG |
| [TODO.md](TODO.md) | 分阶段路线图 |
| [docs/PITFALLS.md](docs/PITFALLS.md) | 工程避坑指南 |
| [docs/REFERENCE.md](docs/REFERENCE.md) | 参考开源项目源码对照 |

**归档规范**：
- 修复真实 bug 后，必须在 `CHANGELOG_ISSUES.md` 新增条目，并更新顶部索引表
- 若该问题暴露了**通用性陷阱**，同步补充到本文对应章节
- 若已录入 `docs/PITFALLS.md`，在条目中互相引用

---

## 12. 当前进度与下一步

| 阶段 | 状态 |
| --- | --- |
| Phase 0：跨语言工程与编译流水线 | ✅ 完成 |
| Phase 1：混合鉴权与国区/国际区动态路由 | ✅ 完成 |
| Phase 2：照片库拉取、万级图库分页与本地缓存（只读预览） | ✅ 完成 |
| Phase 3：Android 本地相册自动备份（增量上传） | ⏳ 待开始 |
| Phase 4：跨生态深度适配（Motion Photo ↔ Live Photo、折叠屏） | ⏳ 待开始 |

### Phase 3 关键提醒

1. **iCloud 上传协议无成熟开源实现**，需参考 [docs/REFERENCE.md](docs/REFERENCE.md) 第 3 节逆向
2. **上传前必须查重** —— 重复上传会产生幽灵重复照片，无法删除
3. **上传前校验 `X-APPLE-WEBAUTH-PCS-Photos` Cookie** —— 无此凭证无法写入照片库
4. **EXIF 时间戳必须准确** —— iCloud 依赖 `assetDate` 排序，写错导致照片乱序
5. **上传必须走文件路径 + `os.File` 流式**，详见第 3 节第 4 条

### Phase 3 建议偿还的技术债

- Go 层返回结构化 `errorCode` / `httpStatus` 字段，取代 Kotlin 侧正则兜底（P2-015）
- Room 正式 Migration（目前 v3 仍使用 `fallbackToDestructiveMigration()`）
- `refreshUrlsForMasters()` 批量刷新入口目前已就绪但未被消费，可在列表预加载时预热
- **深翻页跳顶（P2-016）**：`RemoteMediator` 每次 APPEND 必 invalidate，属 Paging 3 架构性
  行为。当前为缓解态（`initialLoadSize = 1200`，可滑到 ~2400 项）。根治方案：改用日期分组
  UI（滚动锚点为「日期 section + 组内偏移」而非全局 index）时一并替换

---
