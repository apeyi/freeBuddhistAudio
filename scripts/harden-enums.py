#!/usr/bin/env python3
"""Post-process the generated v2 client so response enums decode leniently.

Every response enum in the contract carries an `unknown` member; kotlinx throws
on a value it doesn't know and fails the WHOLE response, so a new server enum
value would blank a screen on installed builds. This rewrites each such enum to
use a custom serializer that maps an unrecognised value to <Enum>.UNKNOWN,
reusing the generator's own decode(). Request-only enums (no UNKNOWN member) are
left untouched — the client must never send `unknown`.

Idempotent; run from generate-api-client.sh after generation.
Usage: harden-enums.py <models-dir>
"""
import sys, re, pathlib

models = pathlib.Path(sys.argv[1])
patched = []
for f in sorted(models.glob("*.kt")):
    s = f.read_text()
    if 'UNKNOWN("unknown")' not in s:      # response enums only
        continue
    m = re.search(r'@Serializable\s*\nenum class (\w+)\(', s)
    if not m or f'{m.group(1)}Serializer' in s:
        continue
    name = m.group(1)
    s = s.replace(f'@Serializable\nenum class {name}(',
                  f'@Serializable(with = {name}Serializer::class)\nenum class {name}(', 1)
    s = s.replace(
        'import kotlinx.serialization.Serializable\n',
        'import kotlinx.serialization.Serializable\n'
        'import kotlinx.serialization.KSerializer\n'
        'import kotlinx.serialization.descriptors.PrimitiveKind\n'
        'import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor\n'
        'import kotlinx.serialization.descriptors.SerialDescriptor\n'
        'import kotlinx.serialization.encoding.Decoder\n'
        'import kotlinx.serialization.encoding.Encoder\n', 1)
    s = s.rstrip() + f'''

/**
 * Lenient decoding (API contract requirement): a value this build doesn't know —
 * e.g. an enum value a newer server added — decodes to {name}.UNKNOWN instead of
 * failing the whole response. Response-only; UNKNOWN is never serialized out.
 */
object {name}Serializer : KSerializer<{name}> {{
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("{name}", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): {name} =
        {name}.decode(decoder.decodeString()) ?: {name}.UNKNOWN
    override fun serialize(encoder: Encoder, value: {name}) =
        encoder.encodeString(value.value)
}}
'''
    f.write_text(s)
    patched.append(name)
print("hardened enums:", ", ".join(patched) if patched else "(none)")
