# [English](Configuration) | [中文](Configuration-zh)

# 配置

**咒文注音**会在你不会念的咒文行下方附一行小字读音（拼音 / 罗马音 / IPA）——吟唱 HUD 与法术典籍/图鉴书页均如此。玩法概述见[快速开始](Getting-Started-zh)。

## Be a Real Wizard — `config/wizardreal/wizardreal.toml`

`[chantReadings]` 小节：

| 键 | 默认值 | 含义 |
|---|---|---|
| `hud` | `true` | 吟唱 HUD 当前咒文行下方显示注音行 |
| `languagePolicy` | `"auto"` | 哪些行注音：`auto` = 只注**非当前游戏语言**的行（英文客户端下的中文行会注音，中文客户端下不会）；`off` = 不注；`selected` = 只注 `languages` 列出的语言桶；`all` = 所有带注音的行都注 |
| `languages` | `""` | `selected` 时生效：逗号分隔的语言桶，如 `"ja,zh"` |
| `pinyinStyle` | `"marks"` | 拼音声调样式：`marks`（符号调 zhēn）或 `numbers`（数字调 zhen1） |

- 吟唱 HUD 固定按 **拼音 > 罗马音 > IPA** 的优先级取注音——没有逐注音法开关；
- `hud` / `languagePolicy` / `languages` 是**客户端显示**选项，游戏启动时读取一次——改完需重启客户端；
- `pinyinStyle` 是**服务端配置**：注音在法术目录构建时一次性派生，服务端的样式直接烘进结果。专用服务器上所有客户端看到的都是服务端的样式——改客户端那份在专用服上无效。改动在下次目录重建时生效（重新进服、用典籍学法术，或 `/reload` 法术数据包）。

## 图鉴书页 — `config/wizardpedia/client.json`

wizardpedia 书页渲染同样的注音，但使用自己的一份策略（`chantLanguagePolicy` / `chantReadLanguages`，语义同上）加逐注音法开关：`methodPinyin` / `methodRomaji`（默认开）与 `methodIpa`（默认关）。该文件在每次打开图鉴时重新读取。两份配置相互独立——两个界面可分别调。

> [← 首页](Home-zh)
