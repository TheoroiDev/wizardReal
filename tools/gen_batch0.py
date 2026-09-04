# -*- coding: utf-8 -*-
"""Matrix batch-0 content generator (07 §3, 2026-09-04).

Writes the batch-0 spell JSONs (10 projectile + 4 utility + explosion
forbidden-chant redo) in the 0.4.0 language-keyed format. ko buckets stay
empty (N4: may lag one batch; the load validator WARNs). Old-format spell
JSONs that are superseded are deleted - ids are re-dealt and learning
counters reset (changelog breaking, voice_overhaul_implementation §6.2).

Run from the wizardreal repo root:
  python tools/gen_batch0.py
"""
import json
from pathlib import Path

SPELLS_DIR = Path("wizardreal/common/src/main/resources/data/wizardreal/voicecast/spells")


def spell(id_, schools, mana, cooldown, effects, difficulty=1.0, requires_learning=False,
          policy=None):
    doc = {
        "id": f"wizardreal:{id_}",
        "schools": schools,
        "mana_cost": mana,
        "cooldown_ticks": cooldown,
        "requires_learning": requires_learning,
        "origin": "wizardreal:wizardry",
        "threshold": -1.0,
        "difficulty": difficulty,
        "trigger": {"languages": TRIGGERS[id_], "ipa": []},
        "effects": effects,
    }
    if policy:
        doc["chant_policy"] = policy
    return doc


def line(text):
    return {"aliases": [text]}


TRIGGERS = {
    "ignis": {"en": ["ignis", "fireball"], "zh": ["火球", "烈焰弹"], "ja": ["イグニス", "ファイアボール"], "ko": []},
    "unda": {"en": ["unda", "water orb"], "zh": ["水弹", "潮珠"], "ja": ["ウンダ", "みずだま"], "ko": []},
    "fulmen": {"en": ["fulmen", "lightning"], "zh": ["落雷", "雷击"], "ja": ["フルメン", "カミナリ"], "ko": []},
    "saxum": {"en": ["saxum", "stone spear"], "zh": ["石矛", "岩枪"], "ja": ["サクスム", "いしやり"], "ko": []},
    "ventus": {"en": ["ventus", "wind blade"], "zh": ["风刃", "气刃"], "ja": ["ウェンタス", "カゼノヤイバ"], "ko": []},
    "spina": {"en": ["spina", "thorn seed"], "zh": ["荆棘种", "棘弹"], "ja": ["スピーナ", "いばらのたね"], "ko": []},
    "sagitta": {"en": ["sagitta", "holy arrow"], "zh": ["圣光矢", "光箭"], "ja": ["サジッタ", "せいこうや"], "ko": []},
    "ossum": {"en": ["ossum", "bone spear"], "zh": ["骨矛", "白骨弹"], "ja": ["オッスム", "ほねやり"], "ko": []},
    "telum": {"en": ["telum", "arcane bolt"], "zh": ["奥术矢", "秘法弹"], "ja": ["テールム", "ひほうだん"], "ko": []},
    "falsum": {"en": ["falsum", "phantom bolt"], "zh": ["幻弹", "虚影弹"], "ja": ["ファルスム", "まぼろしだま"], "ko": []},
    "fulgur": {"en": ["fulgur", "storm brand"], "zh": ["雷蓄", "蓄雷"], "ja": ["フールグル", "ライチク"], "ko": []},
    "semina": {"en": ["semina", "green bounty"], "zh": ["丰收", "丰穰祝福"], "ja": ["セミナ", "ホウジョウ"], "ko": []},
    "sanare": {"en": ["sanare", "cleanse"], "zh": ["净化", "圣愈"], "ja": ["サナーレ", "セイジョ"], "ko": []},
    "velum": {"en": ["velum", "veil"], "zh": ["隐幕", "匿影"], "ja": ["ウェールム", "カクレミノ"], "ko": []},
    "explosion": {"en": ["explosion"], "zh": ["爆裂"], "ja": ["エクスプロージョン"], "ko": []},
}

# Explosion forbidden chant: 6 lines (trigger + 4 body + cast), two body
# variants per language; variant 1 (en/zh/ja) grows out of the legacy chant.
EXPLOSION_CHANTS = {
    "en": {
        "trigger": line("blackest ire, crown of the end"),
        "cast": line("explosion"),
        "body": [
            [line("let the earth split wide"), line("let the heavens bend low"),
             line("sealed in ruin's name"), line("unmake all that stands")],
            [line("ashes wake, the sky ignites"), line("the bell of doom is rung"),
             line("fire without mercy"), line("kneel, o towering walls")],
        ],
    },
    "zh": {
        "trigger": line("以漆黑遮蔽苍穹"),
        "cast": line("爆裂"),
        "body": [
            [line("唤醒赤红雷霆"), line("大地为之崩裂"), line("苍穹为之低头"), line("以毁灭之名封印")],
            [line("灰烬苏醒天穹燃"), line("厄运之钟敲响"), line("无慈之炎奔流"), line("高墙跪伏尘埃")],
        ],
    },
    "ja": {
        "trigger": line("闇をもって天を覆え"),
        "cast": line("エクスプロージョン"),
        "body": [
            [line("紅蓮の雷を目覚めさせよ"), line("大地よ割れ開け"), line("天よかしこめ"), line("破滅の名のもとに封ぜよ")],
            [line("灰は目覚め空は燃える"), line("厄の鐘が鳴る"), line("慈悲なき炎よ流れよ"), line("高き壁よひざせ")],
        ],
    },
}

