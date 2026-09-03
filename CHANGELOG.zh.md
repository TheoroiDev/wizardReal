# 更新日志 — Be a Real Wizard (wizardreal)

中文对照版；英文为主：[CHANGELOG.md](CHANGELOG.md)（两份保持同步，冲突以英文为准）。

## Unreleased（未发布）

### Infrastructure

- CI 将 voicecast 依赖构建进 mavenLocal（远程 maven 就绪前的过渡；voicecast#13）
- Fabric 开发运行捆绑 Carpet 测试 mod（Forge 移植受阻；voicecast#38）

### Changes

- Wiki Server-FAQ 对齐 voicecast 0.3.2（defer 移除）；修正 docs/ref 路径

## 0.3.2 — 2026-09-02

### Features

- 法杖/法术书/卷轴的 Blockbench 3D 物品模型，卷轴与法术书带学派染色变体；法杖灵光环境特效；学派物品模型属性
- Wizardpedia 集成：法术目录自导出并以零依赖推送给 wizardpedia（`wizardpedia:catalog` provider 端）
- 开发命令组 `/wr learn|unlearn|cast` 与 `/spellinfo`（wizardreal#18）
- 战利品表法术书绑定随机可学法术

### Bugfixes

- `PlayerMagicState` 每 5 分钟自动存盘（wizardreal#7）
- 创造模式标签、学派染色与物品名回落到同步的法术目录
- 法术书/卷轴封面在 GUI 正确渲染；origin 语言键改用点号（E2E 验证）
- 开发运行 `runServer`/`runClient` 目录分离——Windows 可并行
- 语音模型以硬链接自动预置进运行目录

### Modding/API

- `spell_catalog.json` 自导出：DTO + builder + S2C（catalog 线格式 v1 provider 端）

### Infrastructure

- 文档审计 001 修复；Wiki 重构为 GitHub-wiki 布局；GitHub Actions 构建流水线；issue 模板；add-to-project workflow

## 0.3.1 — 2026-09-01

### Changes

- M7b 兼容性加固；依赖 voicecast 0.3.1

### Modding/API

- 移除 wizardreal 本地 `ModDetection`（由 voicecast 的 `compat/ModDetection` 提供）

### Infrastructure

- 添加 Simple Voice Chat 作为开发 mod 用于 M7b 共存测试

## 0.3.0 — 2026-09-01（工作区拆分基线）

### Features

- 玩法基线（M4–M7a）：15 个数据包驱动的法术覆盖 10 学派、法力消耗与冷却、法杖/卷轴/法术书物品、first_lock 变体仪式吟唱、施法 HUD
- 服务端权威施法：识别结果在服务端校验（冷却/法力/origin）

### Modding/API

- 数据包法术 schema（docs/spells/spell_json.md）；机器可读 JSON Schema 位于 `wizardreal/schema/spell.schema.json`
