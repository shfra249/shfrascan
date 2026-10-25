#!/usr/bin/env bash
# Download Tesseract tessdata_fast models into the app assets directory.
# Usage: scripts/fetch-tessdata.sh [language ...]
# With no arguments, refreshes the ten languages bundled by default.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/tessdata"
BASE="https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main"

if [ "$#" -gt 0 ]; then
    LANGS=("$@")
else
    LANGS=(eng deu fra spa ita por rus chi_sim ara hin)
fi

mkdir -p "$DEST"
for lang in "${LANGS[@]}"; do
    echo "Fetching $lang..."
    curl -fL --retry 3 --retry-delay 2 \
        -o "$DEST/$lang.traineddata" \
        "$BASE/$lang.traineddata"
done

echo
ls -lh "$DEST"
echo "Done. Rebuild the app to bundle the models."