BATCH = [
    spell("ignis", ["fire"], 8, 40,
          [{"type": "wizardreal:projectile", "entity": "minecraft:small_fireball", "speed": 1.5},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.blaze.shoot"}]),
    spell("unda", ["water"], 8, 40,
          [{"type": "wizardreal:projectile", "entity": "minecraft:snowball", "speed": 1.8},
           {"type": "wizardreal:knockback", "range": 5, "angle_cos": 0.5, "power": 0.6},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.generic.splash"}]),
    spell("fulmen", ["lightning"], 12, 60,
          [{"type": "wizardreal:lightning", "range": 48},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.lightning_bolt.thunder"}]),
    spell("saxum", ["earth"], 10, 50,
          [{"type": "wizardreal:projectile", "entity": "minecraft:arrow", "speed": 2.6},
           {"type": "wizardreal:sound", "sound": "minecraft:block.stone.hit"}]),
    spell("ventus", ["air"], 8, 40,
          [{"type": "wizardreal:projectile", "entity": "minecraft:snowball", "speed": 2.2},
           {"type": "wizardreal:knockback", "range": 8, "angle_cos": 0.5, "power": 1.6},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.phantom.flap"}]),
    spell("spina", ["nature"], 9, 45,
          [{"type": "wizardreal:projectile", "entity": "minecraft:arrow", "speed": 2.0},
           {"type": "wizardreal:sound", "sound": "minecraft:block.azalea_leaves.break"}]),
    spell("sagitta", ["holy"], 9, 45,
          [{"type": "wizardreal:projectile", "entity": "minecraft:spectral_arrow", "speed": 2.4},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.arrow.shoot"}]),
    spell("ossum", ["necromancy"], 11, 55,
          [{"type": "wizardreal:projectile", "entity": "minecraft:wither_skull", "speed": 1.2},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.wither_skeleton.ambient"}]),
    spell("telum", ["arcane"], 10, 45,
          [{"type": "wizardreal:projectile", "entity": "minecraft:shulker_bullet", "speed": 1.8},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.evoker.cast_spell"}]),
    spell("falsum", ["illusion"], 8, 40,
          [{"type": "wizardreal:projectile", "entity": "minecraft:egg", "speed": 2.0},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.zombie_villager.confused"}]),
    spell("fulgur", ["lightning"], 12, 80,
          [{"type": "wizardreal:status_effect", "effect": "minecraft:glowing", "duration": 160, "amplifier": 0},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.lightning_bolt.impact"}]),
    spell("semina", ["nature"], 12, 100,
          [{"type": "wizardreal:heal", "amount": 6},
           {"type": "wizardreal:status_effect", "effect": "minecraft:regeneration", "duration": 200, "amplifier": 1},
           {"type": "wizardreal:sound", "sound": "minecraft:block.composter.fill"}]),
    spell("sanare", ["holy"], 14, 120,
          [{"type": "wizardreal:heal", "amount": 8},
           {"type": "wizardreal:status_effect", "effect": "minecraft:absorption", "duration": 200, "amplifier": 1},
           {"type": "wizardreal:sound", "sound": "minecraft:block.beacon.power_select"}]),
    spell("velum", ["illusion"], 12, 200,
          [{"type": "wizardreal:status_effect", "effect": "minecraft:invisibility", "duration": 160, "amplifier": 0},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.enderman.teleport"}]),
    spell("explosion", ["arcane"], 60, 1200,
          [{"type": "wizardreal:explosion", "range": 16, "power": 4, "set_fire": True},
           {"type": "wizardreal:sound", "sound": "minecraft:entity.generic.explode"}],
          difficulty=2.5, requires_learning=True,
          policy={"power_per_line": [0.5, 0.6, 0.7, 0.8, 0.9, 1.0],
                  "skip_allowed": False, "interruptible": False}),
]


def build_explosion_doc():
    return {
        "id": "wizardreal:explosion",
        "schools": ["arcane"],
        "mana_cost": 60,
        "cooldown_ticks": 1200,
        "requires_learning": True,
        "origin": "wizardreal:wizardry",
        "threshold": -1.0,
        "difficulty": 2.5,
        "trigger": {"languages": TRIGGERS["explosion"], "ipa": []},
        "chants": {"languages": EXPLOSION_CHANTS},
        "chant_policy": {"power_per_line": [0.5, 0.6, 0.7, 0.8, 0.9, 1.0],
                          "skip_allowed": False, "interruptible": False},
        "effects": [
            {"type": "wizardreal:explosion", "range": 16, "power": 4, "set_fire": True},
            {"type": "wizardreal:sound", "sound": "minecraft:entity.generic.explode"},
        ],
    }


SUPERSEDED = ["aegis", "arcanum", "explosion", "fulmen", "gaia", "ictus", "ignis",
              "mare", "mortis", "sanctus", "semina", "tempest", "umbra", "ventus", "vitae"]


def main():
    SPELLS_DIR.mkdir(parents=True, exist_ok=True)
    removed = []
    for name in SUPERSEDED:
        p = SPELLS_DIR / f"{name}.json"
        if p.exists():
            p.unlink()
            removed.append(name)
    written = []
    for doc in BATCH:
        if doc["id"] == "wizardreal:explosion":
            doc = build_explosion_doc()
        p = SPELLS_DIR / f"{doc['id'].split(':')[1]}.json"
        p.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
        written.append(doc["id"])
    print(f"removed {len(removed)} superseded: {', '.join(removed)}")
    print(f"wrote {len(written)} batch-0 spells:")
    for w in written:
        print(" ", w)


if __name__ == "__main__":
    main()
