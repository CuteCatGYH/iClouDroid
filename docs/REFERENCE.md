# 核心参考开源项目 (GitHub 源码对照表)

> 在开发 Go 协议层与 Android 特性适配时，无需从零抓包，请直接对照以下成熟开源项目的实现逻辑。
>
> 返回 [TODO.md](TODO.md)

---

## 1. Go 语言底座与会话管理 (直接复用/移植)

### [rclone/rclone](https://github.com/rclone/rclone)

- **重点参考路径**：[`backend/iclouddrive/api`](https://github.com/rclone/rclone/tree/master/backend/iclouddrive/api)
- **参考价值**：已用 Go 完整实现 iCloud 的 `Session` 管理、`Trust Token` 自动续期、PCS (Protected Cloud Storage) Cookie 握手以及 HTTP 请求头伪装。
- **注意**：`rclone` 目前仅实现了云盘（`iclouddrive`），未内置照片后端，因此我们只抽取其 `api` 包作为底层网络与鉴权底座。

**本项目已复用点**：

- `Session` 结构设计（见 [ISS-001](CHANGELOG_ISSUES.md#iss-001-cookie-值双引号未剥离导致-go-静默丢弃全部凭证)）
- `/setup/ws/1/validate` 握手流程（见 [ISS-002](CHANGELOG_ISSUES.md#iss-002-accountlogin-调用顺序错误返回-400-missing-apple_id-field)）
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

## 2A. 本项目实测的字段字典 (云上贵州账号)

> **来源**：2026-10 在真实账号上跑的一次性探针（`/records/lookup` **不带 `desiredKeys`**，
> 以探针服务器实际存储的全部字段）。所有结论均为实测，非推测。
> 探针代码已删除，结论保留在本节。

### 2A.1 `CPLMaster` 完整字段列表

从一条真实照片的 master 记录中实测到 32 个字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `recordName` | string | 主键，对应项目中的 `masterId` |
| `itemType` | string | UTI，如 `public.heic` / `public.jpeg` |
| `filenameEnc` | base64 | 文件名（需 base64 解码） |
| `originalOrientation` | int | 原始旋转角 |
| `importDate` | date | 导入 iCloud 的时间 |
| `originalCreationDate` | date | **拍摄时间（EXIF 原始）** |
| `recordModificationDate` | date | 记录最后修改时间 |
| **`mediaMetaDataEnc`** | base64 | **EXIF/GPS 元数据（binary plist）→ 见 2A.2** |
| **`mediaMetaDataType`** | string | 固定为 `CGImageProperties` |
| **`resOriginalFingerprint`** | string | **原图指纹 → Phase 3 查重关键** |
| `resJPEGMedFingerprint` | string | 中图指纹 |
| `resJPEGThumbFingerprint` | string | 缩略图指纹 |
| `resOriginalRes` / `resJPEGMedRes` / `resJPEGThumbRes` | ASSETID | 三类资源（见 2A.3） |
| `resOriginalWidth/Height` | int | 原图尺寸 |
| `resJPEGMedWidth/Height` | int | 中图尺寸 |
| `resJPEGThumbWidth/Height` | int | 缩略图尺寸 |
| `resOriginalFileType` / `resJPEGMedFileType` / `resJPEGThumbFileType` | string | MIME |
| `resOriginalFileSize` / `resJPEGMedFileSize` / `resJPEGThumbFileSize` | int | 字节数 |
| `dataClassType` | int | 数据分类（照片/视频） |
| `videoFrameRate` | int | 视频帧率 |
| `fullSizeJPEGSource` | string | 全尺寸 JPEG 来源 |
| `importedBy` | ref | 导入设备/应用引用 |
| `importedByBundleIdentifierEnc` | base64 | **导入应用 Bundle ID**（可用于分类） |
| `importedByDisplayNameEnc` | base64 | 导入应用显示名 |

> [!NOTE]
> `mediaMetaDataEnc` 是 **精简后的元数据**（实测 742~3859 字节），不是完整 EXIF 块。
> Apple 只保留了展示需要的子集，这反而降低了传输与解析开销。

### 2A.2 `mediaMetaDataEnc` 内部结构

- **编码**：base64 外层，解出后是 **binary plist**（魔数 `bplist00`）
- **顶层字典**：与 Apple `CGImageProperties` 常量同构

| 子字典 | 实测包含的键 |
| --- | --- |
| `{TIFF}` | `Model`、`DateTime`、`ResolutionUnit`、`XResolution`、`YResolution`、`PixelXDimension`、`PixelYDimension` |
| `{Exif}` | `DateTimeOriginal`、`FNumber`、`ExposureTime`、`ISOSpeedRatings`、`FocalLength`、`FocalLenIn35mmFilm`、`LensModel`、`BrightnessValue`、`ExposureBiasValue`、`Make`、`Model` |
| `{GPS}` | `Latitude`、`Longitude`、`Altitude`、`Speed`、`ImgDirection`、`HPositioningError`、`DateStamp`、`TimeStamp` |
| `{PNG}` | `DPIWidth`、`DPIHeight`、`ColorModel`、`ProfileName`、`Orientation` |
| 其他 | `{IPTC}`、`PrimaryImage`、`Headroom`、`Depth` |

**实测样本分布**（扫描 25 条记录）：

| 类型 | 字节数 | 是否带 `{GPS}` | 数量 |
| --- | --- | --- | --- |
| 相机拍摄（开启定位） | 3775~3859 | 是 | 8 条 |
| 截图 / 未开定位 | 742~1035 | 否 | 17 条 |

> [!IMPORTANT]
> **`{GPS}` 字典不是必然存在的。** 截图、或拍摄时关闭了定位服务的照片都没有。
> 任何详情面板必须按字段存在性条件渲染，不能假设 GPS 恒有。

### 2A.3 资源对象（ASSETID）的子键

```json
"resOriginalRes": ["size", "wrappingKey", "referenceChecksum", "downloadURL", "fileChecksum"]
```

| 子键 | 用途 |
| --- | --- |
| `downloadURL` | 临时直链，寿命 ~15-18 分钟（见 [ISS-026](CHANGELOG_ISSUES.md#iss-026)） |
| `size` | 字节数 |
| `fileChecksum` | 内容校验和 |
| `referenceChecksum` | 引用校验和 |
| `wrappingKey` | 加密密钥材料 |

### 2A.4 `CPLAsset` 上的位置字段

除 master 的 EXIF 之外，`CPLAsset` 自身还带：

```
assetDate, masterRef, locationEnc
```

> [!TIP]
> `locationEnc` 是**独立的位置字段**。只需判断“有没有位置”而不需要解码整个 plist 时，
> 直接读它即可，无需解析 `mediaMetaDataEnc`。

### 2A.5 对后续功能的影响

| 功能 | 数据来源 | 可行性 |
| --- | --- | --- |
| 照片详情面板 | `mediaMetaDataEnc` | ✅ 光圈/快门/ISO/焦距/镜头 + GPS |
| 列表位置标记 | `CPLAsset.locationEnc` | ✅ 无需解码 plist |
| **Phase 3 上传查重** | `resOriginalFingerprint` | ✅ **无需自己算指纹** |
| Albums 按来源分类 | `importedByBundleIdentifierEnc` | ✅ 区分拍摄 vs 导入 |

**唯一未解决的工程问题**：Go 标准库没有 binary plist 解析器。
建议引入 [`howett.net/plist`](https://pkg.go.dev/howett.net/plist)（纯 Go、无 CGO，兼容 gomobile）。

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
