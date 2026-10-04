import csv, json, re, difflib, collections, os, sys
import xlsx

# usage: python3 tools/data/build.py <folder with Debug_Essences, Debug_InfinityBlocks, Debug_Keys and the cheat sheet xlsx>
HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)

SRC = sys.argv[1] if len(sys.argv) > 1 else '../../this_will_be_deleted'
OUT = '../../data'
report = collections.defaultdict(list)

ITEMS = {i.split(':', 1)[1] for i in json.load(open('items.json'))}

# price text -> degg range
UNIT = {'e': 0.25, 'ember': 0.25, 'embers': 0.25,
        'd': 1, 'degg': 1, 'deggs': 1,
        's': 64, 'stegg': 64, 'steggs': 64,
        'sh': 1728, 'shegg': 1728, 'sheggs': 1728,
        'ch': 46656, 'chegg': 46656, 'cheggs': 46656}

def price_range(text):
    if not text:
        return None
    t = text.lower().replace(',', '.').strip()
    upper_only = 'or less' in t or 'less than' in t
    t = t.replace('or less', '').replace('less than', '').strip()
    m = re.fullmatch(r'(\d+(?:\.\d+)?)\s*([a-z]*)\s*(?:-\s*(\d+(?:\.\d+)?)\s*([a-z]*))?', t)
    if not m:
        return None
    a, ua, b, ub = m.groups()
    unit = ub or ua
    if unit not in UNIT:
        return None
    lo = float(a) * UNIT[ua or unit]
    hi = float(b) * UNIT[unit] if b else lo
    whole = lambda x: int(x) if x == int(x) else x
    return [0 if upper_only else whole(lo), whole(hi)]

def tier(letter, text):
    t = {'price': text.strip()}
    r = price_range(text)
    if r:
        t['deggs'] = r
    return t

# names
def norm(s):
    return re.sub(r'[^a-z0-9]', '', s.lower())

def ident(s):
    return re.sub(r'_+', '_', re.sub(r'[^a-z0-9]+', '_', s.lower())).strip('_')

# sheet typos and chatter
TYPOS = {'Meele': 'Melee', 'Recieved': 'Received', 'Likeley': 'Likely', 'GrantsStrength': 'Grants Strength', 'Rabbid': 'Rabid'}
JUNK = {'?', '???', 'wdym'}
REWORD = {
    'Cursed af. ': '',
    ' --ABSORPTION 1  BUGGED, DOES NOT WORK': ', level 1 bugged',
    'only if you have NONE': 'only if you have none',
}

def clean(v):
    if isinstance(v, list):
        v = [clean(x) for x in v]
        v = [x for x in v if x is not None]
        return v or None
    if not isinstance(v, str):
        return v
    v = v.strip()
    if v in JUNK:
        return None
    for a, b in REWORD.items():
        v = v.replace(a, b)
    for a, b in TYPOS.items():
        v = re.sub(rf'\b{a}\b', b, v)
    return v

def num(v):
    v = clean(v)
    if v is None:
        return None
    try:
        f = float(v)
        return int(f) if f.is_integer() else f
    except ValueError:
        return v  # keeps things like "4?" or "3(?)"


# essences: explanations
TYPE = {'⚔': 'weapon_tool', '👕': 'armor', '✨': 'spell'}
expl = {}
for row in list(csv.reader(open(f'{SRC}/Debug_Essences/explanations.csv')))[1:]:
    if not row or not row[0].strip():
        continue
    name, types, levels, desc = (row + ['', '', '', ''])[:4]
    name = clean(name)
    expl[norm(name)] = {
        'name': name,
        'applies_to': [TYPE[c] for c in types if c in TYPE],
        'levels': levels.strip() or None,
        'description': clean(desc) or None,
    }

# essences: prices sheet
KEY_OF = {'tempest': 'tempest', 'paradox': 'paradox', 'dimension': 'dimensional', 'inferno': 'inferno', 'chaos': 'chaos'}
def key_from(label):
    l = label.lower()
    for k, v in KEY_OF.items():
        if k in l:
            return v
    return None

