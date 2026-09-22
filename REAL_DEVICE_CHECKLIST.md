# wizardreal 真机测试 Checklist（语音施法 E2E）

> **如何使用**：① 执行环境——JDK 21 跑 Gradle，`gradlew :wizardreal-<fabric|forge>:runClient / runServer`；run 目录自动分离、runClient 用户名固定 `dev`、模型按 `resources/models/manifest.txt` 硬链接预置（先确认 voicecast 0.5.0 已 `publishToMavenLocal` 且 `gradle.properties` 的 `voicecast_version` 同步）。② 判定记录回写本文件末"判定记录"表；日志与截图写 `wizardreal/test/logs/`。③ 双终端流程/服务端冒烟判定词/排障以 `docs/testing/README.md` 为准，本清单不重复。
> 分级：P0 = 挡 TRL 8 闸门（builder plan 循环 2：真机 E2E 此前为零）；P1 = 发布前必过；P2 = 质量加固。**命题 P3：双终端 E2E 施法成功率 ≥80%（每法术 10 次，含首试）。**

## 0. 环境前置

- ☐ `feat/voice-reject-trim` + `feat/g2p` 已合并回 main（builder plan 循环 1 收口），本清单在 main 上执行。——✅ 2026-09-13 完成（merge `d034582`，全量 build 全绿）
- ☐ wizardreal fabric runServer 冒烟过（`Done` → 引擎就绪 → `Pushed … recognizer pronunciations` → 优雅关停；判定词见 `docs/testing/README.md` §2.3）。
- ☐ 创造模式备好典籍/法杖；`/wr learn` 可用（熟练度铺路）。

## 1. P0 — TRL 8 闸门（builder plan 循环 2）

### WR-P0-1 · 双终端 E2E：阶梯咏唱（chant_stages）

- 场景：多行法术/禁咒的阶梯咏唱全路径（首行即门、断章提前施放、禁咒拒跳章、连错失败）。
- 前置：fabric 双终端；explosion（6 行禁咒）已学习；rift/domain/vincula/blade 任一禁咒可进。
- 步骤：① 空闲念 explosion L1 变体（如"以漆黑遮蔽苍穹"）② 逐句念完 6 行 ③ 新一轮念到第 2 句后改念咒名"爆裂" ④ 禁咒中途念咒名跳章 ⑤ 同一行连错 3 次（grace 后）。
- 判定：① 直接进入吟唱且第 1 行已勾（首行即门）；② 施放且威力 1.0 档；③ 立即施放且威力 = 2 句档；④ 不施放并走失败路径（禁咒拒跳章）；⑤ 咏唱失败 + 黑暗惩罚，30 秒内二次失败时长叠加。
- 证据：`wizardreal/test/logs/e2e-chant-<日期>/`（双侧 latest.log + HUD 截图）；结果计 P3 口径。
- 状态：☐

### WR-P0-2 · 75 法术家族抽样施法（命题 P3 主体）

- 场景：magic_eco 04 扩军后的 75 家族在真机可念、可施、可复现。
- 前置：WR-P0-1 环境就绪；`/reload` 可用。
- 步骤：① 粗测：75 家族每族首试 ≥1 次（zh 触发词为主，en 抽半）② 精测：抽 10 法术（10 school 各 1 + 禁咒 1）各 10 次含首试，记录成功次数 ③ `/reload` 后看注册数报告。
- 判定：精测合计成功率 ≥80%（**P3 可证伪口径**）；`/reload` 注册数 = expected、无 parse 失败 WARN、无 `[spell-validate] L1 conflict` WARN。
- 证据：`wizardreal/test/logs/e2e-matrix-<日期>/`（逐法术成功表 + latest.log）。
- 状态：☐

### WR-P0-3 · 拒识负样本实测

- 场景：CTC 权威拒识分级 + 语言桶裁剪在真机的误触发治理效果。
- 前置：`wizardreal.voice.rejectLevel` 默认 0（legacy 基线）；准备 20 条日常对话/无关语音负样本（可参照 ipa 实验室 117 条 clean-negative 口径）。
- 步骤：① 基线：空闲念 20 条负样本，记录误触发数 ② 开启 rejectLevel（逐级）后同语料复测 ③ zh 引擎会话念 en 触发词、en 会话念 zh 触发词（`[voice] languages` 桶裁剪）④ 正例 10 条复测确认召回降幅。
- 判定：① 基线误触发数记录在案（不设硬门）；② 开启后误触发显著下降且正例召回降幅 ≤3pp（对齐 builder plan P1 口径）；③ 跨语言触发词 0 触发。
- 证据：`wizardreal/test/logs/e2e-reject-<日期>.log`（逐条判定表）。
- 状态：☐

### WR-P0-4 · g2p 真机拼读验证

