# [中文](../../zh/admin/Server-Setup.md) | [English](../../en/admin/Server-Setup.md)

# 服务器搭建

> [← 返回管理员目录](../Home.md) · 下一篇：[配置参考](Configuration.md)

## 工作原理（先读这个）

VoiceCast 的识别**完全在服务器端**：

- 客户端只采集麦克风 → Opus 压缩（约 **3 KB/s** 每个正在说话的玩家）→ 通过**原版 Minecraft 连接**发送；
- 服务器运行 Vosk / ONNX 推理，匹配法术后施放；
- **不需要开放额外端口**、不需要额外防火墙规则；玩家电脑不下载模型、不跑推理。

## 安装

把 `voicecast-forge-*.jar`（或 fabric 版）与 `wizardreal-forge-*.jar` 放入服务端 `mods/`。两个模组的服务端都要装；wizardreal 对 voicecast 是硬依赖。

## 模型下载

- 服务器启动时会**预热默认引擎**（`[server] defaultEngine`，默认 Vosk 英文 ~40 MB），玩家首次选用其他引擎时按需下载并**全服共享**；
- 下载走 HTTPS，内置镜像测速（**hf-mirror.com 优先**，huggingface.co 回退），sha256 校验；
- **无外网/下载慢**的服务器：
  - 用 JVM 参数代理：`-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10808`（Java 不读 `HTTPS_PROXY` 环境变量，但 VoiceCast 的下载器会探测该环境变量并应用）；
  - 或设 `[server] autoDownload = false` 并**手动放置**模型到 `config/voicecast/models/<模型id>/`（Vosk 解压后需含 `am/ conf/ graph/` 子目录）；
- 模型目录与校验清单见[配置参考 → models.json](Configuration.md)。

## 内存与硬件建议

| 规模 | 建议 |
|---|---|
| ≤20 在线（3–5 人同时说话） | 4 核 / 8 GB 即可 |
| ~50 在线（~10 人说话） | 16 核 / 16 GB |
| 100 在线 | 32 核 / 32 GB，且需要先做识别器闲置回收（见[性能](Performance.md)） |

共享模型层内存：Vosk 英文 ~150–250 MB；全部 4 种 Vosk 语言 ~0.8–1 GB；IPA（q4 权重 + ONNX 运行时）~400–600 MB。每个说话玩家的会话另占 30–80 MB。详见[性能](Performance.md)。

## 访问控制（快速上手）

- `[server] enabled = false` 一键全服禁用（不预热模型）；
- `[players] whitelist` 填 UUID 只允许名单内玩家使用语音（留空 = 所有人）；
- 引擎白名单 `[engines] allowed` 限制玩家可选引擎，防止触发大模型下载；
- 详见[访问控制](Access-Control.md)。

## 验证

1. 启动日志出现 `Server voice engine ready: vosk-text`；
2. 客户端进入世界、持杖右键说话，波形变绿；
3. `/voicecast engine` 可查看玩家当前引擎。

## 注意事项

- **专用服务器安全**：voicecast 服务端代码不引用任何客户端/LWJGL 类；ONNX/Vosk 的全平台 natives 已内置（Linux x64/arm、macOS 可用）；
- 法力与学习进度保存在世界目录 `wizardreal_player_magic.nbt`，**正常停止服务器时写盘**（崩溃可能丢最后一段进度）；
- 升级模组：配置 schema 自动迁移（`config/voicecast/voicecast.toml` 带版本号），旧的 `server.properties`/`client.properties` 会被一次性导入并删除。
