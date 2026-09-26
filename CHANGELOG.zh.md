# 更新日志 — Be a Real Wizard (wizardreal)

中文对照版；英文为主：[CHANGELOG.md](CHANGELOG.md)（两份保持同步，冲突以英文为准）。

## Unreleased（未发布）

### Features

- 咒文注音层（咏唱读法标注）：服务端在目录构建期对每条咏唱行一次性派生 `readings` 注音映射——zh 行得有调本调拼音（教科书标调规则，调号取自内嵌数据表；v1 不做变调），ja 行得平文式罗马字（新增纯 JVM KanaRomaji 表：促音叠辅音、语尾 っ 以撇号表示、长音合流为 macron、按 mora 分隔输出、外来音节 ティ/ファ/ウィ 与 ん=n）。注音以固定键集（`pinyin`/`romaji`/`ipa`）随目录下发；派生严格 fail-closed（任一不可转字母即缺该键——不造假读法），标点/空白按分隔符丢弃（手订模板本就不含停顿 token，丢标点才与手订同源）；行手订 IPA 永远优先于 G2P 派生。lab 新增 lint（`check_readings.py`）交叉核对派生与手订 IPA，兼任 Stage-A 吸收清单（首跑全量：689 条手订行中 687 条完全收敛）
- 咏唱 HUD 注音（D4，默认开启）：咏唱进行中在当前行上方显示小号灰色注音行，读法取自目录派生的 readings；语言策略与书页同语义（`[chantReadings] languagePolicy`：auto=非显示语言才注 / off / 手选语言集 / all，配 `languages` 指定手选集）。新增 `[chantReadings] pinyinStyle` 选择拼音显示样式——符号调（`zhēn`，默认）或数字调（`zhen1`，永远可渲染）——在目录派生期应用，改动于下次目录重建（登录 / 数据包重载）后生效
- G2P 管线补全：为全部 75 个法术 JSON 此前为空的 ipa 字段生成 769 条草案（zh 咏唱行全覆盖；ja 受限于假名行待形态分析器），并新增 `[voice] g2pDrafts` 运行时填充（现**默认开**，见 Changes），服主无需动数据包即可给自定义词生成模板
- 游戏内 G2P（字素转音素）草案：为未知/自定义咒语词生成 IPA 模板——汉字转拼音（内置 MIT 许可数据表，2.67 万字）+ 声母/韵母 espeak 风格 IPA 组合、假名（促音/长音/拗音）与谚文分解；人工精选模板永远优先，草案保持严格（任一段不可转换即不出模板），且必须通过 ipa 回测质量闸门才会进入识别器
- 语音误触发治理：`[voice] languages` 配置把识别词表与匹配候选裁剪到启用语言桶（无桶的 legacy 发音始终保留）；CTC 权威拒识等级（`wizardreal.voice.rejectLevel`，默认 0 = 原行为）可在精度优先服务器上抑制就近吸附的兜底匹配层
- 目录 v3（配合 wizardpedia 双页图鉴）：条目携带 `entity`（mob 条目实时实体预览）、学派 tags（右轨筛选）、基础+各阶效果摘要（`wizardreal.effect.<type>` lang 键，覆盖全部 22 个内置效果类型 en+zh——未来法术自动覆盖）、按语言嵌套的咏唱变体、阶梯咏唱数据（`chant_stages`：门=完成行数/熟练度 + 耗魔/冷却覆盖），以及熟练度/耗魔/冷却/难度标量（wizardreal#27）

### Changes

