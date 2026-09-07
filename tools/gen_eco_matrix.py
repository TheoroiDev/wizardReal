#!/usr/bin/env python3
"""gen_eco_matrix.py — magic_eco 04 法术生态矩阵 → spell JSON 量产.

策略：就地升级。现有 data/wizardreal/voicecast/spells/*.json 的触发词/IPA/咏唱
全部保留（ASR 手工调优资产），按矩阵补 chant_stages 阶梯 + 每阶效果/演出；
新家族全量生成（触发词按 docs/spells/04_naming_systems 拉丁词库）。

产出：
  - 重写/新增 data/wizardreal/voicecast/spells/*.json
  - lang 文件（en_us/zh_cn）合并 spell.<id>.name + 阶名
  - stdout 报告（家族数/阶数/校验结果）

用法：python tools/gen_eco_matrix.py  （wizardreal 仓库根运行）
"""
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPELL_DIR = ROOT / "wizardreal" / "common" / "src" / "main" / "resources" / "data" / "wizardreal" / "voicecast" / "spells"
LANG_DIR = ROOT / "wizardreal" / "common" / "src" / "main" / "resources" / "assets" / "wizardreal" / "lang"

# ---------------------------------------------------------------- primitives

def proj(entity, speed=1.5, count=1, spread=0.0, on_hit_note=None):
    e = {"type": "wizardreal:projectile", "entity": entity, "speed": speed}
    if count != 1: e["count"] = count
    if spread: e["spread"] = spread
    return e

def beam(range_=30, width=1, damage=8, set_fire=False, fire_seconds=3):
    return {"type": "wizardreal:beam", "range": range_, "width": width,
            "damage": damage, "set_fire": set_fire, "fire_seconds": fire_seconds}

def explosion(range_=16, power=4, set_fire=False):
    return {"type": "wizardreal:explosion", "range": range_, "power": power, "set_fire": set_fire}

def lightning(range_=48):
    return {"type": "wizardreal:lightning", "range": range_}

def heal(amount):
    return {"type": "wizardreal:heal", "amount": amount}

def status(effect, duration=200, amplifier=0):
    return {"type": "wizardreal:status_effect", "effect": effect, "duration": duration, "amplifier": amplifier}

def knockback(range_=6, angle_cos=0.5, power=1.1):
    return {"type": "wizardreal:knockback", "range": range_, "angle_cos": angle_cos, "power": power}

def hex_(effect, amplifier=0, duration=100, range_=8, angle_cos=None, radius=None, at_target=False, mob_filter=""):
    h = {"type": "wizardreal:hex", "effect": effect, "amplifier": amplifier,
         "duration": duration, "range": range_}
    if angle_cos is not None: h["angle_cos"] = angle_cos
    if radius is not None: h["radius"] = radius
    if at_target: h["at_target"] = True
    if mob_filter: h["mob_filter"] = mob_filter
    return h

def bind(duration=60, range_=16, group=False):
    return {"type": "wizardreal:bind", "duration": duration, "range": range_, "group": group}

def pull(range_=6, strength=0.8, include_items=True):
    return {"type": "wizardreal:pull", "range": range_, "strength": strength, "include_items": include_items}

def blink(distance=6):
    return {"type": "wizardreal:blink", "distance": distance}

def surface(block, shape="circle", radius=2, duration_ticks=200):
    return {"type": "wizardreal:surface", "block": block, "shape": shape,
            "radius": radius, "duration_ticks": duration_ticks}

def barrier(block, shape="wall", width=3, height=3, radius=2, duration_ticks=200):
    return {"type": "wizardreal:barrier", "block": block, "shape": shape, "width": width,
            "height": height, "radius": radius, "duration_ticks": duration_ticks}

def summon(entity, count=1, duration_ticks=600):
    return {"type": "wizardreal:summon", "entity": entity, "count": count, "duration_ticks": duration_ticks}

def excavate(shape="tunnel", length=8, radius=1):
    return {"type": "wizardreal:excavate", "shape": shape, "length": length, "radius": radius}

def harvest(radius=3, mode="harvest", replant=True):
    return {"type": "wizardreal:harvest", "radius": radius, "mode": mode, "replant": replant}

def smelt(radius=1.5):
    return {"type": "wizardreal:smelt", "radius": radius}

def visual(particle, shape="ring", radius=1.5, count=30, duration_ticks=20, y_offset=1.0, at_point=False):
    v = {"type": "wizardreal:visual", "particle": particle, "shape": shape, "radius": radius,
         "count": count, "duration_ticks": duration_ticks, "y_offset": y_offset}
    if at_point: v["at_point"] = True
    return v

def dust(r, g, b, scale=1.6):
    return {"dust": [r, g, b], "dust_scale": scale}

def weather(mode="rain", duration_seconds=120):
    return {"type": "wizardreal:weather", "mode": mode, "duration_seconds": duration_seconds}

def light(glow_range=0, glow_duration=100, night_vision_seconds=0):
    return {"type": "wizardreal:light", "glow_range": glow_range,
            "glow_duration": glow_duration, "night_vision_seconds": night_vision_seconds}

def sound(id_, volume=1.0, pitch=1.0):
    return {"type": "wizardreal:sound", "sound": id_, "volume": volume, "pitch": pitch}

def particles(particle, count=10, spread=0.5, speed=0.1, y_offset=1.0):
    return {"type": "wizardreal:particles", "particle": particle, "count": count,
            "spread": spread, "speed": speed, "y_offset": y_offset}

# ---------------------------------------------------------------- school language

S = {
    "fire":      {"dust": (255, 120, 20), "rel": "minecraft:entity.blaze.shoot",      "line1": (1.0, 1.1),
                  "img_en": ["ember", "ash", "pyre", "cinder"],
                  "img_zh": ["赤炎", "灰烬", "烈焰", "熔火"],
                  "img_ja": ["紅蓮", "灰", "焔", "熔火"]},
    "water":     {"dust": (80, 180, 255), "rel": "minecraft:item.bucket.empty",       "line1": (1.0, 1.2),
                  "img_en": ["tide", "deep", "mist", "foam"],
                  "img_zh": ["潮汐", "深渊", "烟波", "浪涌"],
                  "img_ja": ["潮", "深淵", "霧", "泡"]},
    "lightning": {"dust": (230, 230, 255), "rel": "minecraft:entity.lightning_bolt.impact", "line1": (0.8, 1.4),
                  "img_en": ["storm", "spark", "thunder", "static"],
                  "img_zh": ["雷暴", "电光", "惊雷", "雷鸣"],
                  "img_ja": ["嵐", "火花", "雷鳴", "電光"]},
    "earth":     {"dust": (160, 110, 70), "rel": "minecraft:block.stone.break",       "line1": (0.7, 0.9),
                  "img_en": ["stone", "bedrock", "mountain", "clay"],
                  "img_zh": ["磐石", "大地", "山岳", "尘壤"],
                  "img_ja": ["岩", "大地", "山", "土"]},
    "air":       {"dust": (200, 240, 255), "rel": "minecraft:entity.phantom.flap",    "line1": (0.9, 1.3),
                  "img_en": ["gale", "zephyr", "sky", "whisper"],
                  "img_zh": ["罡风", "清风", "长空", "风语"],
                  "img_ja": ["疾風", "微風", "空", "風の声"]},
    "nature":    {"dust": (100, 220, 120), "rel": "minecraft:block.azalea.break",     "line1": (1.0, 1.0),
                  "img_en": ["root", "bloom", "thorn", "wildwood"],
                  "img_zh": ["根须", "新芽", "荆棘", "林野"],
                  "img_ja": ["根", "芽", "茨", "森"]},
    "holy":      {"dust": (255, 230, 140), "rel": "minecraft:block.beacon.activate",  "line1": (1.1, 1.2),
                  "img_en": ["light", "hymn", "halo", "grace"],
                  "img_zh": ["圣光", "圣咏", "辉环", "恩泽"],
                  "img_ja": ["光", "聖歌", "光輪", "恵み"]},
    "necromancy":{"dust": (170, 110, 220), "rel": "minecraft:block.enchantment_table.use",   "line1": (0.7, 0.8),
                  "img_en": ["grave", "wraith", "bone", "dusk"],
                  "img_zh": ["幽冢", "亡影", "白骨", "暮色"],
                  "img_ja": ["墓", "亡霊", "骨", "黄昏"]},
    "arcane":    {"dust": (200, 120, 255), "rel": "minecraft:entity.evoker.cast_spell", "line1": (1.0, 1.1),
                  "img_en": ["sigil", "aether", "nexus", "rune"],
                  "img_zh": ["秘印", "以太", "枢纽", "符文"],
                  "img_ja": ["印", "以太", "環", "ルーン"]},
    "illusion":  {"dust": (180, 210, 255), "rel": "minecraft:entity.illusioner.cast_spell", "line1": (0.9, 1.1),
                  "img_en": ["shade", "veil", "mirror", "dream"],
                  "img_zh": ["幻影", "帷幕", "镜界", "梦境"],
                  "img_ja": ["幻", "帷", "鏡", "夢"]},
}

# ---------------------------------------------------------------- chant text

EN_TPL = ["{r}, {i} of the old tongue", "by {i}, {r} awakens", "{r}, hear the {i}",
          "o {i}, lend {r} thy breath", "{r} rises on {i} wings", "let {i} crown {r}"]
ZH_TPL = ["{r}初现，{i}为证", "以{i}为引，{r}苏醒", "{r}啊，谛听{i}",
          "{i}啊，借{r}以息", "{r}乘{i}而至", "以{i}加冕{r}"]
JA_TPL = ["{r}よ、{i}とともに", "{i}に導かれ、{r}顕現せり", "{r}よ、{i}を聴け",
          "{i}よ、{r}に息を貸せ", "{r}は{i}に乗て立つ", "{i}をもって{r}を飾れ"]

