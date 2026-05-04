#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "Building React app..."
npm run build

DEST="../backend/app/src/main/resources/static"
echo "Copying dist/ to $DEST"
rm -rf "$DEST"
mkdir -p "$DEST"
cp -R dist/* "$DEST/"

echo "Done. Run './gradlew :app:bootJar' to package the unified jar."
