# [English](../Home.md) | [中文](Home.md)

# Be a Real Wizard · 中文 Wiki

用**语音咏唱施法**的 Minecraft 1.20.1 双平台（Fabric / Forge）模组。手持法杖、按住右键念咒语——识别在服务器端完成，由 VoiceCast 引擎驱动。

## 10 秒快速开始

1. 同时安装 `voicecast` 与 `wizardreal` 两个模组；
2. 首次进入世界选择识别引擎（推荐先试 ~40 MB 的 **Vosk**，想用中文咏唱选 **IPA**）；
3. 合成或找到一根**法杖**，主手持握；
4. **按住右键说话**：念 "ignis" → 火球出手。

## 玩家文档

| 页面 | 内容 |
|---|---|
| [快速开始](player/Getting-Started.md) | 安装、引擎选择、模型下载、法杖、PTT 操作、HUD 图例 |
| [施法系统](player/Spellcasting.md) | 法力/冷却、法杖与学派、典籍学习、卷轴 |
| [法术一览](player/Spells.md) | 15 个内置法术的触发词、数值与效果 |
| [仪式吟唱](player/Ritual-Chants.md) | 长咒语逐句咏唱：变体、重试、超时、取消 |
| [排障与 FAQ](player/Troubleshooting-FAQ.md) | 麦克风、红波形、识别不到、引擎切换 |

## 服务器管理员文档

| 页面 | 内容 |
|---|---|
| [服务器搭建](admin/Server-Setup.md) | 工作原理（无额外端口）、模型下载与代理、内存建议 |
| [配置参考](admin/Configuration.md) | `voicecast.toml` 全键 + `models.json`/镜像/自托管 |
| [访问控制](admin/Access-Control.md) | 总开关、UUID 白名单、引擎白名单、权限模组钩子 |
| [性能与容量](admin/Performance.md) | 容量表、内存/CPU 画像、降负载手段 |
| [数据包法术](admin/Datapack-Spells.md) | 用 JSON 新增/覆盖法术，`/reload` 生效 |
| [服务器 FAQ](admin/Server-FAQ.md) | 权限/下载/升级/数据保存常见问题 |

## 相关资料

- [CREDITS](../../../CREDITS.md) — 第三方组件与许可（jar 内也随附 NOTICE）
- 开发者向：[AGENTS.md](../../../AGENTS-wizardreal.md)（构建约定）、[法术 JSON 完整 schema](../../../docs/spell_json.md)、[多引擎容量推导](../../../docs/multi_engine.md)、[wizardpedia 图鉴计划](../../../docs/wizardpedia.md)
