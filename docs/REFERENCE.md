# 核心参考开源项目 (GitHub 源码对照表)

> 在开发 Go 协议层与 Android 特性适配时，无需从零抓包，请直接对照以下成熟开源项目的实现逻辑。
>
> 返回 [TODO.md](../TODO.md)

---

## 1. Go 语言底座与会话管理 (直接复用/移植)

### [rclone/rclone](https://github.com/rclone/rclone)

- **重点参考路径**：[`backend/iclouddrive/api`](https://github.com/rclone/rclone/tree/master/backend/iclouddrive/api)
- **参考价值**：已用 Go 完整实现 iCloud 的 `Session` 管理、`Trust Token` 自动续期、PCS (Protected Cloud Storage) Cookie 握手以及 HTTP 请求头伪装。
- **注意**：`rclone` 目前仅实现了云盘（`iclouddrive`），未内置照片后端，因此我们只抽取其 `api` 包作为底层网络与鉴权底座。

**本项目已复用点**：

- `Session` 结构设计（见 [ISS-001](../CHANGELOG_ISSUES.md#iss-001-cookie-值双引号未剥离导致-go-静默丢弃全部凭证)）
- `/setup/ws/1/validate` 握手流程（见 [ISS-002](../CHANGELOG_ISSUES.md#iss-002-accountlogin-调用顺序错误返回-400-missing-apple_id-field)）
- `tap()` Token 静默刷新流程（**尚未移植**，见 TODO Phase 1 遗留项）

---

## 2. CloudKit (`ckdatabasews`) 照片协议逆向字典

### [icloud-photos-downloader/icloud_photos_downloader (`icloudpd`)](https://github.com/icloud-photos-downloader/icloud_photos_downloader)

- **重点参考路径**：`src/pyicloud_ipd/services/photos.py`
- **参考价值**：目前社区维护最活跃的 iCloud 照片同步工具。完美展示了如何处理国区（`icloud.com.cn`）路由、CloudKit `PrimarySync` 分区初始化、游标分页拉取以及 Live Photo 下载。
- **本项目用途**：Phase 2 的 `/records/query` 分页实现、Phase 4 的 Live Photo 下载

### [picklepete/pyicloud](https://github.com/picklepete/pyicloud)

- **重点参考路径**：`pyicloud/services/photos.py`
- **参考价值**：Python 版 iCloud 协议鼻祖，适合快速查阅 `CPLAsset` 与 `CPLMaster` 的 JSON 字段映射关系。
- **本项目用途**：字段字典速查

### [steilerDev/icloud-photos-sync](https://github.com/steilerDev/icloud-photos-sync)

- **参考价值**：展示了如何利用 CloudKit 的 `syncToken` / `zoneChangeToken` 获取增量变更（Delta Sync），而非每次全量遍历图库。
- **本项目用途**：Phase 2 增量同步实现

---

## 3. CloudKit 照片上传协议参考 (攻克上传难点)

### [simulot/immich-go](https://github.com/simulot/immich-go) / [bbernhard/icloud-photos-uploader](https://github.com/bbernhard/icloud-photos-uploader)

- **参考价值**：由于 `icloudpd` 仅支持下载，上传照片至 iCloud 的 `records/modify` 与 `/assets/upload` (MMCS 对象存储分块上传) 报文结构需参考此类上传工具。
- **备选方案**：直接通过桌面浏览器在 `icloud.com.cn` 手动上传一张图进行抓包对照。
- **本项目用途**：Phase 3 流式上传链路

---

## 4. Android 端动态照片 (Motion Photo) 拆解与合成参考

### [deckerst/aves](https://github.com/deckerst/aves)

- **备选参考**：Android 官方 `ExifInterface` XMP 规范
- **参考价值**：处理三星等 Android 设备的 Motion Photo（单文件尾部追加 MP4 封装）与苹果 Live Photo（`HEIC/JPG` + `.MOV` 双文件通过 `ContentIdentifier` 绑定）之间的容器拆解与 XMP 元数据解析。
- **本项目用途**：Phase 4 跨生态动态照片互转