def chant_lines(fam, school, n_middle, spell_words, cast_words=None):
    """Generate per-language trigger/cast/body (2 variants x n_middle lines).
    L1 template rotates per family id so entry lines stay mutually distinct
    (04 §8 rule 6 / SpellLoadValidator L1 distinctness).
    cast_words: per-language spell-name override (hand-tuned trigger alias[0]
    of existing spells) so the cast line always equals a trigger alias."""
    s = S[school]
    root_en, root_zh, root_ja = spell_words
    img_en, img_zh, img_ja = s["img_en"], s["img_zh"], s["img_ja"]
    rot = fam.get("_rot", 0)
    en_trig = EN_TPL[rot % 6].format(r=root_en, i=img_en[rot % 4])
    zh_trig = ZH_TPL[rot % 6].format(r=root_zh, i=img_zh[(rot >> 2) % 4])
    ja_trig = JA_TPL[rot % 6].format(r=root_ja, i=img_ja[(rot >> 4) % 4])
    cw = cast_words or {}
    en_cast = cw.get("en", root_en)
    zh_cast = cw.get("zh", root_zh)
    ja_cast = cw.get("ja", root_ja)

    def body_en(k):
        pool = [
            [f"let the {img_en[0]} answer", f"weave {img_en[1]} into my words", f"bind the {img_en[2]} to my will",
             f"by the first {img_en[0]}, i swear", f"none shall quiet the {img_en[1]}"],
            [f"{img_en[1]} wakes beneath my voice", f"the {img_en[2]} remembers my name", f"gather, o {img_en[0]} unbound",
             f"the {img_en[3]} leans close to listen", f"so it is written in {img_en[1]}"],
        ]
        return pool[k % 2][:n_middle]

    def body_zh(k):
        pool = [
            [f"以{img_zh[0]}之名应召", f"织{img_zh[1]}入我言", f"缚{img_zh[2]}于我意",
             f"初火{img_zh[0]}为誓", f"{img_zh[1]}不止息"],
            [f"{img_zh[1]}随吾声而醒", f"{img_zh[2]}识得吾名", f"来兮，无羁之{img_zh[0]}",
             f"{img_zh[3]}侧耳聆听", f"{img_zh[1]}所书必成"],
        ]
        return pool[k % 2][:n_middle]

    def body_ja(k):
        pool = [
            [f"{img_ja[0]}よ応えよ", f"我が詞に{img_ja[1]}を織れ", f"{img_ja[2]}を我が意に縛れ",
             f"原初の{img_ja[0]}に誓う", f"{img_ja[1]}を止められぬ"],
            [f"声に呼び覚まれし{img_ja[1]}", f"{img_ja[2]}は名を憶える", f"集え、縛られぬ{img_ja[0]}",
             f"{img_ja[3]}は耳を傾ける", f"然り、{img_ja[1]}に記されし"],
        ]
        return pool[k % 2][:n_middle]

    def mk(lines):
        return [{"aliases": [l]} for l in lines]

    return {
        "en": {"trigger": {"aliases": [en_trig]}, "cast": {"aliases": [en_cast]},
               "body": [mk(body_en(0)), mk(body_en(1))]},
        "zh": {"trigger": {"aliases": [zh_trig]}, "cast": {"aliases": [zh_cast]},
               "body": [mk(body_zh(0)), mk(body_zh(1))]},
        "ja": {"trigger": {"aliases": [ja_trig]}, "cast": {"aliases": [ja_cast]},
               "body": [mk(body_ja(0)), mk(body_ja(1))]},
    }

# ---------------------------------------------------------------- families
# stage: (mastery, effects builder fn(school, dust), mana_mult, cd_ticks)

def F(id_, name_en, name_zh, name_ja, schools, words, base_effects, stages,
      mana=10, cooldown=60, difficulty=1.0, requires_learning=False, tier="normal",
      origin="wizardreal:wizardry"):
    return dict(id=id_, en=name_en, zh=name_zh, ja=name_ja, schools=schools, words=words,
                base=base_effects, stages=stages, mana=mana, cooldown=cooldown,
                difficulty=difficulty, requires_learning=requires_learning, tier=tier,
                origin=origin)

# per-family stage tables: (after_lines, mastery, mana_mult, cd_override, effects(school))
def stg(mastery, mana_mult, cd, fx):
    return dict(mastery=mastery, mana_mult=mana_mult, cd=cd, fx=fx)

D = lambda s: dust(*S[s]["dust"])
REL = lambda s: S[s]["rel"]

M = []
# ===== FIRE =====
M.append(F("ignis", "Ignis / Fireball", "火球", "イグニス", ["fire"], ("ignis", "火球", "イグニス"),
    [proj("minecraft:small_fireball", 1.5), sound("minecraft:entity.blaze.shoot")],
    [stg(0, 1.5, 120, lambda s: [proj("minecraft:fireball", 1.2), visual(D(s), "ring", 1.0, 24, 10, 1.0, True), sound("minecraft:entity.ghast.shoot", 1.0, 1.1)]),
     stg(40, 2.5, 300, lambda s: [proj("minecraft:fireball", 1.0), explosion(6, 3, True), visual(D(s), "burst", 3.0, 40, 15, 1.0, True), sound("minecraft:entity.blaze.shoot", 1.2, 0.8)]),
     stg(70, 4.0, 600, lambda s: [proj("minecraft:fireball", 0.9), explosion(10, 5, True), surface("minecraft:fire", "circle", 3, 100), visual(D(s), "pillar", 3.0, 60, 30, 0.0, True), sound("minecraft:entity.blaze.shoot", 1.4, 0.6)])],
    mana=8, cooldown=40))
M.append(F("flamma", "Flamma / Flame Breath", "焰息", "フラマ", ["fire"], ("flamma", "焰息", "フラマ"),
    [beam(12, 1, 6, True), sound("minecraft:entity.blaze.shoot", 1, 1.2)],
    [stg(0, 1.6, 150, lambda s: [beam(16, 1.5, 8, True), visual(D(s), "cone", 2.0, 40, 20), sound("minecraft:entity.blaze.shoot", 1.1, 1.0)]),
     stg(50, 2.6, 350, lambda s: [beam(20, 2, 12, True), knockback(8, 0.4, 1.4), visual(D(s), "cone", 3.0, 60, 25), sound("minecraft:entity.blaze.shoot", 1.2, 0.9)])],
    mana=12, cooldown=80))
M.append(F("incendo", "Incendo / Ashfall", "烟火领域", "インチェンド", ["fire"], ("incendo", "烟火", "インチェンド"),
    [hex_("minecraft:poison", 0, 80, 6, angle_cos=0.5), visual(D("fire"), "cone", 2, 30, 15)],
    [stg(0, 1.5, 140, lambda s: [hex_("minecraft:poison", 1, 120, 8, angle_cos=0.5), status("minecraft:blindness", 60, 0), visual(D(s), "cone", 3.0, 50, 20)]),
     stg(50, 2.6, 320, lambda s: [hex_("minecraft:poison", 1, 160, 10), hex_("minecraft:weakness", 1, 160, 10), visual(D(s), "burst", 5.0, 70, 25, 1.0, True), sound("minecraft:entity.blaze.shoot", 1.3, 0.7)])],
    mana=14, cooldown=100))
M.append(F("aduro", "Aduro / Ashen Brand", "熔甲", "アドゥロ", ["fire"], ("aduro", "熔甲", "アドゥロ"),
    [hex_("minecraft:weakness", 0, 100, 8, angle_cos=0.6), particles("minecraft:smoke", 12, 0.5, 0.05)],
    [stg(0, 1.4, 120, lambda s: [hex_("minecraft:weakness", 1, 140, 8, angle_cos=0.6), hex_("minecraft:wither", 0, 80, 8, angle_cos=0.6), visual(D(s), "cone", 2.5, 40, 20)]),
     stg(50, 2.4, 300, lambda s: [hex_("minecraft:weakness", 2, 200, 10, angle_cos=0.5), hex_("minecraft:slowness", 1, 160, 10, angle_cos=0.5), visual(D(s), "cone", 3.5, 55, 25), sound("minecraft:entity.blaze.shoot", 1.0, 0.7)])],
    mana=12, cooldown=90))
M.append(F("concoctio", "Concoctio / Fire-Smite", "熔炼", "コンコクティオ", ["fire"], ("concoctio", "熔炼", "コンコクティオ"),
    [smelt(1.0), visual(D("fire"), "burst", 0.8, 12, 8, 0.5, True), sound("minecraft:block.fire.extinguish", 0.8, 1.4)],
    [stg(0, 1.5, 120, lambda s: [smelt(2.0), visual(D(s), "ring", 2.0, 30, 12, 0.2, True)]),
     stg(50, 2.5, 300, lambda s: [smelt(4.0), visual(D(s), "pillar", 2.5, 50, 25, 0.0, True), sound("minecraft:block.fire.extinguish", 1.0, 1.0)])],
    mana=14, cooldown=100, difficulty=1.2))
M.append(F("vestibulum", "Vestibulum / Pyre Wall", "火墙", "ウェスティブルム", ["fire"], ("vestibulum", "火墙", "ウェスティブルム"),
    [barrier("minecraft:iron_bars", "wall", 3, 2, 2, 120), visual(D("fire"), "ring", 2.5, 24, 10, 0.2, True)],
    [stg(0, 1.5, 140, lambda s: [barrier("minecraft:iron_bars", "wall", 5, 3, 2, 160), surface("minecraft:fire", "line", 2, 80), visual(D(s), "wall" if False else "ring", 3.0, 36, 12, 0.2, True)]),
     stg(50, 2.5, 320, lambda s: [barrier("minecraft:cobblestone", "cage", 3, 3, 2.5, 140), surface("minecraft:fire", "circle", 2, 100), visual(D(s), "burst", 3.5, 50, 20, 1.0, True), sound("minecraft:block.fire.ambient", 1.2, 0.8)])],
    mana=14, cooldown=120, difficulty=1.2))
M.append(F("incursus", "Incursus / Cinder Step", "火行", "インクルスス", ["air", "fire"], ("incursus", "火行", "インクルスス"),
    [blink(4), visual(D("fire"), "trail", 1.0, 24, 12, 1.0)],
    [stg(0, 1.3, 80, lambda s: [blink(7), visual(D(s), "trail", 1.5, 36, 14, 1.0)]),
     stg(50, 2.2, 200, lambda s: [blink(10), surface("minecraft:fire", "line", 4, 60), visual(D(s), "trail", 2.0, 50, 18, 1.0), sound("minecraft:entity.blaze.shoot", 0.8, 1.4)])],
    mana=8, cooldown=60, difficulty=1.1))

# ===== WATER =====
M.append(F("unda", "Unda / Tide Bolt", "水弹", "ウン ダ", ["water"], ("unda", "水弹", "ウンダ"),
    [proj("minecraft:snowball", 1.8), hex_("minecraft:slowness", 0, 60, 3, radius=2, at_target=True), sound("minecraft:item.bucket.empty")],
    [stg(0, 1.5, 100, lambda s: [proj("minecraft:snowball", 1.8, 3, 0.1), bind(40, 8), visual(D(s), "ring", 1.5, 24, 10, 1.0, True)]),
     stg(50, 2.5, 280, lambda s: [proj("minecraft:snowball", 1.8, 3, 0.1), bind(100, 8), hex_("minecraft:weakness", 1, 100, 4, radius=3, at_target=True), visual(D(s), "burst", 2.5, 40, 15, 1.0, True), sound("minecraft:entity.player.splash", 1.2, 0.9)])],
    mana=8, cooldown=40))