- breaking: `[voice] g2pDrafts` 现默认**开启**（原为关）：无手工 IPA 模板的词表条目在推送时经生产 G2P 链补草案模板（voiceCast#47——0.5.0 引擎切换后 en/ja 咏唱行完全没有 zipa CTC 模板，运行时填充为 G2P 链支持的所有语言恢复模板覆盖：zh/ja/ko；en 暂无 Tier-2、维持 fail-closed）。0.6.0 的 CTC 后验校准（R3/R5 移植）落地后，原先"校准前无 CTC 收益"的保留理由不再成立。草案逐别名严格转换、永不覆盖手工模板——如需旧行为，在配置中设 `g2pDrafts = false`
- 中文语音咒文改词（改词批实验室回测：六个最重混淆对在新词面上从 14-64% 误匹配降到 0-3%）：弃用"幻弹"保留"虚影弹"（falsum）、火墙→燎墙（vestibulum）、唤林→唤森（silva_voco）、疾风步→迅风步（volatus）、轻风步→踏云步（aurae_levitas）、圣光矢→圣光箭（sagitta）；裸短别名"圣光"弃用（"圣光束"保留）——它被其它法术的咒文行包含、持续误触发对方。咒文行已同步新词面，旧别名不再生效；自定义词面命名规范见 wiki「Spell-Alias-Guidelines」
- breaking: `wizardreal:spell_catalog` S2C 升 formatVersion 3；`spell_catalog.json` 导出升 format 3，新增 `effects` 与 `chant_stages`

### Bugfixes

- 多别名法术词的 G2P 草案查询不再串到其它语言的读音：内部 Tier-0 词典把第一条人工 IPA 模板绑给了该表面的**全部**别名——英文别名 "radix" 会拿到中文"缠根"的读音草案、ja 别名拿到 zh 模板（lab readings 审计发现）。现在只有单别名表面注册词典，其余别名落到按文字系统的草案链；此前被错绑遮蔽的 ja 假名/汉字草案也因此恢复可用（2026-09-26）

- 中文/日文语音咏唱不再静默卡死：咏唱引擎的噪声门用 ASCII 正则清洗转写，zh/ja 文本线咏唱行（qwen3）被整句吞掉——咏唱不前进、不报错、只会超时（chant 序列回测发现：zh/ja 行进率恰为 0%）。门控现统计全部 Unicode 字母/数字（\p{L}\p{N}）；en 行为不变

- 咏唱中退出不再由退出处理器重新声明 OPEN 施法模式：VoiceCast 自身的退出清理本就会清掉该玩家的会话与模式声明，两者竞争可能在 VoiceCast 侧留下以已退出玩家 UUID 为键的残留声明

### Infrastructure（基础设施）

- common 子工程现可发布到 Maven（`gradlew publishToMavenLocal` -> `com.theo.wizardreal:wizardreal-common-1.20.1`，镜像 voicecast-common 的 publish 块）：普通未分类 jar + sources，让非 MC 消费方（lab/java-harness 识别 harness、未来工具链）能按坐标解析纯 JVM 子集（G2P、Pronunciation/SpellRegistry api）。仅构建基建——零逻辑改动

### Modding/API

