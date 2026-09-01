# [中文](../../zh/admin/Datapack-Spells.md) | [English](../../en/admin/Datapack-Spells.md)

# 数据包法术

> [← 返回管理员目录](../Home.md) · 上一篇：[性能与容量](Performance.md) · 下一篇：[服务器 FAQ](Server-FAQ.md)

法术是**数据包驱动**的：不需要写 Java 代码，往数据包里加 JSON 就能新增或覆盖法术。完整 schema 与效果参数表见 [`docs/spell_json.md`](../../../../docs/spell_json.md)，本页给管理员最小可用流程。

## 放置位置

```
<世界或资源包>/data/<你的命名空间>/voicecast/spells/<任意名>.json
```

服务端数据包（`world/datapacks/<名字>/data/...` 或客户端单人的 `datapacks/`）。

## 最小示例：一个瞬发法术

```json
{
  "id": "myspells:petra",
  "name": "Petra",
  "mana_cost": 15,
  "cooldown_ticks": 100,
  "schools": ["earth"],
  "aliases": ["petra", "stone", "岩石"],
  "ipa": ["ˈpɛtɹə", "ʃan˥˩ tʂɤ"],
  "effects": [
    { "type": "sound", "sound": "minecraft:block.stone.break", "volume": 1.0, "pitch": 0.8 },
    { "type": "knockback", "range": 4.0, "angle_cos": 0.5, "power": 0.8 }
  ]
}
```

- `id`：命名空间 id（同名 id 会**覆盖内置法术**，含内置数据包条目）；
- `aliases`：**触发词**（多个同义），`ipa` 是 IPA 引擎的发音模板——中文触发词建议同时给拼音模板；
- `mana_cost` 默认 10、`cooldown_ticks` 默认 40、`requires_learning` 默认 false、`origin` 默认 `wizardreal:wizardry`；
- `effects` 按顺序执行，可用类型：`projectile` / `lightning` / `heal` / `status_effect` / `knockback` / `explosion` / `beam` / `sound` / `particles`（参数见 spell_json.md）。

## 仪式法术（逐句咏唱）

加 `chants` 即成为仪式法术（每句含显示键与发音模板）：

```json
"chants": [
  [
    { "display_key": "myspells.chant.petra.zh.l1", "aliases": ["岩石低语"], "ipa": ["jaŋ ʂɤ ti ju˥"] },
    { "display_key": "myspells.chant.petra.zh.l2", "aliases": ["大地回应"], "ipa": ["ta ti xuɪ ɪŋ"] },
    { "display_key": "myspells.chant.petra.zh.l3", "aliases": ["petra", "岩石"], "ipa": ["ˈpɛtɹə"] }
  ]
]
```

- 每个法术可给多组 `chants`（变体），**第一句锁定**使用哪个变体；
- `display_key` 需要在语言文件中提供翻译（`assets/<ns>/lang/zh_cn.json` 等），HUD 按玩家游戏语言显示；
- 最后一句建议就是触发词（与内置法术一致的体验）。

## 生效与校验

1. 放入数据包后执行 **`/reload`**；
2. 加载器会重建法术注册表（内置法术先注册、同名覆盖）、清掉失效的进行中吟唱、并把新的触发词/咒语行推送给语音识别；
3. **严格校验**：某个文件的效果引用了不存在的注册项时，该文件**整个跳过**（不会注册半残法术）——日志会给出原因；
4. 新法术自动进入创造物品栏（预绑定典籍/卷轴），学习/法力/冷却规则与内置一致。
