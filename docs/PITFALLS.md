# 关键工程避坑指南 (必读注意事项)

> 本文档收录 iClouDroid 开发中**必须提前知道**的陷阱。踩过的具体案例见 [CHANGELOG_ISSUES.md](../CHANGELOG_ISSUES.md)。
>
> 返回 [TODO.md](../TODO.md)

---

## 1. 国区（云上贵州）与国际区的严格隔离

**域名与端点**

| 区域 | 登录入口 |
| --- | --- |
| 国区 | `https://setup.icloud.com.cn` |
| 国际区 | `https://setup.icloud.com` |

**强制 Header 校验**

向苹果发送的**所有** API 请求，必须严格携带匹配的 `Origin` 和 `Referer`：

| 区域 | Origin | Referer |
| --- | --- | --- |
| 国区 | `https://www.icloud.com.cn` | `https://www.icloud.com.cn/` |
| 国际区 | `https://www.icloud.com` | `https://www.icloud.com/` |

> [!WARNING]
> 若漏传或混用 `.com` 与 `.com.cn`，苹果网关会直接返回 `421 Misdirected Request` 或 `400 Bad Request`。

---

## 2. 万级图库（10,000+ 照片/视频）的首屏性能与扫描策略

**严禁全量拉取后阻塞等待**

面对上万张照片和数千个视频的存量图库，CloudKit 的 `/records/query` 单次最多返回约 **200 条记录**。
注意：因为每张照片包含 `CPLAsset` 和 `CPLMaster` 两条记录，实际单次请求约对应 **100 张照片**。

**全库月扫描 + 边扫边看**

- 查询时按 `assetDate` 降序请求，使用 `continuationMarker` 游标推进
- 每拉取完一批就写入 `Room` 并重建对应月份的 `photo_month_index`，UI 立即可以浏览
- 游标持久化在 `scan_state`，中断后可断点续扫（下次启动不会重头开始）
- 切到后台由 `MonthScanWorker` 接管，通知栏显示常驻进度

