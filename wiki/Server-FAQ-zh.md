# [English](Server-FAQ) | [中文](Server-FAQ-zh)

# 服务器 FAQ

> [← 返回管理员目录](Home-zh.md) · 上一篇：[数据包法术](Datapack-Spells-zh)

## 玩家反馈"没有使用语音施法的权限"

按顺序检查：

1. `config/voicecast/voicecast.toml` → `[server] enabled` 是否被改成 `false`；
2. `[players] whitelist` 是否非空且不含该玩家 UUID（空名单 = 所有人可用）；
3. 引擎被拒是另一回事：提示 "engine not allowed" 对应 `[engines] allowed` 白名单。

## 模型下载失败 / 服务器不出网

- 给 JVM 加代理：`-Dhttps.proxyHost=<host> -Dhttps.proxyPort=<port>`；VoiceCast 的下载器也会探测 `HTTPS_PROXY` / `HTTP_PROXY` 环境变量；
- 换镜像/自托管：编辑 `config/voicecast/models.json`，把 `urls` 指向你的内网 HTTP 服务（支持多镜像自动测速回退）；
- 完全离线：`[server] autoDownload = false`，然后手动把模型放到 `config/voicecast/models/<模型id>/`（Vosk 解压后需含 `am/ conf/ graph/`）；
- 下载完整性有 sha256/minBytes 校验，失败会自动换镜像重试。

## 玩家说"引擎 X 加载失败"

日志里找 `Server voice engine failed to start: <engine>`。常见原因：模型缺失（上条）、磁盘空间不足、q4 模型下载不完整（IPA 需 ≥150 MB 的 `model_q4.onnx`）。修复后让玩家重新 `/voicecast engine <名字>` 即可重建会话。

## 能只装 voicecast 不装 wizardreal 吗？

可以。voicecast 是独立语音库（无 wizardreal 时没有法术触发，但仍提供引擎 SPI 给其他插件型模组）。反过来 wizardreal **必须**有 voicecast（硬依赖）。

## 需要开放额外端口吗？

不需要。所有语音数据走原版 Minecraft 连接（客户端 → 服务器的小包流）。

## 与 Simple Voice Chat / Plasmo Voice 冲突吗？

不冲突。VoiceCast 的麦克风只在"持杖 + 按住右键"时打开，松开立即释放设备；服务端识别与语音模组互不感知。玩家说话时语音聊天正常收音。

对 Simple Voice Chat 有**正式集成**（M7b）：共存全自动——VoiceCast 与 SVC 共享麦克风，无需任何配置。`voicecast.toml` 的 `[compat] svcCoexistence` 是**客户端本地设置**（每个玩家各自的配置，服务端不读取也不同步）。旧 `defer` 模式已在 voicecast 0.3.2 移除，旧配置自动回落 `share`。详见 `docs/ref/compatibility.md`。

## 如何彻底关闭语音但保留法术？

`[server] enabled = false`。服务器不加载任何模型；wizardreal 仍可用卷轴施法、数据包法术照常工作。

## 数据保存在哪、会丢吗？

- 世界目录 `wizardreal_player_magic.nbt`：每位玩家的法力、已学法术、冷却；
- 服务器**每 5 分钟自动存盘**并在**正常停服时写盘**——崩溃至多丢失最近 5 分钟的进度（定期快照建议交给你的备份方案）；
- 模型与配置在 `config/voicecast/`，与存档无关，可跨存档复用。

## 升级模组要注意什么？

- 配置 schema 带版本号，缺失键自动补默认值并回写；旧 `server.properties`/`client.properties` 一次性导入后删除；
- 模型目录跨版本通用，不需要重新下载；
- 数据包法术的 schema 变更会在日志中以严格校验错误标出，修好 JSON 后 `/reload` 即可。

## 哪里看错误日志？

- 服务端：`logs/latest.log`（搜索 `VoiceCast`、`voice engine`、`Denied`、`ERROR`）；
- 崩溃：`crash-reports/`；
- 客户端识别链路问题让玩家加 `-Dvoicecast.verbose=true` 复现后附日志。
