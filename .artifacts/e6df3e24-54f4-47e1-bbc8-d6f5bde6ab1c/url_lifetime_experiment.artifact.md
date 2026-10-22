# iCloud CDN 直链生命周期实测报告

> 实验目的：为 P2-019（会话代际 bump 过于激进）确定正确的失效判定规则。
> 设备：SM-F9760（物理机，国区账号 `dsid=20118126497`）
> 方法：从 Room DB 导出 `thumbUrl`，用 `curl -r 0-1023` 直接请求，记录 HTTP 状态码。

---

## 实验一：会话重建是否使旧 URL 失效？

**这是 AGENTS.md §4.4 / §5.4 / §6 的核心假设** — 文档反复强调「会话重建即失效」。

| 阶段 | 代际 | HTTP 状态 |
| --- | --- | --- |
| 重建前 | gen=6 | `206` `206` `206` |
| 触发一次 Go 会话重建（bump → gen=7） | gen=7 | `206` `206` `206` |

> [!IMPORTANT]
> **假设被证伪。** 会话重建后旧 URL **完全有效**。
> 旧 URL 的有效性与会话代际**无关**。

---

## 实验二：时间是否是唯一失效因素？

从同一批 URL 生成起持续探测：

| URL 年龄 | HTTP 状态 | 判定 |
| --- | --- | --- |
| 340s (5.7m) | `206` `206` | 有效 |
| 521s (8.7m) | `206` `206` | 有效 |
| 701s (11.7m) | `206` `206` | 有效 |
| 882s (14.7m) | `206` `206` | 有效 |
| **1065s (17.75m)** | **`410` `410`** | **失效** |
| 1245s (20.75m) | `410` `410` | 失效 |
| 1426s (23.8m) | `410` `410` | 失效（不恢复） |

另一批更早的 URL（gen=0，年龄 15 分钟）在首次探测时即 `410`。

> [!IMPORTANT]
> **失效拐点在 882s ~ 1065s 之间，即约 15 ~ 17.75 分钟。**
> 与 AGENTS.md 所称「1~2 小时」相差 **4~8 倍**。

---

## 实验三：`/validate` 握手的 Cookie 变化

连续 3 次强制会话重建，用新增的 `Cookie diff` 诊断日志观察：

```
Cookie diff: added=[] removed=[] valueChanged=[X-APPLE-WEBAUTH-TOKEN, X-APPLE-WEBAUTH-VALIDATE]
Cookie diff: added=[] removed=[] valueChanged=[X-APPLE-WEBAUTH-TOKEN, X-APPLE-WEBAUTH-VALIDATE]
Cookie diff: added=[] removed=[] valueChanged=[X-APPLE-WEBAUTH-TOKEN, X-APPLE-WEBAUTH-VALIDATE]
```

3/3 一致。

| Cookie | 每次 validate 是否轮换 | 说明 |
| --- | --- | --- |
| `X-APPLE-WEBAUTH-VALIDATE` | ✅ 每次都换 | validate 握手专用一次性令牌 |
| `X-APPLE-WEBAUTH-TOKEN` | ✅ 每次都换 | 但**不影响已签发 URL 的有效性**（见实验一） |
| 其余 16 项 | ❌ 不变 | — |

---

## 结论与影响

### 1. P2-019 本体（顺序抖动）✅ 已修复

Go `BuildCookieHeader()` 改用排序后的 key 拼接，Kotlin 侧改为 `Set<Pair>` 比较。
实测加/去排序的对照实验：去掉 `sort.Strings` 后同一 Cookie 集合两次调用顺序即不同，测试捕获。

### 2. 「会话重建 → URL 失效」是错误假设 ⚠️ 需要修正文档与代码

当前代码三处基于这个错误假设：

| 位置 | 逻辑 | 实际问题 |
| --- | --- | --- |
| `markSessionReady(bumpGeneration)` | 凭证变化就 bump | 每次 validate 都变 → 每次真重建都全库标脏 |
| `ICloudPhotoInterceptor`: `generationStale` | 代际不符就强制刷新 | 多数情况是**无谓的刷新** |
| `URL_MAX_AGE_MS = 45min` | 45 分钟后认为过期 | 真实失效是 **~15 分钟**，中间 30 分钟是盲区 |

**后果**：代际 bump 造成大量无谓 `/records/lookup`（P2-008 刷新洪泛的真正来源），
而真正的过期（15 分钟）反而没有主动检测。

### 3. 建议修复方向

**a. 后移 `URL_MAX_AGE_MS` 到实测值以下留安全余量**

```kotlin
// 实测 882s 有效 / 1065s 失效 → 取 12 分钟作为主动刷新阈值
private const val URL_MAX_AGE_MS = 12 * 60 * 1000L
```

**b. `bumpSessionGeneration` 不再由 Cookie 变化触发**

只保留两条 bump 路径：
- `force = true`（明确的强制重建）
- 收到 401/421 且重建成功（真凭证失效）

Cookie 值变化（validate 的一次性令牌）**不足以证明 URL 失效** —— 实验一已证。

**c. 代际仍保留，但降级为"提示"而非"强制刷新"**

保留 `sessionGeneration` 列用于换账号场景检测（P2-018 需要），
但在 Coil 拦截器里，`generationStale` 不再单独作为强制刷新条件，
改为「代际不符 **且** 年龄超阈值」才刷新。

---

## 复现脚本

```bash
# 导出 URL
adb -s <serial> exec-out run-as com.getup.iClouDroid cat databases/icloud_photos.db > db
adb -s <serial> exec-out run-as com.getup.iClouDroid cat databases/icloud_photos.db-wal > db-wal
sqlite3 db "SELECT thumbUrl FROM photo_assets WHERE thumbUrl IS NOT NULL LIMIT 3;" > urls

# 探测（-r 0-1023 避免下载完整文件）
while read -r u; do
  curl -s -o /dev/null -w "%{http_code}\n" --max-time 20 -r 0-1023 "$u"
done < urls
```

> [!NOTE]
> 必须用 GET + `Range` 头。Apple CDN 对 `HEAD` 请求返回 `501`。
