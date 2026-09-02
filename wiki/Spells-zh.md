# [English](Spells) | [中文](Spells-zh)

# 法术一览

> [← 返回首页](Home-zh.md) · 上一篇：[施法系统](Spellcasting-zh) · 下一篇：[仪式吟唱](Ritual-Chants-zh)

共 **15 个内置法术**：5 个瞬发 + 10 个仪式吟唱。所有法术都通过**语音触发**（说触发词），数值均为默认法杖（学徒法杖）下的原始值。

## 瞬发法术（说出触发词立即施放）

| 法术 | 触发词 | 学派 | 法力 | 冷却 | 效果 |
|---|---|---|---|---|---|
| **Ignis** 火焰 | ignis · fire · flame · fireball | 火 | 5 | 2s | 射出小型火球 + 火花粒子 |
| **Fulmen** 落雷 | fulmen · lightning · thunder · thunderbolt | 雷 | 20 | 5s | 48 格内落雷 |
| **Vitae** 生命 | vitae · heal · vitality · cure | 圣 | 10 | 4s | 治疗 8 ❤ |
| **Aegis** 圣护 | aegis · shield · protect · protection · ward | 奥术 | 12 | 6s | 抗性 II + 防火 10 秒 |
| **Ictus** 闪现 | ictus · gust · wind · push · shove · strike | 风 | 3 | 1.5s | 6 格锥形击退 |

## 仪式法术（念触发词进入吟唱，逐句咏唱后施放）

每个仪式法术有 **3 种咒语变体（英文/中文/日文）× 3 句**，第一句锁定的变体即本次吟唱使用；最后一句**通常**是法术的触发词（个别中文变体使用译名；以吟唱 HUD 高亮句为准）。详见[仪式吟唱](Ritual-Chants-zh)。

| 法术 | 触发词 | 学派 | 法力 | 冷却 | 需学习 | 效果 |
|---|---|---|---|---|---|---|
| **Explosion** 爆裂 | explosion · explode · burst · 爆裂 | 火 | 50 | 10s | ✅ | 32 格内落雷 + 威力 6 爆炸（**破坏方块并点燃**） |
| **Arcanum** 奥术 | arcanum · arcane · 奥术 | 奥术 | 35 | 12s | — | 抗性 II + 伤害吸收 III 各 20 秒 |
| **Gaia** 大地 | gaia · 大地 | 土 | 35 | 12s | — | 20 格内威力 3 爆炸（不破坏方块） |
| **Mare** 沧海 | mare · tide · 沧海 | 水 | 30 | 12s | — | 5.5 格锥形强击退 + 气泡 |
| **Mortis** 死亡 | mortis · wither · 亡灵 | 死灵 | 25 | 12s | — | 凋零 II + 缓慢 II 各 10 秒 |
| **Sanctus** 神圣 | sanctus · holy · 圣光 | 圣 | 40 | 14s | — | 治疗 10 ❤ + 再生 II 10 秒 + 伤害吸收 II 20 秒 |
| **Semina** 自然 | semina · nature · 自然 | 自然 | 30 | 12s | — | 治疗 6 ❤ + 再生 II 15 秒 |
| **Tempest** 风暴 | tempest · storm · 风暴 | 雷 | 30 | 12s | — | 48 格内落雷 |
| **Umbra** 暗影 | umbra · veil · 幻影 | 幻术 | 30 | 12s | — | 隐身 + 速度 II 各 20 秒 |
| **Ventus** 疾风 | ventus · gale · 狂风 | 风 | 25 | 10s | — | 8 格锥形强击退 |

> 中文/日文变体的每句咒语都带有拼音/罗马音别名——用 **IPA 引擎**时直接用普通话/日语念即可被识别；用 **Vosk 引擎**时同样可以念拼音/罗马音（Vosk 按拉丁字母转写匹配）。

## 触发词可以整句夹带

匹配器支持整词包含：说 "cast ignis now" 同样能命中（exact match 得分更高，但都超过匹配阈值，施法效果相同；多词触发词如 "explosion magic" 出现在语句中记 0.95 分）。含糊发音会走模糊匹配，得分低于阈值时不会误触发。

## 自定义法术

服务器管理员可以通过**数据包**添加/覆盖法术（不需要写代码），见管理员文档[数据包法术](Datapack-Spells-zh)。
