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
| [P2-010](#p2-010-heic-原图带-hdr-gain-map导致-bitmapfactory-解码失败) | HEIC 原图带 HDR gain map 导致 `BitmapFactory` 解码失败 | 🟠 P1 | Android / Coil | 部分原图打不开 |
| [P2-011](#p2-011-详情页刷新被缩略图去重窗口误伤) | 详情页刷新被缩略图去重窗口误伤 | 🟠 P1 | Android / Repository | 详情页无法自愈 |
| ~~[P2-012](#p2-012)~~ | ~~`append` 指示器作为网格项导致列表跳回顶部~~ | 🔴 P0 | Android / UI | ✅ 已修复（ISS-019） |
| [P2-013](#p2-013-冷启动主线程阻塞约-15-秒) | 冷启动主线程阻塞约 1.5 秒 | 🟠 P1 | Android / 启动 | 启动卡顿、滑动不跟手 |
| ~~[P2-014](#p2-014)~~ | ~~分页期间会话重建回归（17 秒内 4 次）~~ | 🔴 P0 | Android / Repository | ✅ 已修复（ISS-020） |
| [P2-015](#p2-015) | Go 层缺结构化 `errorCode` / `httpStatus`（技术债） | 🟡 P2 | Go 核心层 | Kotlin 侧只能正则猜字符串 |

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

## P2-010：HEIC 原图带 HDR gain map 导致 BitmapFactory 解码失败

**优先级**：🟠 P1（部分原图打不开）
**归属**：`data/coil/ICloudPhotoCoil.kt`
**发现环境**：三星 SM-F9760（Galaxy Z Fold 特别版）/ Android 实机
**状态**：⛔ 待修复

### 现象

部分照片在详情页显示「图片加载失败」，但网格缩略图完全正常。Logcat：

```
19:50:23.285 W ICloudPhotoCoil: fail 801D818D-... quality=ORIGINAL
    error=BitmapFactory returned a null bitmap. ...not encoded as a valid image format.
19:50:30.178 W ICloudPhotoCoil: fail E9A6DB0B-... quality=ORIGINAL
    error=BitmapFactory returned a null bitmap.
```

而**同一素材的缩略图每次都能成功**：

```
19:48:39.451 D ICloudPhotoCoil: ok 801D818D-... quality=THUMB
19:49:04.191 D ICloudPhotoCoil: ok 801D818D-... quality=THUMB
```

### 排查过程（已排除的假设）

| 假设 | 验证方法 | 结论 |
| --- | --- | --- |
| URL 过期 / 403/410 | 错误信息 | ❌ 不是 HTTP 错误，是解码失败 |
| OOM（大图解码内存不足） | 对比尺寸：12MP 成功、5MP 失败 | ❌ 与尺寸无关 |
| `${f}` 占位符未替换 | 查库：780 条全部含占位符，成功的也含 | ❌ 已正确替换 |
| 数据不完整 / 错误页 | `curl` 下载实测 | ❌ `HTTP=200 size=4049893 type=image/heic`，数据完整 |
| HEIC 格式不支持 | `file` 识别两者 | ❌ 两者均为 `ISO Media, HEIF Image HEVC` |

### 真正的差异：`ftyp` 的 compatible brands

```
❌ 失败 IMG_1338.HEIC（5MP / 4.05MB）
   0000 0020 66747970 68656963 ... 6d696631 4d694845 6d696166 68656963
                ftyp     heic        mif1     MiHE     miaf     heic

✅ 成功 IMG_2049.HEIC（12MP / 2.18MB）
   0000 0024 66747970 68656963 ... 6d696631 4d695072 6d696166 4d694842 68656963
                ftyp     heic        mif1     MiPr     miaf     MiHB     heic
```

**失败样本包含 `MiHE` brand，成功样本是 `MiPr` + `MiHB`。**

- `MiHE` = 带 **HDR gain map / 多图容器**的 HEIF
- `MiHB` = 普通 HEVC 主图

三星的 HEVC 解码器路径在遇到 gain map 辅助图时会直接失败，`BitmapFactory` 返回 null。

### 为什么缩略图不受影响

iCloud 下发的 `resJPEGThumbRes` 是 **JPEG**（服务端已转码，剥离了 gain map），所以缩略图永远正常；只有 `resOriginalRes` 的原生 HEIC 才带这个 variant。

### 修复方案

#### 方案 A（推荐）：改用系统 HEIF 解码器 + 限制采样尺寸

```kotlin
// ✓ 修复后：ICloudImageLoaderFactory
import coil.decode.ImageDecoderDecoder

object ICloudImageLoaderFactory {
    fun get(context: Context): ImageLoader {
        return INSTANCE ?: synchronized(this) {
            val repo = PhotoRepository.getInstance(context)
            val loader = ImageLoader.Builder(context)
                .components {
                    // 关键：用系统 ImageDecoder（HEIF gain map 支持完善）
                    // 替代默认的 BitmapFactory 路径
                    add(ImageDecoderDecoder.Factory())
                    add(ICloudPhotoKeyer())
                    add(ICloudPhotoInterceptor(repo))
                }
                .crossfade(true)
                .build()
            INSTANCE = loader
            loader
        }
    }
}
```

同时把 ORIGINAL 请求的 `size` 限定为屏幕可用尺寸，避免解码全分辨率：

```kotlin
// PhotoDetailViewer.kt
ImageRequest.Builder(context)
    .data(model)
    .size(2048)        // 不传则按原图完整解码，内存与兼容性风险都高
    .build()
```

#### 方案 B（兜底）：解码失败时降级到 MEDIUM

```kotlin
// ICloudPhotoInterceptor 中
when (result) {
    is ImageResult.Error -> {
        if (data.quality == ICloudImageModel.Quality.ORIGINAL && !hasDegraded) {
            Log.w(TAG, "ORIGINAL decode failed for ${data.recordName}, degrade to MEDIUM")
            // 用 medUrl 重试，至少让用户看到内容
            return chain.proceed(request.newBuilder().data(medUrl).build())
        }
    }
}
```

> [!TIP]
> **两个方案建议都上**：方案 A 治本（能真正解出原图），方案 B 兜底（A 也挡不住时至少能给中图）。

#### 方案 C（终极方案）：Go 侧转码

在 Go 层用 `bimg` / `libheif` 把 HEIC 转成 JPEG 后返回。彻底绕开客户端兼容性问题，但增加服务端/本地 CPU 开销，且需引入 CGO 依赖，**不建议现阶段做**。

### 验证方法

修复后重新打开 `IMG_1338.HEIC`（master=`AUQhzkGu3AmNvxGLZVpYZieyfodv`）与 `IMG_1335.HEIC`（master=`AWqP3cETVjWRaNG7zO6IJj13uMcz`），预期：

- `ICloudPhotoCoil: ok ... quality=ORIGINAL`
- 详情页能正常显示原图
- 不再出现 `BitmapFactory returned a null bitmap`

---

## P2-011：详情页刷新被缩略图去重窗口误伤

**优先级**：🟠 P1（详情页无法自愈）
**归属**：`data/repository/PhotoRepository.kt` → `refreshUrlsForMaster()`
**状态**：⛔ 待修复

### 现象

详情页原图加载失败后尝试刷新 URL，但被去重逻辑直接拒绝：

```
19:50:15.328 I PhotoDetailViewer: image load failed, refreshing url master=AUQhzkGu3AmNvxGLZVpYZieyfodv, retry=1
19:50:15.332 D PhotoRepository: refreshUrlsForMaster skipped (deduped within window) master=AUQhzkGu3AmNvxGLZVpYZieyfodv
19:50:23.286 I PhotoDetailViewer: image load failed, refreshing url master=AUQhzkGu3AmNvxGLZVpYZieyfodv, retry=1
19:50:23.286 D PhotoRepository: refreshUrlsForMaster skipped (deduped within window) master=AUQhzkGu3AmNvxGLZVpYZieyfodv
```

8 秒内两次请求刷新，**两次都被去重窗口拦下**，详情页永远无法自愈。

### 根因

P2-008 的去重窗口是**按 masterId 维度**的，但刷新实际上同时返回 thumb / med / original **三类 URL**：

```kotlin
// 当前：仅按 masterId 去重
private val refreshGuard = mutableMapOf<String, Long>()   // masterId -> 时间
```

场景：
1. 缩略图先失败 → 触发刷新 → 窗口开启，三类 URL 同时更新
2. 用户点入详情页 → 原图仍失败（下面 P2-010 的 HEIC 解码问题）
3. 详情页请求刷新 → 被同一 masterId 的窗口拦住

更关键的是：**P2-010 的解码失败不是 URL 问题**，刷新 URL 也没用，但会不断撞窗口，形成无效请求 + 无效日志。

### 修复方案

#### 改进 1：只在 URL 真的失效时才刷新

解码失败（`BitmapFactory null`）不应该触发 URL 刷新：

```kotlin
// PhotoDetailViewer.kt
onError = {
    val throwable = it.result.throwable
    val code = (throwable as? HttpException)?.code()
    val isUrlIssue = code == 403 || code == 410

    if (isUrlIssue && retryCount < MAX_RETRY) {
        // 只有真·URL 失效才刷新
        refreshAndRetry()
    } else if (!isUrlIssue) {
        // 解码失败 → 降级策略，不刷新 URL
        Log.w(TAG, "decode failure (not url issue), degrade to MEDIUM")
        degradeQuality()
    }
}
```

#### 改进 2：去重窗口按「masterId + 品质」维度

```kotlin
// ✓ 修复后
private val refreshGuard = mutableMapOf<String, Long>()   // "masterId:THUMB" -> 时间

private suspend fun refreshOnce(masterId: String, quality: String): RefreshedUrls? {
    val key = "$masterId:$quality"
    guardLock.withLock {
        val last = refreshGuard[key] ?: 0L
        if (System.currentTimeMillis() - last < REFRESH_WINDOW_MS) return null
        refreshGuard[key] = System.currentTimeMillis()
    }
    return repository.refreshUrlsForMaster(masterId).getOrNull()
}
```

#### 改进 3：提供强制刷新通道

详情页（单次用户操作）应能绕过去重：

```kotlin
suspend fun refreshUrlsForMaster(masterId: String, force: Boolean = false): Result<RefreshedUrls>
```

> [!IMPORTANT]
> 修好 P2-010 后，这类「解码失败 → 误求刷新」的调用会大幅减少，但去重维度不对的问题仍需修正，否则未来仍会被误伤。

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

## P2-016：孤儿 master 用 `time.Now()` 当 `assetDate` 导致滑到底跳顶 🔴 真凶

**优先级**：🔴 P0（这是真正导致跳顶的根因）
**归属**：`go-core/photos.go` → `parseRecords()`
**发现环境**：三星 SM-F9760 真机，修复 P2-012/014 后仍钉钉跳顶
**状态**：⛔ 待修复

### 现象

真机日志揭示了决定性模式 —— **26 秒内连演 13 次 APPEND，每次 `limit=60` 却只回 30 条**：

```
20:34:53.580 D PhotoRepository: Fetching next photo page (limit=60)
20:34:54.295 D PhotoRepository: Next page returned: 30 photos, hasMore=true
20:34:55.942 D PhotoRepository: Fetching next photo page (limit=60)
20:34:56.676 D PhotoRepository: Next page returned: 30 photos, hasMore=true
…（连演 13 次，持续 26 秒）
```

这种“饥饿循环”会让 `LazyPagingItems` 索引表反复重建 —— 这才是真机跳顶的直接原因。

### 根因

`go-core/photos.go` 的 `parseRecords()` 里，对于**没有匹配到 CPLAsset 的孤儿 master**，
代码把 `time.Now()` 当作排序键：

```go
// ❌ 错的
for mID, m := range masters {
    if !matchedMasters[mID] {
        photos = append(photos, PhotoItem{
            AssetDate: time.Now().UnixMilli(),   // ← 每次请求都是不同值！
            AddedDate: time.Now().UnixMilli(),
            ...
        })
    }
}
```

**灾难性后果**：同一个 master 每次分页被重新入库时，`assetDate` 都变成当前时间，
于是它**永远排在列表最前面**，且每次分页都刷新一次。表现为：

1. 这些孤儿 master 被反复插入第一页位置 → 用户滑到底时它们“凭空出现”在顶部
2. 每次 APPEND 只能带入 30 条有效新数据（其余 30 条是被重置的孤儿）
3. Room invalidation → `PagingSource.invalidate()` → 索引表重建 → **视觉跳顶**

> [!IMPORTANT]
> 这也解释了为何 P2-012（footer）和 P2-014（marker）都修了但跳顶依旧：
> 那两项都是真问题，但**不是跳顶的主因**。主因在数据层。

### 修复方案

#### 方案 A（推荐）：孤儿 master 不入库 / 用确定性时间戳

两种写法，优先第一种：

**A1 — 直接不返回孤儿 master**（最干净）：
一个没有 `CPLAsset` 的孤立 `CPLMaster` 本来就不是用户可见的照片。
不带 `assetDate` 就无法正确排序，强行给个“当前时间”只会污染列表。
直接不加入 `photos` 即可：

```go
// 直接删掉整个 "For any standalone masters that didn't have an asset" 循环
```

> [!WARNING]
> 需先确认这个循环当初的存在理由。若确实依赖它处理某些真实场景，
> 则用 A2。

**A2 — 用确定性时间戳**：
如果必须保留，至少不能用 `time.Now()`。改用 master 自身的稳定信息（如 `recordName`
的哈希派生的固定时间，或直接用 0 并在 Kotlin 侧沉底）：

```go
AssetDate: 0,   // 无真实拍摄时间，由 Kotlin 侧 ORDER BY 自然沉底
```

配合 Kotlin 侧排序 `ORDER BY assetDate DESC`，`0` 会稳定排在最后，且**永不变动**。

#### 方案 B（配套）：Kotlin 侧防御

即使 Go 侧修好，也建议在 `parsePhotoItems()` 里加一道断言：

```kotlin
if (assetDate <= 0) {
    Log.w(TAG, "Dropping photo with invalid assetDate: id=$id")
    continue
}
```

避免将来 Go 侧再回退。

#### 方案 C（已做，保留）：稳定排序键

`PhotoDao` 的 `ORDER BY assetDate DESC, id ASC` 与复合索引（P2-012 修复的一部分）仍然应该保留，
它们是正确的防御，只是**单独不足以解决跳顶**。

### 验证方法

修复后真机滑动，预期：

- `Fetching next photo page` 每次都是 `limit=60` → **返回 60 photos**
- 不再出现 13 连发的饥饿循环
- 滑到底不再跳顶
- logcat 中无 `Dropping photo with invalid assetDate` 告警

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