M.append(F("fluctus", "Fluctus / Riptide Cone", "潮涌", "フラクトゥス", ["water"], ("fluctus", "潮涌", "フラクトゥス"),
    [knockback(6, 0.5, 1.3), sound("minecraft:entity.player.splash")],
    [stg(0, 1.5, 130, lambda s: [knockback(8, 0.5, 1.6), surface("minecraft:water", "circle", 2, 80), visual(D(s), "cone", 2.5, 40, 18)]),
     stg(50, 2.6, 320, lambda s: [knockback(12, 0.4, 2.2), surface("minecraft:water", "circle", 4, 140), visual(D(s), "cone", 4.0, 60, 22), sound("minecraft:entity.player.splash", 1.3, 0.8)])],
    mana=12, cooldown=90))
M.append(F("gurges", "Gurges / Maelstrom", "漩涡", "グルゲス", ["water"], ("gurges", "漩涡", "グルゲス"),
    [pull(5, 0.6), visual(D("water"), "orbit", 2.0, 30, 20)],
    [stg(0, 1.4, 130, lambda s: [pull(7, 0.9), hex_("minecraft:slowness", 1, 80, 7), visual(D(s), "orbit", 2.5, 44, 25)]),
     stg(50, 2.5, 320, lambda s: [pull(10, 1.4), hex_("minecraft:slowness", 2, 120, 10), hex_("minecraft:weakness", 0, 120, 10), visual(D(s), "orbit", 3.5, 60, 30), sound("minecraft:entity.player.splash", 1.1, 0.8)])],
    mana=14, cooldown=110))
M.append(F("diluvium", "Diluvium / Rain-Song", "降雨", "ディルウィウム", ["water"], ("diluvium", "降雨", "ディルウィウム"),
    [weather("rain", 90), sound("minecraft:entity.player.splash", 0.8, 1.2)],
    [stg(0, 1.4, 150, lambda s: [weather("rain", 180), hex_("minecraft:fire_resistance", 0, 200, 4, radius=3), visual(D(s), "pillar", 2.0, 40, 30)]),
     stg(50, 2.2, 300, lambda s: [weather("rain", 300), harvest(3, "grow", True), visual(D(s), "pillar", 3.5, 60, 40), sound("minecraft:entity.player.splash", 1.0, 1.0)])],
    mana=12, cooldown=240, difficulty=1.2))
M.append(F("glacies", "Glacies / Frostwrite", "冰晶", "グラキエス", ["water"], ("glacies", "冰晶", "グラキエス"),
    [surface("minecraft:ice", "circle", 2, 160), hex_("minecraft:slowness", 0, 60, 4, radius=3, at_target=True), sound("minecraft:block.glass.place", 1, 1.3)],
    [stg(0, 1.4, 130, lambda s: [surface("minecraft:ice", "line", 5, 200), visual(D(s), "trail", 1.0, 30, 15)]),
     stg(50, 2.4, 300, lambda s: [surface("minecraft:ice", "circle", 5, 240), hex_("minecraft:slowness", 2, 140, 8, radius=5, at_target=True), visual(D(s), "burst", 4.0, 50, 20, 0.5, True), sound("minecraft:block.glass.break", 1.1, 1.1)])],
    mana=10, cooldown=90, difficulty=1.1))
M.append(F("aqua_vitae", "Aqua Vitae / Cleanse", "净水", "アクアウィタエ", ["water"], ("aqua vitae", "净水", "アクア・ウィタエ"),
    [heal(4), status("minecraft:regeneration", 80, 0), sound("minecraft:item.bottle.empty", 0.9, 1.2)],
    [stg(0, 1.5, 160, lambda s: [heal(6), status("minecraft:regeneration", 120, 1), hex_("minecraft:fire_resistance", 0, 120, 4, radius=4), visual(D(s), "ring", 2.5, 30, 12)]),
     stg(50, 2.4, 340, lambda s: [heal(8), status("minecraft:regeneration", 160, 1), hex_("minecraft:regeneration", 0, 160, 6, radius=6), visual(D(s), "pillar", 3.0, 50, 30), sound("minecraft:item.bottle.empty", 1.1, 1.2)])],
    mana=14, cooldown=140, difficulty=1.2))
M.append(F("mare_gradus", "Mare Gradus / Tide-Walk", "潮行", "マーレグラドゥス", ["water"], ("mare gradus", "潮行", "マーレグラドゥス"),
    [status("minecraft:water_breathing", 600, 0), sound("minecraft:entity.player.splash", 0.7, 1.3)],
    [stg(0, 1.3, 100, lambda s: [status("minecraft:water_breathing", 1200, 0), status("minecraft:night_vision", 240, 0), visual(D(s), "helix", 1.2, 30, 20)]),
     stg(50, 2.2, 240, lambda s: [status("minecraft:water_breathing", 2400, 0), status("minecraft:night_vision", 480, 0), status("minecraft:dolphins_grace", 1200, 0), visual(D(s), "helix", 1.8, 45, 25), sound("minecraft:entity.player.splash", 0.9, 1.2)])],
    mana=8, cooldown=120, difficulty=1.1))

# ===== LIGHTNING =====
M.append(F("fulmen", "Fulmen / Skyfall", "落雷", "フルメン", ["lightning"], ("fulmen", "落雷", "フルメン"),
    [lightning(48), sound("minecraft:entity.lightning_bolt.impact")],
    [stg(0, 1.5, 150, lambda s: [lightning(48), lightning(48), visual(D(s), "cross", 2.0, 30, 10, 1.0, True)]),
     stg(50, 2.6, 340, lambda s: [weather("thunder", 90), lightning(48), lightning(48), lightning(48), visual(D(s), "burst", 4.0, 50, 20, 1.0, True), sound("minecraft:entity.lightning_bolt.thunder", 1.2, 1.0)])],
    mana=12, cooldown=100))
M.append(F("tonitrus", "Tonitrus / Arc Web", "链电", "トニトルス", ["lightning"], ("tonitrus", "链电", "トニトルス"),
    [beam(16, 1, 8), sound("minecraft:entity.lightning_bolt.impact", 0.7, 1.6)],
    [stg(0, 1.5, 140, lambda s: [beam(20, 1, 10), hex_("minecraft:slowness", 1, 80, 20, angle_cos=0.3), visual(D(s), "cone", 3.0, 44, 15)]),
     stg(50, 2.6, 330, lambda s: [beam(24, 1.5, 14), hex_("minecraft:slowness", 2, 120, 24, angle_cos=0.35), hex_("minecraft:weakness", 1, 120, 24, angle_cos=0.35), visual(D(s), "cone", 4.5, 66, 20), sound("minecraft:entity.lightning_bolt.impact", 1.1, 1.3)])],
    mana=12, cooldown=100))
M.append(F("torpor", "Torpor / Stasis Field", "静滞", "トルポル", ["lightning"], ("torpor", "静滞", "トルポル"),
    [hex_("minecraft:slowness", 0, 80, 8), visual(D("lightning"), "orbit", 2.0, 26, 20)],
    [stg(0, 1.4, 130, lambda s: [hex_("minecraft:slowness", 2, 120, 8), hex_("minecraft:weakness", 0, 120, 8), visual(D(s), "orbit", 2.5, 40, 25)]),
     stg(50, 2.5, 320, lambda s: [hex_("minecraft:slowness", 3, 160, 12), hex_("minecraft:weakness", 1, 160, 12), hex_("minecraft:mining_fatigue", 1, 160, 12), visual(D(s), "orbit", 3.5, 55, 30), sound("minecraft:block.bell.use", 0.9, 0.7)])],
    mana=14, cooldown=120))
M.append(F("fulgur", "Fulgur / Storm-Brand", "雷蓄", "フルグル", ["lightning"], ("fulgur", "雷蓄", "フルグル"),
    [status("minecraft:resistance", 200, 0), visual(D("lightning"), "helix", 1.2, 30, 20)],
    [stg(0, 1.4, 130, lambda s: [status("minecraft:resistance", 300, 0), status("minecraft:speed", 300, 1), visual(D(s), "helix", 1.6, 40, 22)]),
     stg(50, 2.3, 300, lambda s: [status("minecraft:resistance", 400, 1), status("minecraft:speed", 400, 1), status("minecraft:strength", 400, 0), visual(D(s), "helix", 2.2, 55, 28), sound("minecraft:entity.lightning_bolt.impact", 0.8, 1.5)])],
    mana=12, cooldown=160, difficulty=1.2))
M.append(F("celere", "Celere / Thunder Dash", "雷驰", "チェレレ", ["air", "lightning"], ("celere", "雷驰", "チェレレ"),
    [blink(6), visual(D("lightning"), "trail", 1.2, 30, 12)],
    [stg(0, 1.3, 90, lambda s: [blink(10), visual(D(s), "trail", 1.6, 40, 14)]),
     stg(50, 2.2, 220, lambda s: [blink(14), lightning(24), visual(D(s), "trail", 2.2, 55, 16), sound("minecraft:entity.lightning_bolt.impact", 0.9, 1.4)])],
    mana=8, cooldown=60, difficulty=1.1))
M.append(F("tempestas", "Tempestas / Sky-Command", "唤雷", "テンペスタス", ["lightning"], ("tempestas", "唤雷", "テンペスタス"),
    [weather("clear", 120), sound("minecraft:entity.lightning_bolt.thunder", 0.6, 1.4)],
    [stg(0, 1.4, 140, lambda s: [weather("rain", 240), visual(D(s), "pillar", 2.5, 40, 30)]),
     stg(50, 2.3, 300, lambda s: [weather("thunder", 300), visual(D(s), "pillar", 4.0, 60, 40), sound("minecraft:entity.lightning_bolt.thunder", 1.2, 1.0)])],
    mana=10, cooldown=300, difficulty=1.3))
M.append(F("galvanum", "Galvanum / Lodestone Call", "引雷", "ガルウァヌム", ["lightning"], ("galvanum", "引雷", "ガルウァヌム"),
    [pull(8, 0.8, False), visual(D("lightning"), "cone", 2.0, 30, 15)],
    [stg(0, 1.4, 140, lambda s: [pull(10, 1.1, False), hex_("minecraft:slowness", 1, 80, 10), visual(D(s), "cone", 3.0, 44, 18)]),
     stg(50, 2.4, 320, lambda s: [pull(14, 1.6, False), hex_("minecraft:slowness", 2, 120, 14), lightning(20), visual(D(s), "cone", 4.5, 60, 22), sound("minecraft:entity.lightning_bolt.impact", 1.0, 1.1)])],
    mana=12, cooldown=110, difficulty=1.1))

