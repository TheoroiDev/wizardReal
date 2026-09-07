import json

p = 'spell.schema.json'
s = open(p, encoding='utf-8').read()

old_anyof_tail = '          { "$ref": "#/$defs/effects/particles" },\n          { "$ref": "#/$defs/effects/addon" }'
new_anyof_tail = '''          { "$ref": "#/$defs/effects/particles" },
          { "$ref": "#/$defs/effects/hex" },
          { "$ref": "#/$defs/effects/bind" },
          { "$ref": "#/$defs/effects/pull" },
          { "$ref": "#/$defs/effects/blink" },
          { "$ref": "#/$defs/effects/surface" },
          { "$ref": "#/$defs/effects/barrier" },
          { "$ref": "#/$defs/effects/summon" },
          { "$ref": "#/$defs/effects/excavate" },
          { "$ref": "#/$defs/effects/harvest" },
          { "$ref": "#/$defs/effects/smelt" },
          { "$ref": "#/$defs/effects/visual" },
          { "$ref": "#/$defs/effects/weather" },
          { "$ref": "#/$defs/effects/light" },
          { "$ref": "#/$defs/effects/addon" }'''
count = s.count(old_anyof_tail)
assert count == 2, f"anyOf tail found {count} times"
s = s.replace(old_anyof_tail, new_anyof_tail)

