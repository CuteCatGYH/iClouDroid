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

## 2. 万级图库（10,000+ 照片/视频）的首屏性能与分页限制

**严禁全量拉取后再返回**

面对上万张照片和数千个视频的存量图库，CloudKit 的 `/records/query` 单次最多返回约 **200 条记录**。
注意：因为每张照片包含 `CPLAsset` 和 `CPLMaster` 两条记录，实际单次请求约对应 **100 张照片**。

**按需分页与倒序排列**

- 查询时按 `assetDate` 降序请求
- Go 层每拉取完一页（100 张）立刻返回给 Android 写入 `Room` 数据库
- 配合 `Paging 3` 实现"打开 App 3 秒内即可浏览最新照片，下拉滑动时继续触发后续游标拉取"

---

## 3. `downloadURL` 临时直链过期与 Coil 缓存失效问题

**问题本质**

`CPLMaster` 中返回的缩略图和原图 `downloadURL`（指向 `cvws.icloud-content.com.cn`）带有一次性签名时间戳，通常在 **1~2 小时后就会过期返回 `403 Forbidden`**。

**解决方案**

1. **自定义 Coil 缓存 Key**
   必须通过 `MemoryCache.Key(recordName)` 和 `diskCacheKey(recordName)` 将缓存主键绑定到永久不变的 `recordName` 上，而不是绑定到会变化的 `downloadURL` 上。

2. **按需刷新直链**
   当本地磁盘缓存未命中、且数据库中记录的 `downloadURL` 已超过 1 小时，由 Coil 的自定义 `Fetcher` 调用 Go 层的 `/records/lookup` 接口，传入单个或一批 `recordName` 实时换取最新的 `downloadURL`。

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
