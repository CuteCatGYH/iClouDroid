# Android iCloud Photos Sync - 开发路线图 (TODO.md)

> **项目定位**：基于 `Go (gomobile .aar)` + `Kotlin (Jetpack Compose)` 的 Android 原生 iCloud 照片客户端与后台自动备份工具。
> **核心目标**：解决"Android 主力机 + 多台 Apple 设备"跨生态照片浏览与自动同步痛点。
> **核心架构**：Android WebView 混合鉴权 + Go 核心层（复用 `rclone` iCloud Session 底座 + 逆向 CloudKit `ckdatabasews` 协议）+ Android 原生系统接口（`MediaStore` / `WorkManager` / `Room`）。

## 📚 文档索引

| 文档 | 内容 |
| --- | --- |
| [README.md](README.md) | 项目介绍、架构图、构建步骤 |
| [AGENTS.md](AGENTS.md) | AI Agent 协作规范、编译调试约定 |
| [CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md) | **已修复问题案例库**（根因分析、修复方案、验证记录） |
| [docs/REFERENCE.md](docs/REFERENCE.md) | 核心参考开源项目与源码对照表 |
| [docs/PITFALLS.md](docs/PITFALLS.md) | 关键工程避坑指南（必读） |
| [docs/ARCHIVE_PHASE1.md](docs/ARCHIVE_PHASE1.md) | Phase 1 详细过程记录与修复方向存档 |

---

## 分阶段开发任务清单

### Phase 0: 跨语言基础工程与编译流水线搭建 ✅

- [x] **工程目录初始化**（Kotlin + Compose + Material 3 主工程、`/go-core` Go Module）
- [x] **打通 `gomobile` 编译链路**（`scripts/build_aar.sh` 一键构建、JNI 异常捕获验证）

### Phase 1: 混合鉴权与国区/国际区动态路由 (MVP 底座) ✅

- [x] **Android 端：WebView 登录拦截与凭证提取器**
  - [x] 国区 / 国际区节点切换开关
  - [x] 加载官方登录页，用户完成密码 + 2FA（提示勾选"信任此浏览器"）
  - [x] 监听跳转后调用 `CookieManager.getCookie()` 提取完整 Cookie
  - [x] **强校验三要素**：`X-APPLE-WEBAUTH-TOKEN`、`X-APPLE-WEBAUTH-HSA-TRUST`、`X-APPLE-WEBAUTH-PCS-CloudKit`
  - [x] `EncryptedSharedPreferences` 加密持久化 Cookie 与节点类型
- [x] **Go 核心层：Session 接管与动态路由表解析**
  - [x] `InitSession(cookieStr, isChinaMainland)` JNI 接口
  - [x] `POST /setup/ws/1/validate` 完成会话握手
  - [x] 解析 `webservices` 提取 `ckdatabasews.url` 与 `pcsRequired`
  - [x] Cookie 解析剥离双引号、跨子域 `CookieMap` 手动注入

**实机验证结论**（emulator-5554 / API 37）

| 项目 | 结果 |
| --- | --- |
| Cookie 捕获 | ✅ 18 项，三要素齐全（TOKEN 154 / HSA-TRUST 265 / PCS-CloudKit 68 字符） |
| 区域路由 | ✅ 国区 `https://www.icloud.com.cn/` |
| 动态路由解析 | ✅ `dsid=20118126497`，`https://p203-ckdatabasews.icloud.com.cn:443` |
| 冷启动恢复 | ✅ 从加密存储静默恢复，无需重新登录 |

> [!NOTE]
> Phase 1 的所有已修复问题及遗留项闭环记录（ISS-001 ~ ISS-011）已完整收录并归档至 [CHANGELOG_ISSUES.md](CHANGELOG_ISSUES.md)。

### Phase 2: 照片库拉取、万级图库分页与本地缓存 (只读预览)

- [ ] **Go 核心层：CloudKit (`ckdatabasews`) 查询接口封装**
  - [ ] 验证工作区状态：`{ckdatabasews.url}/database/1/com.apple.photos.cloud/production/private/zones/list`，确认 `PrimarySync` 分区就绪
  - [ ] `FetchPhotoPage(continuationMarker string, limit int) (jsonResult string, err error)`
    - 路径：`POST {ckdatabasews.url}/database/1/com.apple.photos.cloud/production/private/records/query`
    - 解析 `CPLAsset`：`assetDate`、`masterRef`、`isFavorite`、`isHidden`、`duration`
    - 解析 `CPLMaster`：`recordName`、`itemType`、`resJPEGThumbRes`、`resJPEGMedRes`、`resOriginalRes`、`resOriginalVidComplRes`
- [ ] **Go 核心层：增量同步 (Delta Sync)**
  - [ ] 对接 `/records/changes`，保存并传入 `syncToken`，仅拉取增量变更
- [ ] **Android 端：本地数据库索引与流畅瀑布流**
  - [ ] `Room` 数据库（`PhotoAssetEntity`）
  - [ ] `Paging 3` + `LazyVerticalGrid` 时间线瀑布流（日/月分组 + 列数缩放）
  - [ ] 自定义 `Coil` `Interceptor` 与 `Fetcher`（缓存 Key 绑定 `recordName`）
  - [ ] `Media3 (ExoPlayer)` 云端视频流式播放 + 一键下载写入 `MediaStore`

### Phase 3: Android 本地相册自动备份 (增量上传)

- [ ] **本地相册监听与云端去重**
  - [ ] `MediaStore` 扫描用户勾选目录（`DCIM/Camera`、`Pictures/Screenshots`）
  - [ ] Go 层实现与 CloudKit 一致的文件指纹计算，上传前 `/records/lookup` 批量查重
- [ ] **Go 核心层：CloudKit 照片流式上传链路**
  - [ ] 步骤 1：`/assets/upload` 申请上传凭证，获取临时 `uploadURL`
  - [ ] 步骤 2：Go 层通过本地文件路径 `os.File` 流式上传至 MMCS，提取 `singleFile` 签名
  - [ ] 步骤 3：`/records/modify` 提交事务（`CPLMaster` + `CPLAsset`），写入 EXIF 时间戳与 GPS
- [ ] **Android 端：后台任务调度 (`WorkManager`)**
  - [ ] 周期性任务 + `ContentObserver` 触发器（仅 Wi-Fi / 充电时 / 电量充足）
  - [ ] `ForegroundService (dataSync)` 通知栏展示进度与异常重试

### Phase 4: 跨生态深度适配 (三星 + 苹果痛点打磨)

- [ ] **Motion Photo ↔ Live Photo 互转**
  - [ ] **上传（三星 → iCloud）**：检测 `MotionPhoto_Data` / `MicroVideoOffset` XMP 标签，剥离尾部 MP4 → `MediaMuxer`/`FFmpeg` 转 `.MOV`，写入 `com.apple.quicktime.content.identifier` UUID，JPEG 注入匹配 `MakerApple` ContentIdentifier
  - [ ] **下载（iCloud → 三星）**：`CPLMaster` 含 `resOriginalVidComplRes` 时同时下载图+视频，按三星/Google Motion Photo 规范拼接单文件并写 XMP 偏移量
- [ ] **折叠屏与大屏自适应布局**
  - [ ] `WindowSizeClass` 适配：外屏单栏底部导航 / 内屏与平板 `NavigationRail` + 双栏详情

---

> 参考资料、避坑指南与 Phase 1 详细过程记录已拆分至 [docs/](docs/) 目录，详见上方文档索引。