grid = list(csv.reader(open(f'{SRC}/Debug_Essences/prices.csv')))
ess_tiers = {}
prices = {}
section = None
for row in grid:
    row = row + [''] * 17
    if row[0].strip() and 'Key Essences' in row[0]:
        section = key_from(row[0])
    letter, text = row[12].strip(), row[11].strip()
    if letter and letter != 'Quality Tiers' and text and letter not in ess_tiers and re.fullmatch(r'[A-Z]+', letter):
        ess_tiers[letter] = tier(letter, text)
    name = clean(row[1]) or ''
    if not name or section is None:
        continue
    cap = num(row[2].strip() or None)
    levels, note = {}, None
    for i, v in enumerate(row[3:8], 1):
        v = v.strip()
        if not v:
            continue
        if re.fullmatch(r'[A-Z]+( close to [A-Z]+)?', v):
            levels[str(i)] = v
        elif v.startswith('('):
            # per level stuff like "(no ess form)" or "(can stack to 4)"
            levels[str(i)] = v.strip('()')
        else:
            note = v if note is None else note + '; ' + v
    prices[norm(name)] = {'name': name, 'key': section, 'cap': cap, 'price': levels or None,
                          'price_note': note, 'spell_cost': row[8].strip() or None}

# essences: cheat sheet
sheets = xlsx.read(f'{SRC}/Chaos_Update___Essence_Cheat_Sheet.xlsx')
cheat = {}
layouts = {
    'buff':   dict(cap='C', gcap='D', req='EFG', eff='HIJ', item='K', trig=None, desc='L'),
    'attack': dict(cap='C', gcap='D', req='EFG', eff='HIJ', item='K', trig='L', desc='M'),
    'spell':  dict(cap='C', gcap=None, req='DEF', eff='GHIJKL', item='M', trig=None, desc='N'),
}
for sname, sh in sheets.items():
    if sh['hidden']:
        continue
    kind = 'buff' if sname.startswith('Buffs') else 'attack' if sname.startswith('Attacks') else 'spell' if sname.startswith('Spells') else None
    if not kind:
        continue
    L = layouts[kind]
    k = key_from(sname)
    for rn, r in sorted(sh['rows'].items()):
        if rn < 3 or not r.get('B'):
            continue
        e = {'name': clean(r['B']), 'kind': kind, 'key': k,
             'max_level': num(r.get(L['cap'])),
             'global_cap': num(r.get(L['gcap'])) if L['gcap'] else None,
             'requirements': clean([r[c] for c in L['req'] if r.get(c)]),
             'effects': clean([r[c] for c in L['eff'] if r.get(c)]),
             'item_type': clean(r.get(L['item'])),
             'trigger': clean(r.get(L['trig'])) if L['trig'] else None,
             'details': clean(r.get(L['desc']))}
        if norm(e['name']) in cheat:
            report['cheat sheet duplicate (kept first)'].append(f"{e['name']} ({sname})")
            continue
        cheat[norm(e['name'])] = e

trig_rows = sheets['Triggers']['rows']
triggers = {}
for col, name in trig_rows[1].items():
    triggers[name.lower().replace(' ', '_')] = {
        'name': name,
        'meaning': clean(trig_rows.get(2, {}).get(col)),
        'includes': clean([r[col] for rn, r in sorted(trig_rows.items()) if rn >= 3 and r.get(col)]),
    }

# merge, fuzzy for spelling differences between the sheets
def resolve(pool, target_keys, label):
    mapping = {}
    for k in pool:
        if k in target_keys:
            mapping[k] = k
            continue
        best = difflib.get_close_matches(k, target_keys, n=1, cutoff=0.88)
        if best:
            mapping[k] = best[0]
            report[f'fuzzy matched {label}'].append(f'{pool[k]["name"]} -> {best[0]}')
        else:
            mapping[k] = k
    return mapping

base = dict(expl)
pm = resolve(prices, list(base), 'prices->explanations')
for k, p in prices.items():
    tgt = pm[k]
    if tgt not in base:
        report['in prices, not in explanations'].append(p['name'])
        base[tgt] = {'name': p['name']}
    base[tgt]['_price'] = p
# dimensional buffs sheet: descriptions sit one row too high from frost armor down
if {'frostarmor', 'heightenedsenses', 'reflectmeleeonblock'} <= cheat.keys():
    cheat['heightenedsenses']['details'], cheat['reflectmeleeonblock']['details'], cheat['frostarmor']['details'] = \
        cheat['frostarmor']['details'], cheat['heightenedsenses']['details'], None