# ===== EARTH =====
M.append(F("saxum", "Saxum / Stone Lance", "石矛", "サクスム", ["earth"], ("saxum", "石矛", "サクスム"),
    [proj("minecraft:arrow", 2.0, 1, 0, ), sound("minecraft:block.stone.break")],
    [stg(0, 1.5, 120, lambda s: [proj("minecraft:arrow", 2.0, 5, 0.08), visual(D(s), "cone", 1.5, 30, 12)]),
     stg(50, 2.6, 320, lambda s: [proj("minecraft:fireball", 1.0), explosion(5, 3, False), visual(D(s), "burst", 3.0, 50, 18, 1.0, True), sound("minecraft:block.stone.break", 1.2, 0.7)])],
    mana=10, cooldown=60))
M.append(F("lapides", "Lapides / Rockfall", "落石", "ラピデス", ["earth"], ("lapides", "落石", "ラピデス"),
    [proj("minecraft:arrow", 1.5, 4, 0.15), sound("minecraft:block.gravel.break")],
    [stg(0, 1.5, 130, lambda s: [proj("minecraft:arrow", 1.5, 8, 0.2), visual(D(s), "cone", 2.5, 40, 16)]),
     stg(50, 2.6, 330, lambda s: [proj("minecraft:arrow", 1.5, 12, 0.25), hex_("minecraft:slowness", 1, 100, 10, angle_cos=0.4), visual(D(s), "cone", 4.0, 60, 20), sound("minecraft:block.gravel.break", 1.2, 0.8)])],
    mana=12, cooldown=90))
M.append(F("terra", "Terra / Sunder Ground", "地陷", "テラ", ["earth"], ("terra", "地陷", "テラ"),
    [hex_("minecraft:slowness", 1, 100, 8, radius=4, at_target=True), visual(D("earth"), "ring", 3.0, 36, 15, 0.2, True)],
    [stg(0, 1.4, 140, lambda s: [explosion(4, 2, False), hex_("minecraft:slowness", 1, 140, 6, radius=5, at_target=True), visual(D(s), "ring", 4.0, 48, 18, 0.2, True)]),
     stg(50, 2.5, 330, lambda s: [explosion(6, 3, False), hex_("minecraft:slowness", 2, 200, 8, radius=7, at_target=True), hex_("minecraft:nausea", 0, 160, 8, radius=7, at_target=True), visual(D(s), "ring", 5.5, 66, 22, 0.2, True), sound("minecraft:block.stone.break", 1.3, 0.6)])],
    mana=14, cooldown=140))
M.append(F("murus", "Murus / Bulwark", "石墙", "ムルス", ["earth"], ("murus", "石墙", "ムルス"),
    [barrier("minecraft:cobblestone", "wall", 3, 3, 2, 200), sound("minecraft:block.stone.place")],
    [stg(0, 1.5, 150, lambda s: [barrier("minecraft:cobblestone", "wall", 5, 4, 2, 300), visual(D(s), "ring", 3.0, 30, 12, 0.5, True)]),
     stg(50, 2.5, 340, lambda s: [barrier("minecraft:cobblestone", "ring", 3, 3, 3, 320), barrier("minecraft:cobblestone", "wall", 5, 4, 2, 320), visual(D(s), "ring", 4.5, 44, 16, 0.5, True), sound("minecraft:block.stone.place", 1.1, 0.9)])],
    mana=12, cooldown=140, difficulty=1.1))
M.append(F("fodere", "Fodere / Delve", "掘进", "フォデレ", ["earth"], ("fodere", "掘进", "フォデレ"),
    [excavate("tunnel", 6, 0), particles("minecraft:cloud", 12, 0.5, 0.05), sound("minecraft:block.stone.break", 0.9, 1.1)],
    [stg(0, 1.4, 130, lambda s: [excavate("tunnel", 10, 1), light(0, 0, 120), visual(D(s), "trail", 1.0, 30, 15, 0.5)]),
     stg(50, 2.3, 300, lambda s: [excavate("area", 0, 3), light(12, 100, 240), visual(D(s), "burst", 3.5, 50, 20, 0.5, True), sound("minecraft:block.stone.break", 1.1, 0.9)])],
    mana=10, cooldown=120, difficulty=1.2))
M.append(F("lithos", "Lithos / Stone-Skin", "石肤", "リトス", ["earth"], ("lithos", "石肤", "リトス"),
    [status("minecraft:resistance", 300, 0), visual(D("earth"), "helix", 1.2, 26, 18)],
    [stg(0, 1.4, 140, lambda s: [status("minecraft:resistance", 400, 1), status("minecraft:absorption", 400, 1), visual(D(s), "helix", 1.7, 38, 22)]),
     stg(50, 2.3, 320, lambda s: [status("minecraft:resistance", 600, 2), status("minecraft:absorption", 600, 2), status("minecraft:slowness", 600, 0), visual(D(s), "pillar", 2.0, 50, 26), sound("minecraft:block.stone.place", 1.0, 0.8)])],
    mana=12, cooldown=180, difficulty=1.1))
M.append(F("petra_catena", "Petra Catena / Stone Tomb", "石棺", "ペトラ・カテナ", ["earth"], ("petra catena", "石棺", "ペトラ・カテナ"),
    [barrier("minecraft:cobblestone", "cage", 3, 3, 1.5, 120), sound("minecraft:block.stone.place", 1, 0.8)],
    [stg(0, 1.4, 150, lambda s: [barrier("minecraft:cobblestone", "cage", 3, 3, 2, 180), bind(60, 8), visual(D(s), "burst", 2.5, 40, 15, 1.0, True)]),
     stg(50, 2.5, 340, lambda s: [barrier("minecraft:cobblestone", "cage", 3, 3, 2, 240), bind(120, 12, True), visual(D(s), "burst", 4.0, 60, 20, 1.0, True), sound("minecraft:block.stone.place", 1.2, 0.7)])],
    mana=14, cooldown=160, difficulty=1.3))

# ===== AIR =====
M.append(F("ventus", "Ventus / Wind Blade", "风刃", "ウェントゥス", ["air"], ("ventus", "风刃", "ウェントゥス"),
    [proj("minecraft:snowball", 2.2), knockback(4, 0.4, 0.9), sound("minecraft:entity.phantom.flap")],
    [stg(0, 1.5, 110, lambda s: [proj("minecraft:snowball", 2.2, 3, 0.08), knockback(5, 0.4, 1.1), visual(D(s), "cone", 2.0, 34, 14)]),
     stg(50, 2.5, 300, lambda s: [proj("minecraft:snowball", 2.2, 5, 0.12), knockback(8, 0.4, 1.6), visual(D(s), "cone", 3.5, 55, 18), sound("minecraft:entity.phantom.flap", 1.2, 1.1)])],
    mana=8, cooldown=50))
M.append(F("turbo", "Turbo / Twin Funnel", "龙卷", "トゥルボ", ["air"], ("turbo", "龙卷", "トゥルボ"),
    [knockback(6, 0.3, 1.2), hex_("minecraft:levitation", 0, 40, 5, angle_cos=0.4), visual(D("air"), "helix", 2.0, 36, 25)],
    [stg(0, 1.5, 140, lambda s: [knockback(8, 0.3, 1.5), hex_("minecraft:levitation", 0, 60, 6, angle_cos=0.4), visual(D(s), "helix", 2.8, 50, 30)]),
     stg(50, 2.6, 330, lambda s: [knockback(10, 0.3, 1.9), hex_("minecraft:levitation", 1, 80, 8, angle_cos=0.4), pull(8, 0.7, False), visual(D(s), "helix", 3.8, 66, 34), sound("minecraft:entity.phantom.flap", 1.3, 0.9)])],
    mana=12, cooldown=110))
M.append(F("procella", "Procella / Gale Burst", "气浪", "プロケラ", ["air"], ("procella", "气浪", "プロケラ"),
    [knockback(8, 0.5, 1.8), sound("minecraft:entity.phantom.flap", 1, 0.8)],
    [stg(0, 1.5, 140, lambda s: [knockback(10, 0.5, 2.2), status("minecraft:slowness", 40, 1), visual(D(s), "cone", 4.0, 50, 18)]),
     stg(50, 2.5, 330, lambda s: [knockback(14, 0.45, 2.8), hex_("minecraft:slowness", 1, 80, 14, angle_cos=0.4), visual(D(s), "cone", 5.5, 70, 22), sound("minecraft:entity.phantom.flap", 1.4, 0.7)])],
    mana=12, cooldown=100))
M.append(F("volatus", "Volatus / Windstride", "疾风步", "ウォラトゥス", ["air"], ("volatus", "疾风步", "ウォラトゥス"),
    [blink(6), status("minecraft:slow_falling", 100, 0), sound("minecraft:entity.enderman.teleport")],
    [stg(0, 1.4, 120, lambda s: [blink(9), status("minecraft:slow_falling", 200, 0), status("minecraft:speed", 200, 1), visual(D(s), "trail", 1.5, 36, 14)]),
     stg(50, 2.4, 300, lambda s: [blink(12), status("minecraft:slow_falling", 400, 0), status("minecraft:jump_boost", 400, 2), status("minecraft:speed", 400, 1), visual(D(s), "trail", 2.2, 52, 18), sound("minecraft:entity.enderman.teleport", 1.1, 1.1)])],
    mana=10, cooldown=90, difficulty=1.1))
M.append(F("aurae_levitas", "Aurae Levitas / Zephyr Step", "轻风步", "アウライ・レウィタス", ["air"], ("aurae levitas", "轻风步", "アウライ・レウィタス"),
    [status("minecraft:jump_boost", 300, 2), sound("minecraft:entity.phantom.flap", 0.7, 1.4)],
    [stg(0, 1.3, 110, lambda s: [status("minecraft:jump_boost", 400, 3), status("minecraft:slow_falling", 300, 0), visual(D(s), "helix", 1.5, 34, 20)]),
     stg(50, 2.2, 260, lambda s: [status("minecraft:jump_boost", 500, 4), status("minecraft:slow_falling", 500, 0), status("minecraft:speed", 500, 2), visual(D(s), "helix", 2.2, 50, 24), sound("minecraft:entity.phantom.flap", 0.9, 1.3)])],
    mana=8, cooldown=140, difficulty=1.1))
