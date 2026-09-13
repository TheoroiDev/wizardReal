# 更新日志 — Be a Real Wizard (wizardreal)

中文对照版；英文为主：[CHANGELOG.md](CHANGELOG.md)（两份保持同步，冲突以英文为准）。

## Unreleased（未发布）

### Features

- 游戏内 G2P（字素转音素）草案：为未知/自定义咒语词生成 IPA 模板——汉字转拼音（内置 MIT 许可数据表，2.67 万字）+ 声母/韵母 espeak 风格 IPA 组合、假名（促音/长音/拗音）与谚文分解；人工精选模板永远优先，草案保持严格（任一段不可转换即不出模板），且必须通过 ipa 回测质量闸门才会进入识别器
- 语音误触发治理：`[voice] languages` 配置把识别词表与匹配候选裁剪到启用语言桶（无桶的 legacy 发音始终保留）；CTC 权威拒识等级（`wizardreal.voice.rejectLevel`，默认 0 = 原行为）可在精度优先服务器上抑制就近吸附的兜底匹配层
- 目录 v3（配合 wizardpedia 双页图鉴）：条目携带 `entity`（mob 条目实时实体预览）、学派 tags（右轨筛选）、基础+各阶效果摘要（`wizardreal.effect.<type>` lang 键，覆盖全部 22 个内置效果类型 en+zh——未来法术自动覆盖）、按语言嵌套的咏唱变体、阶梯咏唱数据（`chant_stages`：门=完成行数/熟练度 + 耗魔/冷却覆盖），以及熟练度/耗魔/冷却/难度标量（wizardreal#27）

### Changes

- breaking: `wizardreal:spell_catalog` S2C 升 formatVersion 3；`spell_catalog.json` 导出升 format 3，新增 `effects` 与 `chant_stages`
### Features

- 阶梯咏唱（magic_eco 03）：法术可定义 `chant_stages` 升级阶——咏唱念得更深即切换到该阶自己的效果列表（可覆盖耗魔/冷却），解锁门槛=已完成行数×施法者熟练度百分比**双门槛**；熟练度不足时向下结算到已解锁的最高阶——用低阶练习成长解锁高阶
- 法术生态扩军（magic_eco 04）：内置法术增至 75 个家族（10 流派 × 7+，禁咒 rift/domain/vincula/blade 与爆裂同列 6 行全念仪式），覆盖探索/移动、战斗、挖矿、种地生态位；每家族配 5-6 行三语咏唱 × 2 重掷变体，入口行全库互异（L1 冲突检查清零）
- 新增 13 个数据包效果原语：`hex`（对敌锥面/球域状态）、`bind`（移动锁）、`pull`（漩涡/磁吸）、`blink`（安全步进传送）、`surface`（到期复原的地表覆盖：火/冰/荆棘）、`barrier`（墙/环/囚笼结构，到期复原）、`summon`（临时归属仆从）、`excavate`（隧道/区域挖掘）、`harvest`（作物收割/补种/催熟）、`smelt`（触点熔炼）、`visual`（编排粒子演出——ring/helix/pillar/burst/trail/cone/cross/orbit 形状 + 彩色 dust，随时间回放）、`weather` 与 `light`（发光标记 + 夜视）
- 目录 v2（语音改版 SS8）：法术目录携带语言维度——触发词按语言桶分组、吟唱行按语言分组（legacy 行保持中立 lang key）；每法术新增 `difficulty`/`learning`（熟练度 %）/`skip_allowed`，随目录推送到客户端与 wizardpedia（wizardreal#26）

### Changes

- 施法验证前先解析咏唱阶：耗魔与冷却取该阶覆盖值（法杖乘区照常生效），执行效果切换为该阶列表
- breaking: `spell_catalog.json` 导出升到 format 2 —— `trigger.aliases`/`chants` 按语言分组（`""` = 中立 legacy 桶），并新增 `difficulty`/`learning`/`skip_allowed` 字段
- Wizardpedia 条目可按语言页渲染：触发词与吟唱行按语言交付，图鉴据此实现语言子页

### Features

- 学习体系重做（语音改版 D4/D-D2）：所有法术熟练度从 10% 起步，只在实际施法成功时增长（语音 +2%、典籍研读至 75% 封顶、卷轴后续）；威力随熟练度成长（10%→0.5×、100%→1.0×、全局 2.5× 封顶），`requires_learning` 法术熟练度不足 10% 不可施法；"已掌握"由熟练度派生（knownSpells/forgottenSpells 集合删除——老档自动迁移：已遗忘法术钉在 0%，其余回到基线）
- `/wr (un)learn [player] <spell> [amount]`：数字 = 增减百分点、`N%` = 设为绝对值（默认 ±10%）；`/wr known` 与法术图录均由熟练度派生

### Changes

- breaking: 法术矩阵批次0 重写（07 ×3）：旧 15 法术集替换为语言 keyed 的批次0 矩阵（10 弹位 ignis/unda/fulmen/saxum/ventus/spina/sagitta/ossum/telum/falsum、4 用位 fulgur/semina/sanare/velum、禁咒 explosion 重做为 6 句多语言变体吟唱）；被移除的 id（aegis/arcanum/gaia/ictus/mare/mortis/sanctus/tempest/umbra/vitae）后续批次回归——学习计数随新 id 重计，ko 变体按计划滞后一批（加载校验器 WARN）
- Wiki Server-FAQ 对齐 voicecast 0.3.2（defer 移除）；修正 docs/ref 路径
- 咏唱入口改版（语音改版 D9）：仪式法术的**第一句（L1）**现在用于进入咏唱并锁定变体（入口语句计为第 1 行）；空闲态念触发词/咒名改为尝试破弃快施，需咏唱熟练度（学习系统上线前一律拒绝）
- 吟唱中念出咒名即提前施放，威力按已完成行数取档（`chant_policy.skip_allowed`；禁咒拒绝跳章）
- 吟唱中受击会被打断（07 M1）：≥3 行法术回退一行，更短的直接失败；失败的咏唱施加可叠加的黑暗惩罚（`[chant]` 配置：超时模式按行数（默认 10s×行数）或固定值，黑暗基值/步长/窗口）
- 法术 JSON 新增 `chant_policy` 块：逐行威力档、跳章许可、可打断性、可选自缚；`chants` 支持语言 keyed 新格式（每语言 trigger/cast/body 变体，legacy 数组继续可读）
- 法术效果现在随施法威力缩放（吟唱档位 × 熟练度）：伤害/治疗/射程/爆炸线性缩放，状态时长亚线性（开方）且强化等级至多 +2，弹丸数量随威力增长——威力来自吟唱档位与熟练度；音效/粒子等纯演出不缩放
- 法术加载期校验报告：仪式链首行（L1）近似冲突、禁咒字段组错误（难度 ≥2 但缺 requires_learning/skip_allowed=false）、IPA 覆盖缺口，以及 reload 日志中的注册数量行
- 吟唱 HUD 在法术名旁显示该法术熟练度百分比（经 magic_sync 同步）

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