# same essence, different wording in the cheat sheet
CHEAT_SAME = {'reflectmeeleonblock': 'reflectonmeleeblock', 'increasedhealdamagepotion': 'increasedhealharmpotion'}
for a, b in CHEAT_SAME.items():
    if a in cheat:
        cheat[b] = cheat.pop(a)
cm = resolve(cheat, list(base), 'cheat->explanations')
for k, c in cheat.items():
    tgt = cm[k]
    if tgt not in base:
        # cheat sheet only, not a real essence
        report['cheat sheet only (dropped)'].append(c['name'])
        continue
    base[tgt]['_cheat'] = c

# seen in dumps, missing from the sheets
EXTRA = []
# names that are spelled differently in game, in the sheets and in CallMePete's essence book
ALSO_CALLED = {'Bee Aggression': ['Increased Bee Aggression'], 'Rabid Rabbits': ['Rabbid Rabbits'],
               'Increased Dagger Replenish': ['Increase Dagger Replenish'],
               'Increased Maximum Daggers': ['Increased Max Daggers'],
               'Back Claymore': ['Black Claymore'], 'Increased Heal & Harm Potion': ['Increased Heal & Harm Potions'],
               'Santa Rage': ['Santas Rage'], 'Shadow Fangs': ['Shadow Fang'],
               'Summon Dimension Anomaly': ['Summon Dimensional Anomaly'], 'Telekinesis': ['Telekenisis']}
for x in EXTRA:
    base[norm(x['name'])] = {'name': x['name'], '_extra': x}

# what one level actually does, only where a number is known. "0" = essence without levels
ROMAN = ['', 'I', 'II', 'III', 'IV', 'V']
LEVEL_TEXT = {
    'Backstab': {str(n): f'+{0.8 * n:g} damage multiplier from behind' for n in (1, 2)},
    'Life Steal': {'1': '0.5 hearts per full hit', '2': '1 heart per full hit'},
    'Starve': {'1': '0.5 hunger per full hit'},
    'Heightened Senses': {str(n): f'{5 * n} blocks' for n in (1, 2, 3, 4)},
    'Untouchable': {str(n): f'Mining Fatigue {ROMAN[n]} on the attacker, about 4 s' for n in (1, 2, 3, 4)},
    'Anti-Mage': {str(n): f'{10 * n}% less magic damage' for n in (1, 2, 3)},
    'Absorption': {'1': 'Bugged', '2': '2 absorption hearts every 4 s', '3': '4 absorption hearts every 4 s'},
    'Sneak Fortification': {str(n): '1 absorption heart per second while sneaking, up to 6' for n in (1, 2)},
    # measured in captures, the sheet says longer
    'Wither': {'3': 'Wither III for about 3 s'},
    'Poison': {'3': 'Poison III for about 2 s'},
    'Cripple': {'3': 'Slowness III and Weakness III for 5 s'},
    'Magic Disrupt': {'3': 'No spells for about 2.5 s'},
    'Reduce Heal': {'0': 'Healing halved'},
}
for soul in ('Beast', 'Blood', 'Dimension', 'Dragon', 'Fire', 'Ice', 'Nature', 'Orc', 'Shadow', 'Undead'):
    LEVEL_TEXT[f'{soul} Soul'] = {str(n): f'+{n} {soul.lower()} soul{"s" if n > 1 else ""}' for n in (1, 2, 3)}
# these give the vanilla effect at the same level
for name, effect, top in (('Strength', 'Strength', 4), ('Jump', 'Jump Boost', 5), ('Speed', 'Speed', 5),
                          ('Haste', 'Haste', 2), ('Regeneration', 'Regeneration', 3)):
    LEVEL_TEXT[name] = {str(n): f'{effect} {ROMAN[n]}' for n in range(1, top + 1)}

# melee damage essences. unknown = adds damage but nobody has the number, situational = only in some cases
DAMAGE = {'Increased Melee Damage': 'unknown', 'Critical Plus': 'unknown', 'Healthy Strike': 'unknown', 'Bloodlust': 'unknown',
          'Backstab': 'situational', 'Sage Slayer': 'situational', 'Arrow Strike': 'situational', 'Mounted Damage': 'situational'}

