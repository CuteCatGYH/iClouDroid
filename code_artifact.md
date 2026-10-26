# iClouDroid 工程文档技术债与修订清单

> **用途**：本清单专门梳理现有工程文档（`AGENTS.md`、`PITFALLS.md`、`CHANGELOG_ISSUES.md`、`list.md`、`REFERENCE.md`、`TODO.md`）中的**过时结论、前后矛盾、格式错误与未归档项**。
> **维护目标**：消除文档间的“认知时差”，防止后续开发者或 AI Agent 被已证伪的旧规则误导。

---

## 债务总览表

| 编号 | 涉及文件 | 所在章节 | 问题类型 | 严重度 |
| --- | --- | --- | --- | --- |
| **DOC-01** | `AGENTS.md` / `list.md` | `AGENTS` §5.2, §5.4；`list` 附 1, 附 2 | 已证伪的“会话重建 URL 立即作废”结论未清理，与 §4.4 / §6.1.1 冲突 | 🔴 高 |
| **DOC-02** | `AGENTS.md` / `PITFALLS.md` | `AGENTS` §5.5；`PITFALLS` §8 | 仍在引用已被彻底删除的 `PhotoRemoteMediator` | 🟠 中 |
| **DOC-03** | `AGENTS.md` / `list.md` / `CHANGELOG_ISSUES.md` | `AGENTS` §6.6；`list` P2-020 | `P2-020` 已修复但 `AGENTS.md` 仍标为未修好，且未按规范迁入 `CHANGELOG` | 🟠 中 |
| **DOC-04** | `PITFALLS.md` / `CHANGELOG_ISSUES.md` | `PITFALLS` §21.4；`CHANGELOG` ISS-035 末尾 | 视频 `MIME_TYPE` 规则与“桹障十二”及 `AGENTS` §6.7.5d 相反 | 🔴 高 |
| **DOC-05** | `AGENTS.md` / `REFERENCE.md` / `TODO.md` | `AGENTS` §4.6, §12；`REFERENCE` §2A.5, §3A.4；`TODO` P3 | 关于 `resOriginalFingerprint` 的描述与 Phase 3 探针实测结论脱节 | 🔴 高 |
| **DOC-06** | `CHANGELOG_ISSUES.md` | 文件第 1~2 行 | 顶部标题前存在误敲入的乱码字符 `78+/98+`、`*/fiu` | 🟢 低 |

---

## 逐项修订指南（可直接按此修改文件）

### DOC-01：统一“URL 有效期与会话代际”表述（清理 ISS-026 之前的旧残留）

**背景**：`ISS-026` 实测已证伪“会话重建导致 URL 失效”，确认 URL 跨代际依然返回 200，寿命仅由时间（15~18 分钟，阈值设为 12 分钟）决定，代际 `sessionGeneration` 已降级为辅助提示。但以下三处仍保留着旧说法：

1. **`AGENTS.md` §5.2（会话重建是「昂贵操作」）**
   * **原表述**：`一次重建 = 一次 /validate 网络往返（1~2s）+ 全部已签发 URL 立即作废 + 一次 generation 递增。`
   * **应改为**：`一次重建 = 一次 /validate 网络往返（1~2s）+（仅在 force=true 恢复路径下）一次 generation 递增。注：已签发 URL 不会因会话重建作废（详见 §4.4 与 ISS-026），但频繁重建仍会严重拖慢请求并增加限流风险。`
2. **`AGENTS.md` §5.4（会话代际机制）**
   * **原表述**：`判定：data.sessionGeneration != sessionManager.getSessionGeneration() → 强制刷新。`
   * **应改为**：`判定：代际已降级为辅助信号，仅当「代际不符 且 URL 年龄已过半（> 6 分钟）」时才提前刷新（见 §6.1.1）。刷新成功后仍必须把当前 generation 回写到该行。`