M.append(F("flatu_signum", "Flatu Signum / Wind-Sight", "风语", "フラトゥ・シグヌム", ["air"], ("flatu signum", "风语", "フラトゥ・シグヌム"),
    [light(12, 100, 0), visual(D("air"), "orbit", 2.0, 26, 20)],
    [stg(0, 1.3, 110, lambda s: [light(16, 160, 120), visual(D(s), "orbit", 2.5, 36, 24)]),
     stg(50, 2.2, 260, lambda s: [light(24, 240, 240), visual(D(s), "orbit", 3.5, 50, 30), sound("minecraft:block.amethyst_block.chime", 1.0, 1.2)])],
    mana=8, cooldown=100, difficulty=1.1))
M.append(F("anemos", "Anemos / Wind Blade", "真空刃", "アネモス", ["air"], ("anemos", "真空刃", "アネモス"),
    [beam(20, 1, 9), sound("minecraft:entity.player.attack.sweep", 1, 0.8)],
    [stg(0, 1.5, 130, lambda s: [beam(26, 1.5, 12), visual(D(s), "cone", 2.5, 40, 14)]),
     stg(50, 2.6, 320, lambda s: [beam(30, 2, 15), beam(30, 1, 10), visual(D(s), "cone", 4.0, 60, 18), sound("minecraft:entity.player.attack.sweep", 1.2, 0.7)])],
    mana=12, cooldown=100, difficulty=1.2))

# ===== NATURE =====
M.append(F("spina", "Spina / Thorn Volley", "荆棘", "スピナ", ["nature"], ("spina", "荆棘", "スピナ"),
    [proj("minecraft:arrow", 1.6), surface("minecraft:sweet_berry_bush", "circle", 1, 120), sound("minecraft:block.azalea.break")],
    [stg(0, 1.5, 120, lambda s: [proj("minecraft:arrow", 1.6, 3, 0.1), surface("minecraft:sweet_berry_bush", "circle", 2, 160), hex_("minecraft:poison", 0, 80, 4, radius=3, at_target=True)]),
     stg(50, 2.5, 300, lambda s: [proj("minecraft:arrow", 1.6, 5, 0.12), surface("minecraft:sweet_berry_bush", "circle", 3, 240), hex_("minecraft:poison", 1, 140, 6, radius=5, at_target=True), visual(D(s), "burst", 3.5, 50, 18, 0.5, True)])],
    mana=10, cooldown=70))
M.append(F("radix", "Radix / Rootbind Cone", "缠根", "ラディクス", ["nature"], ("radix", "缠根", "ラディクス"),
    [hex_("minecraft:slowness", 1, 100, 8, angle_cos=0.5), hex_("minecraft:poison", 0, 80, 8, angle_cos=0.5), visual(D("nature"), "cone", 2.5, 36, 18)],
    [stg(0, 1.4, 140, lambda s: [hex_("minecraft:slowness", 2, 140, 8, angle_cos=0.5), hex_("minecraft:poison", 1, 120, 8, angle_cos=0.5), bind(40, 10), visual(D(s), "cone", 3.2, 46, 20)]),
     stg(50, 2.5, 330, lambda s: [hex_("minecraft:slowness", 3, 180, 12, angle_cos=0.45), hex_("minecraft:poison", 1, 180, 12, angle_cos=0.45), bind(80, 12, True), visual(D(s), "cone", 4.5, 62, 24), sound("minecraft:block.grass.break", 1.1, 0.8)])],
    mana=12, cooldown=100))
M.append(F("vireo", "Vireo / Verdant Bind", "束缚藤", "ウィレオ", ["nature"], ("vireo", "束缚藤", "ウィレオ"),
    [bind(60, 14), visual(D("nature"), "trail", 1.0, 24, 12)],
    [stg(0, 1.4, 140, lambda s: [bind(120, 16), hex_("minecraft:poison", 0, 100, 14, angle_cos=0.6), visual(D(s), "trail", 1.5, 34, 15)]),
     stg(50, 2.5, 330, lambda s: [bind(160, 18, True), hex_("minecraft:slowness", 1, 160, 18, angle_cos=0.5), visual(D(s), "cone", 3.5, 52, 20), sound("minecraft:block.grass.place", 1.1, 0.9)])],
    mana=12, cooldown=120, difficulty=1.1))
M.append(F("semina", "Semina / Harvest Song", "丰收", "セミナ", ["nature"], ("semina", "丰收", "セミナ"),
    [harvest(3, "harvest", True), visual(D("nature"), "ring", 2.5, 30, 12, 0.3, True), sound("minecraft:block.azalea.break", 0.9, 1.2)],
    [stg(0, 1.4, 130, lambda s: [harvest(4, "harvest", True), harvest(4, "grow", True), visual(D(s), "ring", 3.5, 42, 15, 0.3, True)]),
     stg(50, 2.3, 300, lambda s: [harvest(6, "harvest", True), harvest(6, "grow", True), visual(D(s), "pillar", 4.0, 60, 24, 0.3, True), sound("minecraft:block.azalea.break", 1.0, 1.1)])],
    mana=10, cooldown=120))
M.append(F("floris", "Floris / Rebound Bloom", "回春", "フロリス", ["nature"], ("floris", "回春", "フロリス"),
    [heal(5), sound("minecraft:block.azalea.break", 0.8, 1.4)],
    [stg(0, 1.4, 140, lambda s: [heal(7), status("minecraft:regeneration", 120, 1), hex_("minecraft:regeneration", 0, 100, 5, radius=5), visual(D(s), "ring", 2.5, 34, 14)]),
     stg(50, 2.4, 320, lambda s: [heal(10), status("minecraft:regeneration", 200, 1), hex_("minecraft:regeneration", 1, 160, 7, radius=7), hex_("minecraft:absorption", 1, 200, 7, radius=7), visual(D(s), "pillar", 3.5, 55, 24), sound("minecraft:block.azalea.break", 1.0, 1.2)])],
    mana=12, cooldown=130, difficulty=1.2))
M.append(F("silva_voco", "Silva Voco / Wildwood Call", "唤林", "シルウァ・ウォコ", ["nature"], ("silva voco", "唤林", "シルウァ・ウォコ"),
    [harvest(2, "grow", True), visual(D("nature"), "helix", 1.8, 34, 25, 0.5, True)],
    [stg(0, 1.4, 150, lambda s: [harvest(4, "grow", True), harvest(3, "harvest", True), visual(D(s), "helix", 2.5, 46, 28, 0.5, True)]),
     stg(50, 2.3, 320, lambda s: [harvest(7, "grow", True), harvest(5, "harvest", True), status("minecraft:haste", 200, 0), visual(D(s), "pillar", 4.0, 60, 30, 0.5, True), sound("minecraft:block.azalea.break", 1.1, 0.9)])],
    mana=12, cooldown=200, difficulty=1.2))
M.append(F("ferae_vocatus", "Ferae Vocatus / Beast Call", "兽唤", "フェライ・ウォカトゥス", ["nature"], ("ferae vocatus", "兽唤", "フェライ・ウォカトゥス"),
    [summon("minecraft:wolf", 1, 1200), sound("minecraft:entity.wolf.ambient")],
    [stg(0, 1.5, 160, lambda s: [summon("minecraft:wolf", 2, 1800), visual(D(s), "orbit", 2.0, 36, 20)]),
     stg(60, 2.6, 360, lambda s: [summon("minecraft:wolf", 2, 2400), summon("minecraft:fox", 2, 2400), status("minecraft:strength", 200, 0), visual(D(s), "orbit", 3.0, 50, 26), sound("minecraft:entity.wolf.howl", 1.1, 1.0)])],
    mana=14, cooldown=300, difficulty=1.4, requires_learning=True))

# ===== HOLY =====
M.append(F("sagitta", "Sagitta / Light Arrow", "圣光矢", "サギッタ", ["holy"], ("sagitta", "圣光矢", "サギッタ"),
    [proj("minecraft:spectral_arrow", 2.0), sound("minecraft:block.beacon.activate", 0.8, 1.4)],
    [stg(0, 1.5, 120, lambda s: [proj("minecraft:spectral_arrow", 2.0, 3, 0.08), visual(D(s), "cone", 1.8, 32, 12)]),
     stg(50, 2.5, 310, lambda s: [proj("minecraft:spectral_arrow", 1.8, 2, 0.05), explosion(4, 2, False), visual(D(s), "burst", 3.0, 46, 16, 1.0, True), sound("minecraft:block.beacon.activate", 1.0, 1.2)])],
    mana=8, cooldown=40))
M.append(F("lumen", "Lumen / Radiance Beam", "圣光束", "ルーメン", ["holy"], ("lumen", "圣光束", "ルーメン"),
    [beam(18, 1, 9, False), status("minecraft:glowing", 60, 0), sound("minecraft:block.beacon.activate")],
    [stg(0, 1.5, 140, lambda s: [beam(22, 1.5, 12), hex_("minecraft:blindness", 0, 60, 22, angle_cos=0.3), visual(D(s), "cone", 2.5, 42, 16)]),
     stg(50, 2.6, 330, lambda s: [beam(26, 2, 15), hex_("minecraft:blindness", 0, 80, 26, angle_cos=0.3), hex_("minecraft:weakness", 1, 120, 26, angle_cos=0.3, mob_filter="minecraft:undead"), visual(D(s), "pillar", 3.0, 55, 20, 1.0, True), sound("minecraft:block.beacon.activate", 1.2, 1.1)])],
    mana=12, cooldown=90))
M.append(F("judicium", "Judicium / Light Verdict", "审判", "ユディキウム", ["holy"], ("judicium", "审判", "ユディキウム"),
    [explosion(6, 2, False), hex_("minecraft:glowing", 0, 100, 8, radius=8), sound("minecraft:block.beacon.power_select")],
    [stg(0, 1.5, 150, lambda s: [explosion(8, 3, False), hex_("minecraft:weakness", 1, 120, 8, radius=8), visual(D(s), "pillar", 3.0, 50, 20, 1.0, True)]),
     stg(60, 2.6, 360, lambda s: [explosion(10, 4, False), proj("minecraft:spectral_arrow", 1.5, 8, 0.3), hex_("minecraft:weakness", 2, 160, 10, radius=10), visual(D(s), "cross", 4.0, 60, 24, 1.0, True), sound("minecraft:block.beacon.power_select", 1.2, 1.0)])],
    mana=16, cooldown=160, difficulty=1.3, requires_learning=True))
