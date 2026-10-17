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