3. **`list.md` 附 1 & 附 2**
   * **原表述**：
     * 附 1：`❌ 只看 fetchedAt 时间差 | 时间是猜测，会话重建可能只隔 10 秒`
     * 附 2：`一次重建 = 一次 /validate 网络往返（1~2 秒）+ 全部已签发 URL 重新签名。`
   * **应改为**：对齐 `ISS-026` 结论，明确 URL 过期由 `fetchedAt` 年龄（12 分钟阈值）和真实 HTTP 403/410 驱动，移除“会话重建导致 URL 失效”的过时描述。

---

### DOC-02：移除对已删除类 `PhotoRemoteMediator` 的引用

**背景**：在 `ISS-027` 中，项目已彻底移除 Paging 3 并删除了 `PhotoRemoteMediator.kt`，改为 `MonthScanEngine` + 本地月分组分页。

1. **`AGENTS.md` §5.5（换区 / 换账号必须清空数据层）**
   * **原表述**：
     * `与 PhotoRemoteMediator.load() 立即拒绝工作`
     * `先阻断再清库 —— 否则正在飞行中的 RemoteMediator APPEND 会 upsertAll() 把已删的行写回。`
     * `本项目已验证的写回路径有两条：PhotoRemoteMediator.load() → upsertAll()...`
   * **应改为**：将 `PhotoRemoteMediator.load()` 替换为 **`MonthScanEngine`（全盘扫描）与 `syncIncremental`（增量同步），以及 Phase 3 新增的 `UploadWorker`**。
2. **`PITFALLS.md` §8（退出登录的写回陷阱）**
   * **原表述**：表格第一行写回路径为 `PhotoRemoteMediator.load() → upsertAll()`。
   * **应改为**：`MonthScanEngine / syncIncremental / UploadWorker → upsertAll()`（可注明历史案例原为 `PhotoRemoteMediator`）。

---

### DOC-03：同步 `P2-020`（月份轻封面缩略图空白）的修复状态并归档

**背景**：`list.md` 中 `P2-020` 状态已更新为 `✅ 已修复`，但主规范文档未同步，且未按 `AGENTS.md` §11 规范迁入 `CHANGELOG_ISSUES.md`。

1. **`AGENTS.md` §6.6（月分组数据层约定）**
   * **原表述**：`月份轻封面（coverAssetId）目前仍未修好：小窗里的月份格子可以点、也不置灰，但缩略图为空白。详见 list.md 待修复项。`
   * **应改为**：`月份轻封面（coverAssetId）曾因多处数据与渲染断链导致空白（已修复，见 ISS-038）。以下三条是排查中确认的硬规则，改任何与封面/Coil 相关的代码前必须先读（新增了第 ④ 条：DAO 必须查 a.masterId 并用 COALESCE 兜底，MonthIndexBar 必须透传 masterId 以支持拦截器换链）。`
2. **`CHANGELOG_ISSUES.md` & `list.md`**
   * 将 `list.md` 中的 `P2-020` 完整排查与修复过程迁移至 `CHANGELOG_ISSUES.md`，编号为 **`ISS-038：年月跳转小窗月份轻封面缩略图空白（四层断链修复）`**，并在顶部索引表登记。
   * 清空 `list.md` 中的 `P2-020` 正文，将其移入 `list.md` 的“已归档至 CHANGELOG_ISSUES.md”表格中。

---

### DOC-04：修正 `CloudMediaProvider` 视频 MIME 类型的冲突总结

**背景**：在 `ISS-035` 的“柹障七”中，曾把视频的 `MIME_TYPE` 报成 `image/jpeg`，结果引发了“桹障十二”（视频被系统当成静态图片，丢失时长角标与视频分类）。最终正确方案已在 `AGENTS.md` §6.7.3 和 §6.7.5d 确立：**`MIME_TYPE` 必须报真实的 `video/*`，同时 `onOpenPreview` 对视频返回真实视频容器**。但以下两处总结忘了同步修改：