M.append(F("sanare", "Sanare / Sanctify", "净化", "サナレ", ["holy"], ("sanare", "净化", "サナレ"),
    [heal(5), status("minecraft:regeneration", 80, 0), sound("minecraft:block.beacon.activate", 0.9, 1.3)],
    [stg(0, 1.4, 150, lambda s: [heal(7), status("minecraft:regeneration", 120, 1), hex_("minecraft:absorption", 1, 200, 5, radius=5), visual(D(s), "ring", 2.5, 34, 14)]),
     stg(50, 2.4, 340, lambda s: [heal(9), hex_("minecraft:regeneration", 1, 160, 7, radius=7), hex_("minecraft:absorption", 2, 240, 7, radius=7), visual(D(s), "pillar", 3.5, 55, 24), sound("minecraft:block.beacon.activate", 1.1, 1.2)])],
    mana=12, cooldown=140, difficulty=1.1))
M.append(F("lux", "Lux / Sanctum Light", "圣辉", "ルクス", ["holy"], ("lux", "圣辉", "ルクス"),
    [light(0, 0, 120), visual(D("holy"), "pillar", 1.5, 30, 25)],
    [stg(0, 1.3, 110, lambda s: [light(12, 200, 240), visual(D(s), "orbit", 2.0, 36, 25)]),
     stg(50, 2.2, 260, lambda s: [light(20, 300, 480), hex_("minecraft:glowing", 0, 200, 20, radius=20, mob_filter="minecraft:undead"), visual(D(s), "pillar", 3.0, 50, 30), sound("minecraft:block.beacon.activate", 1.0, 1.4)])],
    mana=8, cooldown=120, difficulty=1.1))
M.append(F("aegis", "Aegis / Golden Ward", "圣盾", "アイギス", ["holy"], ("aegis", "圣盾", "アイギス"),
    [status("minecraft:absorption", 300, 1), sound("minecraft:block.beacon.activate", 0.9, 1.5)],
    [stg(0, 1.4, 150, lambda s: [status("minecraft:absorption", 400, 2), status("minecraft:resistance", 300, 0), visual(D(s), "orbit", 2.2, 40, 24)]),
     stg(50, 2.4, 340, lambda s: [status("minecraft:absorption", 500, 3), status("minecraft:resistance", 400, 1), hex_("minecraft:absorption", 1, 300, 6, radius=6), visual(D(s), "orbit", 3.0, 55, 28), sound("minecraft:block.beacon.activate", 1.1, 1.3)])],
    mana=12, cooldown=200, difficulty=1.2))
M.append(F("angelus", "Angelus / Choir of Light", "守灵", "アンジェルス", ["holy"], ("angelus", "守灵", "アンジェルス"),
    [summon("minecraft:allay", 1, 1200), sound("minecraft:block.note_block.chime", 1, 1.5)],
    [stg(0, 1.5, 160, lambda s: [summon("minecraft:allay", 2, 1800), visual(D(s), "orbit", 2.5, 44, 26)]),
     stg(60, 2.6, 360, lambda s: [summon("minecraft:allay", 3, 2400), summon("minecraft:iron_golem", 1, 1800), visual(D(s), "orbit", 3.5, 60, 30), sound("minecraft:block.note_block.chime", 1.1, 1.2)])],
    mana=14, cooldown=300, difficulty=1.4, requires_learning=True))

# ===== NECROMANCY =====
M.append(F("ossum", "Ossum / Bone Spike", "骨矛", "オッスム", ["necromancy"], ("ossum", "骨矛", "オッスム"),
    [proj("minecraft:arrow", 1.8), hex_("minecraft:wither", 0, 60, 3, radius=2, at_target=True), sound("minecraft:block.bone_block.break")],
    [stg(0, 1.5, 130, lambda s: [proj("minecraft:arrow", 1.8, 4, 0.1), hex_("minecraft:wither", 0, 80, 4, radius=3, at_target=True), visual(D(s), "cone", 2.0, 36, 14)]),
     stg(50, 2.5, 320, lambda s: [proj("minecraft:arrow", 1.8, 7, 0.12), bind(50, 8), hex_("minecraft:wither", 1, 100, 5, radius=4, at_target=True), visual(D(s), "burst", 3.0, 50, 18, 1.0, True), sound("minecraft:block.bone_block.break", 1.1, 0.8)])],
    mana=10, cooldown=60))
M.append(F("suctus", "Suctus / Siphoning Ray", "汲取", "スクトゥス", ["necromancy"], ("suctus", "汲取", "スクトゥス"),
    [beam(14, 1, 7), heal(3), sound("minecraft:block.enchantment_table.use", 0.9, 1.2)],
    [stg(0, 1.5, 140, lambda s: [beam(18, 1, 9), heal(5), hex_("minecraft:wither", 0, 60, 18, angle_cos=0.3), visual(D(s), "cone", 2.2, 40, 16)]),
     stg(50, 2.6, 330, lambda s: [beam(22, 1.5, 13), heal(8), hex_("minecraft:wither", 1, 100, 22, angle_cos=0.3), visual(D(s), "cone", 3.2, 55, 20), sound("minecraft:block.enchantment_table.use", 1.0, 1.0)])],
    mana=12, cooldown=110))
M.append(F("letum", "Letum / Withering Ground", "凋零", "レトゥム", ["necromancy"], ("letum", "凋零", "レトゥム"),
    [hex_("minecraft:wither", 0, 80, 6, radius=6), visual(D("necromancy"), "ring", 3.0, 36, 18, 0.2, True)],
    [stg(0, 1.4, 150, lambda s: [hex_("minecraft:wither", 1, 120, 8, radius=8), hex_("minecraft:weakness", 1, 120, 8, radius=8), visual(D(s), "ring", 4.0, 48, 20, 0.2, True)]),
     stg(50, 2.5, 340, lambda s: [hex_("minecraft:wither", 2, 160, 10, radius=10), hex_("minecraft:slowness", 2, 160, 10, radius=10), hex_("minecraft:darkness", 0, 160, 10, radius=10), visual(D(s), "pillar", 4.5, 60, 24, 0.2, True), sound("minecraft:block.enchantment_table.use", 1.1, 0.8)])],
    mana=14, cooldown=150))
M.append(F("anima", "Anima / Grave-Bound", "召骸", "アニマ", ["necromancy"], ("anima", "召骸", "アニマ"),
    [summon("minecraft:skeleton", 1, 600), sound("minecraft:entity.skeleton.ambient")],
    [stg(0, 1.5, 160, lambda s: [summon("minecraft:skeleton", 2, 900), visual(D(s), "orbit", 2.2, 40, 22)]),
     stg(60, 2.6, 360, lambda s: [summon("minecraft:skeleton", 3, 1200), summon("minecraft:zombie", 2, 1200), visual(D(s), "orbit", 3.2, 55, 26), sound("minecraft:entity.skeleton.ambient", 1.1, 0.8)])],
    mana=14, cooldown=300, difficulty=1.4, requires_learning=True))
M.append(F("umbra_mortis", "Umbra Mortis / Dread Shroud", "惧影", "ウンブラ・モルティス", ["necromancy"], ("umbra mortis", "惧影", "ウンブラ・モルティス"),
    [knockback(7, 0.5, 1.2), hex_("minecraft:darkness", 0, 100, 7, radius=7), sound("minecraft:entity.warden.ambient", 0.8, 1.2)],
    [stg(0, 1.4, 150, lambda s: [knockback(9, 0.5, 1.5), hex_("minecraft:darkness", 0, 160, 9, radius=9), hex_("minecraft:slowness", 1, 120, 9, radius=9), visual(D(s), "ring", 3.5, 44, 20, 1.0, True)]),
     stg(50, 2.5, 340, lambda s: [knockback(11, 0.45, 1.9), hex_("minecraft:darkness", 0, 200, 12, radius=12), hex_("minecraft:slowness", 2, 160, 12, radius=12), hex_("minecraft:weakness", 1, 160, 12, radius=12), visual(D(s), "pillar", 4.5, 60, 24, 1.0, True), sound("minecraft:entity.warden.ambient", 1.0, 1.0)])],
    mana=14, cooldown=150, difficulty=1.2))
M.append(F("carnifex", "Carnifex / Corpse-Burst", "尸爆", "カルニフェクス", ["necromancy"], ("carnifex", "尸爆", "カルニフェクス"),
    [explosion(5, 2, False), hex_("minecraft:wither", 0, 80, 5, radius=5, at_target=True), sound("minecraft:entity.generic.explode", 0.9, 0.9)],
    [stg(0, 1.5, 150, lambda s: [explosion(7, 3, False), hex_("minecraft:wither", 1, 100, 7, radius=7, at_target=True), visual(D(s), "burst", 3.5, 50, 18, 0.5, True)]),
     stg(50, 2.5, 340, lambda s: [explosion(9, 4, False), explosion(9, 3, False), hex_("minecraft:wither", 1, 140, 10, radius=10, at_target=True), visual(D(s), "burst", 5.0, 70, 22, 0.5, True), sound("minecraft:entity.generic.explode", 1.2, 0.8)])],
    mana=16, cooldown=180, difficulty=1.3, requires_learning=True))
M.append(F("spiritus", "Spiritus / Soul-Lantern", "魂灯", "スピリトゥス", ["necromancy"], ("spiritus", "魂灯", "スピリトゥス"),
    [light(0, 0, 240), visual(D("necromancy"), "orbit", 1.5, 28, 25)],
    [stg(0, 1.3, 120, lambda s: [light(10, 160, 480), visual(D(s), "orbit", 2.0, 36, 26)]),
     stg(50, 2.2, 280, lambda s: [light(20, 240, 600), hex_("minecraft:glowing", 0, 240, 24, radius=24, mob_filter="minecraft:undead"), status("minecraft:night_vision", 600, 0), visual(D(s), "orbit", 3.0, 50, 30), sound("minecraft:block.enchantment_table.use", 0.9, 1.1)])],
    mana=8, cooldown=120, difficulty=1.1))

# ===== ARCANE =====
M.append(F("telum", "Telum / Arcane Volley", "奥术矢", "テルム", ["arcane"], ("telum", "奥术矢", "テルム"),
    [proj("minecraft:shulker_bullet", 1.6), sound("minecraft:entity.evoker.cast_spell")],
    [stg(0, 1.5, 120, lambda s: [proj("minecraft:shulker_bullet", 1.6, 2, 0.1), visual(D(s), "cone", 1.8, 30, 12)]),
     stg(50, 2.5, 310, lambda s: [proj("minecraft:shulker_bullet", 1.6, 4, 0.12), explosion(4, 2, False), visual(D(s), "burst", 3.0, 46, 16, 1.0, True), sound("minecraft:entity.evoker.cast_spell", 1.1, 1.1)])],
    mana=8, cooldown=45))
