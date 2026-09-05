import re, glob

for f in sorted(glob.glob('app/src/main/java/com/hermes/noir/*.java') + glob.glob('app/src/test/java/com/hermes/noir/*.java')):
    s = open(f, encoding='utf-8').read()
    s = re.sub(r'"(?:\\.|[^"\\])*"', '', s)
    s = re.sub(r"'(?:\\.|[^'\\])*'", '', s)
    s = re.sub(r'//.*', '', s)
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    b = s.count('{') - s.count('}')
    p = s.count('(') - s.count(')')
    status = 'OK' if b == 0 and p == 0 else 'MISMATCH'
    print(f'{status}  {f}  braces={b} parens={p}')
