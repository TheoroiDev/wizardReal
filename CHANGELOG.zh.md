# 更新日志 — Be a Real Wizard (wizardreal)

中文对照版；英文为主：[CHANGELOG.md](CHANGELOG.md)（两份保持同步，冲突以英文为准）。

## Unreleased（未发布）

### Changes

- Wiki Server-FAQ 对齐 voicecast 0.3.2（defer 移除）；修正 docs/ref 路径
- 咏唱入口改版（语音改版 D9）：仪式法术的**第一句（L1）**现在用于进入咏唱并锁定变体（入口语句计为第 1 行）；空闲态念触发词/咒名改为尝试破弃快施，需咏唱熟练度（学习系统上线前一律拒绝）
- 吟唱中念出咒名即提前施放，威力按已完成行数取档（`chant_policy.skip_allowed`；禁咒拒绝跳章）
- 吟唱中受击会被打断（07 M1）：≥3 行法术回退一行，更短的直接失败；失败的咏唱施加可叠加的黑暗惩罚（`[chant]` 配置：超时模式按行数（默认 10s×行数）或固定值，黑暗基值/步长/窗口）
- 法术 JSON 新增 `chant_policy` 块：逐行威力档、跳章许可、可打断性、可选自缚；`chants` 支持语言 keyed 新格式（每语言 trigger/cast/body 变体，legacy 数组继续可读）

### Modding/API

- 数据包法术格式的机器可读 JSON Schema，可离线校验法术 JSON：仓库根 `schema/spell.schema.json`（现覆盖 `trigger.languages`、语言 keyed `chants` 与 `chant_policy`）
- `Spell` 新增默认方法 `chantPolicy()`；`DataSpell` 新增带 `ChantPolicy` 的构造器重载（旧参数列表继续可用）

### Infrastructure

- CI 将 voicecast 依赖构建进 mavenLocal（远程 maven 就绪前的过渡；voicecast#13）
- 纯开发测试 mod 移出 gradle 依赖：release jar 预下载到工作区 `resources/devmods/<loader>/`，由 `manifest.txt` 驱动接线（fabric 硬链接进 run mods 目录；forge 作为文件依赖由 Loom 重映射；Carpet 的 Forge 移植仍受阻，voicecast#38）；语音模型事实源移至 `resources/models/`

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

- 数据包法术 schema（docs/spells/spell_json.md）