1. **`CHANGELOG_ISSUES.md` — `ISS-035` 末尾的「经验教训」第 2 个 CAUTION 框**
   * **原表述**：`CloudMediaProvider 的 MIME 必须描述“onOpenPreview 返回的字节”，而不是“原始媒体”。... 按文档写视频报 video/*，Glide 会拿 MediaMetadataRetriever 去解 JPEG，必然失败。`
   * **应改为**：删除或重写该段，使其与上方的“桹障十二”经验教训保持一致——强调 **`MIME_TYPE` 必须报真实媒体类型（`video/*`），而由 `onOpenPreview` 履行契约返回真实视频容器字节**，切勿为了迁就 JPEG 缩略图而篡改 `MIME_TYPE`。
2. **`PITFALLS.md` §21.4**
   * **原表述**：标题为 `21.4 MIME 描述的是“你返回的字节”，不是“原始媒体”`，且链接指向已不存在的锚点 `#673-mime-必须描述onopenpreview-返回的字节`。
   * **应改为**：标题改为 `21.4 MIME 既决定分类也决定解码器（视频行 onOpenPreview 必须返回视频容器）`，链接更新为 `AGENTS.md#673-mime-既决定分类也决定解码器两者对视频冲突`。

---

### DOC-05：将 Phase 3 探针实测结论合入 `AGENTS.md`、`REFERENCE.md` 与 `TODO.md`

**背景**：`research_notes.artifact.md` 已在真机与生产环境验证了上传协议与 `fileChecksum` 机制，推翻了此前文档中“不需要自己算哈希”或“需要先逆向本地指纹算法”的假设。

1. **`AGENTS.md` §4.6 & §12（Phase 3 关键提醒）**
   * **原表述**：
     * §4.6：`上传查重直接用 resOriginalFingerprint（已实测）：Apple 已为原图/中图/缩略图各存了一份指纹字段，不需要自己计算文件哈希。Phase 3 的查重就是一次 /records/query 比对 fingerprint。`
     * §12：`resOriginalFingerprint 已现成，但指纹算法能否在本地复现是 P3 最大未知数，建议作为第一个探针任务`
   * **应改为**：更新为 Round 2 探针实测结论——客户端首先需计算本地文件的 **SHA-256** 发往 `/assets/upload` 换取 `uploadUrl`；流式上传后由 MMCS 服务端返回 21 字节的 `fileChecksum`（即 `CPLMaster.recordName` 与 `resOriginalFingerprint`）。因此前置查重需依赖本地维护的 SHA-256 索引，云端幂等守卫则在获取 `fileChecksum` 后或通过 `/records/lookup` 完成。
2. **`REFERENCE.md` §2A.5、§3、§3A.4**
   * 在 **§3** 中正式补充 Round 2 探针验证通过的端到端上传三步法（`/assets/upload` -> `singleFileUpload` 零内存 `*os.File` 流式 POST（105MB 视频免分片） -> `/records/modify` 原子提交 `CPLMaster` + `CPLAsset`）。
   * 更新 **§3A.4** 的第 ① 点，移除“未知数”描述，替换为已验证的 SHA-256 -> MMCS `fileChecksum` 双阶段查重设计。
3. **`TODO.md` — Phase 3 任务清单**
   * 将已验证的探针结论（如 `singleFile` 免分片流式直传、Room v8 `upload_queue` 表、`dataMutationMutex` 互斥、`PCS-Photos` Cookie 校验）细化更新到 Phase 3 的勾选列表中。

---

### DOC-06：清理 `CHANGELOG_ISSUES.md` 顶部乱码

* **位置**：`CHANGELOG_ISSUES.md` 第 1~2 行
* **现状**：
  ```markdown
  78+/98+
  */fiu# CHANGELOG_ISSUES.md — 已修复问题案例档案
  ```
* **修改**：删掉 `78+/98+` 和 `*/fiu`，恢复标准的 `# CHANGELOG_ISSUES.md — 已修复问题案例档案` 一级标题格式。