M.append(F("chaos", "Chaos / Wandering Bolt", "混沌束", "カオス", ["arcane"], ("chaos", "混沌束", "カオス"),
    [beam(16, 1, 8, True, 2), status("minecraft:levitation", 40, 0), sound("minecraft:entity.evoker.cast_spell", 1, 0.9)],
    [stg(0, 1.5, 140, lambda s: [beam(20, 1.5, 11, True, 3), hex_("minecraft:levitation", 0, 60, 20, angle_cos=0.3), visual(D(s), "cone", 2.5, 44, 16)]),
     stg(50, 2.6, 330, lambda s: [beam(24, 2, 15, True, 4), hex_("minecraft:levitation", 1, 80, 24, angle_cos=0.3), explosion(5, 2, False), visual(D(s), "cone", 4.0, 60, 20), sound("minecraft:entity.evoker.cast_spell", 1.2, 0.8)])],
    mana=12, cooldown=100))
M.append(F("metus", "Metus / Terror", "恐惧", "メトゥス", ["arcane"], ("metus", "恐惧", "メトゥス"),
    [knockback(8, 0.5, 1.4), hex_("minecraft:slowness", 1, 80, 8), sound("minecraft:entity.elder_guardian.curse", 0.8, 1.3)],
    [stg(0, 1.4, 150, lambda s: [knockback(10, 0.45, 1.7), hex_("minecraft:slowness", 2, 120, 10), hex_("minecraft:weakness", 1, 120, 10), visual(D(s), "ring", 3.5, 44, 18, 1.0, True)]),
     stg(50, 2.5, 340, lambda s: [knockback(12, 0.4, 2.1), hex_("minecraft:slowness", 3, 160, 12), hex_("minecraft:darkness", 0, 160, 12), hex_("minecraft:weakness", 2, 160, 12), visual(D(s), "pillar", 4.5, 60, 22, 1.0, True), sound("minecraft:entity.elder_guardian.curse", 1.0, 1.1)])],
    mana=14, cooldown=150, difficulty=1.2))
M.append(F("mutatio", "Mutatio / Mindwarp", "变形", "ムタティオ", ["arcane"], ("mutatio", "变形", "ムタティオ"),
    [hex_("minecraft:nausea", 0, 100, 10, angle_cos=0.5), hex_("minecraft:weakness", 1, 100, 10, angle_cos=0.5), sound("minecraft:entity.zombie_villager.converted", 0.8, 1.4)],
    [stg(0, 1.4, 150, lambda s: [hex_("minecraft:nausea", 1, 160, 12, angle_cos=0.45), hex_("minecraft:weakness", 2, 160, 12, angle_cos=0.45), hex_("minecraft:slowness", 1, 160, 12, angle_cos=0.45), visual(D(s), "cone", 3.0, 46, 18)]),
     stg(60, 2.6, 360, lambda s: [hex_("minecraft:nausea", 1, 240, 16, angle_cos=0.4), hex_("minecraft:weakness", 2, 240, 16, angle_cos=0.4), hex_("minecraft:slowness", 2, 240, 16, angle_cos=0.4), hex_("minecraft:glowing", 0, 240, 16, angle_cos=0.4), visual(D(s), "cone", 4.5, 60, 22), sound("minecraft:entity.zombie_villager.converted", 1.0, 1.2)])],
    mana=12, cooldown=140, difficulty=1.3))
M.append(F("portus", "Portus / Farstep", "传送", "ポルトゥス", ["arcane"], ("portus", "传送", "ポルトゥス"),
    [blink(12), sound("minecraft:entity.enderman.teleport")],
    [stg(0, 1.4, 130, lambda s: [blink(20), status("minecraft:speed", 100, 1), visual(D(s), "trail", 1.5, 40, 14)]),
     stg(50, 2.4, 320, lambda s: [blink(30), status("minecraft:speed", 200, 1), status("minecraft:slow_falling", 100, 0), visual(D(s), "trail", 2.5, 60, 18), sound("minecraft:entity.enderman.teleport", 1.1, 1.0)])],
    mana=10, cooldown=120, difficulty=1.2))
M.append(F("gravitas", "Gravitas / Gravity Well", "引力井", "グラウィタス", ["arcane"], ("gravitas", "引力井", "グラウィタス"),
    [pull(8, 1.0), visual(D("arcane"), "orbit", 2.5, 36, 25, 1.0, True)],
    [stg(0, 1.4, 150, lambda s: [pull(11, 1.4), hex_("minecraft:slowness", 1, 80, 11), visual(D(s), "orbit", 3.2, 50, 28, 1.0, True)]),
     stg(50, 2.5, 340, lambda s: [pull(15, 1.9), hex_("minecraft:slowness", 2, 120, 15), hex_("minecraft:weakness", 1, 120, 15), explosion(4, 2, False), visual(D(s), "orbit", 4.5, 66, 32, 1.0, True), sound("minecraft:entity.evoker.cast_spell", 1.1, 0.8)])],
    mana=14, cooldown=160, difficulty=1.2))
M.append(F("siphon", "Siphon / Essence Drain", "虹吸", "スィフォ", ["arcane"], ("siphon", "虹吸", "スィフォ"),
    [hex_("minecraft:weakness", 1, 100, 10, angle_cos=0.5), heal(3), sound("minecraft:entity.evoker.cast_spell", 0.9, 1.2)],
    [stg(0, 1.4, 150, lambda s: [hex_("minecraft:weakness", 2, 140, 12, angle_cos=0.45), heal(5), status("minecraft:regeneration", 80, 0), visual(D(s), "cone", 3.0, 44, 18)]),
     stg(50, 2.4, 330, lambda s: [hex_("minecraft:weakness", 2, 180, 16, angle_cos=0.4), hex_("minecraft:wither", 0, 100, 16, angle_cos=0.4), heal(8), status("minecraft:absorption", 200, 1), visual(D(s), "cone", 4.5, 60, 22), sound("minecraft:entity.evoker.cast_spell", 1.0, 1.0)])],
    mana=12, cooldown=140, difficulty=1.1))

# ===== ILLUSION =====
M.append(F("falsum", "Falsum / Phantom Volley", "幻弹", "ファルスム", ["illusion"], ("falsum", "幻弹", "ファルスム"),
    [proj("minecraft:egg", 2.0), sound("minecraft:entity.illusioner.cast_spell", 0.8, 1.3)],
    [stg(0, 1.5, 110, lambda s: [proj("minecraft:egg", 2.0, 3, 0.1), visual(D(s), "cone", 2.0, 34, 13)]),
     stg(50, 2.5, 300, lambda s: [proj("minecraft:egg", 2.0, 6, 0.14), hex_("minecraft:darkness", 0, 60, 8, angle_cos=0.4), visual(D(s), "cone", 3.5, 52, 17), sound("minecraft:entity.illusioner.cast_spell", 1.0, 1.1)])],
    mana=8, cooldown=45))
M.append(F("nubes", "Nubes / Blind Fog", "迷雾", "ヌベス", ["illusion"], ("nubes", "迷雾", "ヌベス"),
    [hex_("minecraft:blindness", 0, 80, 7, radius=5, at_target=True), particles("minecraft:cloud", 16, 0.8, 0.03), sound("minecraft:entity.illusioner.mirror_move", 0.8, 1.2)],
    [stg(0, 1.4, 140, lambda s: [hex_("minecraft:blindness", 0, 120, 9, radius=7, at_target=True), hex_("minecraft:slowness", 0, 120, 9, radius=7, at_target=True), visual(D(s), "burst", 4.0, 46, 20, 1.0, True)]),
     stg(50, 2.5, 330, lambda s: [hex_("minecraft:blindness", 0, 160, 12, radius=10, at_target=True), hex_("minecraft:slowness", 1, 160, 12, radius=10, at_target=True), hex_("minecraft:weakness", 0, 160, 12, radius=10, at_target=True), visual(D(s), "pillar", 5.0, 60, 24, 1.0, True), sound("minecraft:entity.illusioner.mirror_move", 1.0, 1.0)])],
    mana=10, cooldown=110))
M.append(F("somnus", "Somnus / Lullaby", "催眠", "ソムヌス", ["illusion"], ("somnus", "催眠", "ソムヌス"),
    [hex_("minecraft:nausea", 0, 120, 8), hex_("minecraft:slowness", 1, 120, 8), sound("minecraft:block.note_block.chime", 0.8, 0.8)],
    [stg(0, 1.4, 150, lambda s: [hex_("minecraft:slowness", 3, 160, 10), hex_("minecraft:weakness", 1, 160, 10), hex_("minecraft:nausea", 1, 160, 10), visual(D(s), "orbit", 2.5, 44, 24)]),
     stg(50, 2.5, 340, lambda s: [hex_("minecraft:slowness", 4, 220, 14), hex_("minecraft:weakness", 2, 220, 14), hex_("minecraft:darkness", 0, 220, 14), visual(D(s), "pillar", 4.0, 58, 26), sound("minecraft:block.note_block.chime", 1.0, 0.7)])],
    mana=12, cooldown=150, difficulty=1.2))
M.append(F("velum", "Velum / Unseen Veil", "隐幕", "ウェルム", ["illusion"], ("velum", "隐幕", "ウェルム"),
    [status("minecraft:invisibility", 200, 0), sound("minecraft:entity.illusioner.mirror_move")],
    [stg(0, 1.4, 150, lambda s: [status("minecraft:invisibility", 400, 0), status("minecraft:speed", 400, 0), visual(D(s), "helix", 1.5, 36, 20)]),
     stg(50, 2.4, 330, lambda s: [status("minecraft:invisibility", 600, 0), status("minecraft:speed", 600, 1), hex_("minecraft:invisibility", 0, 300, 6, radius=6), visual(D(s), "helix", 2.2, 50, 24), sound("minecraft:entity.illusioner.mirror_move", 1.1, 1.1)])],
    mana=12, cooldown=180, difficulty=1.2))
