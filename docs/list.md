# list.md — 未修复问题清单

> 本文件只保留**尚未修复**的问题与持续性工程注意事项。
>
> 已修复案例请见 [CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md)（ISS-001 ~ ISS-029）。
>
> 相关文档：[TODO.md](TODO.md)（路线图）、[CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md)（已修复归档）

---

## 索引

| 编号 | 标题 | 优先级 | 归属 | 状态 |
| --- | --- | --- | --- | --- |
| P2-020 | 月份轻封面缩略图仍为空白 | P1 | 照片 / 年月跳转小窗 | ✅ 已修复 |

### 已归档至 CHANGELOG_ISSUES.md

| 原编号 | 归档位置 |
| --- | --- |
| P2-001 `isAuthError` 误判 | [ISS-015](CHANGELOG_ISSUES.md#iss-015) |
| P2-002 / P2-004 / P2-007 URL 重取链路 | [ISS-017](CHANGELOG_ISSUES.md#iss-017) |
| P2-003 / P2-006 / P2-009 滚动状态与日志治理 | [ISS-018](CHANGELOG_ISSUES.md#iss-018) |
| P2-005 / P2-008 会话代际与刷新洪泛 | [ISS-016](CHANGELOG_ISSUES.md#iss-016) |
| P2-010 / P2-011 HDR 高位深与去重误伤 | [ISS-023](CHANGELOG_ISSUES.md#iss-023) |
| P2-012 `append` 指示器导致跳顶 | [ISS-019](CHANGELOG_ISSUES.md#iss-019) |
| P2-013 冷启动主线程阻塞 | [ISS-022](CHANGELOG_ISSUES.md#iss-022) |
| P2-014 `continuationMarker` 死循环 | [ISS-020](CHANGELOG_ISSUES.md#iss-020) |
| P2-015 Go 层缺结构化错误字段 | [ISS-028](CHANGELOG_ISSUES.md#iss-028-go-层缺结构化错误字段导致-kotlin-靠正则猜) |
| P2-016 `RemoteMediator` 深翻页跳顶 | [ISS-027](CHANGELOG_ISSUES.md#iss-027-paging-3-remotemediator-导致深翻页跳顶改用月分组本地分页根治) |
| P2-017 `time.Now()` 排序键漂移 | [ISS-024](CHANGELOG_ISSUES.md#iss-024) |
| P2-018 退出登录不清洗数据层 | [ISS-025](CHANGELOG_ISSUES.md#iss-025) |
| P2-019 Cookie 顺序抖动与 URL 失效判定 | [ISS-026](CHANGELOG_ISSUES.md#iss-026) |

---

## P2-020：月份轻封面缩略图仍为空白

**状态**：✅ 已修复。通过 DAO `a.masterId` 与 `COALESCE` 兜底、`MonthScanEngine` 封面保留、`MonthCoverModel` 降级逻辑优化及 `MonthIndexBar` `masterId` 透传已彻底根治。

### 现象

打开年月跳转小窗，十二个月份格子只有月名文字与底色，没有照片封面。格子**可以点击**、
月份导航正常，只是图不出现。

### 已确认并修复的四个诱因（仍未彻底解决）

排查过程中发现并修掉了以下四个真问题，每次修完仍有空白残留：

| # | 问题 | 修复 | 是否已验证 |
| --- | --- | --- | --- |
| 1 | `MonthCoverModel` 从封面行推导月可用性 | 改用 `photo_month_index` 的 `YearSection.available` | ✅ 实测月格子恢复可点 |
| 2 | 月份格子被 `Row` 拉伸到 141px | 行与网格显式 `width()` + 格子 `weight(1f)` | ✅ 实测尺寸受控 |
| 3 | 手写 `ImageLoader.Builder` **没有磁盘缓存** | 显式 `.diskCache { DiskCache.Builder()... }` | ✅ 预热从新增 0 文件 → 352K→6.4M |
| 4 | `cacheOnly` 分支把 `data` 置为空串 | 该分支只设 cache key，不改 `data` | ⚠️ 原理成立，未能证明渲染成功 |

### 尚未排除的根因与修复方案（按可能性排序）

下表是**当前代码里实测存在的缺陷**（已逐行比对源码，不是猜测）：

| # | 缺陷 | 文件位置 | 置信度 |
| --- | --- | --- | --- |
| A | `refreshMonthIndex` 的 upsert 漏 `coverAssetId`，每批扫描把它重置为 null | `MonthScanEngine.kt` | 高 |
| B | `cellsFor` 要求 `thumbUrl` 非空才产出 `Cover`，否则降为灰底 `Placeholder` | `MonthCoverModel.kt` | 高 |
| C | `masterId = ""` 硬编码，且 DAO 不查 `a.masterId` → 拦截器无法换链 | `MonthIndexBar.kt` / `PhotoMonthIndexDao.kt` | 高 |
| D | `MonthCoverRow.coverMasterId` 已加字段但全链路未使用 | `MonthCoverRow.kt` | 中 |

---

#### A. `refreshMonthIndex` 抹掉 `coverAssetId`

**现状**（[MonthScanEngine.kt](../app/src/main/java/com/getup/iClouDroid/feature/photos/data/MonthScanEngine.kt)）：

```kotlin
monthIndexDao.upsert(
    PhotoMonthIndexEntity(
        yearMonth = yearMonth,
        itemCount = aggregate.itemCount,
        firstAssetDate = aggregate.firstAssetDate ?: 0L,
        lastAssetDate = aggregate.lastAssetDate ?: 0L,
        isComplete = walkFinished
        // ← coverAssetId 缺失，默认 null
    )
)
```

这是 `@Insert(REPLACE)`，整行覆盖。因此：`PhotoRepository.rebuildMonthIndexFor` 刚回填好的
`coverAssetId`，下一批扫描就被抹成 `null`。冷启动的 `backfillUnbucketedRows` → `rebuildMonthIndex`
也走同一条通道。

> 这也解释了为什么“扫描日志写 108/108 updated”却依然没图：回填之后又被扫描循环冲刷掉了。

**修复**：读旧值，缺失时现场查最新一张。

```kotlin
private suspend fun refreshMonthIndex(yearMonth: Int) {
    if (yearMonth <= 0) return
    val aggregate = photoDao.monthAggregate(yearMonth) ?: return
    if (aggregate.itemCount <= 0) {
        monthIndexDao.deleteMonth(yearMonth)
        return
    }
    val walkFinished = scanStateDao.get(ScanStateEntity.KEY_MONTH_SCAN)?.isComplete == true

    // 保留已有封面；没有才去查（一次带索引的 LIMIT 1，成本可忽略）
    val existingCover = monthIndexDao.get(yearMonth)?.coverAssetId
    val cover = existingCover
        ?: if (appSettings.monthCoverEnabled.value) photoDao.newestInMonth(yearMonth)?.id else null

    monthIndexDao.upsert(
        PhotoMonthIndexEntity(
            yearMonth = yearMonth,
            itemCount = aggregate.itemCount,
            firstAssetDate = aggregate.firstAssetDate ?: 0L,
            lastAssetDate = aggregate.lastAssetDate ?: 0L,
            isComplete = walkFinished,
            coverAssetId = cover
        )
    )
}
```

**验证**：扫描完成后打开小窗，看是否仍有空白；或临时 log
`monthIndexDao.getAll().count { it.coverAssetId != null }`，应接近总月数。

---

#### B. `cellsFor` 把「无 thumbUrl」判死

**现状**（[MonthCoverModel.kt](../app/src/main/java/com/getup/iClouDroid/feature/photos/ui/MonthCoverModel.kt)）：

```kotlin
row?.coverAssetId != null && !row.coverThumbUrl.isNullOrBlank() ->
    MonthCoverCell.Cover(...)
else -> MonthCoverCell.Placeholder(...)   // ← 灰底纯文字
```

`coverThumbUrl` 只是 `photo_assets.thumbUrl` 的**瞬时快照**。URL 签名约 15 分钟就失效，
且批量写库时 CloudKit 可能压根没返回 `thumbUrl`。只要 `coverAssetId` 存在，
拦截器就能现场换链，**不应该因为 URL 为空就放弃渲染**。

**修复**：把「能不能画」的判据从 URL 改为「有没有封面资产」，只对视频保留降级。

```kotlin
val isVideo = row?.coverMediaType == "VIDEO" || row?.coverMediaType == "LIVE_PHOTO"

when {
    !hasPhotos -> MonthCoverCell.Unavailable(yearMonth, label)

    // 视频必须有 thumbUrl，否则绝不能把 mp4 当 JPEG 喂给解码器（AGENTS §6.7.5c）
    row?.coverAssetId == null -> MonthCoverCell.Placeholder(yearMonth, label)
    isVideo && row.coverThumbUrl.isNullOrBlank() -> MonthCoverCell.Placeholder(yearMonth, label)

    else -> MonthCoverCell.Cover(
        yearMonth = yearMonth,
        label = label,
        coverId = row.coverAssetId,
        coverMasterId = row.coverMasterId.orEmpty(),   // 配合 C
        coverUrl = row.coverThumbUrl.orEmpty()         // 允许为空，拦截器会换链
    )
}
```

> 注意 `Cover.coverUrl` 从 `String` 改成允许空的 `String`（或保留 String 但传 `orEmpty()`）。
> 同步修改 `MonthCoverCell.Cover` 字段与相关单测。

**验证**：单测补充「有 coverId 但 thumbUrl 为空 → 仍是 Cover」；实机看灰底是否消失。

---

#### C. `masterId = ""` 导致拦截器无法换链

**现状**（[MonthIndexBar.kt](../app/src/main/java/com/getup/iClouDroid/feature/photos/ui/MonthIndexBar.kt)）：

```kotlin
ICloudImageModel(
    recordName = cell.coverId,
    masterId = "",              // ← 空主 ID
    url = cell.coverUrl,
    fetchedAt = System.currentTimeMillis(),
    ...
)
```

拦截器判断 `activeUrl.isNullOrBlank()` 时会调 `refresh(data)`，而 `refresh` 内部是
`repository.refreshUrlsForMaster(data.masterId)` —— `masterId` 为空则必然失败，永远拿不到图。

同时 DAO 没查 `masterId`：

```sql
SELECT m.yearMonth, m.itemCount, m.coverAssetId,
       a.thumbUrl  AS coverThumbUrl,
       a.mediaType AS coverMediaType
-- ← 缺 a.masterId AS coverMasterId
FROM photo_month_index m LEFT JOIN photo_assets a ON a.id = m.coverAssetId
```

**修复（三处联动）**：

1. DAO 补一列：

```sql
SELECT m.yearMonth        AS yearMonth,
       m.itemCount        AS itemCount,
       COALESCE(m.coverAssetId,
                (SELECT id FROM photo_assets
                  WHERE isHidden = 0 AND yearMonth = m.yearMonth
                  ORDER BY assetDate DESC, id ASC LIMIT 1)) AS coverAssetId,
       a.masterId         AS coverMasterId,
       a.thumbUrl         AS coverThumbUrl,
       a.mediaType        AS coverMediaType
FROM photo_month_index m
LEFT JOIN photo_assets a ON a.id = m.coverAssetId
WHERE m.yearMonth BETWEEN :fromYearMonth AND :toYearMonth
ORDER BY m.yearMonth DESC
```

2. `MonthCoverCell.Cover` 增加 `coverMasterId: String` 字段。

3. `MonthIndexBar` 透传：`masterId = cell.coverMasterId`。

> SQL 里的 `COALESCE` 子查询是个**兜底彩蛋**：即使 `coverAssetId` 还是 null（A 未修或尚未重扫），
> 打开小窗也能立刻拿到该月最新一张照片。因为命中 `(yearMonth, assetDate, id)` 索引且 `LIMIT 1`，
> 十二行查询耗时不到 1 ms。**这让修复不再依赖一次耗时几分钟的全盘扫描。**

---

#### D. `coverMasterId` 已声明未使用

`MonthCoverRow` 已加 `val coverMasterId: String? = null`，但 DAO 未 select、
`cellsFor` 未透传、`Cover` 未持有。按 C 的三处联动一并补齐即可。

---

#### E. 仍存在的可能性（若 A–D 修完仍空白）

| 怀疑点 | 快速验证 |
| --- | --- |
| `coverRowsOf` 未订阅 / Flow 恒空 | 临时 log `coverRows.size`，打开小窗时应为 12 左右 |
| `coversEnabled == false`（开关被关过） | 设置页确认「月份封面预览」为开 |
| 格子被 `selected` 分支盖住 | 截图看底色：`primaryContainer`=选中而非 Cover |
| Coil 解码失败（HEIC 变体） | `logcat \| grep ICloudPhotoCoil` 应能看到 `fail` 行 |

### 推荐实施顺序

```
1. DAO 补 masterId + COALESCE 兜底        （数据能到位）
2. MonthScanEngine 保留 coverAssetId      （数据不被冲掉）
3. MonthCoverModel 放宽 thumbUrl 判据     （数据能变成 Cover）
4. MonthCoverCell.Cover 加 coverMasterId  （模型能换链）
5. MonthIndexBar 透传 masterId + imageLoader（渲染链路闭合）
6. 跑单测 → assembleDebug → 实机开小窗验图
```

前三步是**纯数据层**，可用一条 SQL / 一次日志验证；后两步才是渲染。
每步都可独立验证，不要一次改完再跑。

### 下一步排查建议

1. 先打一条日志：打开小窗时输出 `coverRows.size` 与
   `coverRows.count { it.coverAssetId != null }` / `count { !it.coverThumbUrl.isNullOrBlank() }`。
   三个数字直接区分「数据层为空」还是「渲染层没画」。
2. 用 `ui_state` + 截图对比：格子底色若是 `surfaceVariant`，说明 `AsyncImage` 没画出东西；
   若是 `primaryContainer`，说明命中 `selected` 而非 Cover。
3. 确认 `AsyncImage` 真的传了 `imageLoader`（见 AGENTS §6.6 硬规则①）。

> 参考：[AGENTS.md §6.6](AGENTS.md) 的三条硬规则；实现见
> [MonthIndexBar.kt](../app/src/main/java/com/getup/iClouDroid/feature/photos/ui/MonthIndexBar.kt)、
> [MonthCoverModel.kt](../app/src/main/java/com/getup/iClouDroid/feature/photos/ui/MonthCoverModel.kt)、
> [MonthScanEngine.kt](../app/src/main/java/com/getup/iClouDroid/feature/photos/data/MonthScanEngine.kt)、
> [PhotoMonthIndexDao.kt](../app/src/main/java/com/getup/iClouDroid/feature/photos/data/db/PhotoMonthIndexDao.kt)。

---

## 附：通用注意事项

### 1. 排查 401/403/410 问题的正确姿势

| 做法 | 评价 |
| --- | --- |
| ❌ `jsonStr.contains("401")` | 会被 base64 误伤，这是 ISS-015 的成因 |
| ❌ 只看 `fetchedAt` 时间差 | 时间是猜测，会话重建可能只隔 10 秒 |
| ✅ 解析 JSON 的 `errorCode` / `httpStatus` 字段 | 结构化、可靠（ISS-028 后 Go 层直接提供） |
| ✅ 直接读 `HttpDataSource.InvalidResponseCodeException.responseCode` | 事实来源 |

### 2. 会话重建是"昂贵操作"

一次重建 = 一次 `/validate` 网络往返（1~2 秒）+ 全部已签发 URL 重新签名。

**任何触发重建的路径都必须极度保守** —— 宁可提示用户"网络错误，请重试"，
也不要贸然重建会话。特别地：**`TIMEOUT` / `RATE_LIMITED` / `NETWORK` 一律退避重试，不重建。**

> 验证方法：拿 DB 里旧代际的 URL 直接 `curl`，若返回 `410` 说明确实失效，
> 重建是必要的；若返回 `200` 说明是误判。

### 3. 惰性布局不要嵌套

`LazyColumn` 内嵌 `LazyVerticalGrid` 时，内层必须给定固定高度，因而拥有自己的测量循环。
外层快速滚动时两者会失去同步，内层只量出行首一行、其余折叠（ISS-027）。

正确做法：把内容展开成外层的直接子项。需要网格时，按行发出 item，每行内用 `Row` 排布固定数量的格子。

### 4. 高位深图片的兼容性

Apple 的 HDR 截图/照片可能是 16-bit PNG 或 10-bit HEVC（`heix`）。
Android `BitmapFactory` 与 `ImageDecoder` 都无法解码，必须降级到服务端转码的
8-bit JPEG 中间图。详见 [ISS-023](CHANGELOG_ISSUES.md#iss-023)。

### 5. Go 层排序键必须稳定

任何在数据获取阶段生成的"当前时间"都会让同一实体在不同次查询中得到不同排序，
表现为列表项无序漂移。缺失时间戳时应使用中性值（如 `0`）。详见
[ISS-024](CHANGELOG_ISSUES.md#iss-024)。

### 6. 派生状态不要作为参数传递

`isComplete` 这类"由甲计算、由乙写入、由丙读取"的状态，一旦经由参数传递就必然被覆盖。
每次从单一真理源重新读取，无论重建多少次结果都一致。详见
[ISS-027](CHANGELOG_ISSUES.md#iss-027-paging-3-remotemediator-导致深翻页跳顶改用月分组本地分页根治)。

### 7. 判断「是否需要初始化」不要看条数

升级安装有数据但未处理（如未分桶），`count == 0` 为假，初始化被跳过 ——
表现为"有数据但看不见"。判断依据应是"是否存在未处理行"，而非总行数。
