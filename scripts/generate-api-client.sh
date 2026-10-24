#!/usr/bin/env bash
# Regenerate the vendored v2 API client (Kotlin) from the OpenAPI spec.
# The spec (docs/openapi-v2.yaml) is gitignored; get the current draft from FBA.
# Requires JDK 17+. Downloads openapi-generator-cli if absent.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SPEC="$ROOT/docs/openapi-v2.yaml"
JAR="${OPENAPI_GENERATOR_JAR:-$ROOT/.cache/openapi-generator-cli.jar}"
PKG="com.dharmachakra.fba_android.api"
OUT="$(mktemp -d)"
mkdir -p "$(dirname "$JAR")"
if [ ! -f "$JAR" ]; then
  VER="$(curl -s https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/maven-metadata.xml | grep -oE '<release>[^<]+' | sed 's/<release>//')"
  curl -s -o "$JAR" "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/$VER/openapi-generator-cli-$VER.jar"
fi
java -jar "$JAR" validate -i "$SPEC"
java -jar "$JAR" generate -g kotlin -i "$SPEC" -o "$OUT" \
  --package-name "$PKG" \
  --openapi-normalizer FILTER="tag:service|catalogue|search|auth|me" \
  --additional-properties=serializationLibrary=kotlinx_serialization,library=jvm-okhttp4,enumPropertyNaming=UPPERCASE
DEST="$ROOT/FBAudio-Android/app/src/main/java/com/dharmachakra/fba_android/api"
rm -rf "$DEST"; mkdir -p "$DEST"
cp -r "$OUT/src/main/kotlin/$(echo $PKG | tr . /)/"{models,apis,infrastructure} "$DEST/"
python3 "$ROOT/scripts/harden-enums.py" "$DEST/models"
echo "Regenerated $(find "$DEST" -name '*.kt' | wc -l) files into $DEST"
