import re, html, zipfile

def col_index(letters):
    n = 0
    for ch in letters:
        n = n * 26 + ord(ch) - 64
    return n - 1

def read(path):
    z = zipfile.ZipFile(path)
    ss = []
    if 'xl/sharedStrings.xml' in z.namelist():
        x = z.read('xl/sharedStrings.xml').decode()
        for si in re.findall(r'<si>(.*?)</si>', x, re.S):
            ss.append(html.unescape(''.join(re.findall(r'<t[^>]*>(.*?)</t>', si, re.S))))
    wb = z.read('xl/workbook.xml').decode()
    rels = z.read('xl/_rels/workbook.xml.rels').decode()
    relmap = dict(re.findall(r'Id="([^"]+)"[^>]*Target="([^"]+)"', rels))
    relmap.update({a: b for b, a in re.findall(r'Target="([^"]+)"[^>]*Id="([^"]+)"', rels)})
    sheets = {}
    for m in re.finditer(r'<sheet ([^>]*)/>', wb):
        attrs = dict(re.findall(r'([\w:]+)="([^"]*)"', m.group(1)))
        target = relmap[attrs['r:id']].lstrip('/')
        if not target.startswith('xl/'):
            target = 'xl/' + target
        x = z.read(target).decode()
        rows = {}
        for rm in re.finditer(r'<row [^>]*r="(\d+)"[^>]*>(.*?)</row>', x, re.S):
            r = {}
            for c in re.finditer(r'<c r="([A-Z]+)\d+"([^>]*?)(?:/>|>(.*?)</c>)', rm.group(2), re.S):
                body = c.group(3) or ''
                t = re.search(r't="(\w+)"', c.group(2))
                t = t.group(1) if t else None
                if t == 'inlineStr':
                    val = html.unescape(''.join(re.findall(r'<t[^>]*>(.*?)</t>', body, re.S)))
                else:
                    v = re.search(r'<v>(.*?)</v>', body, re.S)
                    if not v:
                        continue
                    val = html.unescape(v.group(1))
                    if t == 's':
                        val = ss[int(val)]
                val = val.strip()
                if val:
                    r[c.group(1)] = val
            if r:
                rows[int(rm.group(1))] = r
        sheets[attrs['name']] = {'hidden': attrs.get('state') == 'hidden', 'rows': rows}
    return sheets