essences = []
used_level_text = set()
for k, e in sorted(base.items(), key=lambda kv: kv[1]['name'].lower()):
    p, c = e.get('_price', {}), e.get('_cheat', {})
    lv = e.get('levels')
    range_max = int(lv.split('-')[-1]) if lv and re.fullmatch(r'\d+(-\d+)?', lv) else None
    caps = {'explanations': range_max, 'prices': p.get('cap'), 'cheat_sheet': c.get('max_level')}
    known = {s: v for s, v in caps.items() if v is not None}
    ints = {v for v in known.values() if isinstance(v, int)}
    if len(ints) > 1:
        report['max level disagrees'].append(f"{e['name']}: {known}")
    max_level = p.get('cap') if isinstance(p.get('cap'), int) else range_max if range_max else c.get('max_level')
    if p.get('key') and c.get('key') and p['key'] != c['key']:
        report['key disagrees'].append(f"{e['name']}: prices={p['key']} cheat={c['key']}")
    x = e.get('_extra', {})
    out = {
        'id': ident(e['name']),
        'name': e['name'],
        'also_called': ALSO_CALLED.get(e['name']),
        'key': p.get('key') or c.get('key'),
        'kind': c.get('kind') or x.get('kind'),
        'applies_to': e.get('applies_to') or None,
        'levels': lv,
        'max_level': max_level,
        'global_cap': c.get('global_cap'),
        'description': e.get('description'),
        'details': c.get('details'),
        'level_text': LEVEL_TEXT.get(e['name']),
        'damage': DAMAGE.get(e['name']),
        'requirements': c.get('requirements'),
        'effects': c.get('effects'),
        'item_type': c.get('item_type'),
        'trigger': c.get('trigger'),
        'spell_cost': p.get('spell_cost'),
        'price': p.get('price'),
        'price_note': p.get('price_note'),
        'max_level_conflict': known if len(ints) > 1 else None,
        'status': 'confirmed' if x else 'unverified',
    }
    if x.get('min_level_seen'):
        out['max_level'] = f"{x['min_level_seen']}+"
    used_level_text.add(e['name'])
    essences.append({k2: v for k2, v in out.items() if v is not None})

report['level text for an unknown essence'] = sorted(set(LEVEL_TEXT) - used_level_text)
ids = collections.Counter(x['id'] for x in essences)
report['duplicate ids'] = [i for i, n in ids.items() if n > 1]

json.dump({
    'schema_version': 1,
    'received': '2026-09-29',
    'statuses': {'unverified': 'from the sheets', 'confirmed': 'matches a dump'},
    'notes': [
        'max_level: one book, global_cap: all gear',
        'price letters are price_tiers.essence in prices.json',
    ],
    'triggers': triggers,
    'essences': essences,
}, open(f'{OUT}/essences.json', 'w'), ensure_ascii=False, indent=1)

# blocks
BLOCKS = {i.split(':', 1)[1] for i in json.load(open('blocks.json'))}
TYPOS = {'mycellium': 'mycelium', 'beenest': 'bee_nest', 'chizeled': 'chiseled', 'lapiz': 'lapis',
         'grey': 'gray', 'netherack': 'netherrack', 'amethlyst': 'amethyst'}
SPECIAL = {
    'jack_olantern': 'jack_o_lantern', 'netherite': 'netherite_block', 'hay_bale': 'hay_block',
    'drip_stone': 'dripstone_block', 'smooth_quartz_block': 'smooth_quartz', 'glass_pane': 'glass_pane',
    'lapis_lazuli_ore': 'lapis_ore', 'deepslate_lapis_lazuli_ore': 'deepslate_lapis_ore',
    'block_of_lapis_lazuli': 'lapis_block', 'waxed_block_of_copper': 'waxed_copper_block',
}

