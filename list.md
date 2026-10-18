# list.md — Phase 2 待修复问题清单

> 本文件汇总 Phase 2 联调中实测发现的**所有未修复问题**、注意事项与推荐修复方案（含示例代码）。
>
> 修复完成后请将案例迁往 [CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md) 归档，并从本文件删除。
>
> 相关文档：[TODO.md](TODO.md)（路线图）、[CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md)（已修复归档）

---

## 索引

| 编号 | 标题 | 优先级 | 归属 | 影响 |
| --- | --- | --- | --- | --- |
| ~~[P2-001](#p2-001)~~ | ~~`isAuthError` 字符串模糊匹配导致误判会话失效~~ | 🔴 P0 | Android / Repository | ✅ 已修复 |
| ~~[P2-002](#p2-002)~~ | ~~视频播放遇 HTTP 410 不会自动重取链接~~ | 🔴 P1 | Android / 播放器 | ✅ 已修复并实测验证 |
| ~~[P2-003](#p2-003)~~ | ~~`LazyVerticalGrid` 缺少 `state` 参数~~ | 🟠 P1 | Android / UI | ✅ 已修复 |
| ~~[P2-004](#p2-004)~~ | ~~图片详情页遇 403/410 不会重取链接~~ | 🟠 P1 | Android / UI | ✅ 已修复 |
| ~~[P2-005](#p2-005)~~ | ~~Coil 拦截器过期判定只依赖时间~~ | 🟠 P1 | Android / Coil | ✅ 已修复 |
| ~~[P2-006](#p2-006)~~ | ~~`ICloudPhotoCoil` 全链路零日志~~ | 🟡 P2 | Android / 可观测性 | ✅ 已修复 |
| ~~[P2-007](#p2-007)~~ | ~~播放器错误提示硬编码且不可重试~~ | 🟡 P2 | Android / UI | ✅ 已修复 |
| ~~[P2-008](#p2-008)~~ | ~~缩略图批量 410 可能打爆服务端~~ | 🟡 P2 | Android / Coil | ✅ 已修复 |
| ~~[P2-009](#p2-009)~~ | ~~会话重建日志刷屏~~ | 🟢 P3 | Android / 日志 | ✅ 已修复 |

### 🔍 本轮实机（三星 SM-F9760）新发现

| 编号 | 标题 | 优先级 | 归属 | 影响 |
| --- | --- | --- | --- | --- |
| ~~[P2-010](#p2-010)~~ | ~~HEIC 原图带 HDR gain map 导致 `BitmapFactory` 解码失败~~ | 🟠 P1 | Android / Coil | ✅ 已修复（模拟器实测） |
| ~~[P2-011](#p2-011)~~ | ~~详情页刷新被缩略图去重窗口误伤~~ | 🟠 P1 | Android / Repository | ✅ 已修复（含单测） |
| ~~[P2-012](#p2-012)~~ | ~~`append` 指示器作为网格项导致列表跳回顶部~~ | 🔴 P0 | Android / UI | ✅ 已修复（ISS-019） |
| ~~[P2-013](#p2-013)~~ | ~~冷启动主线程阻塞约 1.5 秒~~ | 🟠 P1 | Android / 启动 | ✅ 已修复 |
| ~~[P2-014](#p2-014)~~ | ~~分页期间会话重建回归（17 秒内 4 次）~~ | 🔴 P0 | Android / Repository | ✅ 已修复（ISS-020） |
| [P2-015](#p2-015) | Go 层缺结构化 `errorCode` / `httpStatus`（技术债） | 🟡 P2 | Go 核心层 | Kotlin 侧只能正则猜字符串 |
| [P2-016](#p2-016-remotemediator-每次-append-必-invalidate导致深翻页跳顶) | `RemoteMediator` 每次 APPEND 必 invalidate，导致深翻页跳顶 | 🔴 P0 | Android / Paging | 已确诊；缓解态，待 UI 重构根治 |
| ~~[P2-017](#p2-017)~~ | ~~Go 层 `time.Now()` 作排序键兜底（已清理）~~ | 🔴 P0 | Go 核心层 | ✅ 已修复 |

> [!NOTE]
> P2-001 ~ P2-009 已完成修复并在 `emulator-5554` 实机验证。
> 本轮换到真实手机（三星 SM-F9760）复测，**会话重建已归零（之前 8 秒 3 次）**，P2-001 确认治本。
> 但暴露了 2 个新问题（P2-010 / P2-011），详见下文。
>
> 案例详情已归档至 [CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md)：
> - ISS-015：`isAuthError` 误判（P2-001）
> - ISS-016：会话代际（sessionGeneration）机制（P2-005 / P2-008）
> - ISS-017：URL 自动重取链路统一（P2-002 / P2-004 / P2-007）
> - ISS-018：网格滚动状态保持与日志治理（P2-003 / P2-006 / P2-009）

---

## 修复要点速查

### P2-001：`isAuthError` 结构化判定

`PhotoRepository.isAuthError()` 不再对原始 JSON 做 `contains("401")`（会被 base64 的
`continuationMarker` 误伤）。改为按优先级解析：

1. Go 层结构化 `errorCode == "AUTH_EXPIRED"`
2. `success == false` 时才继续
3. `httpStatus in {401, 421}`
4. 仅对 `errorMessage` 字段用 `\b` 词边界正则匹配 `HTTP 401/421`
5. 解析异常一律返回 `false`（失败即放过，绝不误触发昂贵重建）

**验证**：冷启动 90 项图库 + 连续滚动 3 页，`Rebuilding Go core session` 全程 **0 次**。

### P2-005 / P2-008：会话代际 + 刷新去重

- `SessionManager` 新增持久化 `session_generation` 计数器
- `PhotoAssetEntity` 新增 `sessionGeneration`（Room v2，`fallbackToDestructiveMigration`）
- **仅**在 `ensureGoSession()` 真正重建 Go 会话时 `bumpSessionGeneration()` 一次。
  > [!WARNING]
  > 早期实现曾在"会话已就绪"的快速路径上也 bump，导致 generation 每几十毫秒 +1，
  > 每一格缩略图都变成「代际过期」并各发一次 `/records/lookup` —— 正是 P2-008 的放大版。
  > 现已在实机日志中确认修复：`genStale=false` 能稳定收敛。
- 刷新成功后 `PhotoDao.updateUrls()` 必须同时写入**当前** `sessionGeneration`，
  否则该行会永远停留在 `genStale=true`。
- `PhotoRepository.refreshUrlsForMaster()` 三层去重：
  1. 同一 masterId 的 in-flight 请求合并（`Deferred` 共享）
  2. 5 分钟窗口内成功刷新过的直接读 DB
  3. 会话刚重建时对并发刷新加 20~120ms 随机抖动，避免同一瞬间打爆 `/records/lookup`
- 另提供 `refreshUrlsForMasters(List<String>)` 批量接口（Go 侧已支持数组入参）

**验证**：日志中 `refreshUrlsForMaster skipped (deduped within window)` 与
`joining in-flight request` 大量出现，说明去重生效。

### P2-002 / P2-004 / P2-007：URL 重取链路统一

| 路径 | 刷新策略 |
| --- | --- |
| Coil 缩略图 | 事前：代际 / 45min 年龄；事后：`ErrorResult` 重试一次 |
| 图片详情 | `SubcomposeAsyncImage.onError` → 刷新 → `reloadToken++`，上限 2 次 |
| 视频播放 | `remember(currentUrl)` 重建播放器 + `seekTo(lastPosition)` 续播，上限 2 次 |
| 相册下载 | 事前代际/年龄检查 + HTTP 403/410 响应捕获后换链重试 |

播放器错误提示按 HTTP 码分流（410/403 / 404 / 其他网络错误 / 解码失败），并提供"重试"按钮。

### P2-003：网格滚动状态

`rememberSaveable(saver = LazyGridState.Saver)` 承载滚动状态；切换列数前记录
`firstVisibleItemIndex`，`LaunchedEffect(gridColumns)` 中恢复，避免缩放时视觉跳变。

### P2-006 / P2-009：日志治理

- `ICloudPhotoCoil` 输出结构化的 `load / ok / fail / refreshed / retry` 日志，
  仅打印 URL 的 host，**绝不打印带签名的完整 URL**
- `PhotoRepository` 中高频分页/同步日志统一降为 `Log.d`
- Cookie 落盘仅在内容实际变化时打印，并把长度对比放进 `Log.d`

---

## ~~P2-010：HEIC 原图带 HDR gain map 导致 BitmapFactory 解码失败~~ ✅ 已修复

**状态**：代码已修复，模拟器实测通过。

### 修复内容

**1. 加载器改用 `ImageDecoderDecoder`**

```kotlin
// ICloudPhotoCoil.kt → ICloudImageLoaderFactory
.components {
    add(ImageDecoderDecoder.Factory())   // 关键：系统 ImageDecoder，支持 HEIF gain map
    add(ICloudPhotoKeyer())
    add(ICloudPhotoInterceptor(repo, SessionManager(context.applicationContext)))
}
```

`ImageDecoder`（API 28+）能正确处理带 `MiHE` brand 的 HDR gain map HEIF，
而 Coil 默认的 `BitmapFactory` 路径会返回 null bitmap。

> [!NOTE]
> 依赖变更：`ImageDecoderDecoder` 在 Coil 2.7 里**不在 `coil-base`**，
> 而在 `io.coil-kt:coil-gif` 模块中。已在 `libs.versions.toml` 添加 `coil-gif`
> 并在 `app/build.gradle.kts` 中引入。

**2. 详情页限制解码尺寸**

```kotlin
// PhotoDetailViewer.kt
ImageRequest.Builder(context)
    .data(model)
    .size(2048)      // 不传则按原图完整解码，内存与兼容性风险都高
    .build()
```

### 关于“降级到 MEDIUM”

原计划同时实施方案 B（解码失败降级 MEDIUM）作为兜底。实际上 **`pickUrl` 已有降级链**：

```kotlin
Quality.ORIGINAL -> entity.originalUrl ?: entity.medUrl ?: entity.thumbUrl
```

且方案 A 已从根源解决 gain map 问题，故未额外加拦截器降级逻辑，避免引入新的歧义。

### 验证结果

模拟器（Medium Phone API 37）实测：

```
22:46:00.813 D/ICloudPhotoCoil: load 95663D45-... quality=ORIGINAL hasUrl=true ageStale=false genStale=false
22:46:06.933 D/ICloudPhotoCoil: ok 95663D45-... quality=ORIGINAL
```

✓ 原图加载成功，**未出现** `BitmapFactory returned a null bitmap`。
连续打开多张 HEIC 原图（`IMG_2115` / `IMG_2114`）均正常显示。

> [!NOTE]
> 真机（三星 SM-F9760）尚未复测 —— 当时设备断开。建议后续用
> `IMG_1338.HEIC` / `IMG_1335.HEIC`（带 `MiHE` gain map 的真实失败样本）补充验证。

---

## ~~P2-011：详情页刷新被缩略图去重窗口误伤~~ ✅ 已修复

**状态**：代码已修复，新增 4 个单测（共 12 个全部通过）。

### 修复内容（两个改进）

#### 改进 1：只在真的是 URL 失效时才刷新

解码失败（如 HEIC gain map）不是 URL 问题，刷新 URL 毫无意义，只会白白消耗
`/records/lookup` 并刷屏日志。现在按 HTTP 状态码分流：

```kotlin
// PhotoDetailViewer.kt → SubcomposeAsyncImage.onError
val httpCode = (throwable as? coil.network.HttpException)?.response?.code
isUrlIssue = httpCode == 403 || httpCode == 410

if (isUrlIssue && retryCount < MAX_IMAGE_RETRY) {
    // 只有真·URL 失效才刷新
} else {
    // 解码失败 —— 不刷新，直接给出区分性文案
    Log.w(TAG, "image decode failed (not a url issue) master=... error=...")
    loadFailed = true
}
```

错误文案也相应区分：

| 情况 | 文案 |
| --- | --- |
| 链接失效且重试耗尽 | 原图加载失败 (链接已失效) |
| 解码失败 | 原图无法解码 (该格式暂不支持) |

#### 改进 2：提供强制刷新通道

```kotlin
suspend fun refreshUrlsForMaster(
    masterId: String,
    force: Boolean = false     // 新增
): Result<RefreshedUrls>
```

`force = true` **仅跳过去重层 2**（时间窗口），层 1（in-flight 合并）依然生效，
所以不会因为强制而制造并发风暴。

详情页调用点：

```kotlin
val refreshed = repository.refreshUrlsForMaster(photo.masterId, force = true)
```

> [!IMPORTANT]
> 为何只绕过层 2：详情页是**单次用户操作**，不可能形成突发；
> 但若被网格缩略图 30 秒前的刷新窗口否决，就会出现“安全措施反而导致永久修不好”的尴尬。
> 层 1 保留则保证即使真的并发也不会重复请求。

### 为何不做“去重窗口按 masterId + 品质”

`list.md` 原方案提议把去重键改为 `"masterId:THUMB"`。但 `/records/lookup`
**一次就返回全部三种品质的 URL**（thumb / med / original），按品质拆分键会导致
同一 master 发出多次完全重复的请求，适得其反。改用 `force` 参数更精准。

### 验证

**单测（新增 `ImageLoadFailureTest`，4 个）**：

| 用例 | 断言 |
| --- | --- |
| HTTP 410 / 403 | `isExpiredUrl = true` |
| HTTP 404 / 500 / 200 | `isExpiredUrl = false` |
| `IOException`（解码失败）/ `null` | `isExpiredUrl = false` |
| `httpCodeOf` | 仅 HTTP 异常返回码 |

`testDebugUnitTest`：**12 passed, 0 failed**。

**模拟器实测**：连续打开多张原图，均为 `ok ... quality=ORIGINAL`，
无多余 `/records/lookup`，无 `deduped within window` 拦截。

> [!NOTE]
> 真正的“URL 过期 → 强制刷新→ 成功”链路需等服务端签名自然过期才能完整复现，
> 目前靠单测 + 代码审阅保证逻辑正确。

---

## ~~P2-012：`append` 指示器作为网格项导致列表跳回顶部~~ ✅ 已修复

> 已归档至 [CHANGELOG_ISSUES.md → ISS-019](CHANGELOG_ISSUES.md#iss-019-append-指示器作为网格项导致列表跳回顶部)。
>
> **关键追加发现**：`list.md` 原诊断只识别了「footer 作为网格项使 itemCount ±1」这一个源。
> 实际还有**第二个抖动源** —— `PhotosScreen.kt` 的 empty/loading/error 分支原本是
> `if / else if / else` 兄弟关系，refresh 期间任一帧 `itemCount == 0` 会把
> `LazyVerticalGrid` 连同 `LazyGridState` 一起摘出组合树，再挂回来时索引归零。
>
> 因此真正修复需**两处**：
> 1. footer 移出网格（`Column` + `weight(1f)`，footer 用 `LinearProgressIndicator`）
> 2. 网格常驻组合树，empty/loading/error 改为叠加 overlay
>
> 只做第 1 点，跳顶会变少但**不消失**，复测时极易误判为已修复。
>
> **实测结果**（emulator-5554）：连续两次 APPEND（150 → 210 → 240 项），
> 视口单调前进（`IMG_0047` → `IMG_1327…IMG_0018`），首屏项未重现。

---

## P2-013：冷启动主线程阻塞约 1.5 秒

**优先级**：🟠 P1（启动卡顿、滑动不跟手）
**归属**：`MainActivity.kt` / `ui/auth/AuthViewModel.kt` / `ui/auth/ICloudWebView.kt`
**发现环境**：三星 SM-F9760 实机，冷启动
**状态**：⛔ 待修复

### 现象

冷启动后滑动明显卡顿，`Choreographer` 实测：

```
19:59:32.473  Skipped 32 frames!   ≈ 530ms
19:59:32.498  Skipped 35 frames!   ≈ 580ms
19:59:33.219  Skipped 59 frames!   ≈ 980ms   ← 最严重
19:59:40.308  Skipped 36 frames!   ≈ 600ms
```

**累计跳掉 162 帧，约 1.5 秒主线程完全阻塞。**

### 根因（三个叠加）

#### 1. Cookie 校验在主线程执行

日志中 PID/TID 均为同值（`20585 20585`）→ 主线程：

```
19:59:32.561  20585 20585 I ICloudAuth: ── Cookie probe @ restored-from-storage
19:59:32.561  20585 20585 I ICloudAuth:    total=18 names=[...]      ← 18 项全量打印
19:59:32.561  20585 20585 I ICloudAuth:    OK   X-APPLE-WEBAUTH-TOKEN (len=154)
...
19:59:32.675  20585 20585 I ICloudAuth: ── Cookie probe @ https://www.icloud.com.cn/
19:59:32.675  20585 20585 I ICloudAuth:    total=18 names=[...]      ← 又一大段
```

`CookieValidationResult.validate()` 多次 `split(';')` 遍历 + `logToLogcat` 逐行打印，全在主线程。

#### 2. WebView 被意外提前初始化

关键线索：冷启动阶段就出现了 `checkCookies source=onPageStarted`：

```
19:59:32.674  20585 20585 D ICloudAuth: checkCookies source=onPageStarted
19:59:32.675  20585 20585 I ICloudAuth: ── Cookie probe @ https://www.icloud.com.cn/
```

**这说明 `AuthUiState` 短暂经过了 `NotLoggedIn`**，`AuthScreen` 被组合，`ICloudWebView` 被创建。

WebView 初始化是重量级操作（加载 `libwebviewchromium.so` 几十 MB + 启动渲染进程）—— 这就是那 59 帧（~980ms）卡顿的元凶。

成因：`AuthViewModel.init{}` 里的 `checkExistingSession()` 是 `suspend` 函数，在它完成前 `_uiState` 仍是初始值 `NotLoggedIn()`，于是 **UI 先渲染了一帧登录页**。

#### 3. 首屏 SQLite 首次访问

```
19:59:32.674  hiddenapi: Accessing hidden method ...SQLiteDatabase;->getThreadSession()
19:59:32.674  hiddenapi: Accessing hidden method ...SQLiteSession;->beginTransaction(...)
```

DB 首次打开（含建表校验 + hiddenapi 反射）也在启动关键路径上。

### 修复方案

#### 修复 1：`AuthUiState` 增加 `Checking` 初始态（最关键）

```kotlin
// AuthViewModel.kt
sealed interface AuthUiState {
    data object Checking : AuthUiState                    // 新增
    data class NotLoggedIn(val isChinaMainland: Boolean = true) : AuthUiState
    data class Authenticating(val message: String) : AuthUiState
    data class LoggedIn(...) : AuthUiState
    data class Error(...) : AuthUiState
}

private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Checking)  // 改初始值
```

`MainActivity` 渲染分流：

```kotlin
// MainActivity.kt
when (val state = authState) {
    is AuthUiState.Checking -> {
        // 什么都不建，避免 WebView 提前初始化
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
    is AuthUiState.LoggedIn -> PhotosScreen(...)
    else -> AuthScreen(viewModel = authViewModel)
}
```

> [!IMPORTANT]
> `Checking` 分支**绝对不能**组合 `AuthScreen`，否则 WebView 仍会被创建。

#### 修复 2：Cookie 校验转后台线程

```kotlin
// AuthViewModel.checkExistingSession()
suspend fun checkExistingSession() {
    val (isLoggedIn, cookieStr, isChina, dsid, ckUrl) = withContext(Dispatchers.IO) {
        SessionSnapshot(
            isLoggedIn = sessionManager.isLoggedIn(),
            cookieStr = sessionManager.getCookieString() ?: "",
            isChina = sessionManager.isChinaMainland(),
            dsid = sessionManager.getDsid() ?: "",
            ckUrl = sessionManager.getCkDatabaseUrl() ?: ""
        )
    }

    if (!isLoggedIn) {
        _uiState.value = AuthUiState.NotLoggedIn(isChina)
        return
    }

    // 校验与日志都在 Default 线程
    val validation = withContext(Dispatchers.Default) {
        CookieValidationResult.validate(cookieStr).also { it.logToLogcat("restored-from-storage") }
    }

    // 成功路径直接置 LoggedIn，全程不会产出 NotLoggedIn
    if (dsid.isNotBlank() && ckUrl.isNotBlank()) {
        _uiState.value = AuthUiState.LoggedIn(dsid, ckUrl, isChina, validation.hasHsaTrust)
    } else {
        initGoSession(cookieStr, isChina, validation.hasHsaTrust)
    }
}
```

#### 修复 3：日志降级

`CookieValidationResult.logToLogcat()` 中的逐项 `Log.i` 改为：

- 正常情况只打印**一行**汇总（`total=18 valid=true missing=[]`）
- 逐项明细降到 `Log.d`，且仅在 `BuildConfig.DEBUG` 下输出
- 绝不在主线程调用

```kotlin
fun logToLogcat(pageUrl: String?, tag: String = AUTH_LOG_TAG) {
    if (!BuildConfig.DEBUG) return
    Log.d(tag, "cookie probe @$pageUrl total=${cookieNames.size} valid=$isValid missing=$missingKeys")
    // 明细不再逐条 Log.i
}
```

#### 修复 4（可选）：DB 预热

在 `Application.onCreate()` 用后台线程预打开数据库：

```kotlin
class IClouDroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getInstance(this@IClouDroidApp).photoDao().getCount().first()
        }
    }
}
```

### 验证方法

修复后冷启动，预期：

- `Choreographer: Skipped` 帧数降到 **个位数**
- 冷启动阶段**不再出现** `checkCookies source=onPageStarted`
- 滑动从第一帧就跟手

> [!TIP]
> 排除 P2-012（底部跳顶）后再测一次卡顿，两项可能互相叠加影响。

---

## ~~P2-013：冷启动主线程阻塞约 1.5 秒~~ ✅ 已修复

### 实际改动（三处）

**1. `AuthUiState` 新增 `Checking` 初始态**（最关键）

```kotlin
sealed interface AuthUiState {
    data object Checking : AuthUiState   // 新增
    data class NotLoggedIn(...) : AuthUiState
    ...
}

private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Checking)   // 原为 NotLoggedIn()
```

`Checking` 分支在 `AuthScreen` 与 `MainActivity` 里**都只渲染一个 spinner**，
**绝不渲染 `AuthScreen` / `NotLoggedInContent`** —— 那会构造 `ICloudWebView`，
加载几十 MB 的 `libwebviewchromium.so` 并启动渲染进程。

**2. `checkExistingSession()` 合并为单次 IO 跳转**

原实现有 5 次独立的 `withContext(Dispatchers.IO)`，每次都重新访问 keystore 支撑的
`EncryptedSharedPreferences`。现在合并为一次，并把 `CookieValidationResult.validate()`
与 `logToLogcat()` 一起搬进 IO：

```kotlin
val snapshot = withContext(Dispatchers.IO) {
    SessionSnapshot(
        isLoggedIn = ..., cookieStr = ..., isChina = ...,
        dsid = ..., ckUrl = ...,
        validation = ...also { it.logToLogcat("restored-from-storage") }
    )
}
```

**3. `logToLogcat()` 降为单行 `Log.d` + `BuildConfig.DEBUG` 门控**

原来每次探针 5 条 `Log.i` + 完整 cookie 名列表；现在一行摘要，且 release 构建直接编译掉：

```kotlin
if (!BuildConfig.DEBUG) return
Log.d(tag, "cookie probe @$pageUrl total=${cookieNames.size} token=$hasAuthToken ...")
```

> [!NOTE]
> 为此在 `app/build.gradle.kts` 开启了 `buildFeatures { buildConfig = true }`。

### 验证结果（三星 SM-F9760 真机，`am force-stop` 后冷启动）

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| `Choreographer: Skipped` | **197 frames**（~3.3 秒） | **37 frames**（~0.6 秒） |
| `checkCookies source=onPageStarted` | 出现（WebView 被提前创建） | **完全消失** |
| `WebViewFactory: Loading com.google.android.webview` | 出现 | **未出现** |
| Cookie 探针日志 | 5 行 `Log.i` 全量打印 | **1 行 `Log.d` 摘要** |
| 会话恢复 | 正常 | 正常（静默恢复 2490 项） |

关键日志（修复后，全程无 WebView 初始化）：

```
21:18:23.346 D/ICloudAuth: cookie probe @restored-from-storage total=18 token=true hsa=true pcs=true valid=true missing=[none]
21:18:23.578 I/PhotoRepository: Rebuilding Go core session (force=false)...
21:18:23.826 I/Choreographer: Skipped 37 frames!
```

### 剩余 37 帧的来源（不属于本问题）

日志显示这批跳帧发生在首屏缩略图批量加载时：

```
21:18:23.723 D/ICloudPhotoCoil: load ... quality=THUMB hasUrl=true ageStale=false genStale=true
…（同时多条）
21:18:23.826 I/Choreographer: Skipped 37 frames!
```

`genStale=true` 说明会话重建后全部 URL 失效，首屏数十格同时触发刷新。
这是**首屏渲染成本**，不是冷启动阻塞，且已从 3.3 秒降到 0.6 秒。
若需进一步优化，可在会话重建后预热首屏 URL（`refreshUrlsForMasters` 批量接口已就绪但未消费）。

---

## ~~P2-014：分页期间会话重建回归（17 秒内 4 次）~~ ✅ 已修复

> 已归档至 [CHANGELOG_ISSUES.md → ISS-020](CHANGELOG_ISSUES.md#iss-020-continuationmarker-失效后仍被重放引发-401--重建死循环)。
>
> **根因**：`continuationMarker` 是 CloudKit 的分页游标，绑定在签发它的会话上。会话重建后
> 它就在服务端失效了，但本地 `syncStateDao` 里的那份没被清理。重建后仍拿旧 marker 重试
> → `401` → 再重建 → 再 `401`，形成 17 秒 4 次重建的死循环。
>
> **修复**：仅在 `fetchNextPage()` 的 `isAuthError` 恢复路径上作废 marker，然后**用空 marker
> 重新拉取**（而不是重放旧 marker）。重叠部分按 `id` 在 upsert 时去重。
>
> **⚠️ 踩坑**：初版把清理放在了 `ensureGoSession()` 里，而该方法**也会在普通冷启动
> （`force=false`）时执行** —— 那种场景下 cursor 仍然有效且是继续分页的唯一依据，
> 清掉后分页直接停摆（`No more photos to fetch via continuationMarker`）。
> **教训：“会话重建” ≠ “cursor 失效”**，失效判定必须绑定在“恢复路径”而非“重建动作”上。
>
> **验证**：冷启动后滑到底穿越 240 条缓存 + 触发新分页，仅 1 次 `force=false` 重建，
> 0 次 `auth error`，0 次强制重建，分页游标正常递增接续。

---

## P2-016：`RemoteMediator` 每次 APPEND 必 invalidate，导致深翻页跳顶

**优先级**：🔴 P0（已确诊；但属 Paging 3 架构性行为，待 UI 重构时一并解决）
**归属**：`data/repository/PhotoRepository.kt` → `PagingConfig`；`PhotoRemoteMediator`
**发现环境**：三星 SM-F9760 真机
**状态**：⚠️ 根治方案暂缓（等 UI 重构）；当前为「推后悬崖」的缓解态

### 最终结论（经四轮排查 + `GridDiag` 实测确认）

**根因不是数据层，是 Paging 3 的 `RemoteMediator` 机制本身。**

因果链条：

1. 每次 APPEND，`PhotoRemoteMediator.load()` 返回 `MediatorResult.Success`
2. Paging 3 收到 `Success` 后 **无条件 invalidate `PagingSource`**（官方设计，无法绕过）
3. invalidate 后重新加载，**重载窗口长度 = `initialLoadSize`**
4. 用户当前位置超出该窗口 → `firstVisibleItemIndex` 被 clamp 到窗口内 → **视觉跳顶**

### 关键实测证据（`GridDiag` 日志）

在 `LazyGridState` 上挂 `snapshotFlow` 直接观测，得到了铁证：

```
20:55:35.298 index=1344 itemCount=1827 appending=true    ← APPEND 进行中
20:55:35.466 index=1344 itemCount=1200 appending=false   ← 塌到 initialLoadSize=1200
20:55:35.491 index=1168 itemCount=1200                   ← index 被 clamp 回 1168
20:55:35.548 index=1164 itemCount=1260                   ← 一页页涨回来
20:55:35.579 index=1164 itemCount=1320
…
20:55:36.629 index=1164 itemCount=1740                   ← index 卡在 1164 不动
```

**塌陷点恒定等于 `initialLoadSize`**，两组对照完全验证：

| `initialLoadSize` | 实测塌陷到的 `itemCount` |
| --- | --- |
| 60 | **60**（用户从 index 305 掉回 16） |
| 360 | **360**（用户从 index 564 掉回 324） |
| 1200 | **1200**（用户从 index 1344 掉回 1164） |

> [!IMPORTANT]
> 这也解释了用户描述的「跳回位置随滑深同步下移」：
> 滑得越深，离 `initialLoadSize` 越远，被 clamp 得越多。

### 为什么调参不能根治

调大 `initialLoadSize` 只是**把悬崖往后推**，并未消除。图库有多少张，
就总有一个深度会撞上悬崖。这是结构性问题，不是参数问题。

### 已经做的缓解（保留）

`PagingConfig` 当前配置：

```kotlin
pageSize = 60,
prefetchDistance = PREFETCH_DISTANCE,   // 60 * 3
initialLoadSize = INITIAL_LOAD_SIZE     // 60 * 20 = 1200
```

- 真机能从「滑到 240 就跳」改善到「滑到 2400 才跳」
- `prefetchDistance` 曾试过 `60*8`，导致 APPEND 并发过猛，
  CloudKit 返回 `context deadline exceeded`，已回落到 `60*3`

### 为什么不建议现在重写

用户已明确：**最终版本会换成 Google Photos 风格的 UI**。
而分组式 UI 的滚动锚点是「日期 section + 组内偏移」，**不是全局整数 index**，
天然的不会以同样形式遭遇 index 塌陷。

因此现在为旧 UI 做脱离 Paging 3 的重写，属于**投入会被丢弃、风险却真实存在**：
脱离 Paging 3 意味着自建 prefetch / 错误重试 / 加载状态 / 并发控制，
而这些现在是免费获得的。

### 待办（等 UI 重构时一并处理）

- [ ] 采用日期分组 UI 时，滚动锚点改为 `DateSectionKey + offset`
- [ ] 评估是否保留 Paging 3；若保留，验证分组后 `initialLoadSize` 是否仍需调大
- [ ] 若届时仍用 `LazyGridState`，改用 `firstVisibleItemKey` 做锚定而非 index

### 已排除的假设（避免后人重复踩坑）

| 曾经的判断 | 为何不成立 |
| --- | --- |
| footer 作为网格项使 itemCount ±1 | 真问题，但是次要因素，修完跳顶依旧 |
| `continuationMarker` 失效被重放 | 真问题（ISS-020），但修完跳顶依旧 |
| 孤儿 master 用 `time.Now()` 污染排序 | 真问题，已修，但不是跳顶主因 |
| 排序键不稳定（`assetDate` 同秒并列） | 真问题，已加 `id` tiebreaker，但非主因 |

> [!NOTE]
> 上述四项都已修复且应当保留（见 ISS-019 / ISS-020），它们各自解决了
> 真实的次要缺陷。跳顶的**唯一充分原因**是本文开头描述的 `RemoteMediator` invalidate。

### 配套待办：`append` 错误提示不应直接吐 URL

当前 `PhotosScreen.kt` 把 `appendState.error.message` 直接展示给用户，可能吐出：

```
Post "https://p203-ckdatabasews.icloud.com.cn/...?clientId=...&dsid=...":
context deadline exceeded (Client.Timeout exceeded while awaiting headers)
```

既难以理解，又含会话参数（违反 AGENTS.md §8.2 “绝不打印完整签名 URL”的精神）。
应改为友好文案 + 重试按钮，详情只进 logcat。

---

## ~~P2-017：Go 层 `time.Now()` 作排序键兜底~~ ✅ 已修复

**优先级**：🔴 P0（虽然非跳顶主因，但会造成照片位置漂移）
**归属**：`go-core/photos.go` → `parseRecords()`
**状态**：✅ 已修复并重新构建 AAR

### 问题

`go-core/photos.go` 有两处用 `time.Now()` 给缺失时间戳的记录兜底，
而 `assetDate` 是 `ORDER BY assetDate DESC` 的排序键：

```go
// ❌ 位置 1：孤儿 master（无对应 CPLAsset）
AssetDate: time.Now().UnixMilli(),
AddedDate: time.Now().UnixMilli(),

// ❌ 位置 2：CPLAsset 本身缺 assetDate/addedDate
if assetDate == 0 {
    assetDate = time.Now().UnixMilli()
}
```

**后果**：同一行每次分页重新入库时排序键都变，在列表中的位置持续漂移，
且永远被顶到最前面。虽然不是跳顶主因，但是明确的错误。

### 修复

两处均改为稳定的 `0`（DESC 排序下自然沉底、永不移动）：

```go
AssetDate: 0,   // P2-016: never time.Now() — must be stable across fetches
```

同时移除不再使用的 `"time"` import。

Kotlin 侧同步加防御（[PhotoRepository.parsePhotoItems](file:///Users/syh/iClouDroid/app/src/main/java/com/getup/iClouDroid/data/repository/PhotoRepository.kt)）：

```kotlin
val rawAssetDate = obj.optLong("assetDate", 0L)
val assetDate = if (rawAssetDate > 0L) rawAssetDate else 0L   // 不再 fallback 到 now
val addedDate = obj.optLong("addedDate", 0L).takeIf { it > 0L } ?: assetDate
```

### 验证

`./scripts/build_aar.sh` 成功、`go test ./...` 通过。

---

---

## P2-015：Go 层缺结构化错误字段（技术债）

**优先级**：🟡 P2（不直接影响功能，但让 Kotlin 侧只能猜字符串）
**归属**：`go-core/photos.go` → `PhotoPageResult` / `PhotoChangesResult` / `RefreshUrlsResult`；
以及 `PhotoRepository.isAuthError()`
**发现环境**：修复 P2-014 时梳理错误传递链路时发现
**状态**：⛔ 待修复（需改 Go + 重新 `./scripts/build_aar.sh`）

### 问题

Go 侧的结果结构体**既没有 `errorCode` 也没有 `httpStatus`**，遇到非 200 时只把状态码
拼进**自然语言字符串**：

```go
// go-core/photos.go
type PhotoPageResult struct {
	Success      bool   `json:"success"`
	ErrorMessage string `json:"errorMessage,omitempty"`   // ← 只有这一个
}

// 出错时：
ErrorMessage: fmt.Sprintf("records/query HTTP %d: %s", resp.StatusCode, string(bodyBytes))
```

于是 `PhotoRepository.isAuthError()` 的前三级（`errorCode` / `success` / `httpStatus`）
全部落空，只能靠第 4 级正则从 `errorMessage` 里挖：

```kotlin
AUTH_STATUS_REGEX = Regex("\\bHTTP\\s+(?:401|421)\\b")
```

这能工作，但极其脆弱 —— 只要 Go 侧改动错误文案格式，判定就静默失效。

### 修复方案

按 `AGENTS.md` 第 12 节已列出的技术债条目补齐：

```go
type PhotoPageResult struct {
	Success      bool   `json:"success"`
	// ...
	ErrorMessage string `json:"errorMessage,omitempty"`
	ErrorCode    string `json:"errorCode,omitempty"`    // AUTH_EXPIRED / NETWORK / SERVER / QUERY_INVALID
	HTTPStatus   int    `json:"httpStatus,omitempty"`
}
```

三个结果结构体（`PhotoPageResult` / `PhotoChangesResult` / `RefreshUrlsResult`）
建议统一添加，并在所有 `resp.StatusCode != http.StatusOK` 分支里填上。

> [!IMPORTANT]
> 本项需同时修改 Go 代码与 AAR，**必须**先跑 `./scripts/build_aar.sh`。
> 暂未开始，建议在 Phase 2 收尾时一并做。

---

## 附：通用注意事项

### 1. 排查 401/403/410 问题的正确姿势

| 做法 | 评价 |
| --- | --- |
| ❌ `jsonStr.contains("401")` | 会被 base64 误伤（P2-001 的成因） |
| ❌ `System.currentTimeMillis() - fetchedAt > 50min` | 时间是猜测，会话重建可能只隔 10 秒 |
| ✅ 解析 JSON 的 `errorMessage` / `errorCode` / `httpStatus` | 结构化、可靠 |
| ✅ 直接读 `HttpDataSource.InvalidResponseCodeException.responseCode` | 事实来源 |

### 2. 会话重建是"昂贵操作"

一次重建 = 一次 `/validate` 网络往返（约 1~2 秒）+ 全部 URL 立即失效 + 一次 generation 递增。

**任何触发重建的路径都必须极度保守** —— 宁可提示用户"网络错误，请重试"，
也不要贸然重建会话。

### 3. Room + Paging 3 的隐式失效

`PhotoDao.upsertAll()` 写入会触发 Room `InvalidationTracker` → `PagingSource.invalidate()`
→ 列表重建。这是 Paging 3 的正常行为，但当它高频发生时会表现为列表跳位。
P2-001 修复后写入频率回归正常，配合 P2-003 的 `gridState` 保持，已不再感知到跳位。
