# Generates the full 07_design_roadmap matrix (45 spells) data pack.
# Existing batch-0 files are kept; this adds the 30 missing cells.
# Effects for §4 primitives not yet implemented (hex/surface/barrier/blink/
# chain/summon) are PLACEHOLDERS built from shipped primitives — noted per
# spell below and flagged "07_placeholder": true.
import json
import os
from pathlib import Path

OUT = Path("wizardreal/common/src/main/resources/data/wizardreal/voicecast/spells")

def beam(damage, **kw):
    e = {"type": "wizardreal:beam", "damage": damage}
    e.update(kw)
    return e

def status(effect, duration=200, amplifier=0):
    return {"type": "wizardreal:status_effect", "effect": effect,
            "duration": duration, "amplifier": amplifier}

def knock(power, rng=6.0):
    return {"type": "wizardreal:knockback", "range": rng, "power": power}

def snd(name):
    return {"type": "wizardreal:sound", "sound": name}

def proj(entity, speed=1.5, count=1, spread=0.0):
    return {"type": "wizardreal:projectile", "entity": entity,
            "speed": speed, "count": count, "spread": spread}

# id, school, role, mana, cd, diff, learn, aliases{en,zh,ja}, effects, placeholder
S = [
 # ---- 束 (beam) ----
 ("flamma", "fire", 12, 60, 1.0, False,
  {"en": ["flamma", "flame cone"], "zh": ["焰息", "火焰喷射"], "ja": ["フラマ", "炎のブレス"]},
  [beam(6.0, set_fire=True), snd("minecraft:entity.blaze.shoot")], True),
 ("fluctus", "water", 12, 60, 1.0, False,
  {"en": ["fluctus", "tide cone"], "zh": ["潮涌", "潮涌锥"], "ja": ["フルクトゥス", "潮のブレス"]},
  [beam(5.0), knock(1.6), snd("minecraft:entity.generic.splash")], True),
 ("tonitrus", "lightning", 14, 60, 1.25, False,
  {"en": ["tonitrus", "chain lightning"], "zh": ["链电", "连锁雷"], "ja": ["トニトルス", "連鎖雷"]},
  [beam(9.0), snd("minecraft:entity.lightning_bolt.thunder")], True),
 ("lapides", "earth", 12, 60, 1.0, False,
  {"en": ["lapides", "rock shower"], "zh": ["落石", "落石锥"], "ja": ["ラピデス", "落石のブレス"]},
  [beam(7.0), snd("minecraft:block.stone.hit")], True),
 ("turbo", "air", 12, 60, 1.0, False,
  {"en": ["turbo", "tornado cone"], "zh": ["龙卷", "龙卷风"], "ja": ["トゥルボ", "竜巻"]},
  [beam(4.0), knock(1.8), snd("minecraft:entity.phantom.flap")], True),
 ("radix", "nature", 12, 60, 1.0, False,
  {"en": ["radix", "root cone"], "zh": ["缠根", "藤蔓缠绕"], "ja": ["ラディクス", "根のブレス"]},
  [beam(4.0), status("minecraft:slowness", 200, 1), snd("minecraft:block.grass.break")], True),
 ("lumen", "holy", 12, 60, 1.0, False,
  {"en": ["lumen", "holy beam"], "zh": ["圣光束", "圣光"], "ja": ["ルーメン", "聖光のブレス"]},
  [beam(8.0), snd("minecraft:entity.evoker.cast_spell")], True),
 ("suctus", "necromancy", 12, 60, 1.25, False,
  {"en": ["suctus", "drain cone"], "zh": ["汲取", "汲取锥"], "ja": ["スクトゥス", "吸収のブレス"]},
  [beam(5.0), {"type": "wizardreal:heal", "amount": 3.0},
   snd("minecraft:entity.wither.hurt")], True),
 ("chaos", "arcane", 12, 60, 1.25, False,
  {"en": ["chaos", "chaos beam"], "zh": ["混沌束", "混沌"], "ja": ["カオス", "混沌のブレス"]},
  [beam(6.0), snd("minecraft:entity.evoker.cast_spell")], True),
 ("nubes", "illusion", 12, 60, 1.0, False,
  {"en": ["nubes", "mist cone"], "zh": ["迷雾锥", "雾隐"], "ja": ["ヌベス", "霧のブレス"]},
  [beam(0.0), status("minecraft:blindness", 100, 0),
   snd("minecraft:entity.illusioner.mirror_move")], True),
 # ---- 控 (control, hex placeholder = status/knockback) ----
 ("incendo", "fire", 14, 80, 1.25, False,
  {"en": ["incendo", "ember field"], "zh": ["烟火领域", "灼烧"], "ja": ["インチェンド", "火の領域"]},
  [status("minecraft:blindness", 120, 0), snd("minecraft:entity.blaze.ambient")], True),
 ("gurges", "water", 14, 80, 1.25, False,
  {"en": ["gurges", "whirlpool"], "zh": ["漩涡", "涡流"], "ja": ["グルゲス", "渦潮"]},
  [status("minecraft:slowness", 160, 2), knock(0.8), snd("minecraft:entity.generic.splash")], True),
 ("torpor", "lightning", 14, 80, 1.25, False,
  {"en": ["torpor", "stasis field"], "zh": ["静滞", "麻痹领域"], "ja": ["トルポル", "静止の域"]},
  [status("minecraft:slowness", 140, 3), snd("minecraft:entity.lightning_bolt.impact")], True),
 ("terra", "earth", 14, 80, 1.25, False,
  {"en": ["terra", "ground sink"], "zh": ["地陷", "大地沉陷"], "ja": ["テラ", "地割れ"]},
  [status("minecraft:mining_fatigue", 200, 2), snd("minecraft:block.gravel.break")], True),
 ("procella", "air", 14, 80, 1.25, False,
  {"en": ["procella", "gale burst"], "zh": ["气浪", "风爆"], "ja": ["プロケラ", "突風"]},
  [knock(2.2, 8.0), snd("minecraft:entity.phantom.flap")], True),
 ("vireo", "nature", 14, 80, 1.25, False,
  {"en": ["vireo", "binding vine"], "zh": ["束缚藤", "藤缚"], "ja": ["ウィレオ", "束縛蔓"]},
  [status("minecraft:slowness", 140, 4), snd("minecraft:block.azalea_leaves.break")], True),
 ("judicium", "holy", 14, 80, 1.25, False,
  {"en": ["judicium", "judgment"], "zh": ["审判", "天罚领域"], "ja": ["ユディキウム", "裁き"]},
  [status("minecraft:glowing", 160, 0), snd("minecraft:entity.lightning_bolt.thunder")], True),
 ("letum", "necromancy", 14, 80, 1.25, False,
  {"en": ["letum", "wither field"], "zh": ["凋零领域", "凋零"], "ja": ["レトゥム", "萎れの域"]},
  [status("minecraft:wither", 120, 1), snd("minecraft:entity.wither.spawn")], True),
 ("metus", "arcane", 14, 80, 1.25, False,
  {"en": ["metus", "terror"], "zh": ["恐惧", "恐怖扩散"], "ja": ["メトゥス", "恐怖"]},
  [status("minecraft:darkness", 140, 0), snd("minecraft:entity.warden.heartbeat")], True),
 ("somnus", "illusion", 14, 80, 1.25, False,
  {"en": ["somnus", "sleep"], "zh": ["催眠", "催眠领域"], "ja": ["ソムヌス", "催眠"]},
  [status("minecraft:darkness", 200, 0), status("minecraft:slowness", 200, 2),
   snd("minecraft:entity.illusioner.cast_spell")], True),
 # ---- 用 (utility) ----
 ("aduro", "fire", 10, 60, 1.25, False,
  {"en": ["aduro", "melt armor"], "zh": ["熔甲", "卸甲"], "ja": ["アドゥロ", "鎧溶かし"]},
  [status("minecraft:weakness", 200, 1), snd("minecraft:item.armor.equip_iron.break")], True),
 ("diluvium", "water", 10, 60, 1.25, False,
  {"en": ["diluvium", "rain field"], "zh": ["降雨", "豪雨领域"], "ja": ["ディルビウム", "降雨領域"]},
  [snd("minecraft:item.trident.thunder"), snd("minecraft:weather.rain")], True),
 ("murus", "earth", 10, 60, 1.0, False,
  {"en": ["murus", "stone wall"], "zh": ["石墙", "大地壁垒"], "ja": ["ムルス", "石の壁"]},
  [snd("minecraft:block.stone.place")], True),
 ("volatus", "air", 10, 60, 1.5, False,
  {"en": ["volatus", "wind step"], "zh": ["疾风步", "风行"], "ja": ["ウォラトゥス", "疾風歩"]},
  [status("minecraft:speed", 100, 1), snd("minecraft:entity.enderman.teleport")], True),
 ("anima", "necromancy", 14, 80, 1.5, False,
  {"en": ["anima", "raise bones"], "zh": ["召骸", "骸骨召唤"], "ja": ["アニマ", "骸骨召喚"]},
  [snd("minecraft:entity.skeleton.ambient")], True),
 ("mutatio", "arcane", 12, 80, 1.25, False,
  {"en": ["mutatio", "transform"], "zh": ["变形", "变鸡术"], "ja": ["ムタティオ", "変身"]},
  [snd("minecraft:entity.chicken.ambient")], True),
 # ---- 禁咒录 (forbidden, 4 new; explosion exists) ----
 ("rift", "arcane", 28, 300, 2.5, True,
  {"en": ["rift", "void rift"], "zh": ["虚空裂隙", "裂隙"], "ja": ["リフト", "虚空の裂け目"]},
  [{"type": "wizardreal:explosion", "range": 10.0, "power": 3.0},
   snd("minecraft:entity.evoker.cast_spell"), snd("minecraft:entity.enderman.teleport")], True),
 ("domain", "holy", 28, 300, 2.5, True,
  {"en": ["domain", "blade domain"], "zh": ["剑雨领域", "剑域"], "ja": ["ドメイン", "剣の領域"]},
  [{"type": "wizardreal:explosion", "range": 12.0, "power": 2.0},
   snd("minecraft:entity.evoker.prepare_summon")], True),
 ("vincula", "holy", 26, 280, 2.0, True,
  {"en": ["vincula", "sky chains"], "zh": ["天锁缚", "天锁"], "ja": ["ヴィンクラ", "天の鎖"]},
  [status("minecraft:slowness", 200, 5), snd("minecraft:block.chain.break")], True),
 ("blade", "arcane", 30, 400, 3.0, True,
  {"en": ["blade", "thousand blades"], "zh": ["千剑刃", "千剑"], "ja": ["ブレード", "千の剣"]},
  [proj("minecraft:arrow", 2.0, 8, 6.0), snd("minecraft:item.trident.throw")], True),
]

def spell_doc(sid, school, mana, cd, diff, learn, aliases, effects, placeholder):
    return {
        "id": f"wizardreal:{sid}",
        "schools": [school],
        "mana_cost": mana,
        "cooldown_ticks": cd,
        "requires_learning": learn,
        "origin": "wizardreal:wizardry",
        "threshold": -1.0,
        "difficulty": diff,
        **({"07_placeholder": True} if placeholder else {}),
        "trigger": {"languages": {**aliases, "ko": []}, "ipa": []},
        "effects": effects,
    }

count = 0
for sid, school, mana, cd, diff, learn, aliases, effects, ph in S:
    out = OUT / f"{sid}.json"
    if out.exists():
        print("skip existing", sid)
        continue
    doc = spell_doc(sid, school, mana, cd, diff, learn, aliases, effects, ph)
    out.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    count += 1
print(f"generated {count} spells")