- breaking: CatalogPayload v4——咏唱行结构化为 `CatalogLine(text, readings)` 记录（取代纯字符串）；`wizardreal:spell_catalog` S2C 通道升 formatVersion 4（每行 = 文本 ≤160 + readings 映射，键 ≤8 / 值 ≤128 UTF-16 单位，超限截断不拒绝）；`spell_catalog.json` 导出升 format 4，每条咏唱行携带 `key`/`text`/`readings`
- 语音匹配器落定 lab 校准的 S6 工作点（wizardreal#29）：IPA 音素匹配器以数据驱动的加权编辑距离取代平权 Levenshtein——872 对混淆代价表以 jar 资产随包分发（`assets/wizardreal/phoneme_costs.tsv`；表内代价匹配时 `clamp(raw x 2.0, 0.1, 1)`，表外替换保持平权 1.0，插入/删除双向 0.6，原"目标音素免费跳过"取消——被吞音素现计 0.6）。lab 台架上方言口音召回显著提升（正样本 74.3% vs 60.7%，负误触 8/300）；代价资产缺失为硬错误，无平权回退
- CTC margin 拒识随语义 v2 链沿用（VoiceCast 侧规则）：前两名模板后验差距小于 0.02 时该语句整组 CTC 分数清零——voicecast 裁定器读取 margin 前的差距，被压制的 top1 本可过 forward 阈值时判 `AMBIGUOUS`；per-spell 阈值以推送期 `ThresholdHint` 数据过界（v2，见下）
- 文本别名匹配的精确同分平局改按最长别名优先（最具体匹配），再按最小 spell id——短别名被包含在其他法术更长别名内时不再靠候选顺序抢胜
- 新增 `PerModeThresholdProvider` 机制接口（match 包），按模式暴露三层匹配器阈值，`fullVocabulary()` 默认实现返回现行常量；按模式重标定的接线随四模式施法期路由（#30）落地
- 四模式施法期词表路由（issue #30，D-15 用户裁决）随 VoiceCast 依赖 0.5.0 落地：
  - 自由施法运行于 OPEN 模式（玩家进服即声明）——按 P30 复验后监工裁决，OPEN 维持全词表 ∩ 引擎语言桶，候选集与 #30 前默认完全一致：触发语+释放语的精炼已回退（Phonetics 层会把缩圈后的 OPEN 误触发重排而非消除），自由施法行为不变
  - 阶梯咏唱开始即对当前法术声明 CHANT_CONFIRM（咏唱中会话只听该法术的行），完成/提前释放/失败/取消/超时/退出/注册表重载即回退 OPEN
  - per-mode forward 阈值随 voicecast 资产 `assets/voicecast/mode_thresholds.tsv` 分发（首批镜像出厂 0.10 常数——行为中性）；match 包的 `ResourceModeThresholdProvider` 负责读取，重标定值偏离常数前不接线
  - 练习入口（B 水晶 / E 引导 / 导师 NPC）后续经 `CastMode.PRACTICE_CONFIRM` 接入（M4——本单只交付接口，尚无消费点）
- breaking：语音交互契约重键到 VoiceCast 语义化 v2 结果（engine-swap C1b，VoiceCast 依赖仍为 0.5.0 并含 C1b api）：识别 final 携带裁定结果 `Decision`（EXACT / NEAR / AMBIGUOUS / REJECTED）+ `spellId`/`pronId`/`score`/`alternatives`，`ChantGate` 变为纯语义门（Decision -> ENTER / INSTANT / SKIP / NONE）。消费方 matcher 链（templateScores 读取、音素/文本匹配器、`FORWARD_MATCH_THRESHOLD` 常量、ctcPresent 门槛）删除——决策权收归 voicecast，wizardreal 只把裁定映射为玩法；玩家可感知行为由共享等价性向量（`c1b_vectors.json`，voicecast + wizardreal 测试共用）钉死等价，契约文档见 `docs/ref/voicecast-recognition-contract.md`
- breaking：matcher 机器迁入 voicecast（本仓删除 `PhonemeMatcher`、`SpellMatcher`、`Phonetics` 与代价资产 `assets/wizardreal/phoneme_costs.tsv`）；`com.theo.wizardreal.api.Pronunciation` 成为 wizardreal 自有内容类型（voicecast 推送类型为 `SessionVocabulary`）；`PerModeThresholdProvider`/`ResourceModeThresholdProvider` 改产 `ThresholdHint` 数据而非 matcher 常量
- reject level 重键到 Decision：`EXACT` 任意 level 接受，`NEAR` 仅 level 0，`AMBIGUOUS`/`REJECTED` 一律不施法；旧 ctcPresent 条件性的分面压制（level ≥ 1 杀触发词匹配面、level ≥ 2 杀宽松首行面）改为推送期阈值 hint（数据过界）——首行即门优先级与咏唱进度门不变

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
- 回测工具（`tools/benchmark`：`IpaBench`/`EngineBench`/`LiveBench` + `ipafill.py`/`engbench.py`）移植到 0.5.0 契约——ipa 臂经 `SessionVocabulary` 驱动 `ZipaPhonemeRecognizer`、读取 `RecognitionDiagnostics.templateScores()`，文本臂为 Qwen3-ASR 离线引擎，对着已发布的 voicecast jar 编译验证；已删除的 streaming/SenseVoice 引擎分支移除

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
