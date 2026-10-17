# Phase 1 过程存档：混合鉴权与动态路由

> 本文档存档 Phase 1 的**详细测试过程、可观测数据与完整的修复方向推演**。
> 已落地的修复案例已提炼归档至 [CHANGELOG_ISSUES.md](../CHANGELOG_ISSUES.md)；待办事项见 [TODO.md](../TODO.md)。
>
> 返回 [TODO.md](../TODO.md)

---

## 一、测试轮次记录

### 轮次 1：Android 侧鉴权链路 ✅ / Go 侧 ❌

**Android 侧**：三要素 Cookie 校验完全通过。

| 校验项 | 实测结果 |
| --- | --- |
| 国区路由 | ✅ 加载 `https://www.icloud.com.cn/`，页脚"由云上贵州运营" |
| Cookie 捕获总数 | ✅ 18 项 |
| `X-APPLE-WEBAUTH-TOKEN` | ✅ 长度 154 |
| `X-APPLE-WEBAUTH-HSA-TRUST` | ✅ 长度 265（"信任此浏览器"生效） |
| `X-APPLE-WEBAUTH-PCS-CloudKit` | ✅ 长度 68 |
| 三要素校验 | ✅ `isValid=true missing=[none]`；登录前正确判定 0 项不放行 |
| 加密持久化 | ✅ `Persisting cookie set (region=cn)` |
| 跨 JNI 传递 | ✅ `Calling Go InitSession (isChinaMainland=true, hasHsaTrust=true)` |