- 场景：游戏内 g2p 草案（汉/假名/谚文 → 音素）在 IPA 引擎下真机可拼可识。
- 前置：客户端引擎切到 `zipa-ipa`；选 3 组模板缺失的法术词（未登录汉字、含促音/拗音/长音的日语词、可分解谚文词）。
- 步骤：① 每组词真人念 10 次 ② 对照同词的 ipa-backtest 实验室命中率 ③ 故意混入完全不可转换段。
- 判定：真机命中率与实验室回测结论同量级（偏差 >20pp 记挂起待查）；不可转换段只降级不出坏模板（无崩溃、无错误施法）。
- 证据：`wizardreal/test/logs/e2e-g2p-<日期>.log`（逐组命中表）。
- 状态：☐

## 2. P1 — 发布前必过

### WR-P1-1 · 学习体系与阶梯双门（行数 × 熟练度）

- 步骤：① 新档全法术 t=10% 起步、威力 0.5× ② requires_learning 法术 t<门槛时 `/wr cast` 被拒（提示含 %）③ 熟练度不足时念深层阶梯 → 解析到最高解锁档 ④ `/wr learn <spell>` 默认 +10% 与难度折扣各验一次。
- 判定：与 CHANGELOG 口径一致（0.5x@10% → 1.0x@100%；双门取低档；点数 = 百分比 × 难度）。证据：`wizardreal/test/logs/e2e-learn-<日期>.log`。状态：☐

### WR-P1-2 · 13 新原语效果抽验

- 步骤：hex/bind/pull/blink/surface/barrier/summon/excavate/harvest/smelt/visual/weather+light 每原语至少 1 个法术真机施放并目视效果。
- 判定：每个原语效果可见且行为符合 `docs/magic_eco/04_spell_matrix.md` 描述；无客户端崩溃。证据：`wizardreal/test/logs/e2e-primitives-<日期>/`（每原语 1 截图）。状态：☐

### WR-P1-3 · 咏唱打断与超时

- 步骤：① 3+ 行法术吟唱中受击 ② ≤2 行法术受击 ③ interruptible=false 禁咒受击 ④ per_line（默认）与 fixed 超时模式各验一次。
- 判定：① 回退一行（HUD 高亮后退）② 整体失败 ③ 咏唱不受影响 ④ 超时走失败路径（黑暗）。证据：`wizardreal/test/logs/e2e-interrupt-<日期>.log`。状态：☐

### WR-P1-4 · forge 双终端同型复验

- 步骤：forge runClient + runServer，走 WR-P0-1 ③④ 与 WR-P0-2 精测 3 法术。
- 判定：与 fabric 同判。证据：`wizardreal/test/logs/e2e-forge-<日期>/`。状态：☐

### WR-P1-5 · HUD 与威力缩放

- 步骤：① 观察 chant HUD 标题旁熟练度 % 与咒文行语言 ② t=10/55/100 三点实测治疗量/伤害 ③ 满熟练测弹数类 ④ 观察音效/粒子。
- 判定：咒文行 = 玩家引擎语言 aliases[0]；伤害/治疗线性、状态时长 sqrt、弹数威力 ≥2 翻倍、音效粒子不缩放。证据：`wizardreal/test/logs/e2e-hud-power-<日期>.log`。状态：☐

## 3. P2 — 质量加固

### WR-P2-1 · `/wr` 命令组回归

- 步骤：`learn/unlearn/cast/known/spells/spellinfo` + console 无目标时报 "A player is required"。
- 判定：与 `docs/testing/README.md` §2.4（2026-09-02 记录）行为一致。证据：`wizardreal/test/logs/cmd-regression-<日期>.log`。状态：☐

### WR-P2-2 · spell_catalog.json format 3 导出核对

- 步骤：进服一次后检查 `<游戏目录>/wizardreal/spell_catalog.json`。
- 判定：formatVersion 3；`effects` 与 `chant_stages` 字段在；触发词/咒文按语言分桶；UTF-8 CJK 完好。证据：文件拷贝归档 `wizardreal/test/logs/`。状态：☐

### WR-P2-3 · lockout 与连续施法回归

- 步骤：施法完成后 3 秒内重复发声。
- 判定：不重触发（旧机制不变）。证据：`wizardreal/test/logs/lockout-<日期>.log`。状态：☐

### WR-P2-4 · 老档迁移

- 步骤：0.3.x 存档载入后查 `/wr known` 与可施性。
- 判定：遗忘法术 t=0（不可施）、其余回基线；无 NBT 异常。证据：`wizardreal/test/logs/migration-<日期>.log`。状态：☐

## 4. 判定记录

| 日期 | 项 | 结果（过/挂 + 成功率等关键数字） | 日志位置 |
|---|---|---|---|
| | | | |
