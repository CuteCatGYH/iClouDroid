78+/98+
*/fiu# CHANGELOG_ISSUES.md — 已修复问题案例档案

> **用途**：沉淀 iClouDroid 开发过程中**实测复现 → 定位根因 → 修复验证**的完整问题案例，供后续 Phase 遇到相似故障时快速比对，避免重复踩坑。
>
> **记录格式**：现象 → 日志证据 → 根因 → 修复方案 → 验证结果。
>
> **相关文档**：[TODO.md](TODO.md)（待办与路线图）、[AGENTS.md](AGENTS.md)（Agent 开发规范）、[README.md](../README.md)（项目说明）

---

## 索引

| 编号 | 标题 | 优先级 | 归属 | 状态 |
| --- | --- | --- | --- | --- |
| [ISS-001](#iss-001-cookie-值双引号未剥离导致-go-静默丢弃全部凭证) | Cookie 值双引号未剥离导致 Go 静默丢弃全部凭证 | 🔴 P0 | Go 核心层 | ✅ 已修复 |
| [ISS-002](#iss-002-accountlogin-调用顺序错误返回-400-missing-apple_id-field) | `accountLogin` 调用顺序错误返回 400 | 🔴 P0 | Go 核心层 | ✅ 已修复 |
| [ISS-003](#iss-003-sessioninitresultischina-错误分支未赋值) | `SessionInitResult.IsChina` 错误分支未赋值 | 🟠 P1 | Go 核心层 | ✅ 已修复 |
| [ISS-004](#iss-004-cookie-跨子域下发规则缺失) | Cookie 跨子域下发规则缺失 | 🟠 P1 | Go 核心层 | ✅ 已修复 |
| [ISS-005](#iss-005-authviewmodel-区域取值三处推断不一致) | `AuthViewModel` 区域取值三处推断不一致 | 🟠 P1 | Android 层 | ✅ 已修复 |
| [ISS-006](#iss-006-登录成功后-icloudwebview-重复触发-initsession) | 登录成功后 `ICloudWebView` 重复触发 `InitSession` | 🟡 P2 | Android 层 | ✅ 已修复 |
| [ISS-007](#iss-007-accountlogin-补救路径落地与会话过期规范) | `accountLogin` 补救路径落地与会话过期规范 | 🔴 P0 | Go 核心层 | ✅ 已修复 |
| [ISS-008](#iss-008-token-静默刷新机制与跨语言凭证同步) | Token 静默刷新机制与跨语言凭证同步 | 🟠 P1 | Go / Android 跨语言 | ✅ 已修复 |
| [ISS-009](#iss-009-无-apple-id-回归能力与自动化测试补齐) | 无 Apple ID 回归能力与自动化测试补齐 | 🟡 P2 | 测试基建 | ✅ 已完成 |
| [ISS-010](#iss-010-冷启动主线程读取加密存储导致界面跳帧) | 冷启动主线程读取加密存储导致界面跳帧 | 🟡 P2 | Android 层 | ✅ 已修复 |
| [ISS-011](#iss-011-webview-控制台跨域与预加载噪音未拦截) | WebView 控制台跨域与预加载噪音未拦截 | 🟢 P3 | Android 层 | ✅ 已优化 |
| [ISS-012](#iss-012-冷启动恢复跳过-go-会话重建导致照片接口全局报-no-active-session) | 冷启动恢复跳过 Go 会话重建 | 🔴 P0 | Android / 跨语言 | ✅ 已修复 |
| [ISS-013](#iss-013-isautherror-误判原始-json-字符串导致每次分页触发重复鉴权) | `isAuthError` 误判原始 JSON 字符串导致每次分页触发重复鉴权 | 🔴 P1 | Android 层 | ✅ 已修复 |
| [ISS-014](#iss-014-直链失效捕获重试与-clientid-游标关联断开) | 临时直链失效捕获重试与 ClientID 游标关联断开 | 🔴 P1 | Go 核心层 & Coil Interceptor | ✅ 已修复 |
| [ISS-015](#iss-015-isautherror-字符串全文匹配误判导致会话反复重建与列表跳位) | `isAuthError` 字符串全文匹配误判导致会话反复重建与列表跳位 | 🔴 P0 | Android / Repository | ✅ 已修复 |
| [ISS-016](#iss-016-会话代际机制与-url-刷新洪泛治理) | 会话代际（sessionGeneration）机制与 URL 刷新洪泛治理 | 🟠 P1 | Android / Session + Coil | ✅ 已修复 |
| [ISS-017](#iss-017-视频与图片详情页遇-403410-不会自动重取链接) | 视频与图片详情页遇 403/410 不会自动重取链接 | 🔴 P1 | Android / UI | ✅ 已修复 |
| [ISS-018](#iss-018-网格滚动位置丢失与日志刷屏) | 网格滚动位置丢失与日志刷屏 | 🟡 P2 | Android / UI + 日志 | ✅ 已修复 |
| [ISS-019](#iss-019-append-指示器作为网格项导致列表跳回顶部) | `append` 指示器作为网格项导致列表跳回顶部 | 🔴 P0 | Android / UI | ✅ 已修复 |
| [ISS-020](#iss-020-continuationmarker-失效后仍被重放引发-401--重建死循环) | `continuationMarker` 失效后仍被重放，引发 401 → 重建死循环 | 🔴 P0 | Android / Repository | ✅ 已修复 |
| [ISS-021](#iss-021-remotemediator-每次-append-必-invalidate导致深翻页跳顶) | `RemoteMediator` 每次 APPEND 必 invalidate，导致深翻页跳顶 | 🔴 P0 | Android / Paging | ✅ 已修复（ISS-027 根治） |
| [ISS-022](#iss-022-冷启动时-login-webview-被提前构造阻塞主线程-33-秒) | 冷启动时 Login WebView 被提前构造，阻塞主线程 ~3.3 秒 | 🟠 P1 | Android / 启动 | ✅ 已修复 |
| [ISS-023](#iss-023-hdr-高位深原图无法解码--解码失败误触发-url-刷新) | HDR 高位深原图无法解码 + 解码失败误触发 URL 刷新 | 🟠 P1 | Android / Coil + Repository | ✅ 已修复 |
| [ISS-024](#iss-024-go-层用-timenow-作排序键兜底导致照片位置持续漂移) | Go 层用 `time.Now()` 作排序键兜底，导致照片位置持续漂移 | 🔴 P0 | Go 核心层 | ✅ 已修复 |
| [ISS-025](#iss-025-退出登录不清洗照片库与图片缓存导致换区换号后残留脏数据) | 退出登录不清洗照片库与图片缓存，导致换区/换号后残留脏数据 | 🟠 P1 | Android / Session + Storage | ✅ 已修复 |
| [ISS-026](#iss-026-cookie-序列化顺序抖动与会话重建即失效的错误假设) | Cookie 序列化顺序抖动与「会话重建即失效」的错误假设 | 🟠 P1 | Go / Android 跨语言 | ✅ 已修复 |
| [ISS-027](#iss-027-paging-3-remotemediator-导致深翻页跳顶改用月分组本地分页根治) | Paging 3 `RemoteMediator` 导致深翻页跳顶，改用月分组本地分页根治 | 🔴 P0 | Android / UI + Paging | ✅ 已修复 |
| [ISS-028](#iss-028-go-层缺结构化错误字段导致-kotlin-靠正则猜) | Go 层缺结构化错误字段，导致 Kotlin 靠正则猜 | 🟡 P2 | Go / Android 跨语言 | ✅ 已修复 |
| [ISS-029](#iss-029-initsession-未将握手结果回写至-session导致进程内调用拼出空-host) | `InitSession` 未将握手结果回写至 `Session`，导致进程内调用拼出空 host | 🟠 P1 | Go 核心层 | ✅ 已修复 |
| [ISS-030](#iss-030-已删除照片永不消失且最近删除照片被当成正常照片入库) | 已删除照片永不消失，且「最近删除」照片被当成正常照片入库 | 🔴 P0 | Go / Android 跨语言 | ✅ 已修复 |
| [ISS-031](#iss-031-全库扫描只增不减已永久删除的照片永远残留) | 全库扫描只增不减，已永久删除的照片永远残留 | 🔴 P0 | Android / Repository + Scan | ✅ 已修复 |
| [ISS-032](#iss-032-设置页侧边返回手势直接退出应用) | 设置页侧边返回手势直接退出应用，且无预测返回效果 | 🟠 P1 | Android / UI | ✅ 已修复 |
| [ISS-033](#iss-033相册-ui-重构与日月视图monet-动态取色) | 相册 UI 重构，引入日/月视图、Monet 动态取色、本地相册管理 | 🟡 P2 | Android / UI | ✅ 已完成 |
| [ISS-034](#iss-034photopicker-云端媒体提供方不被系统识别) | PhotoPicker 云端媒体提供方不被系统识别（白名单 + Shizuku 一键激活） | 🟠 P1 | Android / Provider | ✅ 已修复 |
| [ISS-035](#iss-035photopicker-缩略图空白多层柹障式修复与已知残留) | **PhotoPicker 缩略图空白：多层柹障式修复与已知残留**（含桹障十二：视频被错报为图片）| 🔴 P0 | Android / Provider | ⚠️ 已修 12 层，待实机复测 |

---

## ISS-001：Cookie 值双引号未剥离导致 Go 静默丢弃全部凭证

**优先级**：🔴 P0（阻塞 Phase 1 全部后续工作）
**归属**：Go 核心层 — `../go-core/session.go` → `NewSession()`
**发现时间**：Phase 1 首次真机联调

### 现象

Android 侧三要素 Cookie 校验 **完全通过**（`isValid=true`），但 Go 层请求 Apple `/accountLogin` 直接返回 `400`。整个过程 Go 没有抛出任何显式异常，属于**静默数据丢失**。

### 日志证据

```
I/ICloudAuth: Three-element cookie validation PASSED, handing over to Go core.
I/ICloudAuth: Calling Go InitSession (isChinaMainland=true, hasHsaTrust=true)
I/GoLog: net/http: invalid byte '"' in Cookie.Value; dropping invalid bytes
I/GoLog: net/http: invalid byte '"' in Cookie.Value; dropping invalid bytes
...（连续 16 次）
I/ICloudAuth: Go InitSession returned: {"success":false,"errorMessage":"accountLogin failed with status 400: {\"success\":false,\"error\":\"Missing apple_id field\"}","isChina":false}
```

### 根因

`android.webkit.CookieManager.getCookie()` 返回的 Cookie 字符串形如：

```
X-APPLE-WEBAUTH-TOKEN="v=2:AAAA..."; X-APPLE-WEBAUTH-HSA-TRUST="v=2:BBBB..."; ...
```

**值被双引号包裹**。而 `net/http` 的 `Cookie.Valid()` 会拒绝含非法字节（`"`）的 `Cookie.Value`，并在发送请求时**静默丢弃**该 Cookie，仅打印一行警告。

原 `NewSession` 的解析逻辑只做了 `strings.TrimSpace()`：

```go
// ❌ 修复前
kv := strings.SplitN(part, "=", 2)
if len(kv) == 2 {
    cookies = append(cookies, &http.Cookie{
        Name:   strings.TrimSpace(kv[0]),
        Value:  strings.TrimSpace(kv[1]),  // 引号残留 → net/http 丢弃
        Domain: domain,
        Path:   "/",
    })
}
```

丢弃数量恰好 16 项，与三要素 + 各 PCS 凭证总数吻合 —— **发往 Apple 的请求实际上是个空 Cookie，等同于未登录**。

### 修复方案

```go
// ✅ 修复后
kv := strings.SplitN(part, "=", 2)
if len(kv) != 2 {
    continue
}
name := strings.TrimSpace(kv[0])
if name == "" || strings.HasPrefix(name, "$") { // 跳过 $Version / $Path / $Domain 等 RFC2965 属性
    continue
}
value := strings.Trim(strings.TrimSpace(kv[1]), `"`) // 关键：剥离双引号
```

配套改动：`Session` 结构新增 `CookieMap map[string]string`，在解析阶段一次性落盘，供 `NewRequest` 显式注入（见 [ISS-004](#iss-004-cookie-跨子域下发规则缺失)）。

### 验证结果

| 验证项 | 修复前 | 修复后 |
| --- | --- | --- |
| `invalid byte '"' in Cookie.Value` | ❌ 16 次 | ✅ **完全归零** |
| Cookie 是否真正送达 Apple | ❌ 全量丢弃 | ✅ 生效 |

### 经验教训

> [!WARNING]
> **Go `net/http` 对非法 Cookie 是「静默丢弃 + 单行警告」，不会返回 error。**
> 任何跨语言（WebView → Go）传递 Cookie 的场景，必须显式剥离双引号，并在解析后**打印解析数量统计**，否则此类故障永远不可见。

---

## ISS-002：`accountLogin` 调用顺序错误返回 400 Missing apple_id field

**优先级**：🔴 P0（阻塞 Phase 1 动态路由解析）
**归属**：Go 核心层 — `../go-core/account.go` → `AccountLogin()`
**发现时间**：Phase 1 首次真机联调（与 ISS-001 同时暴露，ISS-001 修复后依然存在）

### 现象

Cookie 已正确送达后，`POST /setup/ws/1/accountLogin` 仍返回：

```json
{"success":false,"error":"Missing apple_id field"}
```

HTTP Status `400`。

### 根因

**协议理解偏差**。`accountLogin` 是「用 Apple ID + 密码换 WebAuth Token」的接口，必须携带 `accountName` 字段。而本项目的实际场景是：

> 用户已在 WebView 内完成 SRP + 2FA 全流程，**Token 已经在 Cookie 里了**。
> 此时需要做的是「用现有 Cookie 校验会话有效性并拉取路由表」，而不是再登录一次。

正确接口是 `POST /setup/ws/1/validate`，它才是「携带 Cookie 换取 `dsInfo` + `webservices`」的握手接口。

### 修复方案

```go
// ✅ 修复后：改用 /validate 做会话握手
func (s *Session) ValidateSession() (*SessionInitResult, error) {
    q := url.Values{}
    q.Set("clientBuildNumber", "2422Hotfix23")
    q.Set("clientMasteringNumber", "2422Hotfix23")
    q.Set("clientId", uuid.New().String())
    q.Set("clientVersion", "2.1")

    validateURL := fmt.Sprintf("%s/setup/ws/1/validate?%s", s.BaseURL, q.Encode())
    req, err := s.NewRequest(http.MethodPost, validateURL, "{}")
    // ...
    // 从响应直接取 dsInfo.dsid / webservices["ckdatabasews"].url / pcsRequired
}
```

`accountLogin` 降级保留，仅作为「`validate` 失败且 `X-APPLE-WEBAUTH-HSA-TRUST` 存在」时的补救路径，且 body 必须含：

```json
{"accountName":"<email>","dsWebAuthToken":"<token>","extended_login":true}
```

### 验证结果

```json
{"success":true,"dsid":"20118126497","ckDatabaseUrl":"https://p203-ckdatabasews.icloud.com.cn:443","isChina":true}
```

- ✅ `success: true`
- ✅ `dsid: 20118126497`
- ✅ `ckDatabaseUrl` 解析出 `p203` 节点，域名后缀 `.icloud.com.cn` 与国区一致

### 参考实现

- `icloudpd` → `src/pyicloud_ipd/base.py`（`_authenticate` 与 `validate` 流程）
- `rclone` → `backend/iclouddrive/api/session.go`（`Authenticate` + `tap()`）

---

## ISS-003：`SessionInitResult.IsChina` 错误分支未赋值

**优先级**：🟠 P1（数据正确性，不阻塞主流程）
**归属**：Go 核心层 — `../go-core/core.go` → `InitSession()`
**发现时间**：Phase 1 首次真机联调

### 现象

Kotlin 侧明确传入 `isChinaMainland = true`（日志已确认），但 Go 返回的 JSON 中：

```json
{"success":false,"errorMessage":"...","isChina":false}
```

`isChina` 恒为 `false`。

### 根因

`InitSession` 有多个**提前返回**的失败分支，构造 `SessionInitResult` 时只填了 `Success` 与 `ErrorMessage`，遗漏 `IsChina`：

```go
// ❌ 修复前
session, err := NewSession(cookieStr, isChinaMainland)
if err != nil {
    res, _ := json.Marshal(SessionInitResult{
        Success:      false,
        ErrorMessage: err.Error(),
        // IsChina 缺失 → 零值 false
    })
    return string(res)
}
```

### 修复方案

所有失败分支统一透传区域标识：

```go
// ✅ 修复后
res, _ := json.Marshal(SessionInitResult{
    Success:      false,
    ErrorMessage: err.Error(),
    IsChina:      isChinaMainland, // 单点透传，避免多处遗漏
})
```

建议进一步收敛为统一的 `failResult(isChina, msg)` 辅助函数，杜绝同类遗漏。

### 验证结果

修复后成功分支返回 `"isChina":true`，与传入值一致。

### 经验教训

> [!TIP]
> Go 结构体的零值（`false` / `""` / `0`）与「业务合法值」无法区分。
> 只要结果结构里带标识字段，**每个 return 分支都必须显式赋值**，或统一走构造函数。

---

## ISS-004：Cookie 跨子域下发规则缺失

**优先级**：🟠 P1（Phase 1 不阻塞，但 **Phase 2 必然踩到**）
**归属**：Go 核心层 — `../go-core/session.go`
**发现时间**：Phase 1 代码审查 + 实测验证

### 现象

`/validate` 成功后拿到 `ckDatabaseUrl = https://p203-ckdatabasews.icloud.com.cn:443`。但如果继续用 `http.Client{Jar: jar}` 的自动 Cookie 匹配机制，打到该地址时会**没有 Cookie**，服务端必然返回 `401 / 421`。

### 根因

两层问题叠加：

1. **`cookiejar` 只绑定了初始域名**
   原实现 `jar.SetCookies(u, cookies)` 中 `u` 是 `setup.icloud.com.cn`，`Domain` 设为 `.icloud.com.cn`。而 CloudKit 实际节点是 `p203-ckdatabasews.icloud.com.cn`、对象存储是 `cvws.icloud-content.com.cn` —— 后者甚至**不属于** `icloud.com.cn` 父域。

2. **`golang.org/x/net/publicsuffix` 把 `com.cn` 视为公共后缀**
   `cookiejar` 默认使用此列表，会**拒绝**将 Cookie 设置到 `com.cn` 的父域，导致 `.icloud.com.cn` 的域规则失效。

### 修复方案

放弃 `cookiejar` 自动匹配，改为 `Session` 内部维护 `CookieMap` 并**每次请求显式注入**：

```go
// ✅ 修复后
type Session struct {
    // ...
    CookieMap map[string]string // 原始凭证，脱离 jar 的域限制
}

func (s *Session) NewRequest(method, reqURL, body string) (*http.Request, error) {
    // ...
    // 全量注入 Cookie Header，不依赖目标 host 的域匹配
    var parts []string
    for k, v := range s.CookieMap {
        parts = append(parts, k+"="+v)
    }
    if len(parts) > 0 {
        req.Header.Set("Cookie", strings.Join(parts, "; "))
    }
    return req, nil
}
```

**备选方案**（若坚持使用 jar）：实现自定义 `cookiejar.PublicSuffixList`，把 `icloud.com.cn`、`icloud.com`、`icloud-content.com.cn` 全部纳入私有后缀白名单。

### 待验证

> [!IMPORTANT]
> 本修复的**代码已落地**，但 Phase 1 的 `/validate` 走的是 `setup` 域，尚未真正触发跨子域场景。
> **真正的验证点在 Phase 2 的 `zones/list` 请求** —— 这是检验本方案是否生效的唯一早期信号。

### 额外注意

- `:443` 显式端口会让 host 匹配变成 `p203-ckdatabasews.icloud.com.cn:443`，与 `.icloud.com.cn` 不等价，jar 方案必须额外处理。
- `Origin` / `Referer` 必须与区域严格匹配（国区 `.com.cn`，国际区 `.com`），混用会触发 `421 Misdirected Request`，与 Cookie 问题是独立的第二个雷区。

---

## ISS-005：`AuthViewModel` 区域取值三处推断不一致

**优先级**：🟠 P1（存在写错区域的风险）
**归属**：Android 层 — `../app/src/main/java/com/getup/iClouDroid/ui/auth/AuthViewModel.kt`
**发现时间**：Phase 1 代码审查

### 现象

用户在「国际区」完成登录后，本地持久化可能被写成「国区」，导致下次冷启动时请求错误的 `setup.icloud.com.cn`。

### 根因

`onCookiesExtracted` 中区域来源分散在三个位置，且时序不同步：

```kotlin
// ❌ 修复前
val isChina = when (val state = _uiState.value) {
    is AuthUiState.NotLoggedIn -> state.isChinaMainland   // ① 来自 UI 状态
    is AuthUiState.Error -> state.isChinaMainland         // ② 来自错误态快照
    else -> sessionManager.isChinaMainland()              // ③ 来自持久化
}
```

`Authentication` / `LoggedIn` 状态下会走 ③，而 ① ② 可能是**旧快照** —— 用户点击切换区域后，`_uiState` 与 `sessionManager` 之间存在短暂窗口期不一致。

### 修复方案

统一以 `SessionManager` 为**单一真理源（Single Source of Truth）**：

```kotlin
// ✅ 修复后
val isChina = sessionManager.isChinaMainland()
```

`setRegion()` 已经负责同步写入 `sessionManager`，所有读取路径统一从此处取值，消除状态推断分歧。

### 验证结果

- ✅ 冷启动后区域正确恢复为「中国大陆 (云上贵州)」
- ✅ `InitSession` 入参 `isChinaMainland=true` 与 UI 显示一致

### 经验教训

> [!TIP]
> 同一份业务状态存在多个副本（UI State / Repository / VM 字段）时，必须指定唯一真理源。
> 其余副本只能作为**只读投影**，禁止参与决策。

---

## ISS-006：登录成功后 `ICloudWebView` 重复触发 `InitSession`

**优先级**：🟡 P2（性能 + 日志噪音，不阻塞功能）
**归属**：Android 层 — `../app/src/main/java/com/getup/iClouDroid/ui/auth/ICloudWebView.kt`
**发现时间**：Phase 1 首次真机联调

### 现象

同一套 Cookie 集，`InitSession` 被连续调用 5 次：

```
I/ICloudAuth: checkCookies source=onPageStarted
I/ICloudAuth: Three-element cookie validation PASSED, handing over to Go core.
I/ICloudAuth: Calling Go InitSession (isChinaMainland=true, hasHsaTrust=true)
I/ICloudAuth: Go InitSession returned: {...}
I/ICloudAuth: checkCookies source=onPageFinished
I/ICloudAuth: Three-element cookie validation PASSED, handing over to Go core.
I/ICloudAuth: Calling Go InitSession (isChinaMainland=true, hasHsaTrust=true)
...（共 5 轮）
```

### 根因

`WebViewClient` 的 `onPageStarted` / `onPageFinished` / `shouldOverrideUrlLoading` 三个回调都会执行 `checkCookies()`。iCloud 主页加载伴随多次重定向，每次回调都判定三要素齐全 → 每次都触发 `onCookiesExtracted` → 每次都调 Go。

`AuthViewModel` 里的守卫条件 `_uiState.value is Authenticating` 无法拦住 —— 因为 Go 调用在协程里，第一次调用还没把状态改成 `Authenticating` 时，第二、三次回调已经进来了（**竞态**）。

### 修复方案

在 `ICloudWebView` 内部引入 `hasSubmitted` 哨兵位，一旦提交成功立即置位：

```kotlin
// ✅ 修复后
var hasSubmitted by remember { mutableStateOf(false) }

private fun checkCookies(source: String, pageUrl: String?) {
    if (hasSubmitted) return                     // 关键：提交后彻底停止
    if (pageUrl == null) return
    val validation = CookieValidationResult.validate(cookieManager.getCookie(pageUrl))
    if (validation.isValid) {
        hasSubmitted = true                      // 置位，后续回调全部短路
        latestOnExtracted(validation)
    } else {
        latestOnDiagnostic(validation)
    }
}
```

配套：切换区域时重置 `hasSubmitted`；`AuthUiState.Error` 的重试按钮直接切回 `NotLoggedIn` 重新拉起 WebView。

### 验证结果

- ✅ 提交后不再重复触发，`Calling Go InitSession` 只出现一次
- ✅ 冷启动走 `SessionManager` 分支，完全绕过 WebView

### 经验教训

> [!WARNING]
> 「状态机守卫」在异步场景下不可靠 —— 状态变更发生在协程中，而回调可能在状态更新前批量到达。
> 这类场景必须用**同步的布尔哨兵位**（主线程执行，无竞态）而非 Flow/State 做幂等控制。

---

## ISS-007：`accountLogin` 补救路径落地与会话过期规范

**优先级**：🔴 P0（保障会话过期后的自愈与错误状态规范）
**归属**：Go 核心层 — `../go-core/account.go` → `TryAccountLoginRemediation()`
**发现时间**：Phase 1 遗留项梳理

### 现象

在原方案中，当主路径 `/setup/ws/1/validate` 握手成功时可以正常提取路由表；但当长效运行后 `X-APPLE-WEBAUTH-TOKEN` 短期凭证过期时（服务端返回 HTTP 401 或 421），缺乏自愈与重试补救分支，直接抛错导致 App 鉴权中断。

### 根因

缺少对会话失效状态的降级处理机制。Apple 在 `validate` 返回 401/421 时，若客户端持有未过期的 `X-APPLE-WEBAUTH-HSA-TRUST`（有效期 30~60 天），可以通过携带 `trustToken` 的 `POST /setup/ws/1/accountLogin` 尝试静默换取新 Token。

### 修复方案

在 `account.go` 的 `ValidateSession()` 中增加自愈逻辑：

```go
// ✅ 修复后
if resp.StatusCode == 421 || resp.StatusCode == 401 {
    remedyResult, remedyErr := s.TryAccountLoginRemediation()
    if remedyErr == nil && remedyResult != nil {
        return remedyResult, nil
    }
    return nil, fmt.Errorf("AUTH_EXPIRED: session validation failed (HTTP %d). HSA-TRUST renewal failed: %v", resp.StatusCode, remedyErr)
}
```

并在 `TryAccountLoginRemediation()` 中构造包含 `trustToken` 与 `extended_login: true` 的登录请求体，请求成功后通过 `ExtractAndSaveCookies` 提取并更新新凭证。

### 验证结果

- ✅ 正常 `validate` 成功直通。
- ✅ 触发 401/421 且持有 HSA-TRUST 时走降级补救分支；补救失败时输出带有 `AUTH_EXPIRED` 规范标记的错误日志，供 Android 层统一挂起后台任务并提示重新登录。

---

## ISS-008：Token 静默刷新机制与跨语言凭证同步

**优先级**：🟠 P1（防止 Token 续期后 Android 端未同步落盘）
**归属**：Go / Android 跨语言交互 — `../go-core/session.go` & `AuthViewModel.kt`
**发现时间**：Phase 1 遗留项梳理

### 现象

当 Apple 服务端在响应中通过 `Set-Cookie` 动态更新或轮转 `X-APPLE-WEBAUTH-TOKEN` 等凭证时，Go 内存中的凭证已更新，但 Android 端的 `EncryptedSharedPreferences` 仍保存旧 Cookie。应用重启后依然读取旧 Cookie 发起请求。

### 根因

`gomobile` 跨 JNI 边界对复杂异步回调支持有限，缺少从 Go 到 Kotlin 的凭证同步机制。

### 修复方案

1. **Go 核心层**：在 `Session` 增加 `UpdatedCookies` 标记与 `ExtractAndSaveCookies(resp)`，当且仅当收到有效新 Cookie 时置位；导出 `HasCookieUpdates()` 和 `GetCurrentCookieString()`。
2. **Android 层**：在 `AuthViewModel.initGoSession()` 完成初始化后，异步检查 `Icloudcore.hasCookieUpdates()`，若有新 Cookie 则自动写入 `SessionManager` 持久化存储。

### 验证结果

- ✅ Go 核心层提供状态轮询与凭证输出；
- ✅ JNI 编译生成的 `classes.jar` 导出对应静态方法；
- ✅ 续期凭证即时落盘。

---

## ISS-009：无 Apple ID 回归能力与自动化测试补齐

**优先级**：🟡 P2（工程质量与回归测试保障）
**归属**：测试基建 — `../go-core/session_test.go` & `CookieValidationResultTest.kt`
**发现时间**：Phase 1 遗留项梳理

### 现象

先前缺乏单元测试，代码改动后无法在脱机或无 Apple ID 凭据的环境下快速校验核心逻辑是否发生回退。

### 修复方案

1. **Go 核心层**：新增 `session_test.go`，测试 `NewSession` Cookie 解析（双引号剥离、RFC 属性项过滤）、全量 Cookie 注入格式、以及 `GetSessionStateSummary()` 诊断输出。
2. **Android 侧**：新增 `CookieValidationResultTest.kt`，覆��空值校验、三要素匹配、Cookie 名称大小写不敏感（如小写 `cloudkit`）、缺少关键凭证识别、带引号字符串长度计算等 5 个用例。

### 验证结果

- ✅ `go test -count=1 -v .` 全部通过。
- ✅ `./gradlew :app:testDebugUnitTest` 单元测试全部通过。

---

## ISS-010：冷启动主线程读取加密存储导致界面跳帧

**优先级**：🟡 P2（用户体验与渲染流畅度）
**归属**：Android 层 — `AuthViewModel.kt`
**发现时间**：Phase 1 实机运行 Logcat 监控（`Skipped 34 frames`）

### 现象

应用冷启动时，Logcat 偶尔出现 `Choreographer: Skipped 34 frames! The application may be doing too much work on its main thread.`。

### 根因

`AuthViewModel` 在 `init {}` 块中直接调用 `checkExistingSession()`，并在主线程同步访问 `SessionManager`（内部解密 `EncryptedSharedPreferences`），触发了密文解密和 I/O 阻塞。

### 修复方案

将 `checkExistingSession()` 的持久化凭证读取调整至 `viewModelScope.launch` 并在 `withContext(Dispatchers.IO)` 中异步执行：

```kotlin
// ✅ 修复后
init {
    viewModelScope.launch {
        checkExistingSession()
    }
}

suspend fun checkExistingSession() {
    val isLoggedIn = withContext(Dispatchers.IO) { sessionManager.isLoggedIn() }
    if (isLoggedIn) {
        val cookieStr = withContext(Dispatchers.IO) { sessionManager.getCookieString() ?: "" }
        // ...
    }
}
```

### 验证结果

- ✅ 冷启动不再在主线程执行同步加解密 I/O，首屏加载更流畅。

---

## ISS-011：WebView 控制台跨域与预加载噪音未拦截

**优先级**：🟢 P3（日志清洁度）
**归属**：Android 层 — `ICloudWebView.kt`
**发现时间**：Phase 1 WebView 联调

### 现象

WebView 控制台频繁刷出大量 Apple 官方页面的内部无害错误：
- `gatewayws.icloud.com.cn/pds/.../batchRegisterToken` CORS 报错
- `wallpaper.svg was preloaded using link preload but not used`

### 修复方案

在 `ICloudWebView` 中配置 `WebChromeClient.onConsoleMessage`，针对包含 `batchRegisterToken`、`preloaded using link preload`、`CORS` 的噪音消息进行拦截过滤，仅打印关键业务日志。

### 验证结果

- ✅ 过滤无害网络噪音，Logcat 专注于 `ICloudAuth` 业务鉴权。

---

## 附：Phase 1 反馈回路（验证成功的正向案例）

以下内容虽非「问题」，但作为**完整验证链路**的证明一并归档，便于后续 Phase 复用同样的验证手法。

### 三要素 Cookie 实测捕获结果

| 校验项 | 实测结果 |
| --- | --- |
| 国区路由 | ✅ 加载 `https://www.icloud.com.cn/`，页脚「由云上贵州运营」 |
| Cookie 捕获总数 | ✅ 18 项 |
| `X-APPLE-WEBAUTH-TOKEN` | ✅ 长度 154 |
| `X-APPLE-WEBAUTH-HSA-TRUST` | ✅ 长度 265（「信任此浏览器」生效） |
| `X-APPLE-WEBAUTH-PCS-CloudKit` | ✅ 长度 68 |
| 三要素校验 | ✅ `isValid=true missing=[none]`；登录前正确判定 0 项不放行 |
| 加密持久化 | ✅ `Persisting cookie set (region=cn)` |
| 跨 JNI 传递 | ✅ `Calling Go InitSession (isChinaMainland=true, hasHsaTrust=true)` |
| 冷启动恢复 | ✅ 强制停止后重启，直接从 `EncryptedSharedPreferences` 恢复，无 WebView |

### 完整 Cookie 名单（实测 18 项）

```
X-APPLE-UNIQUE-CLIENT-ID
X-APPLE-WEBAUTH-USER
X_APPLE_WEB_KB-<随机串>
X-Apple-GCBD-Cookie
X-APPLE-WEBAUTH-HSA-TRUST
X-APPLE-WEBAUTH-PCS-{Documents,Photos,Cloudkit,Safari,Mail,Notes,News,Sharing}
X-APPLE-WEBAUTH-{LOGIN,VALIDATE,TOKEN}
X-APPLE-DS-WEB-SESSION-TOKEN
X-APPLE-WEB-ID
```

### 排查用 Logcat Tag 速查

| Tag | 来源 | 用途 |
| --- | --- | --- |
| `ICloudAuth` | Android 侧探针 | Cookie 捕获、校验、JNI 调用、区域判定 |
| `GoLog` | Go 侧 `net/http` | Cookie 丢弃告警、TLS/HTTP 错误 |
| `chromium` | WebView 内核 | 页面 JS 报错、CORS 噪音 |

### 已知噪音（可忽略）

- `gatewayws.icloud.com.cn/pds/WebObjects/pushregistry.woa/webBatchRegister/batchRegisterToken` 的 CORS 报错 —— 页面推送注册行为，与鉴权无关。
- `wallpaper.svg was preloaded using link preload but not used` —— Apple 页面自身的 preload 提示。
- `MediaCodec / Codec2 / c2.goldfish.h264.decoder` 系列错误 —— 模拟器软解渲染噪音。

---

## ISS-012：冷启动恢复跳过 Go 会话重建导致照片接口全局报 No Active Session

**优先级**：🔴 P0（阻塞 Phase 2 只读预览）
**归属**：Android 层 / 跨语言会话管理 — `PhotoRepository.kt`
**发现时间**：Phase 2 实测联调

### 现象

App 冷启动后，界面直接落到 `LoggedIn` 状态及照片界面，但刷新或拉取照片时均报错 `No active session in Go core` 或 `PrimarySync 分区尚未就绪`。点击 UI “重试” 按钮亦无法恢复。

### 根因

1. `AuthViewModel.checkExistingSession()` 在检测到本地 `SessionManager` 缓存了 `dsid` 和 `ckDatabaseUrl` 时，直接将 UI 状态置为 `LoggedIn` 状态，跳过了 `Icloudcore.initSession()`。
2. Go 层的 `currentSession` 是 Go 进程级别的全局指针变量。Android App 进程冷启动或重新加载后，Go 核心层的 `currentSession` 为 `nil`。
3. `PhotoRepository` 先前未设立会话存活守卫，直接发起 `CheckPrimaryZone()` 或 `FetchPhotoPage()`，进而被 Go 层的 `GetSession() == nil` 拦截拒绝。

### 修复方案

在 `PhotoRepository` 引入懒加载会话守卫 `ensureGoSession(force: Boolean = false)`：
1. 每次调用 `Icloudcore` 接口前，先检查 `Icloudcore.getSessionStateSummary()`；
2. 若 Go 会话丢失，自动利用 `SessionManager` 中持久化的 Cookie 和节点设置透明重建 Go 核心层会话（通过 `Mutex` 保证并发安全）；
3. 若请求遇到 HTTP 401/421 或 Token 过期，自动强刷新会话（`force = true`）并重试单次 API 请求；
4. 若凭证静默刷新成功，将最新的 Cookie 自动回写同步至 `SessionManager`。

### 验证结果

- 冷启动后无需手动重新登录，自动重建 Go 会话并成功拉取首屏照片与增量变更；
- Gradle 编译与单元测试 100% 通过。

---

## ISS-013：`isAuthError` 误判原始 JSON 字符串导致每次分页触发重复鉴权

**优先级**：🔴 P1（性能与网络抖动）
**归属**：Android 层 — `PhotoRepository.kt`
**发现时间**：Phase 2 瀑布流滚动测试

### 现象

在滚动加载下一页照片时，系统频繁打印 `fetchNextPage returned auth error, forcing session refresh...` 并在后台发起一轮完整 `/validate` 握手。

### 根因

先前 `isAuthError(jsonStr)` 直接在响应字符串上执行 `jsonStr.contains("401") || jsonStr.contains("421")`。当分页响应返回的 `continuationMarker` 游标串或照片 UUID 中刚好包含 `"401"` 或 `"421"` 子串时（如 `"continuationMarker": "eyJ...401...="`），会被误判为鉴权失效，从而触发一次不必要的强行会话重建。

### 修复方案

将 `isAuthError` 改为先解析 JSON，仅在 `success == false` 时提取 `errorMessage` 字段进行错误码匹配：

```kotlin
private fun isAuthError(jsonStr: String): Boolean {
    try {
        val json = JSONObject(jsonStr)
        if (json.optBoolean("success", false)) return false
        val err = json.optString("errorMessage", "")
        return err.contains("401") || err.contains("421") || err.contains("AUTH_EXPIRED")
    } catch (e: Exception) {
        return false
    }
}
```

### 验证结果

- 滚动瀑布流加载后续分页时不再误触发会话重建，单次请求响应顺畅，日志恢复清洁。

---

## ISS-014：临时直链失效捕获重试与 ClientID 游标关联断开

**优先级**：🔴 P1（详情页大图/视频/下载可用性）
**归属**：Go 核心层 (`../go-core/photos.go`) & Android 层 (`ICloudPhotoCoil.kt`, `MediaStoreDownloader.kt`)
**发现时间**：Phase 2 详情页预览与原图下载实测

### 现象

1. 点开大图详情页或播放视频时，出现 `HTTP 410 Gone` 或 `403 Forbidden`，导致图片加载失败或 ExoPlayer 视频暂停。
2. 每次调用 `photosBaseURL` 均重新生成 `clientID`，导致 CloudKit 服务端将同一会话的不同分页请求判定为不同客户端。

### 根因

1. 苹果 CloudKit 照片直链（`cvws` 节点）带有一性签名，当服务端生成新 Session 或时间失效后，旧直链返回 HTTP 410。而先前的 `ICloudPhotoInterceptor` 和 `MediaStoreDownloader` 仅依赖基于时间戳的预判（50分钟），在遭遇 410/403 响应时未捕捉 `ErrorResult` 进行按需重新换链。
2. Go 侧 `photosBaseURL()` 每次调用均重新生成 `clientID` UUID，打破了分页游标与 Session 的长连接绑定。

### 修复方案

1. **Go 核心层**：在 `Session` 结构中持久化 `ClientID`，仅在会话创建时生成一次并全生命周期复用。
2. **Coil 拦截器**：`ICloudPhotoInterceptor` 在捕获 Coil `ErrorResult` 时，触发 `repository.refreshUrlsForMaster(data.masterId)` 向 CloudKit `/records/lookup` 换取新直链，并用新直链重试请求。
3. **MediaStore 下载器**：`MediaStoreDownloader` 在 HTTP 响应码为 403 或 410 时，自动调用 `refreshUrlsForMaster` 重新申请直链并无缝重试下载。
4. **视频播放器 UX 优化**：`VideoPlayerView` 添加 `Player.Listener`，遇到播放源异常时呈现清晰错误引导并提示用户通过按钮下载到本地播放。

### 验证结果

- 实测在模拟器中加载 300 项照片/视频瀑布流，预览大图与流式下载功能全部恢复正常工作。

---

## ISS-015：`isAuthError` 字符串全文匹配误判导致会话反复重建与列表跳位

**优先级**：🔴 P0（连锁引发 3 个表面症状）
**归属**：Android 层 — `PhotoRepository.kt`
**发现时间**：Phase 2 第二轮联调（快速滑动瀑布流）

### 现象

快速滑动瀑布流时：

1. 列表**跳回中间位置**（滚动位置丢失）
2. 部分**缩略图变灰 / 显示「iCloud」占位符**
3. 日志中每拉一页就出现一次 `Rebuilding Go core session (force=true)`，8 秒内 3 次

**成功 / 失败交替出现**，且失败总是紧跟重建后的首次请求。

### 根因

```kotlin
// ❌ 修复前
private fun isAuthError(jsonStr: String): Boolean {
    return jsonStr.contains("401") || jsonStr.contains("421") || ...
}
```

CloudKit 的 `continuationMarker` 是 base64 随机串，字符集含数字 `0-9`，
一段 128 字符的 marker 中出现连续 `"401"` 的概率约 2~3%。快速翻页几十次必然命中。

### 连锁反应

```
marker 恰好含 "401"
    → isAuthError 误判 → ensureGoSession(force = true)
    → Go 侧 CookieMap 整体替换，新会话签名
    → 数据库里所有旧 URL 签名失效（403/410）
    → 缩略图加载失败 → 显示「iCloud」占位符
    → upsertAll 写入 → Room InvalidationTracker → PagingSource.invalidate()
    → 列表重建 → 滚动位置丢失
```

### 修复方案

改为结构化判定，按优先级：

```kotlin
private fun isAuthError(jsonStr: String): Boolean {
    return try {
        val json = JSONObject(jsonStr)

        // 1. Go 层结构化错误码
errorCode == "AUTH_EXPIRED"
        // 2. 成功响应直接放过
        // 3. httpStatus in {401, 421}
        // 4. 仅对 errorMessage 用词边界正则匹配
        AUTH_STATUS_REGEX.containsMatchIn(msg)
    } catch (e: Exception) {
        false  // 解析失败宁可放过，绝不误重建
    }
}

private val AUTH_STATUS_REGEX =
    Regex("""(?:HTTP|status|StatusCode)\s*[:=]?\s*(?:401|421)\b""", RegexOption.IGNORE_CASE)
```

三条关键改进：只扫 `errorMessage` 字段、用 `\b` 词边界、异常返回 `false`。

### 验证结果

- 冷启动 90 项图库 + 连续滚动 3 页，`Rebuilding Go core session` 全程 **0 次**
- 列表不再跳位，缩略图不再变灰

---

## ISS-016：会话代际机制与 URL 刷新洪泛治理

**优先级**：🟠 P1
**归属**：Android 层 — `SessionManager.kt` / `PhotoRepository.kt` / `PhotoAssetEntity.kt` / `ICloudPhotoCoil.kt`
**发现时间**：Phase 2 第二轮联调

### 现象

1. Coil 拦截器过期判定只依赖时间（50 分钟），但实际 URL 是「会话重建即失效」，可能只隔 10 秒
2. 若简单改为“失败后刷新重试”，网格 60+ 个 item 同时失败会各发一次 `/records/lookup`，极易被 Apple 限流

### 根因

`fetchedAt` 是入库时间，重建后仍是新值，所以判定为“未过期”，不刷新，直接拿死链请求。

### 修复方案

**① 会话代际计数器**

```kotlin
// SessionManager.kt
fun getSessionGeneration(): Int = prefs.getInt(KEY_SESSION_GENERATION, 0)
fun bumpSessionGeneration(): Int { /* +1 并持久化 */ }
```

`PhotoAssetEntity` 新增 `sessionGeneration`（Room v2）。拦截器判定：

```kotlin
val generationStale = data.sessionGeneration != sessionManager.getSessionGeneration()
val ageStale = System.currentTimeMillis() - data.fetchedAt > 45 * 60 * 1000L
```

**② 只在真正重建时 bump**

> [!WARNING]
> 初版实现曾在 `ensureGoSession()` 的“会话已就绪”快速路径上也 bump，导致 generation
> 每几十毫秒 +1，每一格缩略图都变成“代际过期”并各发一次 `/records/lookup` —— 正是
> P2-008 洪泛的放大版。
>
> 实机日志证据：
> ```
> D/ICloudPhotoCoil: refreshed xxx -> host=cvws.icloud-content.com.cn
> D/ICloudPhotoCoil: load xxx genStale=true   ← 刷新成功后仍然 stale
> ```
>
> 现已改为**仅在 `initSession` 成功后 bump 一次**，日志确认 `genStale=false` 能稳定收敛。

**③ 刷新成功后回写 generation**

```kotlin
photoDao.updateUrls(
    masterId, thumb, med, orig, vid, fetchedAt, sessionGeneration = currentGeneration
)
```

否则该行永远停留在 `genStale=true`，每次加载都重复刷新。

**④ 三层刷新去重**

```kotlin
suspend fun refreshUrlsForMaster(masterId: String): Result<RefreshedUrls> {
    // 1. 5 分钟窗口内成功刷新过 → 直接读 DB
    // 2. 同一 masterId 的 in-flight 请求 → 共享 Deferred
    // 3. 会话刚重建时 → 20~120ms 随机抖动，摊平突发
}
```

另提供 `refreshUrlsForMasters(List<String>)` 批量接口（Go 侧 `RefreshAssetUrls` 已支持数组入参）。

---

## ISS-017：视频与图片详情页遇 403/410 不会自动重取链接

**优先级**：🔴 P1
**归属**：Android 层 — `PhotoDetailViewer.kt`
**发现时间**：Phase 2 第二轮联调

### 现象

- 打开视频详情页：`ExoPlaybackException / InvalidResponseCodeException: 410`，控件显示 `00:00 / 00:00`
- 点击图片详情页：显示“图片加载失败”
- 但**同一个文件点“下载”却能成功** —— 因为下载路径有刷新逻辑，播放/预览路径没有

### 修复方案

**视频**：以 `currentUrl` 为 `remember` key 重建播放器，保留进度并限制重试：

```kotlin
val exoPlayer = remember(currentUrl) {
    ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(currentUrl))
        prepare()
        if (lastPosition > 0) seekTo(lastPosition)

        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                lastPosition = currentPosition
                val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                when {
                    code == 410 || code == 403 -> handleExpiredUrl(code)
                    code == 404 -> playerError = "视频已从云端删除"
                    code != null -> playerError = "网络错误 (HTTP $code)"
                    else -> playerError = "播放失败：${error.errorCodeName}"
                }
            }
        })
    }
}
```

**图片**：`SubcomposeAsyncImage.onError` → 刷新 → `reloadToken++` 触发重组，上限 2 次。

**错误提示**：按 HTTP 码分流（不再硬编码 410），并提供“重试”按钮。

### 验证结果

- 视频在线播放遇 410 自动换链后继续播放，进度不丢失
- 图片详情页加载成功，错误时展示可重试提示

### 端到端实测记录（三星 SM-F9760 / Android 实机）

**测试前置**：先用 `curl` 预检库内视频直链，确认均为真死链：

```
IMG_1340.MOV -> HTTP 410
IMG_2066.MOV -> HTTP 410
IMG_2057.MOV -> HTTP 410
```

**播放全过程日志**：

```
20:04:51.236 I ExoPlayerImpl: Init 89f0ee1 [AndroidXMedia3/1.5.1] [q8q, SM-F9760, samsung, 37]
20:04:54.691 E ExoPlayerImplInternal: Caused by: InvalidResponseCodeException: Response code: 410
20:04:54.693 I PhotoDetailViewer: video url expired (HTTP 410), refreshing master=AVslHAx2kbQZc/Dk+OoksfW2niBJ, retry=1
20:04:56.167 I ExoPlayerImpl: Init 9d148e7                        ← 新 URL 建新播放器
20:04:56.186 I ExoPlayerImpl: Release 89f0ee1                     ← 旧实例正确释放
20:04:58.203 D CCodecConfig: c2::u32 raw.sample-rate.value = 44100  ← 音频轨启动，播放恢复
```

**结论**：从 410 命中到播放恢复耗时约 **3.5 秒**，全程无用户干预。

### 设计要点验证矩阵

| 设计要点 | 实测证据 |
| --- | --- |
| 检测 410 触发刷新 | `Source error: 410` → `video url expired (HTTP 410)` |
| 状态码准确提取 | 直接读 `InvalidResponseCodeException.responseCode`，非字符串匹配 |
| 自动重建播放器 | `Init 9d148e7` 新实例 |
| 旧实例无泄漏 | `Release 89f0ee1` |
| 测试条件有效 | `curl` 预检三个视频全为 `HTTP 410` |
| 播放真正恢复 | 音频解码器 `sample-rate = 44100` 正常初始化 |

### 连带验证的正向行为

同一次会话里还观察到 P2-008（刷新去重/并发合并）与 P2-005（会话代际）在真实压力下正确收敛：

```
20:04:57.962 D PhotoRepository: refreshUrlsForMaster skipped (deduped within window)
20:04:57.966 D PhotoRepository: refreshUrlsForMaster joining in-flight request master=ARPxwz57sqaxSfL02gwGDdb3bc+X
20:04:56.508 D ICloudPhotoCoil: refreshed E74C5958-... -> host=cvws.icloud-content.com.cn
```

批量刷新请求被合并，未出现请求风暴。

---

## ISS-018：网格滚动位置丢失与日志刷屏

**优先级**：🟡 P2
**归属**：Android 层 — `PhotosScreen.kt` / `ICloudPhotoCoil.kt` / `PhotoRepository.kt`
**发现时间**：Phase 2 第二轮联调

### 现象

1. 列表因任何原因重组时滚动位置归零
2. Coil 载入失败时 Logcat 完全无输出，只能靠 `grep 410` 倒推
3. 会话重建日志大量刷屏，淹没真实错误

### 修复方案

**滚动状态**（三层保障）：

```kotlin
val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
LazyVerticalGrid(columns = ..., state = gridState, ...)

// 切换列数时保持首个可见项
LaunchedEffect(gridColumns) {
    if (pendingRestoreIndex >= 0) {
        gridState.scrollToItem(pendingRestoreIndex)
        pendingRestoreIndex = -1
    }
}
```

**Coil 日志**（不泄露签名 URL）：

```kotlin
Log.d(TAG, "load ${recordName} quality=$quality hasUrl=... ageStale=... genStale=...")
Log.d(TAG, "refreshed $recordName -> host=${hostOf(url)}")
Log.w(TAG, "fail $recordName error=${throwable.message}")
```

**日志降级**：高频分页/同步日志统一降为 `Log.d`；Cookie 落盘仅在内容实际变化时打印。

### 验证结果

- 快速滚动 + 切换列数，滚动位置保持
- Logcat 可清晰追踪单张图片的 load → refresh → ok 全链路

---

## ISS-019：`append` 指示器作为网格项导致列表跳回顶部

**优先级**：🔴 P0（高频、强烈影响体验）
**归属**：Android 层 — `PhotosScreen.kt`
**发现时间**：Phase 2 第三轮联调（三星 SM-F9760 实机）
**对应待办**：P2-012

### 现象

用户精确描述：

> 「滑动瀑布流还是会有跳到最顶上的时候，而且我注意到跳到最顶上一般都处在滑到最底下——后面从没被加载过的照片、视频正在加载缩略图时——能看到最底下正在加载的图标时。」

触发条件高度一致：滑到列表最底部 + 底部显示 `append` Loading 指示器 + 新一批项缩略图正在加载。

### 根因（两个叠加的 itemCount 抖动源）

**源 1：`append` 指示器作为网格项**

原实现把 `loadState.append` 做成 `LazyVerticalGrid` 里的一行 `item(span = ...)`。
`Loading ↔ NotLoading` 每切换一次，**网格 itemCount 就 ±1**。用户在底部时
`firstVisibleItemIndex` 接近总数，itemCount 缩短时索引溢出，Compose 自动 clamp 到 0。

**源 2（更隐蔽）：`LazyVerticalGrid` 被 if/else 分支摘出组合树**

原实现的 empty / loading / error 三层分支是 `if / else if / else` 兄弟关系：

```kotlin
if (refresh is Loading && itemCount == 0) { ... }
else if (refresh is Error && itemCount == 0) { ... }
else if (itemCount == 0) { ... }
else { LazyVerticalGrid(state = gridState, ...) }   // ← grid 可能被整体摘除
```

refresh 期间只要有一帧 `itemCount == 0` 成立，`LazyVerticalGrid` 连同它持有的
`LazyGridState` 就一起离开组合树；再挂回来时是**全新的 state**，`firstVisibleItemIndex = 0`。

> [!IMPORTANT]
> 只修复源 1（`list.md` 原方案 A）**不能根治** —— 跳顶会变少但不消失，复测时极易误判为已修复。

### 修复方案

**1. footer 移出网格**：外层 `Column` + `LazyVerticalGrid(Modifier.weight(1f))`，
底部用 `LinearProgressIndicator` / 错误行承载 append 状态，网格 itemCount 恒定。

**2. 网格常驻组合树**：empty / loading / error 改为**叠加在网格之上的 overlay**
（`Box` 内 `if (itemCount == 0) { ... }`），不再参与 if/else 分支竞争：

```kotlin
Column {
    Box(Modifier.weight(1f)) {
        LazyVerticalGrid(state = gridState) { items(...) }   // 永远存在
        if (itemCount == 0) { /* overlay：loading / error / empty */ }
    }
    when (appendState) { is Loading -> LinearProgressIndicator(); is Error -> ... }
}
```

**3. 不采纳 `list.md` 原方案 B**：`snapshotFlow` 主动 `scrollToItem` 的 clamp 补丁会掩盖问题
而非根治，且会与用户的滚动操作打架。

### 验证结果

`emulator-5554` 实测，4 列网格从 `150 项` 连续滑到底：

| 观察项 | 结果 |
| --- | --- |
| 第 1 次 APPEND（150 → 210 项） | ✅ 视口从 `IMG_0047` 连续下移，无跳顶 |
| 第 2 次 APPEND（210 → 240 项） | ✅ 视口推进到 `IMG_1327`…`IMG_0018`，无跳顶 |
| 首屏项（`IMG_2109`）是否重新出现 | ✅ 未出现，单调前进 |
| 底部 footer 出现/消失时网格位移 | ✅ 无位移（footer 在网格外） |

> [!NOTE]
> 复测时另发现 4 次 `Rebuilding Go core session (force=true)`（约 17 秒内）。
> 它们是**独立的第二个 bug**，已单独作为 P2-014 / ISS-020 处理。

---

## ISS-020：`continuationMarker` 失效后仍被重放，引发 401 → 重建死循环

**优先级**：🔴 P0
**归属**：Android 层 — `PhotoRepository.fetchNextPage()`
**发现时间**：Phase 2 第三轮联调（修复 ISS-019 后拉 logcat 顺手发现）
**对应待办**：P2-014

### 现象

每取一页就报一次鉴权错误 + 完整会话重建，17 秒内 4 次：

```
1792236629: I PhotoRepository: Fetching next photo page with marker EgYZJuDLe+0aKAo... (limit=60)
1792236630: W PhotoRepository: fetchNextPage returned auth error, forcing session refresh...
1792236630: I PhotoRepository: Rebuilding Go core session (force=true)...
1792236632: I PhotoRepository: Rebuilt Go session result: {"success":true,...}
1792236633: I PhotoRepository: Next page returned: 30 photos, hasMore=true   ← 注意：30 条，不是 60
```

### 根因

`continuationMarker` 是 CloudKit 的分页游标，**绑定在签发它的会话上下文上**。
会话重建后它就在服务端失效了，但本地 `syncStateDao` 里那份没被清理：

1. 重建后仍拿旧 marker 去 `/records/query` → Apple 答 `401`
2. `isAuthError()` 正确地判定为鉴权错误 → 触发重建
3. 重建后**仍然拿同一个旧 marker 再试** → 又 401
4. 但 Go 层 `ExtractAndSaveCookies` 在收 401 时已保存了新 cookie，
   于是**下一次**带着新 cookie + 旧 marker 恰好能过 → 拿到从头开始的 30 条（非接续）
5. 新一轮的 marker 又随下一次会话变更失效 → 循环

> [!IMPORTANT]
> 与 ISS-015 是**不同**的问题，不要混淆：
> ISS-015 是“不该触发却触发”（子串误伤），本条是“该修的东西（marker）没修”。

### 修复方案

#### 关键设计点：只在“已确认 401”的恢复路径上作废 marker

```kotlin
private suspend fun invalidateContinuationCursor() {
    val state = runCatching { syncStateDao.getSyncState(KEY_PRIMARY_SYNC) }.getOrNull() ?: return
    if (state.continuationMarker.isNullOrBlank()) return
    Log.d(TAG, "Auth recovery - dropping dead continuationMarker")
    syncStateDao.setSyncState(state.copy(continuationMarker = null, hasMore = true))
}
```

```kotlin
// fetchNextPage()
if (isAuthError(jsonStr)) {
    ensureGoSession(force = true)
    invalidateContinuationCursor()                   // ← 先作废死 marker
    jsonStr = Icloudcore.fetchPhotoPage("", limit)  // ← 从头拉，不再重放旧 marker
}
```

重叠部分按 `id` 在 `upsertAll` 时去重。

### ⚠️ 修复过程中引入并修正的新 bug

初版把 `invalidateContinuationCursor()` 放在了 `ensureGoSession(force)` 成功之后。
但 `ensureGoSession` **也会在普通冷启动（`force=false`）时执行** —— 那种场景下
我们只是重接一个缓存的 DB，**持久化的 cursor 仍然完全有效，而且它是继续分页的唯一依据**。

实际后果（日志实锤）：

```
20:19:10.368 D PhotoRepository: Session rebuilt - dropping stale continuationMarker
20:19:10.442 D PhotoRepository: No more photos to fetch via continuationMarker  ← 分页直接停摆
```

每开一次 App，分页能力就被清空一次，列表永远停在缓存的那 240 条。

> [!CAUTION]
> 通用教训：**“会话重建” ≠ “cursor 失效”**。
> 只有服务端明确回了 401、告诉我们这个 cursor 不被接受时，它才真的死了。
> 将失效判定绑定在“恢复路径”上，而不是“重建动作”上。

### 验证结果（emulator-5554）

冷启动后快速滑到列表底部（穿越 240 条缓存 + 触发新分页）：

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| `Rebuilding Go core session (force=true)` | 4 次 / 17 秒 | **0 次** |
| `Rebuilding Go core session (force=false)` | 1 次 | **1 次**（仅冷启动） |
| `fetchNextPage auth error` | 每页 1 次 | **0 次** |
| 分页游标 | 每页重头开始（30 条） | **正常递增接续** |
| 冷启动后 `dropping continuationMarker` | — | **0 次**（已修正误清） |

关键日志（修复后，全程仅一次重建）：

```
20:20:50.084 I PhotoRepository: Rebuilding Go core session (force=false)...
20:20:51.664 D PhotoRepository: Rebuilt Go session result: {"success":true,...}
20:20:51.696 D PhotoRepository: Cookies refreshed (len 2798 -> 2798)
                          ← 再没有任何 rebuild / auth error
```

### 遗留（未做）

Go 层 `PhotoPageResult` 目前既无 `errorCode` 也无 `httpStatus` 字段，Kotlin 侧只能靠
正则从 `errorMessage` 里猜（`\\bHTTP\\s+(?:401|421)\\b`）。建议后续按 `AGENTS.md` 第 12 节
的技术债条目补齐结构化字段，需同时重新 `../scripts/build_aar.sh`。

---

## ISS-021：`RemoteMediator` 每次 APPEND 必 invalidate，导致深翻页跳顶

**优先级**：🔴 P0
**归属**：Android 层 — `PhotoRepository` 的 `PagingConfig` / `PhotoRemoteMediator`
**发现时间**：Phase 2 第四轮真机联调（三星 SM-F9760）
**对应待办**：P2-016
**状态**：⚠️ 已确诊，缓解态；根治待 UI 重构

### 现象

列表向下滚动时会突然“跳回顶部”。用户描述经历了两次演变：

1. 最初：「滑到最底部就跳顶」
2. 调参后：「不一定每次都到顶，跳回的位置会随滑动深度同步下移」

### 排查历程（四个被排除的假设）

这一轮总共排除了四个“看起来很像”的原因，均属**真问题但非主因**：

| 假设 | 修复 | 结果 |
| --- | --- | --- |
| `append` 指示器作为网格项使 itemCount ±1 | 移出网格（ISS-019） | 真问题，修完仍跳 |
| `continuationMarker` 失效被重放 | 失效路径作废（ISS-020） | 真问题，修完仍跳 |
| 孤儿 master 用 `time.Now()` 污染排序 | 改稳定 0（ISS-021 附带） | 真问题，修完仍跳 |
| `assetDate` 同秒并列排序不稳定 | 加 `id` tiebreaker | 真问题，修完仍跳 |

### 转折点：加诊断日志实测

前四轮都是“基于推断直接改代码”，均未命中。第五轮改为在 `LazyGridState`
上挂 `snapshotFlow` 实测，立刻拿到了铁证：

```kotlin
LaunchedEffect(gridState) {
    snapshotFlow {
        Triple(
            gridState.firstVisibleItemIndex,
            lazyPagingItems.itemCount,
            lazyPagingItems.loadState.append !is LoadState.NotLoading
        )
    }.collect { Log.d("GridDiag", "index=${it.first} itemCount=${it.second} appending=${it.third}") }
}
```

日志结果：

```
20:55:35.298 index=1344 itemCount=1827 appending=true    ← APPEND 进行中
20:55:35.466 index=1344 itemCount=1200 appending=false   ← 塌到 initialLoadSize=1200
20:55:35.491 index=1168 itemCount=1200                   ← index 被 clamp 回 1168
20:55:35.548 index=1164 itemCount=1260                   ← 一页页涨回来
…
20:55:36.629 index=1164 itemCount=1740                   ← index 卡在 1164 不动
```

### 根因

**根因不在数据层，在 Paging 3 的 `RemoteMediator` 机制本身。**

1. 每次 APPEND，`PhotoRemoteMediator.load()` 返回 `MediatorResult.Success`
2. Paging 3 收到 `Success` 后 **无条件 invalidate `PagingSource`**（官方设计）
3. invalidate 后重新加载，**重载窗口长度 = `initialLoadSize`**
4. 用户当前位置超出该窗口 → `firstVisibleItemIndex` 被 clamp → **视觉跳顶**

官方文档明确写道：

> Finally, the `PagingSource` invalidates itself, and the `Pager` creates a new instance
> to load the fresh data from the database.

**塌陷点恒定等于 `initialLoadSize`**，三组对照完全验证：

| `initialLoadSize` | 实测塌陷到的 `itemCount` |
| --- | --- |
| 60 | **60**（用户从 index 305 掉回 16） |
| 360 | **360**（用户从 index 564 掉回 324） |
| 1200 | **1200**（用户从 index 1344 掉回 1164） |

> [!IMPORTANT]
> 这完美解释了「跳回位置随滑深同步下移」：
> 滑得越深，离 `initialLoadSize` 越远，被 clamp 得越多。

### 修复（缓解态）

调大 `initialLoadSize` 与 `prefetchDistance`，把悬崖推到用户实际滑不到的深度：

```kotlin
pageSize = 60,
prefetchDistance = 60 * 3,    // APPEND 提前 3 页触发
initialLoadSize = 60 * 20     // 1200，远超单屏
```

效果：真机从「滑到 240 就跳」改善到「滑到 2400 才跳」。

> [!WARNING]
> `prefetchDistance` 曾试过 `60 * 8`（480）。在快速长距离滑动下，Paging 连续发起多个
> APPEND，CloudKit 返回 `context deadline exceeded (Client.Timeout exceeded while awaiting
> headers)`，列表卡在 1140 项停止加载。已回落到 `60 * 3`。

### 为何不现在根治

调大 `initialLoadSize` 只是**把悬崖往后推，并未消除**。图库有多少张，
就总有一个深度会撞上悬崖。这是结构性问题，不是参数问题。

但用户已确定：**最终版本会换成 Google Photos 风格的 UI**。分组式 UI 的滚动锚点是
「日期 section + 组内偏移」而非全局整数 index，天然的不会以同样形式遭遇 index 塌陷。

现在为旧 UI 做脱离 Paging 3 的重写，属于「投入会被丢弃、风险却真实存在」。

### 待办（UI 重构时一并处理）

- [ ] 滚动锚点改为 `DateSectionKey + offset`
- [ ] 评估是否保留 Paging 3
- [ ] 若仍用 `LazyGridState`，改用 `firstVisibleItemKey` 而非 index 做锚定
- [ ] `append` 错误提示改为友好文案（当前直接吐完整 URL，含会话参数）

### 验证证据

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| 可连续滑动深度 | ~240 | **~2400** |
| `itemCount` 塌陷点 | 60 | **1200** |
| CloudKit 超时 | 无 | 无（`prefetchDistance` 回落至 `60*3` 后） |
| 会话重建 | 0 | **0** |

---

## ISS-022：冷启动时 Login WebView 被提前构造，阻塞主线程 ~3.3 秒

**优先级**：🟠 P1
**归属**：Android 层 — `AuthViewModel` / `AuthScreen` / `MainActivity` / `ICloudWebView`
**发现时间**：Phase 2 第三轮真机联调
**对应待办**：P2-013

### 现象

冷启动后滑动明显卡顿，`Choreographer` 实测：

```
19:59:32.473  Skipped 32 frames!
19:59:32.498  Skipped 35 frames!
19:59:33.219  Skipped 59 frames!   ← 最严重
19:59:40.308  Skipped 36 frames!
```

累计跳掉 162 帧（约 3.3 秒主线程阻塞）。

### 根因（三个叠加）

#### 1. `AuthUiState` 初始值是 `NotLoggedIn`（最关键）

```kotlin
private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.NotLoggedIn())  // ❌
```

`checkExistingSession()` 是 `suspend` 函数，在它完成前 `_uiState` 仍是 `NotLoggedIn`，
于是 **UI 先渲染了一帧登录页** → `AuthScreen` 被组合 → `ICloudWebView` 被创建。

WebView 初始化是重量级操作（加载几十 MB 的 `libwebviewchromium.so` + 启动渲染进程）。
关键铁证：冷启动阶段就出现了 `checkCookies source=onPageStarted`，说明页面真的开始加载了。
而这个 WebView 一瞬后就被 `LoggedIn` 撑掉，**纯浪费**。

#### 2. Cookie 校验在主线程

`checkExistingSession()` 跑在 `viewModelScope`（Main），其中
`CookieValidationResult.validate()` 与 `logToLogcat()` 直接在主线程执行。
且它有 **5 次独立的** `withContext(Dispatchers.IO)`，每次都重新访问
keystore 支撑的 `EncryptedSharedPreferences`。

#### 3. 日志刷屏

`logToLogcat()` 每次探针输出 5 条 `Log.i`，含完整 cookie 名列表与逐项长度。

### 修复方案

**1. 新增 `Checking` 初始态**

```kotlin
sealed interface AuthUiState {
    data object Checking : AuthUiState   // 新增
    ...
}
private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Checking)
```

>`Checking` 分支在 `AuthScreen` 与 `MainActivity` 里**都只渲染一个 spinner**，
> **绝不渲染 `AuthScreen` / `NotLoggedInContent`** —— 否则 WebView 仍会被构造。

同时 `MainActivity` 的 `else -> AuthScreen` 会吞掉任何未知状态，
必须把 `Checking` 显式路由出去：

```kotlin
when (val state = authState) {
    is AuthUiState.Checking -> { Box { CircularProgressIndicator() } }
    is AuthUiState.LoggedIn -> { PhotosScreen(...) }
    else -> AuthScreen(viewModel = authViewModel)
}
```

**2. 合并为单次 IO 跳转**

```kotlin
val snapshot = withContext(Dispatchers.IO) {
    SessionSnapshot(
        isLoggedIn = ..., cookieStr = ..., isChina = ..., dsid = ..., ckUrl = ...,
        validation = ...also { it.logToLogcat("restored-from-storage") }
    )
}
```

**3. 日志降级**

```kotlin
fun logToLogcat(pageUrl: String?, tag: String = AUTH_LOG_TAG) {
    if (!BuildConfig.DEBUG) return
    Log.d(tag, "cookie probe @$pageUrl total=${cookieNames.size} token=$hasAuthToken ...")
}
```

> [!NOTE]
> 为此在 `../app/build.gradle.kts` 开启了 `buildFeatures { buildConfig = true }`。

### 验证结果（三星 SM-F9760 真机，`am force-stop` 后冷启动）

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| `Choreographer: Skipped` | **197 frames**（~3.3 秒） | **37 frames**（~0.6 秒） |
| `checkCookies source=onPageStarted` | 出现 | **完全消失** |
| `WebViewFactory: Loading com.google.android.webview` | 出现 | **未出现** |
| 会话恢复 | 正常 | 正常（静默恢复 2490 项） |

关键日志（修复后，全程无 WebView 初始化）：

```
21:18:23.346 D/ICloudAuth: cookie probe @restored-from-storage total=18 token=true hsa=true pcs=true valid=true missing=[none]
21:18:23.578 I/PhotoRepository: Rebuilding Go core session (force=false)...
21:18:23.826 I/Choreographer: Skipped 37 frames!
```

### 剩余 37 帧（不属于本问题）

剩余跳帧发生在首屏缩略图批量加载时：

```
21:18:23.723 D/ICloudPhotoCoil: load ... quality=THUMB genStale=true
…（同时多条）
21:18:23.826 I/Choreographer: Skipped 37 frames!
```

`genStale=true` 说明会话重建后全部 URL 失效，首屏数十格同时触发刷新。
这是**首屏渲染成本**，不是冷启动阻塞。若需进一步优化，可在会话重建后用
`refreshUrlsForMasters` 批量预热首屏 URL（接口已就绪但未消费）。

---

## ISS-023：HDR 高位深原图无法解码 + 解码失败误触发 URL 刷新

**优先级**：🟠 P1
**归属**：`feature/photos/data/image/ICloudPhotoCoil.kt` / `feature/photos/ui/PhotoDetailViewer.kt` / `PhotoRepository`
**发现时间**：Phase 2 第三轮真机联调
**对应待办**：P2-010 / P2-011

两个问题強相关：第一个导致原图打不开，第二个使得“打不开”永远无法自愈、且不断发出无效请求。

---

### P2-010 —— 现象

部分照片在详情页显示「图片加载失败」，但：

- 网格缩略图正常
- 下载正常
- 拍摄信息（EXIF）正常

用户提供了决定性线索：

> 无法打开的全都是 iPad 的截图。iPad 屏幕捕捉设置为 HDR 就无法查看，改为 SDR 就可以。

---

### P2-010 —— 根因（经字节级分析修正）

**早期结论（已废弃）**：归因为 `ftyp` 的 `MiHE` brand（HDR gain map）。

拿到用户提供的三张 iPad 原始文件后，字节级分析推翻了这一假设：

```
IMG_1350.PNG: 2732x2048  bitDepth=16  colorType=2 (RGB)   ← HDR 截图
IMG_1351.PNG: 2732x2048  bitDepth=16  colorType=2 (RGB)
IMG_1352.HEIC: ftyp heix | mif1 MiHE miaf heix           ← 10-bit HEVC
```

| 格式 | 位深 | Android `BitmapFactory` | `ImageDecoder` | macOS `sips` |
| --- | --- | --- | --- | --- |
| PNG `bitDepth=16` | 16-bit | ❌ | ❌ | ✅ |
| HEVC `heix` Main 10 | 10-bit | ❌ | ⚠️ 部分 | ✅ |

**真正的根因是高位深**，`MiHE` 只是伴随特征（Apple 的 HDR 都住这个 brand），并非因果。

四条排除证据：

1. **数据完好**：`curl` 下载 `HTTP=200`，字节头正常；macOS `sips` 可成功转 JPEG
2. **与 URL 无关**：SDR 图脏 `genStale=true` 刷新后依旧成功；HDR 图换新 URL 依旧失败
3. **与尺寸无关**：12MP HEIC 成功、5MP HEIC 失败
4. **与 `ImageDecoder` 无关**：`ImageDecoderDecoder.Factory()` 已注册且 `.size(2048)` 已加，依旧报 `BitmapFactory` 错误（Coil 回退）

### 为何缩略图、下载都正常

| 路径 | 数据来源 | 位深 |
| --- | --- | --- |
| 缩略图 | iCloud `resJPEGThumbRes` | **8-bit JPEG**（服务端转码）✅ |
| 下载 | 原始字节写文件，不解码 | 16-bit 原样保存 ✅ |
| 详情页预览 | 需解码成 Bitmap | **16-bit → 失败** ❌ |

---

### P2-010 —— 修复（A + C）

#### A：原图解码失败时自动降级到 MEDIUM

```kotlin
var degradedToMedium by remember(photo.id) { mutableStateOf(false) }
var isDegrading by remember(photo.id) { mutableStateOf(false) }

val quality = if (degradedToMedium) MEDIUM else ORIGINAL

onError = {
    when {
        isUrlIssue -> { /* 刷新 URL 重试 */ }

        // 非 URL 问题 + 还是原图 → 几乎必定是高位深解码失败
        !isUrlIssue && quality == ORIGINAL -> {
            isDegrading = true          // 立刻翻转，避免错误面板闪一下
            scope.launch {
                withFrameNanos { }      // 必须延到下一帧，否则 AsyncImage 不重建
                degradedToMedium = true
            }
        }

        else -> loadFailed = true       // 中图也失败 → 走 C
    }
}
```

#### C：中图也失败时给出明确提示

```kotlin
error = {
    if (isDegrading) {
        CircularProgressIndicator()   // 过渡帧不画错误面板
    } else {
        Text(
            when {
                isUrlIssue -> "原图加载失败 (链接已失效)"
                degradedToMedium -> "该图片为 HDR 高动态范围格式，暂不支持预览\n可点击下方按钮下载原图"
                else -> "原图无法解码 (该格式暂不支持)"
            }
        )
    }
}
```

#### 两个实现陷阱

> [!WARNING]
> **陷阱 1：`onError` 里直接改 state 不会生效**
> 回调执行时 Coil 还在收尾失败的请求，新 model 会被吞掉。实测表现为“打印了 `degrading to MEDIUM` 但没有 MEDIUM 请求”。必须 `withFrameNanos { }` 延到下一帧。

> [!WARNING]
> **陷阱 2：错误面板会闪几帧**
> 从“原图失败”到“中图加载完成”之间有窗口期，`error` 槽位已被调用。`isDegrading` 标志让过渡期继续显示 loading，消除闪烁。

---

### P2-010 —— 验证结果（三星 SM-F9760 实机）

```
17:54:59.314  W ICloudPhotoCoil: fail 9426766F... quality=ORIGINAL  error=BitmapFactory null
17:55:00.784  W PhotoDetailViewer: original decode failed ..., degrading to MEDIUM
17:55:00.869  D ICloudPhotoCoil: load 9426766F... quality=MEDIUM
```

- ✅ HDR 图片正常显示（用户确认，多张 HDR 均有效）
- ✅ 原图失败仅 **1 次**，降级仅 **1 次**（修复前会重复 2 次）
- ✅ SDR 图片（`406B4317` / `818C472C`）仍走 ORIGINAL，**无误降级**
- ✅ 提示闪烁已消除（`isDegrading` 生效）

### P2-010 —— 后续可选优化

| 方案 | 说明 | 优先级 |
| --- | --- | --- |
| Go 层转码 | 用 `libheif`/`libvips` 把高位深转 8-bit | 🟡 需引入 CGO |
| 预判位深 | 读文件头提前判断，避免“先试后降” | 🟢 收益有限 |
| 超大幅图保护 | 12288×8192 的 DJI 图同样需 `.size()` 限制 | 🟢 已由 `.size(2048)` 覆盖 |

---

### P2-011 —— 现象与修复

#### 现象

详情页原图加载失败后尝试刷新 URL，却被去重窗口直接拒绝：

```
19:50:15.328 I PhotoDetailViewer: image load failed, refreshing url master=AUQhzkGu3...
19:50:15.332 D PhotoRepository: refreshUrlsForMaster skipped (deduped within window)
```

#### 根因

去重窗口是**按 masterId 维度**的，但刷新实际同时返回 thumb / med / original 三类 URL。缩略图先失败开启窗口后，详情页请求原图刷新时被误伤。

更关键的是：**解码失败不是 URL 问题**，刷新 URL 无用，却不断撞窗口产生无效请求。

#### 修复

1. **区分错误类型**：只有 HTTP 403/410 才触发刷新，解码失败走降级
2. **去重键改为 `masterId:quality`**
3. **详情页提供 `force = true` 通道**（单次用户操作不该被网格去重误伤）

```kotlin
// PhotoDetailViewer
val refreshed = repository.refreshUrlsForMaster(photo.masterId, force = true)
```

#### 验证

新增 `ImageLoadFailureTest`：

| 用例 | 断言 |
| --- | --- |
| HTTP 410 / 403 | `isExpiredUrl = true` |
| HTTP 404 / 500 / 200 | `isExpiredUrl = false` |
| `IOException`（解码失败）/ `null` | `isExpiredUrl = false` |

`testDebugUnitTest`：**12 passed, 0 failed**。
    add(ICloudPhotoKeyer())
    add(ICloudPhotoInterceptor(repo, SessionManager(context.applicationContext)))
}
```

**2. 详情页限制解码尺寸**

```kotlin
ImageRequest.Builder(context).data(model).size(2048).crossfade(true).build()
```

> [!IMPORTANT]
> 依赖变更：在 Coil 2.7 里 `ImageDecoderDecoder` **不在 `coil-base`**，
> 而在 `io.coil-kt:coil-gif` 模块中（尽管名字叫 gif）。已在 `libs.versions.toml`
> 添加 `coil-gif` 并引入。

### P2-011 —— 现象与根因

详情页原图加载失败后尝试刷新 URL，但 8 秒内两次请求**都被去重窗口拦下**：

```
19:50:15.328 I PhotoDetailViewer: image load failed, refreshing url master=AUQhzkGu3AmNvxGLZVpYZieyfodv, retry=1
19:50:15.332 D PhotoRepository: refreshUrlsForMaster skipped (deduped within window) master=AUQhzkGu3AmNvxGLZVpYZieyfodv
19:50:23.286 I PhotoDetailViewer: image load failed, refreshing url master=AUQhzkGu3AmNvxGLZVpYZieyfodv, retry=1
19:50:23.286 D PhotoRepository: refreshUrlsForMaster skipped (deduped within window) master=AUQhzkGu3AmNvxGLZVpYZieyfodv
```

P2-008 的去重窗口是**按 masterId 维度**的，而缩略图先失败时已开启窗口，
详情页请求被同一窗口拦住。更糟的是：**解码失败根本不是 URL 问题**，
刷新 URL 本就无用，却不断撞窗口形成无效请求 + 无效日志。

### P2-011 —— 修复（两个改进）

**改进 1：只在真的是 URL 失效时才刷新**

抽取出可测的纯函数 `ImageLoadFailure`：

```kotlin
internal object ImageLoadFailure {
    fun httpCodeOf(throwable: Throwable?): Int? =
        (throwable as? coil.network.HttpException)?.response?.code

    fun isExpiredUrl(throwable: Throwable?): Boolean {
        val code = httpCodeOf(throwable) ?: return false
        return code == 403 || code == 410
    }
}
```

详情页据此分流，且错误文案区分「链接失效」与「无法解码」。

**改进 2：提供强制刷新通道**

```kotlin
suspend fun refreshUrlsForMaster(
    masterId: String,
    force: Boolean = false     // 新增
): Result<RefreshedUrls>
```

`force = true` **仅跳过去重层 2**（时间窗口），层 1（in-flight 合并）依然生效。

> [!IMPORTANT]
> 为何只绕过层 2：详情页是**单次用户操作**，不可能形成突发；
> 但若被网格缩略图 30 秒前的刷新窗口否决，就会出现“安全措施反而导致永久修不好”的尴尬。
> 层 1 保留则保证即使真的并发也不会重复请求。

### 为何不采用「按 masterId + 品质去重」

原方案提议把去重键改为 `"masterId:THUMB"`。但 `/records/lookup`
**一次就返回全部三种品质的 URL**（thumb / med / original），按品质拆分键会导致
同一 master 发出多次完全重复的请求，适得其反。改用 `force` 参数更精准。

### 验证结果

**模拟器（Medium Phone API 37）**：

```
22:46:00.813 D/ICloudPhotoCoil: load 95663D45-... quality=ORIGINAL hasUrl=true ageStale=false genStale=false
22:46:06.933 D/ICloudPhotoCoil: ok 95663D45-... quality=ORIGINAL
```

✓ 原图加载成功，**未出现** `BitmapFactory returned a null bitmap`。

**单测（新增 `ImageLoadFailureTest`）**：

| 用例 | 断言 |
| --- | --- |
| HTTP 410 / 403 | `isExpiredUrl = true` |
| HTTP 404 / 500 / 200 | `isExpiredUrl = false` |
| `IOException`（解码失败）/ `null` | `isExpiredUrl = false` |

`testDebugUnitTest`：**12 passed, 0 failed**。

### 待补充验证

真机（三星 SM-F9760）未复测。建议用真实失败样本确认：

| 文件 | masterId |
| --- | --- |
| `IMG_1338.HEIC` | `AUQhzkGu3AmNvxGLZVpYZieyfodv` |
| `IMG_1335.HEIC` | `AWqP3cETVjWRaNG7zO6IJj13uMcz` |

---

## ISS-024：Go 层用 `time.Now()` 作排序键兜底，导致照片位置持续漂移

**优先级**：🔴 P0（非跳顶主因，但会造成照片位置漂移）
**归属**：Go 核心层 — `../go-core/photos.go` → `parseRecords()`；Android 防御 `PhotoRepository.parsePhotoItems`
**发现时间**：Phase 2 深翻页跳顶排查期间
**对应待办**：P2-017

### 现象

排查深翻页跳顶（ISS-021）时发现：部分照片在列表中位置不稳定，每次分页刷新都向前移动。

### 根因

`../go-core/photos.go` 有两处用 `time.Now()` 给缺失时间戳的记录兜底，
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
且永远被顶到最前面。

### 修复

两处均改为稳定的 `0`（DESC 排序下自然沉底、永不移动）：

```go
AssetDate: 0,   // never time.Now() — must be stable across fetches
```

同时移除不再使用的 `"time"` import。

Kotlin 侧同步加防御（`PhotoRepository.parsePhotoItems`）：

```kotlin
val rawAssetDate = obj.optLong("assetDate", 0L)
val assetDate = if (rawAssetDate > 0L) rawAssetDate else 0L   // 不再 fallback 到 now
val addedDate = obj.optLong("addedDate", 0L).takeIf { it > 0L } ?: assetDate
```

### 验证

`../scripts/build_aar.sh` 成功、`go test ./...` 通过。

### 经验教训

> [!WARNING]
> **排序键必须是稳定的。** 任何在数据获取阶段生成的“当前时间”都会让同一实体在不同次的
> 查询中得到不同排序，表现为列表项无序漂移。缺失时间戳时应使用中性值（如 `0`），
> 而不是当前的 `time.Now()`。

---

## ISS-025：退出登录不清洗照片库与图片缓存，导致换区/换号后残留脏数据

**优先级**：🟠 P1（换账号/换区时必然触发，表现为“图片全部加载失败”）
**归属**：`ui/auth/AuthViewModel.kt` → `logout()`；`feature/photos/data/PhotoRepository`；
`feature/photos/data/image/ICloudPhotoCoil`；`feature/photos/data/db/SyncStateDao`
**发现时间**：Phase 2 收尾
**对应待办**：原 P2-018

### 现象

切换国区/国际区，或更换 Apple ID 后重新登录：

- 列表仍显示上一个账号的照片（数量、文件名都不对）
- 图片大面积加载失败（直链属于旧账号，已失效）
- `dsid` 与新登录账号不匹配，`/records/query` 返回空或报错

### 根因

`logout()` 只清理了**会话层**，未清理**数据层**：

| 数据 | 主键 | 是否账号相关 |
| --- | --- | --- |
| `photo_assets` 表 | `recordName` | ✅ CloudKit 记录 ID 属于特定账号 |
| `sync_state` 表 | `PRIMARY_SYNC` | ✅ 存 `syncToken` / `continuationMarker` |
| Coil 磁盘缓存 | `icloud_{recordName}_{quality}` | ✅ 图片字节来自旧账号 |

新账号登录后 `bumpSessionGeneration()` → 旧行全部 stale → 首屏批量刷新，
但用的是**新账号 Cookie** 查**旧账号 recordName**，必然失败。

**附带缺陷**：`removeAllCookies(null)` 是异步的，传 `null` 只是放弃回调，
紧接着的 `flush()` 可能在删除真正生效前就落盘，导致进程重启后旧 Cookie 仍存在。

### 修复

分四层改造，顺序不可颠倒：

**1. `SyncStateDao` 补充全表清空**

```kotlin
@Query("DELETE FROM sync_state")
suspend fun clearAll()
```

**2. `PhotoRepository` 提供清理入口与阻断开关**

```kotlin
@Volatile var isWiping: Boolean = false; private set
fun beginWipe() { isWiping = true; ... }
fun endWipe()   { isWiping = false; ... }

suspend fun wipeAllData(): Boolean = withContext(Dispatchers.IO) {
    syncStateDao.clearAll()   // 先清游标：失败时最坏只是重新全量，而不是留指向已删行的 token
    photoDao.clearAll()
    refreshMutex.withLock { recentRefreshes.clear(); inFlightRefreshes.clear() }
    ...
}
```

所有网络入口（`checkZoneReady` / `fetchInitialPage` / `fetchNextPage` / `syncIncremental` /
`refreshUrlsForMaster` / `refreshUrlsForMasters`）开头统一 `wipeBlocked()` 拦截。

**3. `PhotoRemoteMediator.load()` 首行检查 `isWiping`**

这是关键一环：正在飞行中的 APPEND 若在 `clearAll()` 之后完成 `upsertAll()`，
就会把旧账号的行重新写回。

**4. `ICloudImageLoaderFactory.clearCaches()` + `logout()` 串联**

```kotlin
fun logout() {
    _uiState.value = AuthUiState.NotLoggedIn(currentRegion)   // UI 先切走
    viewModelScope.launch {
        repository.beginWipe()          // 1. 先阻断，再清库
        try {
            repository.wipeAllData()                                  // 2. Room
            ICloudImageLoaderFactory.clearCaches(app, container)      // 3. Coil
            clearWebViewCookies()                                     // 4. Session（最后）
            sessionManager.clearSession()
        } finally {
            repository.endWipe()
        }
    }
}
```

`clearWebViewCookies()` 把异步回调桥接为挂起函数，确保 `flush()` 在删除**之后**才执行：

```kotlin
private suspend fun clearWebViewCookies() = withContext(Dispatchers.Main) {
    val cm = CookieManager.getInstance()
    suspendCancellableCoroutine<Unit> { cont ->
        cm.removeAllCookies { if (cont.isActive) cont.resume(Unit) }
    }
    cm.flush()
    WebStorage.getInstance().deleteAllData()
}
```

### 验证（物理机 SM-F9760 / API 37）

| 项目 | 清理前 | 清理后 |
| --- | --- | --- |
| `photo_assets` 行数 | 164 | **0** ✅ |
| `sync_state` 行数 | 1 | **0** ✅ |
| Coil `cache/image_cache/` | 57344 B 内容文件 | **仅剩 `journal` 空索引** ✅ |
| 磁盘 Cookie 表 (`app_webview/Default/Cookies`) | 19 项 | **0 行** ✅ |
| 加密存储 Cookie 字段 | 有 | **已删（仅剩 keyset）** ✅ |

日志证据（一次性完整执行，无异常）：

```
16:55:39.424 I/PhotoRepository: Session teardown started - blocking all photo network entry points
16:55:39.428 I/PhotoRepository: Photo database wiped (photo_assets + sync_state)
16:55:39.632 W/PhotosViewModel: checkZoneReady failed: 正在退出登录，已暂停照片同步   ← 守卫生效
16:55:39.696 I/ICloudPhotoCoil: Coil memory + disk caches cleared
16:55:39.699 I/ICloudAuth: WebView cookies and WebStorage cleared
16:55:39.702 I/ICloudAuth: Logout complete (region=global) db=true coil=true
16:55:39.702 I/PhotoRepository: Session teardown finished - photo network entry points re-enabled
```

退出后 Cookie 探针回到 `total=0 token=false hsa=false pcs=false valid=false`，
登录页重新加载后出现的 `total=1` 属于苹果登录页自身下发的新 Cookie，不是凭证残留。

### 验证中发现的额外缺陷：Cookie 探针竞态把会话写回

首轮实机验证时（模拟器 / 1523 项图库），数据层清洗全部成功，但 **`shared_prefs` 重启后仍有 7 个业务键**。
日志暴露了竞态：

```
16:58:38.725 W/PhotosViewModel: checkZoneReady failed: 正在退出登录，已暂停照片同步   ← 守卫生效
16:58:38.857 I/ICloudAuth: Three-element cookie validation PASSED, handing over to Go core once.
16:58:38.859 I/ICloudAuth: Calling Go InitSession (isChinaMainland=true, hasHsaTrust=true)
16:58:39.023 I/ICloudAuth: WebView cookies and WebStorage cleared
16:58:39.028 I/ICloudAuth: Logout complete (region=cn) db=true coil=true
```

**根因**：`removeAllCookies` 是异步的。在它完成之前，登录页的 `onPageStarted` 已经触发，
此时 `CookieManager` 仍持有**旧的 19 项 Cookie**，探针校验通过 → `onCookiesExtracted()`
→ `saveSession()` + `initSession()`，把刚销毁的会话原地重建。

**为何旧守卫没能拦住**：`onCookiesExtracted()` 原有的挡位是
`if (_uiState.value is Authenticating || is LoggedIn) return`。`logout()` 第一时间就把状态
置为 `NotLoggedIn`，所以这个判断**恰好放行**了竞态回调 —— 典型的「异步状态机挡不住同步回调」
（见 [docs/PITFALLS.md](PITFALLS.md) §7）。

**修复**：新增同步哨兵位 `AuthViewModel.isLoggingOut`：

```kotlin
@Volatile private var isLoggingOut: Boolean = false

fun logout() {
    isLoggingOut = true                  // 第一行，早于任何清理
    ...
    viewModelScope.launch {
        repository.beginWipe()
        try { /* 四阶段清理 */ }
        finally {
            repository.endWipe()
            delay(COOKIE_CAPTURE_GRACE_MS)   // 等登录页彻底重载
            isLoggingOut = false
        }
    }
}

fun onCookiesExtracted(result: CookieValidationResult) {
    if (isLoggingOut) { ...; return }    // 新增
    ...
}
```

`forceExtractCookies()` 同步加同样守卫。`COOKIE_CAPTURE_GRACE_MS = 800L` 是为了覆盖
「Cookie 已删但登录页尚未完成重载」的窗口。

**修复后同一场景日志**（危险窗口内探针全程为空，再无 `InitSession`）：

```
17:00:23.087 I/PhotoRepository: Session teardown started - blocking all photo network entry points
17:00:23.118 I/PhotoRepository: Photo database wiped (photo_assets + sync_state)
17:00:24.046 D/ICloudAuth: checkCookies source=onPageStarted
17:00:24.047 D/ICloudAuth: cookie probe @https://www.icloud.com.cn/ total=0 ... valid=false
17:00:24.168 I/ICloudAuth: WebView cookies and WebStorage cleared
17:00:24.188 I/ICloudAuth: Logout complete (region=cn) db=true coil=true
17:00:25.048 D/ICloudAuth: Credential capture re-enabled      ← 800ms 守卫窗口结束
17:00:25.768 D/ICloudAuth: cookie probe @... total=0 ... valid=false
```

| 项目 | 修复前 | 修复后 |
| --- | --- | --- |
| `photo_assets` | 320 → 0 | 320 → 0 ✅ |
| `sync_state` | 1 → 0 | 1 → 0 ✅ |
| Coil 缓存文件数 | 56 → 0 | 56 → 0 ✅ |
| `shared_prefs` 业务键 | **7（会话写回）** | **1（仅 `is_china_mainland`）** ✅ |
| 危险窗口内 `InitSession` | 被触发 | **未出现** ✅ |

> [!NOTE]
> 退出后 `shared_prefs` 保留 2 个 `__androidx_security_crypto_encrypted_prefs_*_keyset__`
> （加密密钥，**必须**保留）和 1 个加密的 `is_china_mainland`。后者是 `clearSession()`
> 的既定行为：记住用户上次选的服务区域，让登录页不变区。

### 经验教训

> [!IMPORTANT]
> **退出登录 = 清除「一切账号作用域状态」，而不只是 Cookie。** 凡是以服务端 ID 作主键的
> 本地缓存（Room 行、图片字节、同步游标），在会话重建代际后会变成“有效 key + 失效凭证”的
> 最恶劣组合 —— 表面有数据，实则全部不可用。

> [!WARNING]
> 清理顺序必须是「**阻断 → 数据层 → 图片层 → 会话层**」。先清数据层再阻断，飞行中的
> `upsertAll()` 会把已删的行写回；先清 Cookie 再清数据，则中途任何一次自动刷新都会用空
> Cookie 打 Apple 接口。

> [!CAUTION]
> **异步清理必须配合同步哨兵位。** WebView 的 Cookie 回调不关心你是否正在退出；
> `removeAllCookies` 完成前旧 Cookie 仍然可读。只用 `AuthUiState` 判断挡不住这种竞态，
> 必须在 `logout()` 第一行就置 `@Volatile` 布尔位，并在清理 + 页面重载都结束后才释放。

---

## ISS-026：Cookie 序列化顺序抖动与「会话重建即失效」的错误假设

**优先级**：🟠 P1（每次真会话重建都全库标脏，是 P2-008 刷新洪泛的真正来源）
**归属**：`../go-core/session.go` → `BuildCookieHeader()`；
`feature/photos/data/PhotoRepository.kt` → `applyRefreshedCookies()` / `ensureGoSession()`；
`feature/photos/data/image/ICloudPhotoCoil.kt` → `URL_MAX_AGE_MS` / 代际判定
**发现时间**：ISS-025 验证期间顺带发现
**对应待办**：原 P2-019
**状态**：✅ 已修复

### 现象

在一次**普通冷启动**（未换号、未过期）中，日志出现：

```
16:55:24.174 D/PhotoRepository: Cookies rotated (len 2893 -> 2893)
16:55:24.177 I/PhotoRepository: Session generation -> 3 (URLs previously minted are now stale)
```

前后**长度完全相同**（2893），却被判定为「已轮转」，并进而 bump 了 generation。

### 根因一：Cookie 序列化顺序抖动

`BuildCookieHeader()` 遍历 Go `map` 拼装 Cookie 字符串：

```go
for k, v := range s.CookieMap {   // ← map 迭代顺序在 Go 中是随机化的
    b.WriteString(k); b.WriteString("="); b.WriteString(v)
}
```

Go 语言**故意**打乱 map 的迭代顺序，因此同一份 Cookie 集合每次序列化出的字符串顺序都不同。
而 `applyRefreshedCookies()` 用**字符串相等**判断是否轮转：

```kotlin
if (updatedCookies.isNullOrBlank() || updatedCookies == oldCookies) return false
```

两者结合 → `updatedCookies != oldCookies` 恒为 true → 每次冷启动全库标 stale。

### 根因二（更深）：`/validate` 每次轮换两个 Cookie

修复顺序问题后，实测发现仍有 bump：

```
Cookie diff: added=[] removed=[] valueChanged=[X-APPLE-WEBAUTH-TOKEN, X-APPLE-WEBAUTH-VALIDATE]
```

连续 3 次强制重建，3/3 一致。**苹果每次 `/validate` 握手都会下发新的
`X-APPLE-WEBAUTH-TOKEN` 与 `X-APPLE-WEBAUTH-VALIDATE`。**

所以即使顺序抖动修好了，「Cookie 值变化 → bump generation」这条规则本身也是错的。

### 根因三（根本）：前提假设本身不成立

代码与文档都认为「会话重建 → 所有已签发 URL 立即失效」（见 [AGENTS.md](AGENTS.md) §4.4 /
§5.4 / §6）。**实测证伪了这一点**，详见
[`.artifacts/url_lifetime_experiment.md`](.artifacts/url_lifetime_experiment.md)。

摘要（物理机 SM-F9760，`curl -r 0-1023`）：

| 实验 | 结果 |
| --- | --- |
| gen=6 的 URL，会话重建到 gen=7 后再测 | **`206` 有效** |
| URL 年龄 882s (14.7m) | `206` 有效 |
| URL 年龄 1065s (17.8m) | `410` 失效 |

结论：**URL 有效性由时间决定，与会话代际无关。** CDN 签名绑定的是 record，不是签发它的会话。

同时实测真实寿命是 **~15–18 分钟**，而非文档所称的「1–2 小时」。

### 修复

**1. Go 侧：`BuildCookieHeader()` 按 key 排序（根治顺序抖动）**

```go
keys := make([]string, 0, len(s.CookieMap))
for k := range s.CookieMap {
    keys = append(keys, k)
}
sort.Strings(keys)                 // ← 关键：确定性顺序

var b strings.Builder
for i, k := range keys {
    if i > 0 {
        b.WriteString("; ")
    }
    b.WriteString(k)
    b.WriteString("=")
    b.WriteString(s.CookieMap[k])
}
return b.String()
```

**2. Kotlin 侧：改为集合比较（防御层）**

```kotlin
internal fun parseCookiePairs(cookieStr: String?): Set<Pair<String, String>> { ... }

val updatedPairs = parseCookiePairs(updatedCookies)
val oldPairs = parseCookiePairs(oldCookies)
if (updatedPairs == oldPairs) {
    Log.d(TAG, "Cookie updates reported but set is unchanged (order-only jitter)")
    return false
}
```

**3. 代际 bump 不再由 Cookie 变化触发**

```kotlin
// 旧：applyRefreshedCookies(...) 返回 true 也 bump
// markSessionReady(bumpGeneration = force || cookiesRotated)

// 新：只有显式 force（401/421 恢复路径）才 bump
applyRefreshedCookies(isChina, dsid, ckUrl)
markSessionReady(bumpGeneration = force)
```

**4. 年龄阈值对齐实测值（45min → 12min）**

```kotlin
// 实测 882s 有效 / 1065s 失效 → 12 分钟留余量
private const val URL_MAX_AGE_MS = 12 * 60 * 1000L
```

原本 45 分钟的设定留下约 30 分钟的盲区：URL 早已 410，却仍被当作有效，只能靠失败重试兜底。

**5. 代际降级为「提示」而非「强制刷新」**

```kotlin
val generationStaleAndAging = generationStale && ageMs > URL_MAX_AGE_MS / 2
if (activeUrl.isNullOrBlank() || ageStale || generationStaleAndAging) { ...refresh... }
```

单纯的代际不符**不再**触发刷新（实验已证其与有效性无关），但保留作为换号场景（P2-018）的
加速信号：代际变了说明可能换了账号，此时把容忍年龄减半。

### 验证

**单元测试**

| 测试 | 内容 |
| --- | --- |
| `TestBuildCookieHeaderIsDeterministic` | 同一 session 连续 100 次序列化结果必须一致，且 key 有序 |
| `TestBuildCookieHeaderPreservesValueBytes` | base64 值（含 `=`）不被排序破坏 |
| `CookieRotationTest`（8 项） | 顺序不同 → 同一集合；值变/增删 → 真轮转 |
| `UrlStalenessTest`（7 项） | 代际不符不刷新、超龄必刷新、常量不得回到 45min |

`go test ./...` 与 `testDebugUnitTest`（26 项）全部通过。

> [!TIP]
> `TestBuildCookieHeaderIsDeterministic` 做过**回归验证**：临时移除 `sort.Strings(keys)`，
> 测试立即失败，并打印出同一份 Cookie 集合两次调用的不同顺序 —— 证明该测试确实能捕获此 bug。

**实机验证（物理机 SM-F9760）**

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| 冷启动 + 强制重建的 generation bump | 每次 +1 | **0 次** ✅ |
| 首屏 + 滚动 3 屏 Coil 加载失败 | — | **0 次** ✅ |
| 42 分钟老 URL 的加载 | 依赖 410 兜底 | `refreshed -> host=cvws...` **主动换链** ✅ |
| 深层图（IMG_1881/1296/1880/1878） | — | 正常显示 ✅ |

关键日志（重建发生、Cookie 变了，但**没有** bump）：

```
17:40:14.975 I/PhotoRepository: Rebuilding Go core session (force=false)...
17:40:16.075 D/PhotoRepository: Cookie diff: added=[] removed=[] valueChanged=[X-APPLE-WEBAUTH-TOKEN, X-APPLE-WEBAUTH-VALIDATE]
17:40:16.075 D/PhotoRepository: Cookies rotated (pairs 18 -> 18)
                                          ← 此处不再出现 "Session generation -> N"
```

### 经验教训

> [!CAUTION]
> **不要相信「重建会话 = 所有 URL 作废」这类口头结论 —— 用 curl 实测。**
> 本项目为此多写了大量防御代码（代际戳、刷新去重、失败重试），而根因只是一个错误前提。
> 验证方法极简：从 DB 取一条 URL，`curl -r 0-1023`，看 200/206 还是 410。

> [!WARNING]
> **Go 的 map 迭代顺序是随机的。** 任何「遍历 map 拼出字符串，再拿去做相等比较」的代码
> 都是定时炸弹。要么排序，要么比较集合。

> [!NOTE]
> **上游下发的短期令牌不等于凭证轮转。** 苹果每次 `/validate` 都换
> `X-APPLE-WEBAUTH-TOKEN` / `X-APPLE-WEBAUTH-VALIDATE`，但既不影响已签发 URL，
> 也不影响任何业务能力。判定「凭证变化」必须基于**会产生后果的**字段变化。

---

## ISS-027：Paging 3 RemoteMediator 导致深翻页跳顶，改用月分组本地分页根治

**优先级**：🔴 P0
**归属**：`feature/photos/ui` `PhotosScreen` / `PhotosViewModel`；`feature/photos/data` `MonthScanEngine`；`PhotoRepository` / `PhotoDatabase` / `PhotoDao`
**发现时间**：Phase 2 UI 重构
**对应待办**：原 P2-016（含 ISS-021 根治）

### 现象

1. 深翻页时列表跳回顶部（ISS-021，此前只能调参缓解）
2. 扫描报告完成，但每月标题始终带「· 扫描中」
3. **快速上滑时月份标题先出现、内容不加载**，极端情况下“只加载最上面那一个月”
4. 升级/换号后顶部显示「已加载 164 项」，主体却是「暂无 iCloud 照片」

### 根因（四个独立缺陷，逐个击破）

#### ① 跳顶：`RemoteMediator` 架构性缺陷

Paging 3 在每次 `MediatorResult.Success` 后**无条件 invalidate `PagingSource`**，重载窗口 = `initialLoadSize`。超出窗口的 `firstVisibleItemIndex` 被 clamp。已实测确认塔陷点恒等于 `initialLoadSize`（60→60、360→360、1200→1200），**无参数可解**。

#### ② 内容不加载：嵌套惰性布局

每个月是一个嵌套的 `LazyVerticalGrid`。嵌套惰性布局必须给定固定高度，于是**内层拥有自己的测量循环**。快速 fling 后内层只量出行首一行、其余折叠。

#### ③ 月份永远「扫描中」：`isComplete` 被反复覆盖

三重叠加：`markAllMonthsComplete()` 只在循环正常结束时调用；`rebuildMonthIndex()` 每次重建写回 `false` 且先于盖章执行；扫描中每批次也写 `false`。

#### ④ 有数据但看不见：`yearMonth` 从未计算

`parsePhotoItems` 不计算 `yearMonth`，全部行以 `0` 入库；月查询 `WHERE yearMonth = :ym` 永远选不到。唯一补偿是 `scanAll()` 开头的回填，但 `isComplete == 1` 时提前 return，回填被跳过。

### 修复方案

#### 1. 彻底移除 Paging 3，改为月分组 + 行展开

- 删除 `PhotoRemoteMediator.kt`、`photoPagingFlow`、`PhotoDao.pagingSource()`、`INITIAL_LOAD_SIZE`、`PREFETCH_DISTANCE`
- 新建 `MonthScanEngine` 接管全库扫描（断点续扫 / 边扫边看 / 指数退避重试）
- `LazyColumn` 直接发出**行 item**，每个格子都是滚动布局的直接子项，嵌套问题从根上消失

#### 2. `isComplete` 改为派生值

```kotlin
private suspend fun refreshMonthIndex(yearMonth: Int) {
    // 直接从 scan_state 读取，而非由调用方传入
    val walkFinished = scanStateDao.get(ScanStateEntity.KEY_MONTH_SCAN)?.isComplete == true
    monthIndexDao.upsert(PhotoMonthIndexEntity(..., isComplete = walkFinished))
}
```

无论何时重建、由谁触发，全表状态均与「行走是否走完」一致。

#### 3. 写入时即分桶

```kotlin
// parsePhotoItems
PhotoAssetEntity(..., yearMonth = MonthBucketing.yearMonthOf(assetDate))
```

同时 `syncIncremental` 新增 `rebuildMonthIndexFor(...)`，增量同步的照片不再滞留 `yearMonth = 0`。

#### 4. 月份订阅改为全量

可见性驱动的订阅在 fling 中每帧重建，月份刚订阅就被拆、回来时数据未到 → 空白。月份只持 `PhotoAssetEntity`（无位图），全量订阅代价可接受。

#### 5. 换号检测

`ensureGoSession()` 发现 `dsid` 变化时主动 `wipeAllData()`，不再依赖用户走退出登录流程。

### 验证结果（真机 SM-F9760，15,936 项 / 108 个月）

| 场景 | 修复前 | 修复后 |
| --- | --- | --- |
| 6 次快速上滑 | 标题正常、内容只渲 1 张 | 28 个格子正常渲染 |
| 30 次极速上滑（30ms） | 空白（截图 514KB） | 内容完整（2.37MB） |
| 反向 30 次极速上滑 | — | 内容完整（2.37MB） |
| 月份 `isComplete` | 41~90 个月为 0 | 108 个月全部为 1 |
| `yearMonth = 0` 滞留 | 164 行 | 0 行 |
| 年份跳转 → 2020 | — | 正确落位 2020年11月 |
| 冷启动会话重建 | — | 0 次 |

截图体积作客观判据：空白状态 118~514KB，正常状态 1.6~2.4MB。

### 经验教训

> [!IMPORTANT]
> **不要嵌套惰性布局。** 内层 lazy 布局必须给定固定高度，于是它拥有自己的测量循环，
> 外层 fling 时两者会失去同步。改为把内容展开成外层的直接子项，虚拟化只发生在一个地方。

> [!WARNING]
> **派生状态不要作为参数传递。** `isComplete` 由调用方猜、又由多处写回，必然被覆盖。
> 让它每次从单一真理源（`scan_state`）读取，无论重建多少次结果都一致。

> [!CAUTION]
> **不要用“列表是否为空”判断是否需要初始化。** 升级安装有数据但未分桶，
> `count == 0` 为假，初始化被跳过 —— 表现为“有数据但看不见”。判断依据应是
> “是否存在未分桶行”，而非行数。

---

## ISS-028：Go 层缺结构化错误字段，导致 Kotlin 靠正则猜

**优先级**：🟡 P2（技术债，但被月扫描重构放大为阻塞项）
**归属**：`../go-core/errors.go`（新）、`../go-core/photos.go`、`feature/photos/data/PhotoRepository.kt`
**发现时间**：Phase 2 UI 重构前置
**对应待办**：原 P2-015

### 现象

Go 侧只返回 `errorMessage` 字符串，Kotlin 只能靠正则/字符串匹配猜错误类型。

### 根因

早期实现：`jsonStr.contains("401")` 被 base64 `continuationMarker` 误伤（ISS-013 / ISS-015）。
止血后仍是词边界正则。虽然可靠，但**随扫描器提高请求密度会大幅放大风险**：一次误判 = 一次
`/validate` 往返 + 全部缓存 URL 重签。

### 修复方案

新增 `../go-core/errors.go`：

```go
const (
    ErrCodeAuthExpired = "AUTH_EXPIRED"
    ErrCodeRateLimited = "RATE_LIMITED"   // 退避重试，不重建
    ErrCodeTimeout     = "TIMEOUT"         // 退避重试，不重建
    // ...
)

type ResultBase struct {
    Success      bool   `json:"success"`
    ErrorCode    string `json:"errorCode,omitempty"`
    HttpStatus   int    `json:"httpStatus,omitempty"`
    ErrorMessage string `json:"errorMessage,omitempty"`
}
```

四个导出结果结构体嵌入 `ResultBase`。Go 匿名字段会提升到 JSON 顶层，所以**线格式仍是扁平的**，
与现有 Kotlin 解析完全兼容（零破坏性变更）。

Kotlin 侧抽出纯函数 `classifyCoreResponse()` → `CoreErrorKind`，`isAuthError` 降为薄封装，
调用点零改动。

### 验证结果

**真实故障下的实测日志**（扫描中网络中断）：

```
scan transient failure (NETWORK), retrying in 1000ms (attempt 1/5)
... 2000ms -> 4000ms -> 8000ms -> 16000ms
scan giving up after 6 transient failures: dial tcp: lookup ... no such host
scanAll stopped early (scanned=6719, cursor kept) - will resume on the next trigger
```

`NETWORK` 被正确分类为**退避重试**，而非重建会话 —— 这正是本修复的核心价值。

| 测试 | 结果 |
| --- | --- |
| `go test ./...`（含 `errors_test.go`） | ok |
| `CoreErrorTest`（18 项） | 全绿 |
| `testDebugUnitTest` 总计 | 70 passed |

> [!NOTE]
> 为测试 JSON 解析逻辑，新增了 `testImplementation(libs.json)`（`org.json:json`）。
> 必须：Android 的 `org.json` 在 JVM 单测环境是**空实现**，每个方法都抛异常，
> 导致所有用例静默返回 `UNKNOWN`。

---

## ISS-029：`InitSession` 未将握手结果回写至 `Session`，导致进程内调用拼出空 host

**优先级**：🟠 P1（潜伏期长，一旦新增 Go 侧读数据的入口就必现）
**归属**：`../go-core/core.go` → `InitSession()`
**发现时间**：照片详情面板可行性探针

### 现象

探针在 App 进程内调用：

```
initSession -> {"success":true,"dsid":"20118126497",
                "ckDatabaseUrl":"https://p203-ckdatabasews.icloud.com.cn:443"}
```

紧接着任何读数据的调用（`/records/query` 等）却失败：

```
Post "/database/1/com.apple.photos.cloud/production/private/records/query?...&dsid=&...":
unsupported protocol scheme ""
```

注意 URL 里 **host 为空、`dsid` 为空**。

### 根因

`InitSession` 拿到 `ValidateSession()` 的返回值后**直接序列化返回**，从未把它同步到
`session` 结构体：

```go
// ❌ 修复前
result, err := session.ValidateSession()
// ...
resBytes, _ := json.Marshal(result)
return string(resBytes)   // session.DSID / session.CKDatabaseURL 仍是零值
```

而 `photosBaseURL()` 读的是 `s.CKDatabaseURL` 与 `s.DSID`，于是拼出空 host。

### 为何长期未暴露

Kotlin 层读的是 **`InitSession` 的返回值**，自己存进加密存储，下次启动再用 `ensureGoSession`
重新 init 并把路由信息传入。所以主流程里永远没人依赖 `Session` 内部的这两个字段。

一旦出现**进程内直接调 Go 读数据**的路径（诊断面板、未来 Go 侧新增功能），必现此问题。

### 修复

```go
// ✅ 握手成功后立即回写
session.mu.Lock()
if result.DSID != "" {
    session.DSID = result.DSID
}
if result.CKDatabaseURL != "" {
    session.CKDatabaseURL = result.CKDatabaseURL
}
session.mu.Unlock()
```

### 验证

修复后同一探针正常返回字段字典与 GPS 数据。

### 经验教训

> [!IMPORTANT]
> **“返回值”不等于“对象状态”。** 任何一次性结构体返回值都必须同步到长期存活的对象上，
> 否则调用方一旦改变（从读返回值改为读对象），就会在很远的地方以“空字符串”的形式爆。

---

## ISS-030：已删除照片永不消失，且「最近删除」照片被当成正常照片入库

**优先级**：🔴 P0（数据正确性；用户可见的“删了还在”与“不该看到的照片”）
**归属**：`../go-core/photos.go` + `PhotoRepository` / `MonthScanEngine` / `PhotoDao`
**发现时间**：Phase 2 收尾真机联调

### 现象

两个互补的症状，本质是同一个缺口：

1. **删不掉**：在其他 Apple 设备（或 iCloud Web）上删除并清空「最近删除」的照片，
   在本 App 里**永远存在**，图片仍能正常加载。用户描述为「首页那几个大头照和身份证图片」。
2. **不该出现**：仍处于「最近删除」30 天保留期的照片，被当成正常照片展示在画廊里。

### 日志证据

本地库与云端已经不一致，且**多次冷启动、多次手动增量同步都无法收敛**：

```
# 本地库里还躺着云端早已不存在的记录
SELECT id, filename FROM photo_assets ORDER BY assetDate DESC LIMIT 3;
C0D822C7-0FAA-422E-9ED5-7018DEA47AB3|C0D822C7-0FAA-422E-9ED5-7018DEA47AB3   ← master 当照片存进来了

# 增量同步“成功”，但变更数为 0
PhotoRepository: Starting incremental sync with token: HwoECMjsCRgAIhY...
PhotosViewModel: Sync finished, count=0
```

`/records/changes` 对这个 syncToken 返回**空变更集**——因为被软删除的 `CPLAsset` 记录
**仍然存在**于 CloudKit（只是 `isDeleted = 1`），tombstone 只有 30 天保留期满、真正 expunge 后才会产生。

### 根因（四条叠加）

#### 1. `isDeleted` 从未出现在 `desiredKeys` 里

```go
// ❌ 修复前：defaultDesiredKeys 里没有 "isDeleted"
var defaultDesiredKeys = []string{
    "recordName", "recordType", "recordChangeTag", "masterRef",
    "assetDate", "addedDate", "isFavorite", "isHidden", // ← isHidden 有，isDeleted 没有
    ...
}
```

CloudKit 只会返回 `desiredKeys` 里列出的字段。于是 `parseRecords` 里那句
`rec.Fields["isDeleted"].AsInt64() == 1` 的判定**恒为 false**——不是逻辑写错，是字段根本没传回来。

#### 2. 孤儿 `CPLMaster` 被无条件伪造成照片

`CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted` 这个视图只在**服务端**过滤 `CPLAsset`，
但与之配对的 `CPLMaster`（含 `downloadURL`）在回收站 30 天保留期内**照常返回**。
旧代码把没有 Asset 的 Master 补成 `assetDate = 0` 的照片：

```go
// ❌ 修复前：任何未配对的 master 都被捏造成一张照片
for mID, m := range masters {
    if !matchedMasters[mID] {
        photos = append(photos, PhotoItem{ID: mID, MasterID: mID, AssetDate: 0, ...})
    }
}
```

这既解释了「大头照」的来源，也解释了上面库里那行 `id == masterId` 的记录。

#### 3. 「最近删除」查询的 recordType 用错了三次

按 `pyicloud` 的 `PHOTO_ALBUMS` 表去拉回收站列表，连续三次被服务端打回：

| 尝试 | 服务端返回 |
| --- | --- |
| `recordType: CPLAssetDeletedByExpungedDate` | `404 Did not find record type` |
| `...InSmartAlbumByAssetDate` + `filterBy: smartAlbumType` | `400 Expected a parameter with name: smartAlbum` |
| 同上 + `smartAlbum` 放 `query` 内 / 顶层 | 同样的 `400` |
| **`recordType: CPLAssetAndMasterDeletedByExpungedDate`** | ✅ `success: true` |

关键在于 `obj_type` 与 `list_type` 是**两个不同的标识符**：前者只用于
`HyperionIndexCountLookup` 的计数查询，后者才是真正的 recordType。

#### 4. 增量同步基线几乎不存在

- `MonthScanEngine` 跑完全库遍历后**从不写入** `sync_state.syncToken`；
- `syncIncremental` 只拉一页，`moreComing = true` 时直接丢弃后续变更；
- `syncPhotos` 的启动路径在全库扫完后**直接 return**，从不触发增量同步。

于是「服务端有变更」这件事根本没有机制被发现。

### 修复

**Go 核心层** `../go-core/photos.go`

- `defaultDesiredKeys` 增加 `"isDeleted"`；`PhotoItem` 增加 `IsDeleted bool`
- 删除「孤儿 master 伪造照片」的整段逻辑（回收站照片的 master 不允许进入画廊）
- `deletedIDs` 同时收集 `recordName` 与 `masterRef`（tombstone 可能只带其中一个）
- 新增 `FetchRecentlyDeleted()`，走 `CPLAssetAndMasterDeletedByExpungedDate` 分页拉全量回收站 ID

**Android 数据层**

| 位置 | 改动 |
| --- | --- |
| `parsePhotoItems` | 识别 `isDeleted`，把对应 `id`/`masterId` 收集进 `trashedIds`，不再产出实体 |
| `PhotoRepository.purgeTrashed()` | 统一清理：删行 → 重建受影响月份索引 → 淘汰 Coil 缓存 → 记入 `recentlyPurgedIds` |
| `syncIncremental` | 多页拉取（`moreComing` 循环，上限 10 轮）；tombstone 与 trashed 合并处理；`recentlyPurgedIds` 拦截「旧 syncToken 重放」导致的复活 |
| `purgeRecentlyDeleted()` | 启动/刷新时定向清理回收站 |
| `PhotoDao.deleteByIds` | 改为 `WHERE id IN (...) OR masterId IN (...)`，兼容两种维度 |
| `PhotoDao.deleteOrphanRows` | 清掉历史遗留的 `assetDate <= 0 OR id = masterId` 脏行 |
| `MonthScanEngine` | 扫描前清孤儿行；**每页持久化 `syncToken`**；完成后 `scan_state` 与 `sync_state` 双向对齐 |
| `PhotosViewModel` | 全库扫描完成后串联 `purgeRecentlyDeleted()` + `syncIncremental()` |

### 验证

真机（SM-F9760）实测：

```
fetchRecentlyDeleted raw: {"success":true,"deletedIds":[...4 个真实 ID...]}
purgeRecentlyDeleted - cloud reported 4 recently-deleted ids
```

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| 标题总数 | 15960 | 15957 |
| 2026年10月 分组 | 78 项 | 75 项 |
| 用户指认的残留照片 | 在网格中可见 | 已消失 |
| 冷启动 ×2 + 手动增量同步后 | 依旧残留 | `resurrected=0`，不再复活 |

`go test ./...` 新增三个用例均通过：

- `TestOrphanMastersNotAdded` —— 只有 `CPLMaster` 时**不产生**照片
- `TestFetchRecentlyDeleted` —— 回收站查询同时返回 asset 与 master 两个维度
- `TestFetchChanges` —— `isDeleted = 1` 的 `CPLAsset` 被识别为删除

### 经验教训

> [!CAUTION]
> **`desiredKeys` 是白名单，不是过滤器。** 少写一个字段，服务端的判断分支就永远走不到，
> 而且没有任何报错——表现为「代码逻辑看着没错，就是不生效」。新增任何“依据某字段过滤”的行为，
> 必须先确认该字段在 `desiredKeys` 里。

> [!IMPORTANT]
> **CloudKit 的「删除」是两段式的，tombstone 只在第二段出现。**
> 移入「最近删除」时 `CPLAsset` 记录**还在**，只把 `isDeleted` 置 1，`/records/changes`
> **不会**给出任何变更。指望增量同步发现软删除是错的，必须显式拉回收站索引。

> [!WARNING]
> **`xxxByExpungedDate` 这类标识符要分清 `obj_type` 与 `list_type`。**
> 前者给 `HyperionIndexCountLookup` 计数，后者才是 `records/query` 的 `recordType`。
> 探测的可靠依据是服务端错误文案（`404 Did not find record type` vs
> `400 Expected a parameter`），而不是猜参数位置。

---

## ISS-031：全库扫描只增不减，已永久删除的照片永远残留

**优先级**：🔴 P0（数据正确性；用户可复现的“删了还在”）
**归属**：`MonthScanEngine` + `PhotoRepository` + `PhotoDao` + Room v5/v6/v7
**发现时间**：Phase 2 收尾，ISS-030 修复后的真机复查
**状态**：✅ 已修复

### 现象

ISS-030 修完后，用户在 iCloud 上删除并**清空回收站**的照片仍在 App 里，且**多次冷启动、多次增量同步都无法收敢**。

与 ISS-030 的区别：那条修的是“不该入库的入库了”（`isDeleted` / 孤儿 master），
这条是“入库后永远不删”——即使一条都没误入库，删除仍然不会发生。

### 日志证据

```
# 本地库里还躺着云端已彻底不存在的行
photo_assets 总行数               15957
scan_state.scannedCount        15936   ← 比实际少 21

# 语义上是“全量扫描”，实际只做 upsert：
MonthScanEngine: scan complete - 15937 assets across 108 months
# 但库里 → 15958 行，多出来的那些没有任何路径会删
```

数据库取证还发现 155 行属于「同一 `masterId` 多条 `CPLAsset`」，例如：

```sql
SELECT id, filename, assetDate FROM photo_assets WHERE masterId='ASiWjzUxvNN81ZXosCMKLSKZYkFK';
54FBED04-…|IMG_1882.JPG|2024-10-06 22:54:15
79072B11-…|IMG_1882.JPG|2024-08-21 18:05:07
9F987577-…|IMG_1882.JPG|2024-08-09 16:11:28
```

### 根因

**扫描是“只增不减”的。** 它把云端返回的一切 upsert 进去，但从不对“这次没返回的”做任何处理。
而两条删除消息源都盖不住“已永久删除且已离开回收站”的记录：

| 路径 | 能覆盖 | 盖不住 |
| --- | --- | --- |
| `/records/changes` tombstone | 真正 expunge 的记录 | 软删除（`isDeleted=1`）**无任何消息** |
| 回收站索引 | 仍在“最近删除”内的资产 | **已永久删除**的不在这个索引里 |

于是：**一条记录被永久删除后，如果既没产生 tombstone、又已离开回收站索引，
本地就永远没有机会得知它消失了。**

### 修复

把全库扫描从“累加”升级为真正的 **reconciliation（对账）**：

1. **`scan_seen_ids` 暂存表（Room v6）** —— 扫描每页把 `id` 与 `masterId` 写进去，代表“云端仍持有”。
2. **`walkCompleted` 显式信号** —— `runScanLoop` 返回 `ScanRunResult(scanned, walkCompleted)`，
   `walkCompleted = true` 全代码只有一个赋值点：云端不再给 `continuationMarker` 的那一页。
   任何提前退出（wipe/401/网络放弃/协程取消）都是 `false`。
3. **`Reconcile.decide()` 纯函数** —— 保守规则：
   - `id ∉ seen` 且 `masterId ∉ seen` → 删除
   - `masterId ∈ seen` → **全部保留**（同一媒体还有别的存活资产）
4. **`recentlyPurgedIds` 持久化（Room v7 `purged_ids`）** —— 原本是内存 Set，冷启动即清空，
   而陈旧 `syncToken` 的重放恰好发生在冷启动；改为持久化后，删掉的照片不会被“复活”。
5. **`dataMutationMutex`** —— 对账 / 增量同步 / 回收站清理共用一把锁，防止 read-then-delete 交错。
6. **多 CPLAsset 串写修复** —— `updateUrls` 从 `WHERE masterId =` 改为 `WHERE id =`，
   `getPhotoByMasterId`（`LIMIT 1`）改为 `getPhotosByMasterId`（全组）。

### 验证

真机（SM-F9760）实测：

```
scan complete - 15937 assets across 108 months
reconcile: seen=31795 local=15937 purged=22 keptByMaster=0 keptBySeen=15937
```

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| `photo_assets` 行数 | 15958 | 15937 |
| `month_index` 求和 | 15958 | 15937（一致） |
| `scan_state.scannedCount` | 15936（偏差 21） | 15937（一致） |
| 冷启动重扫后复活数 | — | **0** |

`go test ./...` 新增 `TestFetchPhotoPageReportsDeletedIds`；
`testDebugUnitTest` 137 项（含 11 项 `ReconcileTest`）全绿。

### ⚠️ 实施中自己踩的坑（已修复，很有价值）

首次真机验证时，**中途杀进程触发“断点续扫”**，而对账把 700 张真实照片误删了。

原因有两层：

1. `scanAll` 在每次启动都调 `resetSeenIds()`，而**续扫只覆盖剩余页**，
   于是 `seen` 只剩库的后半段。
2. 安全闸门原本是“`seen` ≥ 本地 50%”，而“只剩后半段”恰好高于 50%，**闸门没拦住**。

修复：

- `scan_seen_ids` **仅在 `lastMarker == null`（全新一轮）时清空**；续扫保留前次已记录的 id。
- 闸门改为严格版：**`seenIdCount ≥ localCount`**。
  推导：一个真正完整的 walk，每一行本地记录都至少贡献一个 id 到 `seen`，
  所以完整 walk 必然满足该不等式；任何不满足都是“id 集不完整”的铁证，与原因无关。

验证：续扫日志出现 `resuming walk - keeping earlier page ids`，对账结果 `purged=0`，库完好。

> [!CAUTION]
> **这是“误删可恢复”设计的实战验证。** 700 行被删后，清 `scan_state` 重新全量扫一遍就全部回灌了（15937 恢复），
> 因为对账只删本地缓存，云端数据从未被动过。这也说明：不要为了“避免误删”而放弃对账，
> 正确做法是把闸门做严、把误删做成可回灌。

### 经验教训

> [!IMPORTANT]
> **“全量扫描”不等于“能证明缺失”。** 只 upsert 的扫描只能回答“云端有什么”，
> 回答不了“云端没有什么”。后者必须靠“本次遍历到的全量 ID 集合”做差集。

> [!WARNING]
> **带游标的增量式任务，其“本轮完成”的判据不能是“函数正常返回”。**
> 必须把“是否走完”作为显式返回值传递，否则断点续扫会把自己的前半段当成“不存在”。
> 同理，任何“清理”动作都必须先回答：本次观察到的集合，是全集还是子集？

> [!TIP]
> **“误删可恢复”要作为设计约束写进代码。** 本例中它把一次数据事故降级为一次浪费时间的重扫。
> 实施时我们坚持了一个判断：**误删一张的代价 ≫ 漏删一张的代价**，所以选了全量对账（而非只靠 tombstone），
> 但用三重闸门把误删风险压到接近零。

---

## ISS-032：设置页侧边返回手势直接退出应用

**优先级**：🟠 P1（用户可复现，且现象“像是崩溃”）
**归属**：`ui/AppShell.kt` + 新增 `ui/settings/SettingsContainer.kt`
**发现时间**：设置页上线后的首轮真机试用
**状态**：✅ 已修复

### 现象

在设置页使用**侧边返回手势**，会直接回到**桌面**（应用被关闭）；
而左上角返回箭头却正常回到照片页。

### 根因

本项目没有导航图（Navigation Compose），设置页是一个 **overlay**：
`AppShell` 里一个布尔状态 `settingsOpen` + 条件渲染。

问题在于：**Compose 和 `Activity` 都不知道“上面又压了一层”。** 界面换了，
但没人向系统注册返回处理，于是返回事件按标准流程落到 `MainActivity` → 系统退出应用。

左上角箭头之所以对，是因为它直接调 `onBack = { settingsOpen = false }`，绕过了系统分发。

### 第一轮修复（不完整）

加了 `BackHandler { settingsOpen = false }`。退出应用的问题解决了，
但**没有预测返回效果**：拖动全程面板纹丝不动，系统却在画它自己的预览快照，松手才瞬移消失。
观感割裂，且与查看器（`PhotoViewerScreen`）的已实现行为不一致。

### 最终修复

新增 `ui/settings/SettingsContainer.kt`，把设置页包一层，**同时接两路**：

```kotlin
PredictiveBackHandler(enabled = true) { progressFlow ->
    try {
        progressFlow.collect { e -> gestureProgress = e.progress }  // 跟随手指
        closeWithAnimation()                                        // 松手完成
    } catch (e: CancellationException) {
        exitProgress.animateTo(0f, tween(RESTORE_DURATION_MS))      // 放弃 → 回弹
        throw e
    }
}

BackHandler(enabled = true) { closeWithAnimation() }   // 兵底 + 普通返回键
```

两个 `enabled = true` 不冲突：同一次手势只会被其中一个消费。预测返回可用时前者先拿到；
不可用时流程不触发，后者接管。不需要额外探测系统能力。

**动画只动 `graphicsLayer`**（`translationX` + `alpha`），不用 `Modifier.offset` ——
后者改变布局尺寸，等于每帧重新测量整个设置列表。

`gestureProgress` 与 `exitProgress` 分开存：`Animatable.snapTo` 是 suspend，
不能在非挂起的指针回调里调（与 `ViewerTransitionState` 同一套做法）。

### 验证（SM-F9760）

| 路径 | 结果 |
| --- | --- |
| 侧边手势拖满松手 | ✅ 面板跟随手指平移淡出 → 回到照片页 |
| 侧边手势拖到一半取消 | ✅ 回弹，设置页保持打开 |
| 左上角返回箭头 | ✅ 回到照片页 |
| 硬件 / 普通返回键 | ✅ 回到照片页 |
| `topResumedActivity` | 始终 `com.getup.iClouDroid/.MainActivity`，从未退出应用 |

### 经验教训

> [!IMPORTANT]
> **Compose 里的“新页面”不会自动获得返回语义。** 只要它是 overlay（而非
> Activity / 导航图目的地），就必须自己接住返回。三条路径（侧边手势 / 自画箭头 / 返回键）
> **必须行为一致**，新增任何 overlay 页面时先把这三条接上再接其他。

> [!TIP]
> 已有正确实现时（本例的查看器），**必须对齐而不是另写一套**。
> 第一轮修复只做了“不退出应用”这个最低限度，用户立刻就能看出两者观感不一样。

---

## 附：CloudKit 字段实测结论

探针成果（字段字典、`mediaMetaDataEnc` 内部结构、GPS 分布、fingerprint 可用性）已归档至：

- [docs/REFERENCE.md 第 2A 节](REFERENCE.md) —— 完整字段字典与实测样本分布

要点：

| 结论 | 影响 |
| --- | --- |
| `mediaMetaDataEnc` = base64(binary plist) | 详情面板需引入 plist 解析器（建议 `howett.net/plist`） |
| 含 `{Exif}` / `{GPS}` / `{TIFF}` / `{PNG}` 子字典 | 光圈/快门/ISO/焦距/镜头/GPS/海拔均可取 |
| `{GPS}` 仅有定位的照片才有（实测 8/25） | UI 必须按字段存在性条件渲染 |
| `resOriginalFingerprint` 现成 | **Phase 3 查重无需自己算哈希** |
| `CPLAsset.locationEnc` 独立存在 | 只判断“有无位置”时不必解码 plist |
| `/records/query` 的 `resultsLimit` 必须 > 1 | 传 1 直接 400 |

---

## ISS-033：相册 UI 重构与日/月视图、Monet 动态取色

**优先级**：🟡 P2（体验升级，非阻塞功能）
**归属**：`ui/theme/`（新）、`data/settings/AppSettings.kt`、`ui/settings/`、`feature/photos/ui/`、`feature/local/`（新）
**发现时间**：Phase 2.5 完成后的 UI 改造需求
**状态**：✅ 已完成

### 需求

1. 参考 LineageOS **Glimpse** 重做相册 UI（原界面观感粗糙）
2. 新增以「日 / 月」为单位的排布视图
3. 深/浅色模式开关（可跟随系统）+ Monet 动态取色
4. 本地相册文件夹管理（含媒体权限），为 Phase 3 按文件夹备份预留接口
5. 可设为系统云端媒体提供方

### 架构决策：为何不能把根索引从「月」改成「日」

> [!CAUTION]
> **`photo_assets.yearMonth`（`yyyyMM`）是 Phase 2.5 扫描/对账体系的地基，不可替换为 `yyyyMMdd`。**
>
> 一万张级图库按日分桶会裂出上百个分组，导致：
> - `photo_month_index` 与 `MonthScanEngine` 的断点续扫、全量对账发生连锁破坏
> - 滚动锚点从稳定的“月 key”退化为高频变动的“日 key”，重新引入 ISS-027 的跳顶风险

**实际方案：月为骨架，日只在月内派生。**

| 层 | 做法 |
| --- | --- |
| 持久化 / 扫描 | 保持 `yearMonth`，一字不改 |
| 日分桶 | `DayBucketing.groupPhotosByDay()` 纯函数，**月内**切分为 `DayGroup(dayKey, label, photos)` |
| 渲染 | 月视图：`stickyHeader(h_yyyyMM)` + `items(r_yyyyMM_row)`；<br>日视图：`stickyHeader(dh_yyyyMMdd)` + `items(dr_yyyyMMdd_row)` |
| 约束 | **两种视图均只有一层 `LazyColumn`**，所有行是它的直接子项（AGENTS §6.5） |

### 交付内容

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| 主题 | `ui/theme/ThemeMode.kt`、`Theme.kt`（新） | `SYSTEM/LIGHT/DARK` + Android 12+ `dynamicLight/DarkColorScheme` |
| 设置 | `AppSettings` 新增 `themeMode` / `useDynamicColor` / `galleryViewMode` | 均为 `StateFlow` + `SharedPreferences` |
| 日分组 | `feature/photos/data/DayBucketing.kt`（新） | 本地时区 `yyyyMMdd`，“今天/昨天/前天/M月d日/yyyy年M月d日” |
| 相册页 | `PhotosScreen.kt` 重写 | Glimpse 风格大标题 + 计数徽章 + 日/月切换胶囊 + 8dp 圆角卡片 |
| 本地相册 | `feature/local/`（新） | `LocalAlbumRepository` 读 MediaStore 按 `BUCKET_ID` 归集；`LocalAlbumsScreen` 双/三列卡片 + 权限横幅 |
| 导航 | `Destinations.kt` / `AppShell.kt` | 新增 `Albums` tab；`LocalAlbumsViewModel` 从 `MainActivity` 注入 |

### 数据层新增

`PhotoDao` 新增两个查询（均带索引友好的 `ORDER BY assetDate DESC, id ASC`）：

```kotlin
getMediaPage(limit: Int, offset: Int): List<PhotoAssetEntity>      // PhotoPicker 分页
getStaleUrlAssetIds(olderThan: Long, limit: Int): List<PhotoIdentity>  // URL 预热
```

### 验证

- `DayBucketingTest` 4 项：`yyyyMMdd` 格式、无日期哨兵、相对日期标签、月内分组
- 全量单测 `148 passed`
- 实机（Galaxy Z Fold）：日/月切换、深浅色跟随、Monet 取色、Albums 权限引导均正常

### 经验教训

> [!IMPORTANT]
> **UI 层的新需求不等于数据层要重构。** 日视图看起来需要“按日索引”，但把它做成
> **月内的派生分组**就能同时拿到正确性和性能，且不触动任何扫描/对账代码。
> 改 UI 前先问：这个新分组是“存储维度”还是“展示维度”？

---

## ISS-034：PhotoPicker 云端媒体提供方不被系统识别

**优先级**：🟠 P1
**归属**：`provider/ICloudMediaProvider.kt`（新）、`provider/ShizukuMediaHelper.kt`（新）、`AndroidManifest.xml`、`ui/settings/SettingsScreen.kt`
**发现时间**：ISS-033 第 5 项落地时
**状态**：✅ 已修复

### 现象

`ICloudMediaProvider` 已在 Manifest 正确注册，但系统「云端媒体设置」页面**只显示 Google 相册与“无”**，没有 iClouDroid。

### 排查过程（逐层排除）

| 检查项 | 结果 |
| --- | --- |
| Provider 是否被 PackageManager 注册 | ✅ `Provider Resolver Table` 可见 |
| 权限声明 `MANAGE_CLOUD_MEDIA_PROVIDERS` | ✅ 正确 |
| `content call --method get_cloud_provider` | ✅ 返回 `com.getup.iClouDroid.cloudmedia` |
| `PickerSyncController.allAvailableCloudProviders` | ✅ 已包含本应用 |
| `ConfigStore.allowedCloudProviderPackages` | ❌ **只有 `com.google.android.apps.photos`** |

### 根因：Google 的 OEM 白名单机制

`allowed_cloud_providers` 是 `DeviceConfig`（`mediaprovider` / `storage_native_boot` 命名空间）中的**服务器下发白名单**。
Android 的云端媒体提供方属于 **OEM 试点项目**（每个 OEM 最多提名 3 个应用，需 Google 审核签约）。
未入白名单的应用即使完全合规，**`PhotoPickerSettingsActivity` 也不会渲染它**。

> [!NOTE]
> 这不是 Bug，是刻意的生态管控。但是 `MediaProvider` 同时开放了
> `content call --method set_cloud_provider`，让白名单可以被**直接绕过**——
> 只要调用进程持 `android.uid.shell`（UID 2000）权限。

### 修复：Shizuku 一键激活 + ADB 备用

> [!IMPORTANT]
> 普通第三方 App **拿不到** `MANAGE_CLOUD_MEDIA_PROVIDERS`（`signatureOrSystem` 级别），
> 所以纯应用内方案不可行；而 **Shizuku 恰好持有与 ADB 同等的 shell 权限**。

`ShizukuMediaHelper` 封装三条命令：

```bash
device_config set_sync_disabled_for_tests persistent
device_config override mediaprovider allowed_cloud_providers com.getup.iClouDroid,com.google.android.apps.photos
device_config override storage_native_boot allowed_cloud_providers com.getup.iClouDroid,com.google.android.apps.photos
content call --uri content://media/ --method set_cloud_provider --extra cloud_provider:s:com.getup.iClouDroid.cloudmedia
```

UI 侧（设置 → 系统整合 → 云端媒体提供方）提供双轨：

| 条件 | 行为 |
| --- | --- |
| 已运行 Shizuku | 「请求授权」/「一键激活」按钮，后台静默完成 |
| 未运行 Shizuku | 「复制电脑端 ADB 激活命令」按钮 + 系统设置入口 |

### 验证

- `dumpsys activity provider .../MediaProvider` → `allowedCloudProviderPackages=[com.getup.iClouDroid, ...]`
- `content call --method get_cloud_provider` → `com.getup.iClouDroid.cloudmedia`
- 系统「云端媒体设置」出现 iClouDroid 选项

### 经验教训

> [!TIP]
> **Android 的“平台级集成”多数都带白名单/试点限制。** 遇到“代码完全正确但系统不认”时，
> 优先查 `DeviceConfig` 里是否存在对应的 allowlist 键，而不是继续改代码。

---

## ISS-035：PhotoPicker 缩略图空白，多层柹障式修复与已知残留

**优先级**：🔴 P0（用户首屏即遇到）
**归属**：`provider/ICloudMediaProvider.kt`、`feature/photos/data/db/PhotoDao.kt`
**发现时间**：ISS-034 修复后首次使用系统照片选择器
**状态**：⚠️ **已修 11 层（第 9~11 层为 2026-10 重新归因），待实机复测**

### 现象

系统 PhotoPicker 宫格中**部分缩略图永久空白**，但：

- 点击该图 → “预览”大图**正常显示**
- 选中并确认发送 → **下载正常**
- 往下滑基本正常，**往上滑（进入早期年份）大量空白**

### 为什么这个症状组合极关键

> [!IMPORTANT]
> 「宫格空白 + 预览正常 + 下载正常」三者并存，直接排除了：
> - 网络/URL 问题（下载正常）
> - 文件内容损坏（预览正常）
> - 权限问题（同一个 fd 通道）
>
> 唯一剩下的就是：**宫格缩略图走的是另一条路径**，即 `onOpenPreview` + Glide 解码器选择。

### 柹障一：`CancellationSignal` 中断导致已下数据被丢弃

**日志证据**（修复前）：52 次 MISS 全部伴随 `canceled=true`，且 1 秒后同一资产重试即成功：

```
23:37:16 W openPreview MISS media=03733AAC... canceled=true elapsed=359ms
23:37:22 D openPreview OK   media=03733AAC... bytes=16867 elapsed=189ms   ← 重试成功
```

**根因**：`downloadToFile` 里检测到 `signal.isCanceled` 就 `tempFile.delete()` 并返回。
而 PhotoPicker 一滚动就 cancel，冷缩略图需要「换链 300ms + CDN 300~500ms」，必然超时。

**修复**：**取消 ≠ 失败**。被取消的下载在后台继续完成并落盘，下次请求直接命中磁盘。

### 柹障二：`onQueryMedia` 缺必需列

**根因**：游标缺少 `MEDIA_STORE_URI` 列。AOSP 契约要求该列必须存在（云端条目填 `null`），
缺列时系统无法判定条目可打开，**直接不发起 `onOpenPreview`**。

**修复**：补全列并填入 `null`。

### 柹障三：`SYNC_GENERATION` 与集合信息不一致

**根因**：`onQueryMedia` 的行级 `SYNC_GENERATION` 用的是 `asset.sessionGeneration`（多为 0），
而 `onGetMediaCollectionInfo` 报的是 `sessionManager.getSessionGeneration()`。两者不一致时系统**丢弃该行**。

**修复**：两处统一使用同一 `generation` 值。

### 柹障四：未实现分页

**根因**：一次性返回全库。系统 sync worker 有分页预算，中途放弃 → 只有首屏可用。

**修复**：按 `EXTRA_PAGE_SIZE` / `EXTRA_PAGE_TOKEN` 分页，末页不返回 token。

### 柹障五：单一超时无兜底

**日志证据**（决定性）：

```
23:54:17 W openPreview MISS media=5DE2C10D... canceled=false elapsed=1201ms
23:54:20 D openPreview OK   media=5DE2C10D... elapsed=1ms        ← 文件早已落盘
```

**根因**：`elapsed=1201ms` 恰好撞上 `PREVIEW_WAIT_MS = 1200L`。**只差 1 毫秒就放弃**，
而系统对宫格格子**只请求一次**（不会重试）。

**修复**：两级等待。

```kotlin
private const val PREVIEW_WAIT_MS = 1200L        // 快档：多数图在此完成
private const val PREVIEW_SLOW_WAIT_MS = 4000L   // 慢档：只给掉队的
```

超时后**不再直接返回 null**，而是继续等到总 4s；仍超时时**最后再查一次磁盘**
（下载线程的 `renameTo()` 是最后一个动作，可能刚好落在超时那一瞬间）。

### 柹障六：URL 已过期 4.3 小时，每次首请求都要换链

**日志证据**：327 次下载全部 `refreshed=true`，`ageMs=15606437`。

**修复**：`onQueryMedia` 返回后立即在后台批量预热：

```kotlin
url warm-up: refreshing 119 masters
```

预热后大量请求耗时降到 **3~10ms**（纯磁盘读）。

### 柹障七：视频 MIME 导致 Glide 选错解码器

**日志证据**（最终根因）：

```
java.lang.RuntimeException: setDataSource failed: status = 0xFFFFFFEA
    at android.media.MediaMetadataRetriever._setDataSource
    at com.bumptech.glide.load.resource.bitmap.VideoDecoder.decode
```

统计：**2540 次**失败。

**根因**：PhotoPicker 宫格用 Glide 渲染，**Glide 只根据 `onQueryMedia` 的 `MIME_TYPE` 选解码器**。
我们给视频报 `video/mp4` → Glide 选 `VideoDecoder` → 用 `MediaMetadataRetriever` 解析我们返回的
**JPEG 缩略图** → `status = 0xFFFFFFEA` → 空白。

而“点预览”走的是另一条路径，不经过 Glide 的解码器选择，所以正常——
**完美吻合用户观察到的全部现象**。

**当时的修复**：视频也报 `image/jpeg`。

> [!CAUTION]
> **这个修法是错的，已在桹障十二。** 它修好了格子，但把**视频降级成了照片**。
> 正确做法见桹障十二：`MIME_TYPE` 保持 `video/*`，改由 `onOpenPreview` 返回真实视频容器。

### 柹障八：已安装设备的旧元数据不会自动失效

**根因**：系统按 `LAST_MEDIA_SYNC_GENERATION` 缓存行，值不变就**不重新查询**。
改了 MIME 规则，老设备仍用旧的 `video/*` 行。

**修复**：引入 `SCHEMA_VERSION`，代数 = `sessionGeneration × SCHEMA_VERSION`（7 → 14）。

```kotlin
// SCHEMA_VERSION 必须在 onQueryMedia 列值变化时手工 +1
//  1 — 视频报 video/*，导致缩略图进 VideoDecoder
//  2 — 视频报 image/jpeg，静态缩略图按图片解码（错误的修法，见桹障十二）
//  3 — .heic 也报 image/jpeg；分页改 keyset
//  4 — 视频恢复 video/*，改由 onOpenPreview 提供真容器
```

### 修复后的实测数据

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| `openPreview MISS` | 52 / 445 | **0** |
| `AWAIT timeout` | — | **0** |
| `setDataSource failed` | **2540** | 首屏 **0** |
| 请求耗时分布 | 400~1200ms | 多数 **0~10ms**（磁盘命中）|
| 截图体积（正常判据）| 0.1~0.5MB | **2.5~5.3MB** |

### 柹障九～十一：第三轮修复（2026-10 重新归因）

第二轮留下的结论是“provider 侧 100% 成功，剩余问题在系统侧解码/缓存时序”。
重新逐行对读 `ICloudMediaProvider` 与 `PhotoDao` 后，**这个结论只对了一半** ——
provider 侧仍有三个确定性缺陷，而且每一个都**只在上滑进入早期年份时才密集暴露**，
正好解释了“为什么偏偏是深滑才空白”。

### 柹障九：分页用 OFFSET，而 `photo_assets` 在 picker 会话中途会被增删

**根因**：游标是 `LIMIT :limit OFFSET :offset`（`ORDER BY assetDate DESC, id ASC`）。
但 `photo_assets` 在 picker 打开期间**并非只读**：增量同步 `upsertAll` 新照片、
月扫描/对账 `deleteByIds`。OFFSET 锚定**行号**，任意一行增删都会让后续所有页整体漂移一位：
一行被**跳过**（“少了图”），一行被**重复**（系统按 `ID` 去重丢弃）。

漂移随页数累加，因此**最后几十页（= 最早年份）最严重**。这是三条里最能解释“上滑才空白”的。

> [!IMPORTANT]
> 这个缺陷的症状极具误导性：宫格里有格子（游标有 id/MIME/尺寸）、点预览正常、下载正常，
> 唯独缩略图空白。因为 `onOpenPreview` 用 `getPhotoById(id)` 重新查 DB，与游标只共享那个 id 字符串。

**修复**：改为 keyset 分页，token = `"<assetDate>:<id>"`。

```sql
WHERE isHidden = 0
  AND (assetDate < :lastDate OR (assetDate = :lastDate AND id > :lastId))
ORDER BY assetDate DESC, id ASC LIMIT :limit
```

- 首屏 `getMediaPageFirst(limit)`，后续 `getMediaPageAfter(limit, lastDate, lastId)`
- **纯数字 token 兼容分支**：只可能来自上一版（系统会把 page token 持久化跨 provider 重启），
  只服务一页且绝不再发
- 末页判定：`page.size == pageSize` 才发 token（一整页占满时仍会带 token，需容忍一次空页）

### 柹障十：`length() > 0` 被当成“这是个图”，坏字节永久命中

**根因**：缓存命中判断是 `targetFile.exists() && targetFile.length() > 0`。
而 `200` 响应**不保证是图片字节**：`cvws` 在签名失效时会在 200 状态下返回一小段 HTML/XML 错误正文，
`FileOutputStream` 照写不误。一旦它以 `<mediaId>.thumb` 落盘，就**永久命中**，
之后每次 `onOpenPreview` 都把这个垃圾交给 Glide，而下载路径再也不会被走到——因为文件永远“非空”。

**修复**：引入 `isPlausiblePreview()`（前 12 字节容器魔数 + ≥512B），接入三条返回路径：

| 位置 | 行为 |
| --- | --- |
| Coil 缓存 / 本地缓存命中 | 校验失败 → `delete()` → 重新下载 |
| `downloadToFile(requireImage = true)` | `.tmp` 落盘后先验魔数，不合格则**不 `renameTo`** |
| `onOpenMedia` | `requireImage = false`（全图可能是 mp4/mov）|

**只查魔数不查尺寸**是刻意的：这段代码跑在 binder 路径上，对每个冷格子做 `BitmapFactory` 解码
比它保护的下载本身更贵。魔数已足以捕获“截断”与“CDN 错误正文”两种真实故障。

### 柹障十一：视频缩略图降级到了视频字节

**根因**：`resolveThumbUrl` 的降级链是 `thumbUrl ?: medUrl ?: originalUrl`。
对照片安全（三张同题材不同尺寸），对**视频**是错的 —— `medUrl`/`originalUrl` 就是 `mp4`/`mov` 本身。
把它当 `image/jpeg` 交给 Glide = 必然空白，**且这次请求不会重试**。

实测 `/records/lookup` 偶尔缺少 `thumbUrl`；早期年份每 1000 条含 48~336 个视频，
所以这个缺陷**也只在上滑到早期年份时密集暴露**。

**修复**：`mediaType in {VIDEO, LIVE_PHOTO}` → 只接受 `thumbUrl`，为空则返回 null
并 `refreshUrlsForMaster(force = true)` 重试一次；仍为空就 `Log.w` 并返回 MISS。
宁可一次可归因的 MISS，也不要一个永远解不开的“成功”文件。

### 附带修正

| 项 | 改动 | 理由 |
| --- | --- | --- |
| `.heic` MIME | `image/heic` → `image/jpeg` | 与柹障七同构：`thumbUrl` 是服务端转码 JPEG，报 HEIC 会让 Glide 去 JPEG 里找 HEIF 头 |
| `SCHEMA_VERSION` | 2 → 3 | 上述 MIME 与行顺序变化，不 bump 老设备拿不到 |
| URL 预热 | 进程级一次性 → **每页定向** | 深滑要几分钟，而签名只有 15~18 分钟；老逻辑让早期年份每格都付一次 `/records/lookup` |
| `onOpenPreview` 诊断 | 打印 extras 全量（DEBUG，截断 48 字符）| 柹障七遗留的排查项 |

### 验证

单测 **177 passed**，新增 29 项：

| 测试 | 覆盖 |
| --- | --- |
| `MediaPageKeysetTest`（8）| keyset 全库无重无漏；插入/删除中途不漂移；**OF​FSET 在同样插入下确实会重复（反向证明缺陷存在）**；同秒并列按 id 稳定；空库/超页/整除边界 |
| `PreviewValidationTest`（11）| JPEG/PNG/GIF/WebP/HEIC 接受；空文件、短错误正文、大块非图字节、截断头拒绝 |
| `VideoThumbUrlTest`（10）| 照片降级正常；视频绝不回退到 med/original；空串等同于缺失；大小写不敏感 |

> [!NOTE]
> 实机复测（上滑到最早年份并截图对比）尚未完成，因此本条状态仍标为残留。
> 上机后重点看：`openPreview MISS` 应仍为 0，而 `setDataSource failed` 应从“数百次”降为 0；
> 若出现 `preview cached file is not an image` 说明柹障十在真实环境里发生。

### 仍需跟进

> [!WARNING]
> 若上机后仍有空白，按以下顺序区分：
> 1. `preview cached file is not an image` 是否非 0 → 柹障十仍存在新形态（换个魔数）
> 2. `resolveThumbUrl: video has no thumbUrl` 是否密集 → 苹果侧的确没有转码缩略图
> 3. 以上都不出现但格子仍空 → 才轮到“系统侧解码/缓存时序”，
>    可考虑 `onCreateCloudMediaSurfaceController` 走 AOSP 官方视频预览路径

### 桹障十二：为修桹障七把视频报成 `image/jpeg`，导致视频显示为图片

**优先级**：🔴 P0（功能性回归，用户直接可见）
**归属**：`provider/ICloudMediaProvider.kt`
**发现时间**：桹障九～十一上线后立刻发现

#### 现象

系统相册/选择器里，**视频被当作照片展示**——没有时长角标、不能被识别为视频、
调用方拿到的是 image 类型。格子本身倒是不再空白了。

#### 根因

桹障七的修复“视频报 `image/jpeg`”**只考虑了解码器，没考虑分类**。

`MIME_TYPE` 在 `CloudMediaProviderContract` 里承担两件事：

| 用途 | 看什么 |
| --- | --- |
| 条目**分类**（照片/视频、时长角标、筛选）| `MIME_TYPE` |
| 格子**解码器**选择（Glide）| `MIME_TYPE` |

对视频这两者冲突：

| 报什么 | 分类 | 格子 |
| --- | --- | --- |
| `video/mp4` | ✅ | ❌ `VideoDecoder` 去解 JPEG，`0xFFFFFFEA` |
| `image/jpeg` | ❌ 变成照片 | ✅ |

当时选了后者，等于**用错误的手段修复了一个真实的问题**。

#### 为什么 `STANDARD_MIME_TYPE_EXTENSION` 救不了

它只有 `NONE` / `GIF` / `MOTION_PHOTO` / `ANIMATED_WEBP` 四个值，**没有“视频”**，
无法用来把视频标记出来。

#### 修复：在契约指定的位置拆开

| 列/方法 | 职责 |
| --- | --- |
| `MIME_TYPE` | 媒体**真实类型**：`video/mp4` / `video/quicktime` / `video/x-m4v` |
| `onOpenPreview` | 履行契约另一半（“thumbnail 应与 MIME_TYPE 一致”）—— 视频行**返回真实视频容器** |

`onOpenPreview` 对视频行直接走 `getOrDownloadMediaFile`（与 `onOpenMedia` 同一份字节），
`VideoDecoder` 于是有真的容器可抓帧；取不到时才回退到 JPEG 缩略图（宁可画个图，不要空格）。

判断是否为视频用 `isVideoAsset(mediaId)`，一次主键索引查询，相对下载成本可忽略。

`.heic` 仍报 `image/jpeg`：它的 `thumbUrl` 同样是被转码的 JPEG，但**照片无论报哪个都是照片**，
所以没有分类代价。

#### 验证

`MediaMimeTypeTest` 新增 10 项，其中一条是**穷尽护栏**：

```kotlin
videoNames.forEach { name ->
    val mime = mimeTypeFor(name, "VIDEO")
    assertTrue("$name reported as $mime", mime.startsWith("video/"))
    assertFalse(mime.startsWith("image/"))
}
```

全量单测 **187 passed**。

#### 经验教训

> [!IMPORTANT]
> **修一个 bug 时，问清楚“我放弃的是哪个既有正确行为”。**
> 桹障七 的修复确实消除了 2540 次解码失败，但它同时把视频的类型语义碾掉了——
> 而后者没有任何测试覆盖，所以静默上线了。
>
> 更一般地：**当一个 API 字段同时控制两件事时，不要在同一维度上二选一。**
> 去找「两件事各自由谁表达」，而不是“哪个优先级更高”。
> 本例里 `MIME_TYPE`（类型）+ `onOpenPreview`（字节）本来就是一对，强行让
> `MIME_TYPE` 同时描述两者才是冲突的来源。

### 经验教训

> [!IMPORTANT]
> **“同一张图，一个地方能显示、另一个地方不能”，说明问题在“解码路径”而不在“数据”。**
> 本次所有柹障里，最耗时的是柹障七：前面六项都修好了，`openPreview` 已经 100% 成功返回完整 JPEG，
> 但画面依旧空白——因为问题根本不在 provider 的“返回值”，而在**系统拿到字节后选哪个解码器**。
> 定位它的唯一方法就是去看**系统进程的日志**（`Glide` tag），而不是只看自己进程的日志。

> [!CAUTION]
> **`CloudMediaProvider` 的 MIME 必须描述“`onOpenPreview` 返回的字节”，而不是“原始媒体”。**
> 这与官方文档字面意思（“thumbnail should be in the original MIME_TYPE”）相反——
> 但实测证明：按文档写视频报 `video/*`，Glide 会拿 `MediaMetadataRetriever` 去解 JPEG，必然失败。
> 以实测为准。

> [!IMPORTANT]
> **“provider 返回成功”不等于“provider 做对了”。**
> 本轮最大的教训是：第二轮把结论停在“我们返回了完整 JPEG，所以问题在系统侧”，
> 于是漏掉了三个**输出看上去完全正常**的缺陷 ——
> 返回的是一个真实存在的 JPEG，只不过它属于**另一张照片**（柹障九的游标漂移），
> 或者是一个永远不会被刷新、已经腐坏的缓存文件（柹障十），
> 或者是一段视频字节（柹障十一）。
>
> 验证 provider 不能只看“有没有 bytes”，而要看：
> **这些 bytes 会不会属于正确的 id、会不会被永久缓存、能不能被声称的 MIME 解出来。**
>
> 另一个可靠信号是**症状的分布**：任何“只在深滑/早期年份才出现”的缺陷，
> 几乎都是**随页数或随时间累积**的（游标漂移、URL 过期、缓存腐坏），
> 而不是“时序运气”——后者不会随滚动深度单调恶化。