**Go 侧**：`accountLogin` 返回 `400 Missing apple_id field`。
根因见 [ISS-001](../CHANGELOG_ISSUES.md#iss-001-cookie-值双引号未剥离导致-go-静默丢弃全部凭证) 与 [ISS-002](../CHANGELOG_ISSUES.md#iss-002-accountlogin-调用顺序错误返回-400-missing-apple_id-field)。

### 轮次 2：Go 侧修复后 ✅

```json
{"success":true,"dsid":"20118126497","ckDatabaseUrl":"https://p203-ckdatabasews.icloud.com.cn:443","isChina":true}
```

| 校验项 | 修复前 | 修复后 |
| --- | --- | --- |
| `invalid byte '"' in Cookie.Value` | ❌ 16 次 | ✅ **完全归零** |
| `accountLogin` / `success` | ❌ `400 Missing apple_id field` | ✅ `success: true` |
| `isChina` 回传 | ❌ `false`（传了 true） | ✅ `true` |
| `dsid` | ❌ 空 | ✅ `20118126497` |
| `ckDatabaseUrl` | ❌ 空 | ✅ `https://p203-ckdatabasews.icloud.com.cn:443` |

### 轮次 3：加密存储 + 冷启动恢复 ✅

`adb shell am force-stop` → 冷启动后：

- ✅ 新进程无 WebView 加载、无登录页
- ✅ 直接从 `EncryptedSharedPreferences` 读出 Cookie 并成功调 `InitSession`
- ✅ UI 直接落到 `LoggedIn` 卡片：DSID、路由、HSA-TRUST 状态全部正确还原
- ✅ 全程无 `ICloudAuth` 报错、无 `GoLog` Cookie 丢弃

---

## 二、可观测数据速查

### 完整 Cookie 名单（实测 18 项）

```
X-APPLE-UNIQUE-CLIENT-ID
X-APPLE-WEBAUTH-USER
X_APPLE_WEB_KB-<随机串>
X-Apple-GCBD-Cookie
X-APPLE-WEBAUTH-HSA-TRUST
X-APPLE-WEBAUTH-PCS-Documents
X-APPLE-WEBAUTH-PCS-Photos
X-APPLE-WEBAUTH-PCS-Cloudkit        ← 注意小写 k
X-APPLE-WEBAUTH-PCS-Safari
X-APPLE-WEBAUTH-PCS-Mail
X-APPLE-WEBAUTH-PCS-Notes
X-APPLE-WEBAUTH-PCS-News
X-APPLE-WEBAUTH-PCS-Sharing
X-APPLE-WEBAUTH-LOGIN
X-APPLE-WEBAUTH-VALIDATE
X-APPLE-DS-WEB-SESSION-TOKEN
X-APPLE-WEB-ID
X-APPLE-WEBAUTH-TOKEN
```

### Logcat Tag 速查

| Tag | 来源 | 用途 |
| --- | --- | --- |
| `ICloudAuth` | Android 侧探针 | Cookie 捕获、校验、JNI 调用、区域判定 |
| `GoLog` | Go 侧 `net/http` | Cookie 丢弃告警、TLS/HTTP 错误 |
| `chromium` | WebView 内核 | 页面 JS 报错、CORS 噪音 |

### 已知噪音（可忽略）

- `gatewayws.icloud.com.cn/pds/WebObjects/pushregistry.woa/webBatchRegister/batchRegisterToken` 的 CORS 报错 —— 页面推送注册行为，与鉴权无关
- `wallpaper.svg was preloaded using link preload but not used` —— Apple 页面自身的 preload 提示
- `MediaCodec / Codec2 / c2.goldfish.h264.decoder` 系列错误 —— 模拟器软解渲染噪音

---

## 三、修复方向完整存档

> 以下为当初规划的所有修复方向，保留作为思路存档。**已实施项标注于后**。

### 方向 A：修 Cookie 解析（P0）

✅ **已实施**，详见 [ISS-001](../CHANGELOG_ISSUES.md#iss-001-cookie-值双引号未剥离导致-go-静默丢弃全部凭证)

1. `strings.Trim(strings.TrimSpace(v), "\"")` 剥离双引号；跳过 `$Version` / `$Path` / `$Domain` 属性项
2. `SplitN(part, "=", 2)` 避免 base64/padding `=` 尾部被截断
3. 解析数量统计 —— **尚未实施**，建议后续补充

### 方向 B：改用 `/validate` 完成握手（P0）

✅ **已实施**，详见 [ISS-002](../CHANGELOG_ISSUES.md#iss-002-accountlogin-调用顺序错误返回-400-missing-apple_id-field)

1. `ValidateSession()` + `clientBuildNumber` 等 4 个 query 参数
2. 从响应取 `dsInfo.dsid`、`webservices.ckdatabasews.url`、`pcsRequired`
3. `accountLogin` 降级为补救路径 —— **代码尚未落地**，见 TODO Phase 1 遗留项
4. `rclone` `tap()` 流程 —— 备选，未采用

### 方向 C：跨子域 Cookie 注入（P1）

✅ **已实施**，详见 [ISS-004](../CHANGELOG_ISSUES.md#iss-004-cookie-跨子域下发规则缺失)

1. `Session` 持有 `CookieMap`，`NewRequest` 手动注入
2. 自定义 `PublicSuffixList` —— 未采用（选择了方案 1）
3. `:443` 端口主机名匹配 —— 因采用方案 1 而绕过
4. `Origin` / `Referer` 验算 —— **待 Phase 2 验证**

### 方向 D：错误透传与可观测性（P1）

⚠️ **部分实施**

1. `IsChina` 全分支透传 —— ✅ 已实施，详见 [ISS-003](../CHANGELOG_ISSUES.md#iss-003-sessioninitresultischina-错误分支未赋值)
2. `errorMessage` 带 HTTP status + 响应体 —— ✅ 已实施
3. `LogSessionState()` 辅助方法 —— ❌ **未实施**
4. Kotlin 侧 Debug 页展示 `AccountInfoJSON` —— ❌ **未实施**，当前仅有 Cookie 诊断卡

### 方向 E：Android 侧健壮性（P1/P2）

✅ **已实施**，详见 [ISS-005](../CHANGELOG_ISSUES.md#iss-005-authviewmodel-区域取值三处推断不一致) / [ISS-006](../CHANGELOG_ISSUES.md#iss-006-登录成功后-icloudwebview-重复触发-initsession)

1. 统一区域来源 —— ✅ 已实施
2. `hasSubmitted` 哨兵位 —— ✅ 已实施
3. `Error` 态重试路径 —— ✅ 已实施
4. `valueLength()` 改为 Map + 解析 `Expires` 估算 HSA-TRUST 剩余有效期 —— ❌ **未实施**
5. `CookieValidationResult` 改为摘要（names + hash + length）—— ❌ **未实施**，当前仍在 Compose State 持有明文

### 方向 F：备选验证手段（无 Apple ID 时可回归）

❌ **全部未实施**

1. `go test` 覆盖 `NewSession` Cookie 解析边界用例（双引号、`=`、空值、`$Path`）
2. 硬编码样例 Cookie 串跑 `/validate`（预期 401），只验请求头是否带齐 Cookie
3. `CookieValidationResult.validate()` 参数化单测（大小写、缺项、空串）

> [!TIP]
> 方向 F 是目前**唯一能在无 Apple ID 环境下做回归测试**的手段。ISS-001 这类"静默丢弃"故障正是因为缺少单测才在真机联调才暴露，建议优先补上。
