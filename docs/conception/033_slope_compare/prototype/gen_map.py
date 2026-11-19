import json, math, sys
src, out = sys.argv[1], sys.argv[2]
E = json.load(open(src, encoding='utf-8'))['elements']
lat0 = sum(p['lat'] for x in E for p in x['geometry']) / sum(len(x['geometry']) for x in E)
k = math.cos(math.radians(lat0))
def proj(p): return (p['lon'] * 111320 * k, -p['lat'] * 110540)
feats = []
for x in E:
    t = x['tags']; pts = [proj(p) for p in x['geometry']]
    feats.append({'id': x['id'], 'kind': 'lift' if 'aerialway' in t else 'piste',
                  'lift': t.get('aerialway'), 'diff': t.get('piste:difficulty'),
                  'name': t.get('name') or '', 'pts': pts})
# rotate so downhill (piste start->end) points down
vx = sum(f['pts'][-1][0] - f['pts'][0][0] for f in feats if f['kind'] == 'piste')
vy = sum(f['pts'][-1][1] - f['pts'][0][1] for f in feats if f['kind'] == 'piste')
ang = math.pi / 2 - math.atan2(vy, vx)
c, s = math.cos(ang), math.sin(ang)
for f in feats:
    f['pts'] = [(x * c - y * s, x * s + y * c) for x, y in f['pts']]
xs = [p[0] for f in feats for p in f['pts']]; ys = [p[1] for f in feats for p in f['pts']]
mx, my = min(xs), min(ys); W, H = max(xs) - mx, max(ys) - my
sc = 1000 / max(W, H)
for f in feats:
    f['len'] = round(sum(math.dist(a, b) for a, b in zip(f['pts'], f['pts'][1:])))
    f['pts'] = [[round((x - mx) * sc + 40, 1), round((y - my) * sc + 40, 1)] for x, y in f['pts']]
data = {'w': round(W * sc + 80), 'h': round(H * sc + 80), 'feats': feats}
html = open(sys.argv[3], encoding='utf-8').read().replace('__DATA__', json.dumps(data, ensure_ascii=False))
open(out, 'w', encoding='utf-8').write(html)
print(data['w'], data['h'], len(feats), len(html))
