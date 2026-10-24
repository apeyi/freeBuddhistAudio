#!/usr/bin/env bash
# Regenerate the vendored iOS v2 API client (Swift 5) from the OpenAPI spec.
# Spec (docs/openapi-v2.yaml) is gitignored; get the current draft from FBA. JDK 17+.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SPEC="$ROOT/docs/openapi-v2.yaml"
JAR="${OPENAPI_GENERATOR_JAR:-$ROOT/.cache/openapi-generator-cli.jar}"
OUT="$(mktemp -d)"; mkdir -p "$(dirname "$JAR")"
if [ ! -f "$JAR" ]; then
  VER="$(curl -s https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/maven-metadata.xml | grep -oE '<release>[^<]+' | sed 's/<release>//')"
  curl -s -o "$JAR" "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/$VER/openapi-generator-cli-$VER.jar"
fi
java -jar "$JAR" validate -i "$SPEC"
java -jar "$JAR" generate -g swift5 -i "$SPEC" -o "$OUT" \
  --model-name-prefix V2 \
  --openapi-normalizer FILTER="tag:service|catalogue|search|auth|me" \
  --additional-properties=projectName=FBAApi,responseAs=AsyncAwait,useSPMFileStructure=true
DEST="$ROOT/FBAudio-iOS/FBAudio/Api"
rm -rf "$DEST"; mkdir -p "$DEST"
cp -r "$OUT/Sources/FBAApi/"* "$DEST/"
python3 "$ROOT/scripts/harden-enums-swift.py" "$DEST/Models"
echo "Regenerated $(find "$DEST" -name '*.swift' | wc -l) files into $DEST"
