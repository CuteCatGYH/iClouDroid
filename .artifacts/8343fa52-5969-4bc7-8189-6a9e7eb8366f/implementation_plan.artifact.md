# iClouDroid：P2-015 结构化错误码 + Google 相册风格 UI 重构（根治 P2-016）

## 背景

当前照片列表以 `RemoteMediator` + Paging 3 驱动。Paging 3 在每次 `RemoteMediator.load(APPEND)` 返回 `Success` 后**无条件 invalidate `PagingSource`**，重载窗口等于 `initialLoadSize`。用户滚动深度一旦超出该窗口，`firstVisibleItemIndex` 就被 clamp，表现为"跳回顶部"（[ISS-021](file:///Users/syh/iClouDroid/CHANGELOG_ISSUES.md#iss-021) / [P2-016](file:///Users/syh/iClouDroid/list.md#p2-016)）。

既有缓解手段只是把悬崖从 240 项推到 2400 项，**未消除**。根治必须换掉"全局整数 index 作滚动锚点"这一前提。

同时 [P2-015](file:///Users/syh/iClouDroid/list.md#p2-015) 让 Kotlin 侧只能靠正则猜错误类型。重构后的扫描引擎会显著提高请求密度，错误的错误分类会直接放大为"限流被误判为鉴权失败 → 反复重建会话"，因此**必须先修 P2-015**。

本计划分两阶段交付，阶段一独立可验收，阶段二才动 UI。

---

## User Review Required

> [!IMPORTANT]
> **阶段二是破坏性重构**：`PhotoRemoteMediator.kt` 被删除，`photoPagingFlow` / `PhotoDao.pagingSource()` / `INITIAL_LOAD_SIZE` / `PREFETCH_DISTANCE` 全部移除，`PhotosScreen` 从 `LazyVerticalGrid` 改为 `LazyColumn` + 嵌套网格。`PhotosViewModel` 的 `collectAsLazyPagingItems()` 数据契约整体更换。合并前必须独立 PR 并完成完整的实机回归。

> [!WARNING]
> **Room v3 → v4 是首次正式 Migration**，同时移除 `fallbackToDestructiveMigration()`。已有用户数据（`photo_assets` / `sync_state`）必须无损升级。升级路径会先做迁移验证再合入。

> [!CAUTION]
> **WebView DOM 抓取照片总数依赖 Apple 的私有页面结构**。该实现随时可能失效。按既定决策，抓取失败一律**静默降级**为"不确定进度"模式，绝不因此阻断登录或照片流程。

---

## Open Questions

> [!NOTE]
> 以下问题已在对话中确认，此处仅作记录，无需再次回答。
> 1. 时区策略 → **设备本地时区**。
> 2. 扫描模式 → **边扫边看**。
> 3. 数据策略 → **B3：全量回填 Room + UI 本地分页**。
> 4. UI 形态 → **先做形态 A（月分组），结构上预留形态 B（月 → 日）**。
> 5. 后台扫描 → **WorkManager + 前台服务 + 常驻通知**（二维年月 + 进度条）。
> 6. 总数来源 → **WebView 登录页 DOM 抓取**；失败 → **静默降级为不确定进度**。

> [!IMPORTANT]
> 唯一尚未定死、但可在实施中决断的点：**`CPLAsset` 计数与网页端展示总数的口径差异**。网页端"13,689 张照片, 2,247 个视频"是展示值，而 `FetchPhotoPage` 走的是 `CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted`，可能排除隐藏项，且孤儿 master 会被 `parseRecords` 补成条目。因此分母只能是**近似值**，进度条按 `coerceIn(0f, 0.99f)` 处理，扫描完成时强制置为 100%。若实测偏差超过 10%，改为**纯项数 + 转圈**展示。

---

## Proposed Changes

### 阶段一：P2-015 结构化错误码

目标：让 Go 层直接给出 `errorCode` / `httpStatus`，Kotlin 侧停止用正则猜。**此阶段不改任何 UI、不改分页结构**，验收后即可独立合入。

---

#### Go 核心层

##### [NEW] errors.go

新增错误码常量与分类函数，作为跨语言错误判定的唯一真理源。

```go
const (
    ErrCodeAuthExpired = "AUTH_EXPIRED"
    ErrCodeNotFound    = "NOT_FOUND"
    ErrCodeRateLimited = "RATE_LIMITED"
    ErrCodeTimeout     = "TIMEOUT"
    ErrCodeNetwork     = "NETWORK"
    ErrCodeParse       = "PARSE"
    ErrCodeZoneMissing = "ZONE_MISSING"
    ErrCodeUnknown     = "UNKNOWN"
)

// ResultBase 是统一结果基类。Go 的匿名嵌入会把字段提升到顶层，
// 因此 JSON 形状仍是扁平的，与现有 Kotlin 解析完全兼容（零破坏）。
type ResultBase struct {
    Success      bool   `json:"success"`
    ErrorCode    string `json:"errorCode,omitempty"`
    HttpStatus   int    `json:"httpStatus,omitempty"`
    ErrorMessage string `json:"errorMessage,omitempty"`
}

func ClassifyHTTPStatus(status int) string
func ClassifyNetErr(err error) string
func failBase(code string, status int, msg string) ResultBase
```

关键分类规则：`401`/`421` → `AUTH_EXPIRED`；`404` → `NOT_FOUND`；`429` → `RATE_LIMITED`；`>=500` → `UNKNOWN`；传输层 `context deadline exceeded` / `Client.Timeout` → `TIMEOUT`。

##### [MODIFY] photos.go

四个导出结果结构体嵌入 `ResultBase` 并删除各自的 `Success` / `ErrorMessage` 同名字段：

- `PhotoPageResult`（`FetchPhotoPage`）
- `PhotoChangesResult`（`FetchChanges`）
- `RefreshUrlsResult`（`RefreshAssetUrls`）
- `ZoneCheckResult`（`CheckPrimaryZone`）

每个 `return` 分支改为走统一构造器，覆盖：

| 失败分支 | ErrorCode | HttpStatus |
| --- | --- | --- |
| `GetSession() == nil` | `AUTH_EXPIRED` | 0 |
| `CloudKit database URL is not set` | `AUTH_EXPIRED` | 0 |
| `NewRequest` / `Client.Do` 失败 | `ClassifyNetErr(err)` | 0 |
| `io.ReadAll` 失败 | `NETWORK` | 0 |
| 非 200 响应 | `ClassifyHTTPStatus(resp.StatusCode)` | 真实状态码 |
| JSON 解析失败 | `PARSE` | 响应状态码 |
| `PrimarySync` 未找到 | `ZONE_MISSING` | 200 |

> [!NOTE]
> `CheckPrimaryZone` 原先用 `Success=true, Ready=false` 表达分区缺失，Kotlin 需二次判断。改为 `ZONE_MISSING` 后语义明确，同时**保留** `Ready` 字段不动，避免影响既有调用。

##### [MODIFY] photos.go（附带修正）

把 `photosBaseURL()` 中每次调用都执行的 `generateClientID()` 改为读取 Session 上已持久化的 `ClientID`（ISS-014 的修复在 Session 上有字段，但此处仍在现生成）。属于同一文件的顺手修正，与错误码改造同批交付。

##### [NEW] errors_test.go

覆盖 `ClassifyHTTPStatus` 全部分支（含 401/421/404/429/500/200），以及 `ClassifyNetErr` 对超时与普通网络错误的区分。

---

#### Android 数据层

##### [MODIFY] PhotoRepository.kt

新增统一分类，`isAuthError` 降级为薄封装，调用点零改动：

```kotlin
internal enum class CoreErrorKind { AUTH_EXPIRED, NOT_FOUND, RATE_LIMITED, TIMEOUT, NETWORK, PARSE, ZONE_MISSING, UNKNOWN, NONE }

internal data class CoreError(val kind: CoreErrorKind, val httpStatus: Int, val message: String)

internal fun classifyCoreResponse(jsonStr: String): CoreError
private fun isAuthError(jsonStr: String): Boolean = classifyCoreResponse(jsonStr).kind == CoreErrorKind.AUTH_EXPIRED
```

取舍规则：优先 `errorCode`；其次 `httpStatus`；最后才落到 `AUTH_STATUS_REGEX` 兜底（仅在通过旧 AAR 构建时生效）。

`checkZoneReady` / `fetchInitialPage` / `fetchNextPage` / `syncIncremental` 的签名与流程**完全不变**。

##### [NEW] CoreErrorTest.kt

- 8 种 `kind` 各一例
- `success=true` 且携带 `errorCode` → 仍判为 `NONE`
- base64 风格 `continuationMarker` 含 `"401"` → 判为 `UNKNOWN`（不得误判为 `AUTH_EXPIRED`）
- 空串 / 非 JSON → `UNKNOWN`，不抛异常

---

#### 阶段一验收门槛

| 项 | 命令 | 通过标准 |
| --- | --- | --- |
| Go 编译 | `./scripts/build_aar.sh` | 成功产出 `icloudcore.aar` |
| Go 单测 | `go test ./...` | 全绿 |
| Kotlin 单测 | `testDebugUnitTest` | 全绿（含新增 `CoreErrorTest`） |
| 静态检查 | `analyze_file` | 无 error |
| 实机 | 冷启动 + 连续滚动 3 页 | `Rebuilding Go core session (force=true)` = **0 次**；`AUTH_STATUS_REGEX` 未命中 |

**未全部通过不得进入阶段二。**

---

### 阶段二：UI 重构 + P2-016 根治

#### 2.1 Room v3 → v4

##### [MODIFY] PhotoAssetEntity.kt

新增 `yearMonth: Int = 0`（`yyyyMM`，设备本地时区计算；`0` 表示尚未分桶）。

##### [NEW] PhotoMonthIndexEntity.kt

```kotlin
@Entity(tableName = "photo_month_index")
data class PhotoMonthIndexEntity(
    @PrimaryKey val yearMonth: Int,
    val itemCount: Int,
    val firstAssetDate: Long,
    val lastAssetDate: Long,
    val isComplete: Boolean
)
```

##### [NEW] ScanStateEntity.kt

```kotlin
@Entity(tableName = "scan_state")
data class ScanStateEntity(
    @PrimaryKey val key: String,
    val lastMarker: String?,
    val isComplete: Boolean,
    val scannedCount: Int,
    val estimatedTotal: Int,   // 来自 WebView DOM；0 表示未知
    val updatedAt: Long
)
```

##### [MODIFY] PhotoDatabase.kt

版本升至 4，注册两个新实体，新增 `MIGRATION_3_4`，**移除 `fallbackToDestructiveMigration()`**。

Migration 只做 DDL（加列 + 建表）。**历史行回填不由 Migration 完成**：`ALTER TABLE ... DEFAULT 0` 会给旧行填 0，回填交由 2.2 的后台任务处理，避免在 Migration 中执行长事务。

---

#### 2.2 月索引扫描引擎

##### [NEW] MonthScanEngine.kt

Repository 级单例（由 `AppContainer` 持有），生命周期与前台/后台无关。

核心职责：

1. **断点续扫**：`scan_state.lastMarker` 驱动，中断后从原游标继续。
2. **边扫边看**：每入库一批，立刻对受影响月份调用 `rebuildForMonths()`。
3. **互斥**：内部 `Mutex`，前台与 WorkManager 同时触发时只有一个真正执行。
4. **进度广播**：`StateFlow<ScanProgress>`，包含 `scanned` / `total` / `currentYearMonth` / `isComplete`。

失败处理（依赖阶段一的错误码）：

| CoreErrorKind | 行为 |
| --- | --- |
| `AUTH_EXPIRED` | `ensureGoSession(force = true)` → `invalidateContinuationCursor()` → 从空游标重启扫描 |
| `RATE_LIMITED` | 指数退避重试，**不重建会话** |
| `TIMEOUT` | 退避重试，**不重建会话** |
| 其他 | 记录错误，保留 `lastMarker`，等待下次触发 |

> [!IMPORTANT]
> 严格复用 [ISS-020](file:///Users/syh/iClouDroid/CHANGELOG_ISSUES.md#iss-020) 的教训：**只有服务端明确返回 401/421 时才作废游标**。"会话重建"本身不等于"游标失效"，普通重建路径不得清除 `lastMarker`。

##### 时区工具

`yearMonthOfLocal(assetDateMs: Long): Int` —— `assetDateMs <= 0` 返回 `0`（未分组）；否则用 `Calendar.getInstance()`（设备本地时区）取 `year * 100 + month + 1`。

---

#### 2.3 总数抓取（WebView DOM）

##### [MODIFY] ICloudWebView.kt

扩展 `CookieProbe`，新增异步的读取入口：

```kotlin
class CookieProbe {
    private var extractor: (() -> CookieValidationResult)? = null
    private var countsReader: ((callback: (PhotoCounts?) -> Unit) -> Unit)? = null

    fun extract(): CookieValidationResult? = extractor?.invoke()
    fun readCounts(callback: (PhotoCounts?) -> Unit) { countsReader?.invoke(callback) ?: callback(null) }
}
```

在 `AndroidView` 的 `factory` 中绑定 `countsReader`，通过 `evaluateJavascript` 读取：

```javascript
(function () {
  var t = document.body ? document.body.innerText : "";
  var p = t.match(/([\d,]+)\s*张照片/);
  var v = t.match(/([\d,]+)\s*个视频/);
  return JSON.stringify({
    photos: p ? parseInt(p[1].replace(/,/g, ""), 10) : null,
    videos: v ? parseInt(v[1].replace(/,/g, ""), 10) : null
  });
})()
```

> [!WARNING]
> **`evaluateJavascript` 的回调在主线程派发**，绝不能用 `CountDownLatch` 在主线程同步等待（必然死锁）。整个读取链路必须是回调 / `suspendCancellableCoroutine` 形式。
>
> `evaluateJavascript` 走 WebView native 桥，**不受页面 CSP 限制**，这是该方案成立的技术前提。

##### [MODIFY] AuthViewModel.kt

`forceExtractCookies()` 成功路径中，在 `onCookiesExtracted()` 之前（或并行）发起 `countsReader`：

```kotlin
fun forceExtractCookies() {
    // ... 现有守卫与 probe.extract() 逻辑不变 ...

    if (probeResult.isValid) {
        _cookieProbe.readCounts { counts ->
            // 静默降级：counts 为 null 时不提示、不阻断
            pendingPhotoCount = counts?.total
        }
        onCookiesExtracted(probeResult)
    }
}
```

`counts` 结果通过 `sessionManager`（或 `SessionSnapshot` 的伴随字段）传递给 `MonthScanEngine`，最终写入 `scan_state.estimatedTotal`。

**静默降级**：`counts == null` → `estimatedTotal = 0` → 进度条进入不确定模式（转圈 + "已扫描 N 项 · 当前 YYYY年MM月"），**无任何用户提示**。

---

#### 2.4 Repository 改造：移除 Paging 3

##### [DELETE] PhotoRemoteMediator.kt

整体删除。

##### [MODIFY] PhotoRepository.kt

- 删除 `photoPagingFlow`（`Pager` / `PagingConfig` / `PhotoRemoteMediator` 全部移除）。
- 删除 `INITIAL_LOAD_SIZE` / `PREFETCH_DISTANCE` 常量。
- `fetchInitialPage` / `fetchNextPage` 的**网络与游标逻辑**迁移进 `MonthScanEngine`（`PhotoRepository` 不再承担分页职责，仅保留会话守卫、URL 刷新、增量同步、清洗）。
- 保留 `syncIncremental`，并在 `parsePhotoItems` 中统一计算 `yearMonth`。
- 每次 `upsertAll` 后触发受影响月份的 `rebuildForMonths()`。

##### [MODIFY] PhotoDao.kt

- **删除** `pagingSource()`。
- 新增：

```kotlin
@Query("SELECT * FROM photo_assets WHERE isHidden = 0 AND yearMonth = :yearMonth ORDER BY assetDate DESC, id ASC")
fun observeMonth(yearMonth: Int): Flow<List<PhotoAssetEntity>>

@Query("UPDATE photo_assets SET yearMonth = :yearMonth WHERE id = :id")
suspend fun setYearMonth(id: String, yearMonth: Int)

@Query("SELECT id, assetDate FROM photo_assets WHERE yearMonth = 0")
suspend fun getUnbucketed(): List<PhotoIdDate>

@Query("SELECT COUNT(*) FROM photo_assets WHERE isHidden = 0")
fun observePhotoCount(): Flow<Int>
```

`getAllPhotos()` 保留（详情页回退路径仍可能使用）。

##### [NEW] PhotoMonthIndexDao.kt / ScanStateDao.kt

- `PhotoMonthIndexDao`：`observeAll()`（`ORDER BY yearMonth DESC`）、`rebuild(yearMonth)`、`clearAll()`。
- `ScanStateDao`：`get` / `set` / `clearAll`。

##### [MODIFY] PhotoRepository.wipeAllData()

按 [ISS-025](file:///Users/syh/iClouDroid/CHANGELOG_ISSUES.md#iss-025) 的四阶段清洗，追加清空 `photo_month_index` 与 `scan_state`。**顺序不变**（阻断 → Room → Coil → Cookie/Vault）。

---

#### 2.5 Compose UI（形态 A）

##### [MODIFY] PhotosScreen.kt

顶层结构：

```kotlin
val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

Box(Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        monthIndex.forEach { month ->
            stickyHeader(key = "h_${month.yearMonth}") { MonthHeader(month) }
            item(key = "g_${month.yearMonth}") {
                MonthGrid(month = month, columns = gridColumns, onPhotoClick = viewModel::selectPhoto)
            }
        }
    }
    MonthIndexBar(...)     // 右侧年月索引条
    ScanProgressBar(...)   // 顶部/底部扫描进度
}
```

沿用 [ISS-019](file:///Users/syh/iClouDroid/CHANGELOG_ISSUES.md#iss-019) 的结论：**网格常驻组合树，空/错误态用 overlay 叠加，不做 if/else 兄弟分支**。

##### [NEW] MonthGrid.kt

```kotlin
@Composable
fun MonthGrid(month: PhotoMonthSection, columns: Int, onPhotoClick: (PhotoAssetEntity) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.height(estimateHeight(month.itemCount, columns)),
        userScrollEnabled = false
    ) {
        items(month.photos, key = { it.id }) { PhotoGridItem(it) { onPhotoClick(it) } }
    }
}
```

> [!IMPORTANT]
> 三个必须同时满足的条件，缺一个就会退化为"卡顿"或"手势冲突"：
> 1. `userScrollEnabled = false` —— 否则内层网格吞掉外层滚动手势。
> 2. `Modifier.height(...)` 显式指定高度 —— 否则 `LazyColumn` 测量时会尝试测量网格全部子项，几千项直接卡死。
> 3. 网格项必须是**固定尺寸**（正方形或固定宽高比）+ 固定间距，`estimateHeight` 才能精确。

##### [NEW] MonthHeader.kt

吸顶月份标题：`"2026年8月 · 137 项"`，`Surface` + `MaterialTheme.typography.titleSmall`，高度固定，避免吸顶切换时抖动。

##### [NEW] MonthIndexBar.kt

合并图 1 的年份列表与图 2 的浮动胶囊：

- 常驻显示当前年月胶囊（由 `listState.firstVisibleItemKey` 反推）。
- 展开为年份列表，仅列出 `photo_month_index` 中**实际存在**的年份（不生成空年份）。
- 点击年份 → `listState.animateScrollToItem(目标月索引 * 2)`（每月固定两 item：header + grid）。
- 上下箭头 → 相邻月份。

> [!IMPORTANT]
> **锚定与回读一律使用 key，禁止使用 index**。这是 P2-016 的根治点：`firstVisibleItemKey` 是稳定字符串，不随 `itemCount` 变化；而 `firstVisibleItemIndex` 会在任何列表重构时漂移。

##### [NEW] ScanProgressBar.kt

| 状态 | 展示 |
| --- | --- |
| `estimatedTotal > 0` | `LinearProgressIndicator(progress = scanned / total)` + `"已扫描 3,847 / 16,116 项 · 2023年11月"` |
| `estimatedTotal == 0` | 转圈 + `"已扫描 3,847 项 · 2023年11月"` |
| 扫描完成 | 进度条消失 |
| 扫描失败 | 静默隐藏（与总数抓取失败一致，不打扰用户） |

百分比按 `coerceIn(0f, 0.99f)` 上限截断，扫描完成时整体隐藏 —— 避免"卡在 99%"。

##### [MODIFY] PhotosViewModel.kt

- 移除 `photosPagingData` / `collectAsLazyPagingItems()`。
- 新增 `monthIndex: StateFlow<List<PhotoMonthIndexEntity>>`、`scanProgress: StateFlow<ScanProgress>`、`currentMonthKey: StateFlow<Int?>`。
- 各月份的 `photos` 由 UI 侧按可见月单独 `observeMonth(ym).collectAsStateWithLifecycle()` 订阅，**不把所有照片装进单一 StateFlow**（1.6 万实体全量驻留内存会造成明显压力）。

---

#### 2.6 后台扫描与通知

##### [NEW] MonthScanWorker.kt

`CoroutineWorker`，`doWork()` 内调用 `monthScanEngine.scanAll()`：

- `setForeground(...)`，类型 `FOREGROUND_SERVICE_TYPE_DATA_SYNC`。
- `Constraints.NETWORK_CONNECTED`；**不**设 `requiresCharging`（否则用户可能永远等不到扫描完成）。
- 退避：`BackoffPolicy.EXPONENTIAL`，初始 30s。
- 重试上限 3 次后 `Result.failure()`。

##### [NEW] ScanNotificationHelper.kt

常驻通知：

- 渠道低优先级，`setSilent(true)`、`setOnlyAlertOnce(true)`、`setOngoing(true)`。
- 文本：`"正在后台静默扫描 · 已扫描 3,847 项 · 2023年11月"`。
- 有 `estimatedTotal` 时使用确定进度，否则不确定进度。
- 完成 → `setOngoing(false)`，3 秒后 `cancel()`。
- 失败 → `setOngoing(false)`，允许点击重试。
- **退出登录时必须 `cancel()` 并 `cancelUniqueWork`**（否则清库过程中仍在扫描）。

##### [MODIFY] AndroidManifest.xml

新增权限：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`POST_NOTIFICATIONS`。声明 `MonthScanWorker` 所需的前台服务类型。

##### [MODIFY] AppContainer.kt

新增 `monthScanEngine`（单例 `lazy`）。

##### [MODIFY] AuthViewModel.logout()

在四阶段清洗之前，追加：取消 `MonthScanWorker` 的唯一工作 + 取消扫描通知。

---

#### 2.7 形态 B 预留（月 → 日）

**本阶段不实现**，仅保证演进成本可控：

- 所有月分组逻辑收敛在 `MonthGrid.kt` 一个文件内。
- 数据层无需改动：`assetDate` 已足够派生"日"，不需要新增列。
- 升级时唯一需要改的文件：`MonthGrid.kt` → 改为按日分段的 `DaySectionedGrid`，内部决策是"单行网格 + day header"还是"自研 `LazyLayout` sticky`"。
- `PhotoMonthSection` 的字段设计已预留 `photos: List<PhotoAssetEntity>`，形态 B 只需在组件内二次分组。

预计增量：**+1~1.5 天**，不影响数据层与扫描引擎。

---

### 文档

| 文件 | 变更 |
| --- | --- |
| [CHANGELOG_ISSUES.md](file:///Users/syh/iClouDroid/CHANGELOG_ISSUES.md) | 新增 ISS-027（P2-016 根治）、ISS-028（P2-015 闭环），更新顶部索引 |
| [list.md](file:///Users/syh/iClouDroid/list.md) | P2-015 / P2-016 迁出，归档指向新 ISS |
| [TODO.md](file:///Users/syh/iClouDroid/TODO.md) | 勾选对应项 |
| [AGENTS.md](file:///Users/syh/iClouDroid/AGENTS.md) | §6 更新 URL 刷新与锚点描述；新增"按月锚定"约定 |
| [docs/PITFALLS.md](file:///Users/syh/iClouDroid/docs/PITFALLS.md) | 新增"LazyVerticalGrid 嵌套 LazyColumn"避坑章节 |

---

## Verification Plan

### Automated Tests

**阶段一**

```
./scripts/build_aar.sh
go test ./...                    # 含新增 errors_test.go
gradle_build(":app:assembleDebug testDebugUnitTest")   # 含新增 CoreErrorTest
```

**阶段二**

```
gradle_build(":app:assembleDebug testDebugUnitTest")
```

新增单测清单：

| 测试 | 覆盖 |
| --- | --- |
| `YearMonthBucketingTest` | 本地时区边界（月初/月末/跨年）；`assetDate = 0` → `0` |
| `CoreErrorTest` | 8 种 kind + base64 假阳性 |
| `ScanProgressTest` | 进度百分比 `coerceIn` 上限；完成态判定 |
| `PhotoCountsParseTest` | DOM 正则解析（正常 / 缺失 / 千分位 / 异常返回） |

### Manual Verification

部署至 `emulator-5554` 与物理机（SM-F9760），按 [AGENTS.md §9](file:///Users/syh/iClouDroid/AGENTS.md) 的纪律执行：

| 分类 | 验证项 | 通过标准 |
| --- | --- | --- |
| 功能 | 边扫边看 | 冷启动后首屏很快有内容，底部持续填充 |
| 功能 | 月份分组 | 抽查 5 张照片，归属月份与本地时区一致 |
| 功能 | sticky header | 连续滑动，月份标题在顶部浮停、切换无抖动 |
| 功能 | 年份跳转 | 点击 2024 → 平滑滚动到 2024 首月 |
| 功能 | 月内上下箭头 | 逐月移动，胶囊文字同步 |
| **P2-016** | **连续滑到底 × 5 次** | `firstVisibleItemKey` 无跳变、`itemCount` 无塌陷、无视觉跳顶 |
| **P2-016** | **滑至第 2000 项** | 位置不丢 |
| 会话 | 冷启动 | `Rebuilding Go core session (force=true)` = **0 次** |
| 会话 | 连续滚动 3 页 | 重建 = **0 次** |
| 会话 | 普通重建后 | 无 `dropping continuationMarker` |
| 错误 | 401/421 恢复 | 触发重建 + 扫描重启，最终收敛 |
| 错误 | 429/超时（限速模拟） | **退避重试，不重建会话** |
| 扫描 | 前台 → 后台切换 | 通知出现、进度持续、回前台继续 |
| 扫描 | 后台完成 | 通知自动消失 |
| 扫描 | 退出登录时扫描中 | 通知立即消失、Worker 被取消 |
| 总数 | DOM 抓取成功 | 进度条为确定模式，分母接近网页端展示值 |
| 总数 | DOM 抓取失败（改文案模拟） | 静默降级为转圈 + 项数，无错误提示 |
| 性能 | 冷启动帧耗时 | `Choreographer: Skipped` ≤ 40 帧 |
| 性能 | 滚动流畅度 | 无卡顿；内存不随图库增长线性上升 |
| 迁移 | v3 → v4 | 安装旧版 → 升级，`photo_assets` 行数不变，`yearMonth` 回填完成 |
| 回归 | 详情页 / 视频 / 下载 | 正常（含 ISS-017 / ISS-023 场景） |
| 回归 | 换号清洗 | ISS-025 四阶段清洗完整，新增表一并清空 |

### 回滚策略

- 阶段一独立可回滚：Go 侧字段是**新增**的，旧 Kotlin 仍可解析（`errorCode` 缺失时回落到正则）。
- 阶段二不可与阶段一拆分回滚：一旦 UI 迁移完成，回退需整体 revert 该 PR。

---

## 工作量估算

| 阶段 | 任务 | 估时 |
| --- | --- | --- |
| 一 | Go 结构化错误 + 单测 | 0.5 天 |
| 一 | Kotlin 分类收敛 + 单测 | 0.5 天 |
| 一 | 实机回归 | 0.5 天 |
| 二 | Room v4 迁移 + 回填 | 0.5 天 |
| 二 | MonthScanEngine（续扫 / 重试 / 进度） | 1.5 天 |
| 二 | WebView DOM 总数抓取 | 0.5 天 |
| 二 | Compose UI（sticky / 嵌套网格 / 索引条 / 进度） | 2 天 |
| 二 | WorkManager + 通知 | 0.5 天 |
| 二 | 数据流迁移（移除 Paging 3） | 1 天 |
| 二 | 完整验证 + 文档 | 1.5 天 |
| **合计** | | **约 9 个工作日** |