def item_id(name):
    n = ident(re.sub(r'\s+', ' ', name.strip().lower()).replace("'", ''))
    for bad, good in TYPOS.items():
        n = n.replace(bad, good)
    if n in SPECIAL:
        return SPECIAL[n]
    m = re.fullmatch(r'block_of_(.+)', n)
    if m:
        n = m.group(1) + '_block'
    m = re.fullmatch(r'(.+)_glass_pane', n)
    if m and not n.endswith('stained_glass_pane'):
        n = m.group(1) + '_stained_glass_pane'
    for cand in (n, n + '_block', re.sub(r'_block$', '', n)):
        if cand in ITEMS:
            return cand
    return None

bgrid = list(csv.reader(open(f'{SRC}/Debug_InfinityBlocks/prices-1.csv')))
block_tiers, blocks, notes = {}, [], []
category = None
for row in bgrid:
    row = row + [''] * 12
    name, rating = row[1].strip(), row[2].strip()
    side_text, side_letter = row[4].strip(), row[5].strip()
    if side_letter and re.fullmatch(r'[A-Z]+', side_letter) and side_text and side_letter not in block_tiers:
        block_tiers[side_letter] = tier(side_letter, side_text)
    if side_text.lower().startswith('all '):
        notes.append(side_text)
    if not name:
        continue
    if not rating:
        category = {'Amethlyst': 'Amethyst', 'Netherack/brick': 'Netherrack', 'Glass block': 'Glass'}.get(name.strip(), name.strip())
        continue
    iid = item_id(name)
    if iid and any(b.get('item') == f'minecraft:{iid}' and b['tier'] == rating for b in blocks):
        report['block listed twice (dropped)'].append(name)
        continue
    if iid is None:
        report['blocks with no item id'].append(name)
    unsure = None
    if iid and iid not in BLOCKS:
        report['mapped to an item that isnt a block'].append(f'{name} -> {iid}')
        unsure = f'not placeable, maybe {iid}_block'

    blocks.append({k: v for k, v in {
        'name': iid.replace('_', ' ').title() if iid else re.sub(r'\s+', ' ', name.strip()),
        'item': f'minecraft:{iid}' if iid else None,
        'category': category,
        'tier': rating,
        'unsure': unsure,
    }.items() if v is not None})

# keys
keys = []
for row in list(csv.reader(open(f'{SRC}/Debug_Keys/prices-2.csv')))[1:]:
    row = row + ['', '', '']
    if row[0].strip() and row[1].strip():
        k = {'name': row[0].strip(), 'price': row[1].strip()}
        r = price_range(row[1])
        if r:
            k['deggs'] = r
        else:
            report['key price not parsed'].append(row[1])
        keys.append(k)

for t in list(ess_tiers.values()) + list(block_tiers.values()):
    if 'deggs' not in t:
        report['tier price not parsed (fine if its not a number)'].append(t['price'])

json.dump({
    'schema_version': 1,
    'received': '2026-09-29',
    'unit': 'degg',
    'notation': {'ember': 0.25, 'd': 1, 's': 64, 'sh': 1728, 'ch': 46656},
    'notes': [
        'deggs: [min, max], min 0 = or less',
        'essence and block letters are different scales',
    ],
    'price_tiers': {'essence': ess_tiers, 'block': block_tiers},
    'keys': keys,
    'block_notes': notes + ['bluename infinity blocks can be worth more'],
    'blocks': blocks,
    # the chart prices these as a group, not one by one. deggs [min, max] by the end of the item id. fences and gates count as walls
    'block_groups': {'slab': [20, 32], 'stairs': [20, 32], 'wall': [25, 32], 'fence': [25, 32], 'gate': [25, 32]},
    # deggs to go up to that level, sum from current + 1 to target
    'sharpen_cost': dict(zip(range(1, 31), [1, 2, 3, 4, 6, 7, 9, 11, 13, 16, 18, 21, 23, 27, 31, 34, 38, 42, 47, 52,
                                            57, 62, 68, 74, 80, 87, 94, 100, 108, 116])),
}, open(f'{OUT}/prices.json', 'w'), ensure_ascii=False, indent=1)

json.dump(report, open(os.path.join(OUT, '..', 'tools', 'data', 'last_report.json'), 'w'), ensure_ascii=False, indent=1)
print('essences', len(essences), 'blocks', len(blocks), 'keys', len(keys))
print('essence tiers', list(ess_tiers), 'block tiers', list(block_tiers))
for k, v in report.items():
    print(f'-- {k}: {len(v)}')