new_defs = r'''
      "hex": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type", "effect"],
        "properties": {
          "type": { "const": "wizardreal:hex" },
          "effect": { "$ref": "#/$defs/resourceLocation", "description": "Mob effect id applied to enemies (never the caster)." },
          "amplifier": { "type": "integer", "minimum": 0, "default": 0 },
          "duration": { "type": "integer", "minimum": 1, "default": 100 },
          "range": { "type": "number", "minimum": 0, "default": 8, "description": "Cone length when angle_cos is set, else the default radius." },
          "angle_cos": { "type": "number", "minimum": -1, "maximum": 1, "description": "Set to target a look cone instead of a sphere." },
          "radius": { "type": "number", "minimum": 0, "description": "Sphere radius override (defaults to range)." },
          "at_target": { "type": "boolean", "default": false, "description": "Centre the sphere on the aimed point instead of the caster." },
          "mob_filter": { "type": "string", "description": "Optional entity-type tag id (e.g. minecraft:undead) filtering targets." }
        }
      },
      "bind": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type"],
        "properties": {
          "type": { "const": "wizardreal:bind" },
          "duration": { "type": "integer", "minimum": 1, "default": 60 },
          "range": { "type": "number", "minimum": 0, "default": 16 },
          "angle_cos": { "type": "number", "minimum": -1, "maximum": 1, "default": 0.75 },
          "group": { "type": "boolean", "default": false, "description": "true = root every cone target; false = nearest single target." }
        },
        "description": "Movement lock (speed 0 + jump lock); boss-grade targets (max health >= 100) are immune."
      },
      "pull": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type"],
        "properties": {
          "type": { "const": "wizardreal:pull" },
          "range": { "type": "number", "minimum": 0, "default": 6 },
          "strength": { "type": "number", "minimum": 0, "default": 0.8 },
          "include_items": { "type": "boolean", "default": true }
        },
        "description": "Velocity-impulse drag of entities (and dropped items) toward the caster."
      },
      "blink": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type"],
        "properties": {
          "type": { "const": "wizardreal:blink" },
          "distance": { "type": "number", "minimum": 0, "default": 6 }
        },
        "description": "Marches the caster's bounding box along the look vector; lands at the farthest collision-free spot, fizzles without teleporting when none exists."
      },
      "surface": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type", "block"],
        "properties": {
          "type": { "const": "wizardreal:surface" },
          "block": { "$ref": "#/$defs/resourceLocation" },
          "shape": { "enum": ["circle", "line"], "default": "circle" },
          "radius": { "type": "number", "minimum": 0, "default": 2 },
          "duration_ticks": { "type": "integer", "minimum": 1, "default": 200 }
        },
        "description": "Temporary ground cover on replaceable surface only; every block restores itself on expiry."
      },
      "barrier": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type", "block"],
        "properties": {
          "type": { "const": "wizardreal:barrier" },
          "block": { "$ref": "#/$defs/resourceLocation" },
          "shape": { "enum": ["wall", "ring", "cage"], "default": "wall" },
          "width": { "type": "integer", "minimum": 1, "default": 3 },
          "height": { "type": "integer", "minimum": 1, "default": 3 },
          "radius": { "type": "number", "minimum": 0, "default": 2 },
          "duration_ticks": { "type": "integer", "minimum": 1, "default": 200 }
        },
        "description": "Temporary structure perpendicular to the look direction (wall), a circle of pillars (ring) or a spherical shell around the aimed point (cage); restores on expiry."
      },
      "summon": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type", "entity"],
        "properties": {
          "type": { "const": "wizardreal:summon" },
          "entity": { "$ref": "#/$defs/resourceLocation" },
          "count": { "type": "integer", "minimum": 1, "default": 1 },
          "duration_ticks": { "type": "integer", "minimum": 1, "default": 600 },
          "follow": { "type": "boolean", "default": true, "description": "Tameable types are tamed to the caster; hostile types are pointed at the nearest monster." }
        },
        "description": "Temporary summons discarded with a poof on expiry (hard cap 6 per cast)."
      },
      "excavate": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type"],
        "properties": {
          "type": { "const": "wizardreal:excavate" },
          "shape": { "enum": ["tunnel", "area"], "default": "tunnel" },
          "length": { "type": "number", "minimum": 0, "default": 8 },
          "radius": { "type": "number", "minimum": 0, "default": 1 }
        },
        "description": "Mining magic: a walkable corridor along the look vector or a sphere scoop at the aimed point; unbreakable blocks and liquids skipped (256-block cap)."
      },
      "harvest": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type"],
        "properties": {
          "type": { "const": "wizardreal:harvest" },
          "radius": { "type": "number", "minimum": 0, "default": 3 },
          "mode": { "enum": ["harvest", "grow"], "default": "harvest" },
          "replant": { "type": "boolean", "default": true }
        },
        "description": "Farming magic: break mature crops (replant at age 0) or bonemeal-rush growables around the aimed point."
      },
      "smelt": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type"],
        "properties": {
          "type": { "const": "wizardreal:smelt" },
          "radius": { "type": "number", "minimum": 0, "default": 1.5 }
        },
        "description": "Touch-smelting: ore/smelt blocks around the aimed point pop their furnace product + XP."
      },
      "visual": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type", "particle"],
        "properties": {
          "type": { "const": "wizardreal:visual" },
          "particle": {
            "oneOf": [
              { "$ref": "#/$defs/resourceLocation", "description": "Simple (no-argument) particle id." },
              {
                "type": "object",
                "additionalProperties": false,
                "required": ["dust"],
                "properties": {
                  "dust": { "type": "array", "items": { "type": "number" }, "minItems": 3, "maxItems": 3 },
                  "dust_scale": { "type": "number", "default": 1 }
                }
              }
            ]
          },
          "shape": { "enum": ["ring", "helix", "pillar", "burst", "trail", "cone", "cross", "orbit"], "default": "ring" },
          "radius": { "type": "number", "minimum": 0, "default": 1.5 },
          "count": { "type": "integer", "minimum": 1, "default": 30 },
          "duration_ticks": { "type": "integer", "minimum": 1, "default": 20 },
          "y_offset": { "type": "number", "default": 1 },
          "at_point": { "type": "boolean", "default": false, "description": "true = centre on the aimed point instead of the caster." }
        },
        "description": "Choreographed spell visuals played back over duration_ticks (magic_eco 05)."
      },
      "weather": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type"],
        "properties": {
          "type": { "const": "wizardreal:weather" },
          "mode": { "enum": ["rain", "thunder", "clear"], "default": "rain" },
          "duration_seconds": { "type": "integer", "minimum": 1, "default": 120 }
        }
      },
      "light": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type"],
        "properties": {
          "type": { "const": "wizardreal:light" },
          "glow_range": { "type": "number", "minimum": 0, "default": 0, "description": "0 = no glow marking." },
          "glow_duration": { "type": "integer", "minimum": 1, "default": 100 },
          "night_vision_seconds": { "type": "integer", "minimum": 0, "default": 0, "description": "0 = no night vision." }
        },
        "description": "Perception toolbox: Glowing marking + caster Night Vision."
      },
      "addon": {'''

old_addon = '\n      "addon": {'
assert s.count(old_addon) == 1, s.count(old_addon)
s = s.replace(old_addon, new_defs)

json.loads(s)  # validate
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print("schema extended OK")
