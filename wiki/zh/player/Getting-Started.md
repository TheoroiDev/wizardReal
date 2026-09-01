# [中文](../../zh/player/Getting-Started.md) | [English](../../en/player/Getting-Started.md)

# 快速开始

> [← 返回首页](../Home.md) · 上一篇：无 · 下一篇：[施法系统](Spellcasting.md)

Be a Real Wizard（`wizardreal`）是一个**用语音咏唱施法**的 Minecraft 1.20.1 模组：手持法杖、按住右键念出咒语，即可施放法术。语音识别由配套的 **VoiceCast**（`voicecast`）模组在**服务器端**完成——客户端只负责录音，因此不会在玩家电脑上加载大模型。

## 安装

| 平台 | 需要安装 | 前置 |
|---|---|---|
| Fabric | `voicecast-fabric-*.jar` + `wizardreal-fabric-*.jar` | Fabric Loader、Fabric API、Architectury API |
| Forge | `voicecast-forge-*.jar` + `wizardreal-forge-*.jar` | Forge 47.x、Architectury API |

> 两个模组**必须同时安装**：wizardreal 是玩法（法杖/法术/法力），voicecast 是语音引擎。客户端与服务器都需要。

## 第一次启动：选择识别引擎

进入世界后（或通过 Mod Menu / 模组列表的**配置按钮**，或游戏内命令 `/voicecast settings`）打开引擎选择界面：

| 引擎 | 大小 | 适合 |
|---|---|---|
| **词语识别（Vosk）** `vosk-text` | ~40 MB | 想直接说英文咒语单词（ignis、fulmen…），首次下载快 |
| **音素识别（IPA）** `ipa-phonemes` | ~230 MB | 想按发音念拉丁/英/中/日咒语（按音素匹配，不要求说英文） |

模型只在**首次选用时下载一次**（优先 hf-mirror.com 镜像，失败自动回退 huggingface.co），存放在 `config/voicecast/models/`。服务器会替所有人共享加载，玩家本地不需要大模型。

也可以随时用命令切换（立即生效并记住选择）：

```
/voicecast engine vosk    # 词语识别
/voicecast engine ipa     # 音素识别
```

## 拿到你的第一根法杖

- **学徒法杖**：合成（下金锭/木棍/钻石的斜向排列）；
- **火焰/雷霆法杖**：学徒法杖升级（配方见[施法系统](Spellcasting.md)）；
- 或者去探索：沙漠神殿、丛林神庙、林地府邸、要塞、废弃矿井、地牢的箱子里有小概率刷出成品法杖（3%）、**空白卷轴**（15%）和**法术典籍**（8%）。

## 开口施法（PTT）

1. **主手持有任意法杖**（此时波形 HUD 出现在准星上方）；
2. **按住右键**开始说话——绿色波形会随你的音量起伏；
3. 说出法术的**触发词**（例如 "ignis"，或仪式法术先念触发词再逐句念咒语，见[仪式吟唱](Ritual-Chants.md)）；
4. 松开右键（或停顿 0.7 秒）识别结束，法术命中则立刻施放。

> 波形图例：**红色噪点** = 模型还在下载/加载（上方有金色状态行提示进度）；**绿色波形** = 就绪，随音量起伏；准星下方**灰色斜体**是实时识别结果，识别完成后显示**白色引号文本**并在 3.5 秒后淡出；状态行出现**红色**文字表示出错（鼠标/麦克风被占用、模型缺失等）。

## 下一步

- [施法系统](Spellcasting.md)：法力、冷却、学习与卷轴；
- [法术一览](Spells.md)：15 个内置法术的数值与触发词；
- [仪式吟唱](Ritual-Chants.md)：长咒语逐句咏唱；
- 遇到问题看[排障与 FAQ](Troubleshooting-FAQ.md)。