M.append(F("fallax", "Fallax / Mirror Feint", "替身", "ファッラクス", ["illusion"], ("fallax", "替身", "ファッラクス"),
    [visual(D("illusion"), "cross", 1.5, 30, 20), hex_("minecraft:blindness", 0, 60, 6, radius=6), sound("minecraft:entity.illusioner.mirror_move", 1.0, 0.9)],
    [stg(0, 1.4, 140, lambda s: [visual(D(s), "orbit", 2.0, 40, 25), hex_("minecraft:blindness", 0, 100, 8, radius=8), status("minecraft:speed", 100, 1)]),
     stg(50, 2.4, 320, lambda s: [visual(D(s), "orbit", 3.0, 55, 28), hex_("minecraft:blindness", 0, 140, 10, radius=10), hex_("minecraft:slowness", 1, 140, 10, radius=10), status("minecraft:invisibility", 100, 0), sound("minecraft:entity.illusioner.mirror_move", 1.1, 0.8)])],
    mana=10, cooldown=140, difficulty=1.1))
M.append(F("spectra", "Spectra / Phantasm Wall", "幻墙", "スペクトラ", ["illusion"], ("spectra", "幻墙", "スペクトラ"),
    [barrier("minecraft:white_stained_glass", "wall", 4, 3, 2, 200), sound("minecraft:block.glass.place")],
    [stg(0, 1.5, 150, lambda s: [barrier("minecraft:white_stained_glass", "wall", 5, 4, 2, 300), visual(D(s), "ring", 3.0, 36, 14, 0.5, True)]),
     stg(50, 2.4, 330, lambda s: [barrier("minecraft:white_stained_glass", "ring", 3, 3, 3, 320), barrier("minecraft:white_stained_glass", "wall", 5, 4, 2, 320), visual(D(s), "ring", 4.0, 48, 16, 0.5, True), sound("minecraft:block.glass.place", 1.1, 0.9)])],
    mana=12, cooldown=140, difficulty=1.1))
M.append(F("mentis", "Mentis / Mind's Eye", "心眼", "メンティス", ["illusion"], ("mentis", "心眼", "メンティス"),
    [light(14, 120, 0), sound("minecraft:block.amethyst_block.chime", 0.9, 1.3)],
    [stg(0, 1.3, 120, lambda s: [light(18, 200, 240), visual(D(s), "orbit", 2.2, 36, 22)]),
     stg(50, 2.2, 280, lambda s: [light(26, 300, 480), hex_("minecraft:glowing", 0, 300, 26, radius=26), visual(D(s), "orbit", 3.0, 50, 26), sound("minecraft:block.amethyst_block.chime", 1.0, 1.1)])],
    mana=8, cooldown=120, difficulty=1.1))

# ===== FORBIDDEN (禁咒录) =====
M.append(F("rift", "Rift / Void Maw", "虚空裂隙", "ウィウム", ["arcane"], ("rift", "虚空裂隙", "ウィウム"),
    [pull(10, 1.6, True), explosion(6, 3, False), sound("minecraft:block.portal.ambient")],
    [], mana=26, cooldown=600, difficulty=2.5, requires_learning=True, tier="forbidden"))
M.append(F("domain", "Domain / Blade Tempest", "剑雨领域", "ドメイン", ["arcane"], ("domain", "剑域", "ドメイン"),
    [explosion(8, 2, False), proj("minecraft:arrow", 1.8, 12, 0.35), sound("minecraft:entity.evoker.prepare_summon")],
    [], mana=28, cooldown=600, difficulty=2.5, requires_learning=True, tier="forbidden"))
M.append(F("vincula", "Vincula / Sky Chains", "天锁缚", "ウィンクラ", ["holy"], ("vincula", "天锁", "ウィンクラ"),
    [bind(120, 20, True), visual(D("holy"), "pillar", 5.0, 80, 40), sound("minecraft:block.beacon.activate")],
    [], mana=30, cooldown=800, difficulty=2.5, requires_learning=True, tier="forbidden"))
M.append(F("blade", "Blade / Thousand Swords", "千剑刃", "ブラード", ["arcane"], ("blade", "千剑", "ブラード"),
    [beam(24, 2, 14, False), beam(24, 2, 14, False), proj("minecraft:arrow", 1.5, 16, 0.4), sound("minecraft:entity.player.attack.sweep", 1.2, 0.6)],
    [], mana=30, cooldown=800, difficulty=3.0, requires_learning=True, tier="forbidden"))

FORBIDDEN_KEEP_CHANTS = {"explosion"}  # 手工精修咏唱保留

# ---------------------------------------------------------------- emit

def power_tiers(lines_total):
    """power_per_line ramp: 0.35 at the entry line up to 1.0 at the full chant."""
    n = lines_total + 1  # index 0 = line 1; full chant = lines_total + 1st entry
    return [round(0.35 + 0.65 * i / (n - 1), 2) for i in range(n)]

def build_spell(fam, existing):
    school = fam["schools"][0]
    stages = fam["stages"]
    n_stages = len(stages)
    if fam["tier"] == "forbidden":
        n_middle = 4  # 6-line chant (trigger + 4 + cast)
    else:
        n_middle = 2 * n_stages - 1 if n_stages > 0 else 0
    lines_total = n_middle + 2
    max_lines = max(2, lines_total)
    power = power_tiers(lines_total) if fam["tier"] != "forbidden" or n_middle else power_tiers(max_lines)

    spell = {
        "id": f"wizardreal:{fam['id']}",
        "schools": fam["schools"],
        "mana_cost": fam["mana"],
        "cooldown_ticks": fam["cooldown"],
        "requires_learning": fam["requires_learning"],
        "origin": fam["origin"],
        "threshold": -1.0,
        "difficulty": fam["difficulty"],
        "trigger": existing["trigger"] if existing else make_trigger(fam),
        "effects": fam["base"],
    }
    if fam["tier"] == "forbidden" or n_stages > 0:
        if existing and "chants" in existing and fam["id"] in FORBIDDEN_KEEP_CHANTS:
            spell["chants"] = existing["chants"]
        else:
            spell["chants"] = {"languages": chant_lines(fam, school, n_middle, fam["words"],
                                                         fam.get("_cast_words"))}
        spell["chant_policy"] = {
            "power_per_line": power,
            "skip_allowed": fam["tier"] != "forbidden",
            "interruptible": fam["tier"] != "forbidden",
        }
    if stages:
        cs = []
        for i, stg_ in enumerate(stages):
            entry = {
                "after_lines": 3 + 2 * i if n_stages > 1 else 2,
                "mastery": stg_["mastery"],
                "effects": stg_["fx"](school),
            }
            mana_override = int(round(fam["mana"] * stg_["mana_mult"]))
            if stg_["cd"]:
                entry["mana_cost"] = mana_override
                entry["cooldown_ticks"] = stg_["cd"]
            else:
                entry["mana_cost"] = mana_override
            cs.append(entry)
        spell["chant_stages"] = cs
    # after_lines must not exceed the longest chant (validator hard check)
    max_after = max((c["after_lines"] for c in spell.get("chant_stages", [])), default=0)
    if max_after > lines_total and n_middle > 0:
        n_middle = max_after - 2
    return spell

def make_trigger(fam):
    words = fam["words"]
    en = words[0].lower()  # spaced form: matches the cast line (spell name)
    ja = words[2]
    aliases = [en] if " " not in en else [en] + en.split()
    return {"languages": {
        "en": aliases,
        "zh": [words[1]],
        "ja": [ja] + ([ja.replace("・", "")] if "・" in ja else []),
        "ko": [],
    }, "ipa": []}

# Families introduced by the magic_eco expansion: their trigger words are
# always regenerated (a previous generator run's file on disk must never be
# mistaken for hand-tuned ASR data).
FRESH = {"concoctio", "vestibulum", "incursus", "aqua_vitae",
         "celere", "tempestas", "galvanum", "fodere", "lithos", "petra_catena",
         "aurae_levitas", "flatu_signum", "floris", "silva_voco",
         "ferae_vocatus", "lux", "aegis", "angelus", "umbra_mortis", "carnifex",
         "spiritus", "portus", "gravitas", "siphon", "fallax", "spectra", "mentis"}

def main():
    report = []
    existing_files = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in SPELL_DIR.glob("*.json")}
    matrix_ids = {f["id"] for f in M}
    lang_en = json.loads((LANG_DIR / "en_us.json").read_text(encoding="utf-8"))
    lang_zh = json.loads((LANG_DIR / "zh_cn.json").read_text(encoding="utf-8"))

    # existing spells not in the matrix: keep their files untouched
    untouched = set(existing_files) - matrix_ids
    if untouched:
        report.append(f"[warn] existing spells not in matrix (kept as-is): {sorted(untouched)}")

    rot_counter = {}
    for fam in M:
        school0 = fam["schools"][0]
        rot_counter[school0] = rot_counter.get(school0, 0)
        fam["_rot"] = rot_counter[school0]
        rot_counter[school0] += 1
        existing = None if fam["id"] in FRESH else existing_files.get(fam["id"])
        cast_words = {}
        if existing:
            tl = existing.get("trigger", {}).get("languages", {})
            for lang in ("en", "zh", "ja"):
                if tl.get(lang):
                    cast_words[lang] = tl[lang][0]
        fam["_cast_words"] = cast_words
        spell = build_spell(fam, existing)
        out = SPELL_DIR / f"{fam['id']}.json"
        out.write_text(json.dumps(spell, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        report.append(f"[ok] {fam['id']}: stages={len(spell.get('chant_stages', []))} "
                      f"chants={'Y' if 'chants' in spell else 'N'} {'(new)' if not existing else ''}")
        base = f"spell.wizardreal.{fam['id']}.name"
        lang_en[base] = fam["en"]
        lang_zh[base] = fam["zh"]
        for i, stg_ in enumerate(fam["stages"], 1):
            lang_en[f"spell.wizardreal.{fam['id']}.stage.{i}"] = f"{fam['en'].split(' / ')[0]} · Tier {i}"
            lang_zh[f"spell.wizardreal.{fam['id']}.stage.{i}"] = f"{fam['zh'].split(' / ')[0]} · {i} 阶"

    (LANG_DIR / "en_us.json").write_text(json.dumps(lang_en, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (LANG_DIR / "zh_cn.json").write_text(json.dumps(lang_zh, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    # ---- self checks -----------------------------------------------------
    n_stages_total = sum(len(f["stages"]) for f in M)
    json_ok = all(json.loads(p.read_text(encoding="utf-8")) for p in SPELL_DIR.glob("*.json"))
    triggers = [make_trigger(f)["languages"]["en"][0] for f in M if f["id"] not in existing_files]
    dup = {t for t in triggers if triggers.count(t) > 1}
    report.append(f"total families: {len(M)} (forbidden: {sum(1 for f in M if f['tier']=='forbidden')}), "
                  f"stages: {n_stages_total}, json valid: {json_ok}, dup triggers: {dup or 'none'}")
    print("\n".join(report))

if __name__ == "__main__":
    main()
