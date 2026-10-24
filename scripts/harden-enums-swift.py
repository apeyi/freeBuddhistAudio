#!/usr/bin/env python3
"""Give each response enum in the generated Swift v2 client lenient decoding:
an unrecognised value (one a newer server added) decodes to `.unknown` instead
of throwing and failing the whole response. Request-only enums (no `unknown`
case) are left untouched. Idempotent. Usage: harden-enums-swift.py <models-dir>"""
import sys, re, pathlib
models = pathlib.Path(sys.argv[1]); patched = []
for f in sorted(models.glob("*.swift")):
    s = f.read_text()
    if 'case unknown = "unknown"' not in s or "init(from decoder:" in s:
        continue
    m = re.search(r'public enum (\w+): String, Codable', s)
    if not m: continue
    name = m.group(1)
    init = (f'\n    public init(from decoder: Decoder) throws {{\n'
            f'        let raw = try decoder.singleValueContainer().decode(String.self)\n'
            f'        self = {name}(rawValue: raw) ?? .unknown\n'
            f'    }}\n')
    i = s.rstrip().rfind('}')              # insert before the enum's closing brace
    s = s[:i] + init + s[i:]
    f.write_text(s); patched.append(name)
print("hardened swift enums:", ", ".join(patched) if patched else "(none)")
