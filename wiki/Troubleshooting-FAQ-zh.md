# [English](Troubleshooting-FAQ) | [中文](Troubleshooting-FAQ-zh)

# 排障与 FAQ

> [← 返回首页](Home-zh.md) · 上一篇：[仪式吟唱](Ritual-Chants-zh)

## 没有波形 HUD

1. **主手必须持有法杖**（HUD 只在持杖时显示）；
2. 必须**在世界内**（主菜单不显示）；
3. 确认两个模组都装了：wizardreal（玩法）+ voicecast（语音）。

## 波形是红色噪点

红色 = 模型尚未就绪。看波形上方的**状态行**：

- **金色**"正在下载/准备模型"：首次下载，耐心等待（Vosk ~40 MB / IPA ~150 MB，int4/q4 ONNX）；
- **红色**"引擎加载失败/模型缺失"：看服务器日志 `logs/latest.log`。若服务器设置了 `autoDownload = false`，需要管理员手动放置模型文件；
- **红色**"麦克风不可用"：见下一条。

## "麦克风不可用" / 没有声音被识别

1. 检查操作系统**麦克风隐私权限**是否放行了 Java/Minecraft；
2. 确认系统默认录音设备正确（VoiceCast 使用系统默认输入设备）；
3. 其他程序（Discord、OBS）独占麦克风时可能冲突——关闭独占模式（Windows：设备属性 → 高级 → 取消"独占模式"）；
4. 与 Simple Voice Chat / Plasmo Voice 共存：VoiceCast 的麦克风**只在持杖按住右键时打开**，松开立即释放，正常情况下与语音模组互不干扰。

## 识别不到我的咒语

- **语速放慢、发音清晰**，尤其触发词（"ignis" 不要吞音成 "ign"）；
- `vosk-text` 引擎只认**英文**发音——中文/日文吟唱请切换 CJK 母语引擎（`/voicecast engine vosk-cn` / `vosk-jp`，韩语 `vosk-kr`）；IPA 引擎按音素匹配是另一种替代项，并非中文吟唱的唯一途径；
- IPA 引擎按音素匹配，对非母语发音更宽容（自动容忍松紧元音偏移、吞掉音节尾的辅音）；
- 每个法术有多个别名（见[法术一览](Spells-zh)），换一个好念的试试；
- 准星下方的灰色文字是实时识别结果——如果显示的内容离触发词太远，先确认引擎下载完整（红色状态行消失）。

## 切换引擎

```
/voicecast engine vosk-en  # 词语识别（英文单词）
/voicecast engine vosk-cn  # 中文词语识别（日语/韩语：vosk-jp / vosk-kr）
/voicecast engine ipa      # 音素识别（按发音，支持中/日）
/voicecast settings       # 打开选择界面
```

切换立即生效，选择会记住（`config/voicecast/voicecast.toml` 的 `[client] engine`）。服务器可能限制了可用引擎列表（提示 "engine not allowed" 时联系管理员）。

## 和别人语音聊天冲突吗？

识别在**服务器端**进行，且麦克风只在持杖按右键时开启——你说话时 Simple Voice Chat 仍正常工作。注意：念咒语的内容会被语音模组播出去（毕竟你在念咒）。

## 高级排障（开发者/服主）

- 启动时加 `-Dvoicecast.verbose=true`（单人开发可用 `gradlew runClient -PvoicecastVerbose=true`）输出识别管线日志：`[Mic]`、`[Vosk]`、`[IPA DEBUG]`；
- 日志：客户端与服务器均为 `logs/latest.log`；崩溃看 `crash-reports/`；
- 客户端调试 WAV 录音（源码常量 `VoiceCastConfig.saveDebugWav`，默认关）可证明"录音是否正常"，区别于"识别是否正常"。

## 其他问题

- **法力条不显示**：法力/冷却在进服时自动全量同步，且每 2 秒周期同步——法力条应在加入后 2 秒内出现，若没有再排查其他问题；
- **法术不消耗法力**：创造模式免法力（冷却不免）；
- **吟唱最后一句没反应**：确认念的是**最后一句**（HUD 高亮句），完成锁定 3 秒后再触发下一个仪式；
- 更多问题请到 GitHub Issues 反馈，附上 `logs/latest.log`。