> [!CAUTION]
> **不要用 Paging 3 的 `RemoteMediator` 做这个。** 它在每次 APPEND 成功后无条件
> invalidate `PagingSource`，重载窗口 = `initialLoadSize`，超出窗口的滚动位置被 clamp，
> 表现为深翻页跳回顶部。实测塔陷点恒等于 `initialLoadSize`（60→60、360→360、1200→1200），
> **无参数可解**。详见 [ISS-027](../CHANGELOG_ISSUES.md#iss-027-paging-3-remotemediator-导致深翻页跳顶改用月分组本地分页根治)。

**滚动锚点用月份，不用全局索引**

月分组后每个月是「一个标题 + N 行」，每月行数不同。任何「每月固定 N 项」的索引换算都会错位，
必须从 item key（`h_202608` / `r_202608_3`）解析月份。

---

## 3. `downloadURL` 临时直链过期与 Coil 缓存失效问题

> 本节已有**实测数据**，详见 [ISS-026](../CHANGELOG_ISSUES.md#iss-026) 与
> [url_lifetime_experiment.md](../.artifacts/url_lifetime_experiment.md)。

**问题本质（已实测，推翻旧说法）**

| 旧文档说法 | 实测结果 |
| --- | --- |
| 有效期 **1~2 小时** | ❌ 实际 **~15~18 分钟**（882s 仍 200，1065s 变 410） |
| **会话重建即失效** | ❌ 会话重建后旧 URL 仍返回 200 |

所以：**URL 寿命只由时间决定，与会话代际无关。** CDN 签名绑定的是 record。

**解决方案**

1. **自定义 Coil 缓存 Key**
   缓存主键绑定永久不变的 `recordName`，**绝不要**绑会变的 `downloadURL`：
   `icloud_{recordName}_{quality}`。

2. **按年龄主动换链**
   `URL_MAX_AGE_MS = 12 分钟`（在实测寿命内留余量）。超过就主动调
   `/records/lookup` 换新链，**不要等它变成 410**。

3. **不要用代际作为刷新依据**
   代际只能当「可能换了账号」的提示信号，不能当「URL 已死」的证据。

> [!CAUTION]
> **这类“有效期多久”的结论必须用 curl 实测，不要相信文档（包括本文）。**
> 验证方法：从 DB 取一条 URL → `curl -s -o /dev/null -w '%{http_code}' -r 0-1023 '<url>'`。
> 必须用 GET + `Range`，Apple CDN 对 `HEAD` 请求返回 `501`。

---

## 4. `gomobile` 跨语言调用 (JNI) 的限制与内存红线

**类型限制**

`gomobile bind` 导出的函数签名仅支持基础类型（`string`、`int64`、`bool`、`[]byte`）以及简单的 Go `struct` / `interface`。

> 复杂的数据结构（如照片分页列表）建议在 Go 层统一序列化为 **JSON 字符串（`string`）** 跨边界传递给 Kotlin，在 Kotlin 层用 `kotlinx.serialization` 解析，这样稳定性最高且易于调试。

**无回调函数支持**

`gomobile` **不支持**将 Go 函数指针/闭包传给 Kotlin。需要"推送"的场景（如 Token 静默刷新）只能：
- 轮询（新增 `PollUpdatedCookies()` 之类的导出函数）
- 或每次调用后返回最新状态串

**严禁使用 `[]byte` 传递大文件**

> [!CAUTION]
> 在上传或下载几十 MB 的原图或数 GB 的 4K 视频时，**绝对不能**将文件读入 `[]byte` 跨 JNI 传递，否则会瞬间触发 Android 的 `OutOfMemoryError`。
>
> 正确做法：**Kotlin 只把本地文件的绝对路径（`filePath: String`）或通过 FD 传给 Go 层，由 Go 层直接在本地文件系统中流式读写**。

---

## 5. 高级数据保护 (ADP) 与 2FA 过期保活

**ADP 网页访问开关**

如果账号开启了端到端加密的"高级数据保护"，必须在 iPhone/iPad/Mac 的「设置 → Apple ID → iCloud」中确保开启了 **"在网页上访问 iCloud 数据"**，否则所有 Web API 均会返回权限拒绝。

**2FA 信任期上限**

苹果服务端的 `X-APPLE-WEBAUTH-HSA-TRUST` 有效期硬上限为 **30~60 天**。

当后台同步捕获到 `421` 且无法通过 `trustToken` 刷新会话时，App 需：
1. 自动挂起 `WorkManager` 队列
2. 弹出高优先级系统通知："iCloud 授权已到期，请点击重新完成双重认证"

---

## 6. WebView → Go 的 Cookie 传递陷阱

> 详细案例见 [ISS-001](../CHANGELOG_ISSUES.md#iss-001-cookie-值双引号未剥离导致-go-静默丢弃全部凭证)

**双引号剥离**

`CookieManager.getCookie()` 返回的 Cookie 值可能被双引号包裹（`X-APPLE-WEBAUTH-TOKEN="v=2:..."`）。
Go 的 `net/http` 遇到非法字节会**静默丢弃**该 Cookie（只打印一行警告，不返回 error），导致请求变成空 Cookie。

```go
value := strings.Trim(strings.TrimSpace(kv[1]), `"`)  // 必须
```

**大小写不敏感**

实测 WebView 返回 `X-APPLE-WEBAUTH-PCS-Cloudkit`（小写 k），而设计文档写作 `PCS-CloudKit`。所有名称匹配必须大小写不敏感。

**跨子域注入**

`cookiejar` 默认使用 `golang.org/x/net/publicsuffix`，会把 `com.cn` 视为公共后缀，**拒绝**将 Cookie 设置到父域。且 CloudKit 节点（`pXX-ckdatabasews.icloud.com.cn`）与对象存储（`cvws.icloud-content.com.cn`）属于不同子域。

> 本项目解法：放弃 `cookiejar` 自动匹配，`Session` 内部维护 `CookieMap`，每次请求显式注入 `Cookie` Header。

---

## 7. 状态管理的竞态陷阱

> 详细案例见 [ISS-005](../CHANGELOG_ISSUES.md#iss-005-authviewmodel-区域取值三处推断不一致) / [ISS-006](../CHANGELOG_ISSUES.md#iss-006-登录成功后-icloudwebview-重复触发-initsession)

**单一真理源**

同一份业务状态存在多个副本（UI State / Repository / VM 字段）时，必须指定唯一真理源，其余副本只能作为只读投影，禁止参与决策。

**同步哨兵位 > 异步状态机**

「状态机守卫」（`if (state is Authenticating) return`）在异步场景下不可靠 —— 状态变更发生在协程中，而回调可能在状态更新前批量到达。

> 这类场景必须用**同步的布尔哨兵位**（主线程执行，无竞态）而非 Flow/State 做幂等控制。

**Go 结构体零值陷阱**

Go 结构体的零值（`false` / `""` / `0`）与"业务合法值"无法区分。只要结果结构里带标识字段，**每个 return 分支都必须显式赋值**，或统一走构造函数。

---

## 8. 退出登录（异步清理）的写回陷阱

> 详细案例见 [ISS-025](../CHANGELOG_ISSUES.md#iss-025)

退出登录不是一个瞬间动作，而是一段**异步清理窗口**。在这段窗口里，系统的其他部分仍在正常运行，
并且会把手里的旧状态重新写回去。已实测的两条写回路径：

| 写回路径 | 触发时机 | 后果 |
| --- | --- | --- |
| `PhotoRemoteMediator.load()` → `upsertAll()` | 飞行中的 APPEND 在 `clearAll()` 之后落库 | 旧账号照片行被重新写回 |
| `ICloudWebView` Cookie 探针 → `onCookiesExtracted()` → `saveSession()` | `removeAllCookies` 完成前读到旧 Cookie | 会话被原地重建，`shared_prefs` 保留凭证 |

**为什么 `AuthUiState` 挡不住**

`logout()` 第一件事就是把状态置为 `NotLoggedIn`，而探针回调里的守卫写的是
`if (state is Authenticating || state is LoggedIn) return` —— 于是 `NotLoggedIn` **恰好放行**了回调。

```
38.725 W/PhotosViewModel: checkZoneReady failed: 正在退出登录…   ← Repo 守卫拦住
38.857 I/ICloudAuth: Three-element cookie validation PASSED     ← 状态守卫放行
38.859 I/ICloudAuth: Calling Go InitSession
```

**正确做法**

1. 用 `@Volatile` 布尔位，在 `logout()` **第一行**置位，早于任何清理
2. 所有可能写回数据的异步回调入口都检查该位
3. 释放时机在“清理完成 + 页面重载完成”之后（而非 `clearAll()` 返回就释放）

```kotlin
@Volatile private var isLoggingOut = false

fun logout() {
    isLoggingOut = true                    // 第一行
    viewModelScope.launch {
        repository.beginWipe()
        try { /* 四阶段清理 */ }
        finally {
            repository.endWipe()
            delay(800)                     // 等登录页重载
            isLoggingOut = false
        }
    }
}

fun onCookiesExtracted(r: CookieValidationResult) {
    if (isLoggingOut) return               // 关键
    ...
}
```

> [!CAUTION]
> **新增任何「退出登录时需要清理的状态」，都要反问一次：它会不会被异步回调写回？**
> 只清不拦，等于没清。

**`removeAllCookies` 是异步的**

传 `null` 只是放弃回调，**并不代表已同步完成**。紧接着 `flush()` 有可能把删除前的 jar 落盘，
导致进程重启后旧 Cookie 仍然存在。必须把回调桥接成挂起点：

```kotlin
suspendCancellableCoroutine<Unit> { cont ->
    cookieManager.removeAllCookies { if (cont.isActive) cont.resume(Unit) }
}
cookieManager.flush()
```

---

## 9. Go map 迭代顺序是随机的

> 详细案例见 [ISS-026](../CHANGELOG_ISSUES.md#iss-026)

Go 语言**故意**打乱 `map` 的迭代顺序（防止依赖顺序的实现）。因此任何
「遍历 map 拼出字符串 → 拿去做相等比较 / 持久化 / 下发给其他语言」的代码都是定时炸弹。

本项目踩到的真实形态：

```go
// ❌ 同一份 Cookie 集合，每次调用得到不同的字符串
for k, v := range s.CookieMap {
    b.WriteString(k); b.WriteString("="); b.WriteString(v)
}
```

Kotlin 侧拿这个字符串与持久化的比较，于是 **每次冷启动都被判定为“凭证已轮转”**。

```go
// ✅ 排序后再拼，结果确定
keys := make([]string, 0, len(s.CookieMap))
for k := range s.CookieMap { keys = append(keys, k) }
sort.Strings(keys)
```

> [!WARNING]
> 跨语言边界的字符串必须**保证确定性**。除了 map 顺序，还要提防浮点格式化、时间格式、
> JSON 字段顺序（`encoding/json` 对 struct 是稳定的，对 map 不是）。

**附带教训：短期令牌 ≠ 凭证轮转**

即使顺序稳定了，也不要把「任何 Cookie 值变化」当作「凭证轮转」。苹果每次 `/validate`
都轮换 `X-APPLE-WEBAUTH-TOKEN` / `X-APPLE-WEBAUTH-VALIDATE`，但这既不影响已签发 URL，
也不影响任何业务能力。判定凭证轮转只能基于**会产生后果的**字段变化。

---

## 10. 惰性布局嵌套（LazyColumn 内嵌 LazyVerticalGrid）

> 详细案例见 [ISS-027](../CHANGELOG_ISSUES.md#iss-027-paging-3-remotemediator-导致深翻页跳顶改用月分组本地分页根治)

画廊最初把每个月做成一个嵌套的 `LazyVerticalGrid`，放在外层 `LazyColumn` 里。
为了让它能被测量，必须给它一个显式高度：

```kotlin
// ❌ 内层惰性布局拥有自己的测量循环
LazyColumn {
    items(months) { month ->
        LazyVerticalGrid(
            modifier = Modifier.height(estimateHeight(month.itemCount, columns)),
            userScrollEnabled = false
        ) { items(month.photos) { ... } }
    }
}
```

正常滚动时看不出问题。**快速 fling 之后**内层网格只量出了行首一行、其余折叠，
表现为「月份标题出现了，但内容不加载，只有最上面一个月有图」。

原因是：两个惰性布局各自有独立的测量/回收循环，外层在 fling 时以极快速度重组，
内层的测量结果来不及跟上，最终提交的是不完整的布局。

```kotlin
// ✅ 内容展开为外层的直接子项，虚拟化只发生在一个地方
LazyColumn {
    monthIndex.forEach { month ->
        stickyHeader(key = "h_${month.yearMonth}") { MonthHeader(month) }
        items(count = rowsFor(month.itemCount, columns), key = { "r_${month.yearMonth}_$it" }) { row ->
            MonthRow(photos = monthPhotos[month.yearMonth].orEmpty(), row = row, columns = columns)
        }
    }
}
```

`MonthRow` 内部用 `Row` + `Modifier.weight(1f)` 均分宽度，
`PhotoGridItem` 自带的 `aspectRatio(1f)` 保证单元格为正方形。

> [!CAUTION]
> **不要在 `LazyColumn` / `LazyRow` 内嵌套任何 `Lazy*Grid`。**
> 需要网格时按行发出 item，用 `Row` 排布固定数量的格子。
>
> 另外：`stickyHeader` 与 `items` 的 key 必须能反推出所属分组（如 `h_202608` / `r_202608_3`）。
> 滚动锚点、导航跳转都依赖这一点 —— 不能再用「每月固定 N 项」的假设去算索引。

**验证技巧**：用截图体积作客观判据。空白/异常状态截图通常 < 600KB，
正常渲染（含图片）则 > 1.5MB。比肉眼比对比对更可靠。

---

## 11. 派生状态不要作为参数传递

> 详细案例见 [ISS-027](../CHANGELOG_ISSUES.md#iss-027-paging-3-remotemediator-导致深翻页跳顶改用月分组本地分页根治)

`photo_month_index.isComplete` 表示「全库扫描是否已走完，该月不会再增长」。
最初的写法是由调用方算出这个值并传进重建函数：

```kotlin
// ❌ 三个地方各自写 isComplete，互相覆盖
private suspend fun refreshMonthIndex(yearMonth: Int, isComplete: Boolean)

// 扫描中每批次：写 false
for (ym in touched) refreshMonthIndex(ym, isComplete = false)
// 扫描结束：写 true
monthIndexDao.upsertAll(months.map { it.copy(isComplete = true) })
// 冷启动重建：又写回 false
for (ym in all) refreshMonthIndex(ym, isComplete = false)
```

后果：扫描已经完成并且报过「扫描完成，共 15936 项」，但**每个月标题仍然显示「· 扫描中」**。

```kotlin
// ✅ 每次从单一真理源读取，与调用者无关
private suspend fun refreshMonthIndex(yearMonth: Int) {
    val walkFinished = scanStateDao.get(ScanStateEntity.KEY_MONTH_SCAN)?.isComplete == true
    monthIndexDao.upsert(PhotoMonthIndexEntity(..., isComplete = walkFinished))
}
```

> [!IMPORTANT]
> **「派生状态」只能有一个写入路径。** 只要它经由参数传递，就必然会在某个调用点被覆盖。
> 让读取方每次都从真理源取，无论重建多少次结果都一致。

---

## 12. 判断「是否需要初始化」不要看条数

> 详细案例见 [ISS-027](../CHANGELOG_ISSUES.md#iss-027-paging-3-remotemediator-导致深翻页跳顶改用月分组本地分页根治)

启动时自动扫描的触发条件原本是「本地照片数为 0」：

```kotlin
// ❌ 升级安装有行但未分桶，count != 0，初始化被跳过
repository.totalCountFlow.collect { count ->
    if (count == 0 && !hasAutoStarted) { hasAutoStarted = true; startInitialScan() }
}
```

从 v3 升级到 v4 时，`photo_assets` 有 164 行、`yearMonth` 全为 0、`photo_month_index` 为空。
条件为假 → 扫描不启动 → 转年索引永远为空 → 界面显示「已加载 164 项」但主体是「暂无照片」。

**正确做法**：判断「是否存在未处理行」（`countUnbucketed() > 0`），而不是总行数。

> [!WARNING]
> 任何「本地数量为 0 就去拉取」的初始化条件，在 schema 升级、部分失败、
> 账号切换等场景下都会失效。应该判断「数据是否已达到可用状态」，而非「是否存在」。
