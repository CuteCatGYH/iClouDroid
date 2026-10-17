# AGENTS.md - AI Agent 协助开发规范指南

本文档为参与 **iClouDroid** 项目协同开发的 AI Agent 提供架构导引、编码约定与编译调试规范。

---

## 1. 工程目录结构说明

```
/Users/syh/iClouDroid/
├── TODO.md                 # 完整功能路线图与架构指导说明书
├── README.md               # 项目公开说明与快速上手
├── AGENTS.md               # AI Agent 指导规范 (本文件)
├── scripts/
│   └── build_aar.sh        # Go gomobile 交叉编译构建脚本 (关键!)
├── go-core/                # Go 核心协议层 (go mod init icloudcore)
│   ├── go.mod
│   ├── core.go             # gomobile 导出主入口
│   ├── session.go          # Session & 网络请求封装
│   └── account.go          # CloudKit 账户登录与节点路由
└── app/                    # Android 主工程 (Kotlin + Compose)
    ├── libs/
    │   └── icloudcore.aar  # 由 scripts/build_aar.sh 自动生成的二进制依赖
    └── src/main/java/com/getup/iClouDroid/
        ├── MainActivity.kt
        ├── data/session/   # 本地凭证加密存储
        └── ui/auth/        # 鉴权 WebView 与登录 UI
```

---

## 2. 编译与构建工作流

在修改 Go 代码或 Android 代码后，请严格遵照以下步骤：

1. **若修改了 `/go-core`**：
   **必须**优先运行 `./scripts/build_aar.sh`。不要直接手动调用裸 `gomobile bind` 命令，因为该脚本会自动配置 `JAVA_HOME` 和 `ANDROID_NDK_HOME`。
2. **若修改了 Android 代码**：
   可以使用 `gradle_build` 工具调用 `:app:assembleDebug` 或 `:app:compileDebugKotlin`。
3. **禁止在 Shell 中使用文本替换工具**：
   **切勿**使用 `sed`、`awk` 或 `echo` 修改代码文件，请始终使用 IDE 提供的内置文件写入/替换工具。

---

## 3. Go 跨语言交互 (gomobile) 编写规范

1. **类型限制**：
   `gomobile bind` 仅支持 Go 基本数据类型（`string`, `int64`, `bool`, `[]byte`）及简单 Struct。
2. **数据传递首选 JSON**：
   复杂数据结构（如账号信息、照片列表等）请在 Go 端序列化为 **JSON 字符串**，在 Kotlin 端解析。
3. **异常处理机制**：
   Go 函数返回 `(result, error)` 时，`gomobile` 会将其转化为 Java/Kotlin 中的 `throws Exception`。请在 Kotlin 协程体中总是使用 `try-catch` 包裹调用。
4. **大文件传输禁忌**：
   **绝对不要**用 `[]byte` 传递照片或视频内容，会触发 Android JNI OOM！必须在 Kotlin 传递文件路径，由 Go 直接读写本地文件流。

---

## 4. iCloud 协议开发红线

1. **请求头 (Headers) 强校验**：
   所有发往 Apple 接口的请求，必须携带有正确的 `User-Agent`，以及与节点匹配的 `Origin` 和 `Referer`：
   - 国区：`Origin: https://www.icloud.com.cn`，`Referer: https://www.icloud.com.cn/`
   - 国际区：`Origin: https://www.icloud.com`，`Referer: https://www.icloud.com/`
2. **三要素 Cookie**：
   任何 Session 的建立必须包含：
   - `X-APPLE-WEBAUTH-TOKEN`
   - `X-APPLE-WEBAUTH-HSA-TRUST`
   - `X-APPLE-WEBAUTH-PCS-CloudKit`
