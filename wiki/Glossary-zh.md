# [English](Glossary) | [中文](Glossary-zh)

# 术语表 —— 正典命名

> 面向贡献者的命名正典：数据包作者、wiki 编辑者、所有新增内容的人。新术语转正时，**双语同录**到本页。本页是术语表的起点，不是全部。

## 界名 —— 两级制

界域名采用**两级制**：中文名为**正典名**（lore 权威），英文名为**工作名**（为避开原版 mob/群系同名而设，允许与正典名不逐字对应）。不要把一种语言"修正"成另一种——两级各有职责。

| 正典名（zh） | 工作名（en） |
|---|---|
| 缄默圣域 | the Silent Reach |
| 龙嗣之境 | the Dragon Elegy |
| 深脉之底 | the Deepvein |
| 四季王庭 | the Four Seasons Court |
| 星陨裂隙 | the Starfall Rift |
| 沉没和声之城 | the Sunken Chorus |

## 法术命名约定

- 语音别名**音素优先**：别名必须扛得住误听（见 wiki 页 *Spell-Alias-Guidelines*）；0.6.0 改词批重写了六组最重混淆对。
- 显示名**每种语言内唯一**，由单测锁定（`SpellLangNameLockTest`）：每个法术在双语都有带命名空间的 `spell.<id>.name` 键、不允许点号风格遗留键、不允许重名。
- 命名空间零 IP：法术/字幕/音频名不得携带第三方专有名词（见下方禁用词区）。

## 禁用词区

| 禁用 | 原因 | 改用 |
|---|---|---|
| Excalibur / 誓约胜利之剑 | 第三方 IP（Fate） | blade（千剑刃） |
| Dragon Slave / 龙破斩 | 第三方 IP（Slayers） | rift（虚空裂隙） |
| 幻弹 · 火墙 · 唤林 · 疾风步 · 轻风步 · 圣光矢 · 裸「圣光」 | 0.6.0 改词批弃用的旧别名（误触发源） | 虚影弹 · 燎墙 · 唤森 · 迅风步 · 踏云步 · 圣光箭 · 圣光束 |
| "sequel"（作为「续篇」的英译名词） | 翻译腔——英文 sequel 指续集作品，不是故事的延续 | "the tale, continued" / "continuation" |
