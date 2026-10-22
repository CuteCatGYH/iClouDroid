# list.md — 未修复问题清单

> 本文件只保留**尚未修复**的问题与持续性工程注意事项。
>
> 已修复案例请见 [CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md)（ISS-001 ~ ISS-026）。
>
> 相关文档：[TODO.md](TODO.md)（路线图）、[CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md)（已修复归档）

---

## 索引

| 编号 | 标题 | 优先级 | 归属 | 状态 |
| --- | --- | --- | --- | --- |
| [P2-015](#p2-015) | Go 层缺结构化 `errorCode` / `httpStatus`（技术债） | 🟡 P2 | Go 核心层 | ⛔ 未修复 |
| [P2-016](#p2-016) | `RemoteMediator` 每次 APPEND 必 invalidate，导致深翻页跳顶 | 🔴 P0 | Android / Paging | ⚠️ 缓解态，待 UI 重构根治 |

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
| P2-017 `time.Now()` 排序键漂移 | [ISS-024](CHANGELOG_ISSUES.md#iss-024) |
| P2-018 退出登录不清洗数据层 | [ISS-025](CHANGELOG_ISSUES.md#iss-025) |
| P2-019 Cookie 顺序抖动与 URL 失效判定 | [ISS-026](CHANGELOG_ISSUES.md#iss-026) |

---

## P2-015：Go 层缺结构化错误字段（技术债）

**优先级**：🟡 P2
**归属**：`go-core/` 所有返回 JSON 的导出函数
**状态**：⛔ 未修复

### 问题

当前 Go 侧只返回 `errorMessage` 字符串，Kotlin 侧只能靠正则/字符串匹配猜测错误类型：

```kotlin
// PhotoRepository.isAuthError() —— 典型的无奈之举
Regex("""(HTTP|status|StatusCode)\s*[:=]?\s*(401|421)\b""").containsMatchIn(msg)
```

这是 [ISS-015](CHANGELOG_ISSUES.md#iss-015) 的遗留根源：早期实现用 `contains("401")`
全文匹配，被 base64 的 `continuationMarker` 误伤，导致会话反复重建。虽然已用结构化解析
+ 词边界止血，但**根本解法是让 Go 层直接给出错误码**。

### 修复方案

Go 侧结果结构统一增加两个字段：

```go
type PhotoPageResult struct {
    Success      bool   `json:"success"`
    ErrorMessage string `json:"errorMessage,omitempty"`
    ErrorCode    string `json:"errorCode,omitempty"`   // AUTH_EXPIRED / NOT_FOUND / RATE_LIMITED / UNKNOWN
    HttpStatus   int    `json:"httpStatus,omitempty"`  // 原始 HTTP 状态码
    // ...
}
```

Kotlin 侧即可简化为：

```kotlin
private fun isAuthError(jsonStr: String): Boolean = try {
    val json = JSONObject(jsonStr)
    json.optString("errorCode") == "AUTH_EXPIRED" ||
        json.optInt("httpStatus", 0) in listOf(401, 421)
} catch (e: Exception) {
    false
}
```

### 注意事项

- 改动涉及 `photos.go` 全部导出函数（`FetchPhotoPage` / `FetchChanges` /
  `RefreshAssetUrls` / `CheckPrimaryZone`），需同步更新 Kotlin 解析
- 建议搭配 P2-016 的 UI 重构一起做，避免二次返工

---

## P2-016：`RemoteMediator` 每次 APPEND 必 invalidate，导致深翻页跳顶

**优先级**：🔴 P0
**归属**：`feature/photos/data/PhotoRemoteMediator.kt` + `PhotoRepository` + `PhotosScreen`
**状态**：⚠️ 缓解态（参数调优已尽力），待 UI 重构时修复

### 最终结论（经四轮排查 + `GridDiag` 实测确认）

`RemoteMediator.load(APPEND)` 返回 `MediatorResult.Success` 后，**Paging 3 必定重建
`PagingSource`**（这是 Paging 的固有行为，不是 bug）。重建时 `LazyGridState` 的
`firstVisibleItemIndex` 在新 itemCount 下越界 → clamp 到 0 → **跳回顶部**。

深翻页（APPEND 次数多）时尤其明显，因为每次 APPEND 都触发一次全表刷新。

### 关键实测证据（`GridDiag` 日志）

```
before: firstVisible=412 itemCount=480
RemoteMediator APPEND -> upsertAll(30)
after:  firstVisible=0   itemCount=510    ← 位置归零
```

### 为什么调参不能根治

已尝试并排除的方向：

| 尝试 | 结果 |
| --- | --- |
| 调大 `pageSize` / `prefetchDistance` | 延长触发间隔，但深翻页仍会跳 |
| `enablePlaceholders = true` | 位置相对稳定，但会产生大量 null 占位闪白 |
| `LazyGridState.Saver` | index 本身失效，Saver 救不回来 |
| 把 `append` 指示器移出网格（ISS-019） | 已修复另一类跳顶，但对 invalidate 无效 |

### 已经做的缓解（保留）

1. **`append` 指示器移出 `LazyVerticalGrid`** —— 消除 itemCount ±1 抖动（ISS-019）
2. **`ORDER BY assetDate DESC, id ASC`** —— 加入稳定次级排序键，减少 Room 行序抖动
3. **`upsertAll` 保留原位** —— Room 的 `@Upsert` 对已存在主键做原地更新，不产生新行

### 为什么不建议现在重写

根治需要放弃 `RemoteMediator`，改为：

- **方案 A**：`PagingSource` 直连 Go 层（网络分页 + 内存缓存），放弃 Room 作为分页源
  → 需重写离线缓存策略，收益是彻底消除 invalidate
- **方案 B**：手动分页（自建 `LazyColumn` + 游标状态），完全绕开 Paging 3
  → 需自行实现加载态、错误重试、位置恢复

两者都是**数天量级的重构**，而当前问题只在"深翻页"场景出现，优先级应排在功能开发之后。

### 待办（等 UI 重构时一并处理）

- [ ] 决定采用方案 A 或 B
- [ ] 若选 A：设计 `PagingSource<Int, PhotoAssetEntity>` 的 Go 层直连实现
- [ ] 若选 B：设计滚动位置持久化（存 `recordName` 而非 index）
- [ ] 迁移后删除 `GridDiag` 调试代码

### 已排除的假设（避免后人重复踩坑）

| 假设 | 为何排除 |
| --- | --- |
| `append` 指示器导致 | 已通过 ISS-019 修复，问题仍在 |
| `time.Now()` 排序键漂移 | 已通过 ISS-024 修复，问题仍在 |
| 会话重建导致 | 实测期间 `Rebuilding Go core session` 为 0 次 |
| 图片解码失败导致 | 失败项与跳顶无时间相关性 |

### 配套待办：`append` 错误提示不应直接吐 URL

`PhotosScreen` 的 append 错误分支当前直接展示 `error.message`，可能把带签名的 URL
泄露到 UI。应改为按 `ImageLoadFailure` 分类给出友好文案（与 ISS-023 的处理方式一致）。

---

## 附：通用注意事项

### 1. 排查 401/403/410 问题的正确姿势

| 做法 | 评价 |
| --- | --- |
| ❌ `jsonStr.contains("401")` | 会被 base64 误伤，这是 ISS-015 的成因 |
| ❌ 只看 `fetchedAt` 时间差 | 时间是猜测，会话重建可能只隔 10 秒 |
| ✅ 解析 JSON 的 `errorMessage` / `errorCode` 字段 | 结构化、可靠 |
| ✅ 直接读 `HttpDataSource.InvalidResponseCodeException.responseCode` | 事实来源 |

### 2. 会话重建是"昂贵操作"

一次重建 = 一次 `/validate` 网络往返（1~2 秒）+ 全部已签发 URL 失效。

**任何触发重建的路径都必须极度保守** —— 宁可提示用户"网络错误，请重试"，
也不要贸然重建会话。

> 验证方法：拿 DB 里旧代际的 URL 直接 `curl`，若返回 `410` 说明确实失效，
> 重建是必要的；若返回 `200` 说明是误判。

### 3. Room + Paging 3 的隐式失效

`PhotoDao.upsertAll()` 写入会触发 Room `InvalidationTracker` →
`PagingSource.invalidate()` → 列表重建。这是 Paging 3 的正常行为，
但**当它发生得很频繁时用户就能感知到列表跳位**（参见 P2-016）。

### 4. 高位深图片的兼容性

Apple 的 HDR 截图/照片可能是 16-bit PNG 或 10-bit HEVC（`heix`）。
Android `BitmapFactory` 与 `ImageDecoder` 都无法解码，必须降级到服务端转码的
8-bit JPEG 中间图。详见 [ISS-023](CHANGELOG_ISSUES.md#iss-023)。

### 5. Go 层排序键必须稳定

任何在数据获取阶段生成的"当前时间"都会让同一实体在不同次查询中得到不同排序，
表现为列表项无序漂移。缺失时间戳时应使用中性值（如 `0`）。详见
[ISS-024](CHANGELOG_ISSUES.md#iss-024)。
