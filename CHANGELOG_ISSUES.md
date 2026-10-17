# CHANGELOG_ISSUES.md — 已修复问题案例档案

> **用途**：沉淀 iClouDroid 开发过程中**实测复现 → 定位根因 → 修复验证**的完整问题案例，供后续 Phase 遇到相似故障时快速比对，避免重复踩坑。
>
> **记录格式**：现象 → 日志证据 → 根因 → 修复方案 → 验证结果。
>
> **相关文档**：[TODO.md](TODO.md)（待办与路线图）、[AGENTS.md](AGENTS.md)（Agent 开发规范）、[README.md](README.md)（项目说明）

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

---

## ISS-001：Cookie 值双引号未剥离导致 Go 静默丢弃全部凭证

**优先级**：🔴 P0（阻塞 Phase 1 全部后续工作）
**归属**：Go 核心层 — `go-core/session.go` → `NewSession()`
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
**归属**：Go 核心层 — `go-core/account.go` → `AccountLogin()`
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
**归属**：Go 核心层 — `go-core/core.go` → `InitSession()`
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
**归属**：Go 核心层 — `go-core/session.go`
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
**归属**：Android 层 — `app/src/main/java/com/getup/iClouDroid/ui/auth/AuthViewModel.kt`
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
**归属**：Android 层 — `app/src/main/java/com/getup/iClouDroid/ui/auth/ICloudWebView.kt`
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
**归属**：Go 核心层 — `go-core/account.go` → `TryAccountLoginRemediation()`
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
**归属**：Go / Android 跨语言交互 — `go-core/session.go` & `AuthViewModel.kt`
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
**归属**：测试基建 — `go-core/session_test.go` & `CookieValidationResultTest.kt`
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
**归属**：Go 核心层 (`go-core/photos.go`) & Android 层 (`ICloudPhotoCoil.kt`, `MediaStoreDownloader.kt`)
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
的技术债条目补齐结构化字段，需同时重新 `./scripts/build_aar.sh`。




