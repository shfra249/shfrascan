# shfrascan

A private, fully offline document scanner for Android. shfrascan is a clean-room
implementation of the features people usually pay a subscription for: automatic
edge detection and perspective correction, document filters, on-device OCR in
many languages, watermark-free searchable PDFs, PDF merging and splitting,
DOCX/XLSX/CSV/TXT export, offline full-text search, movable signatures and
image layers, one-tap privacy redaction, and sharing or printing.

There is no account, no ads, no telemetry, no watermark, and no cloud. The app
does not even request the `INTERNET` permission.

shfrascan is not affiliated with, derived from, or endorsed by CamScanner or any
other commercial scanner. It contains no proprietary code, assets, or branding
from those products.

## Features

- **Capture** with CameraX: multi-page capture, flash toggle, tap-to-focus.
- **Import** existing images (multi-select) and PDFs, either as a new document
  or appended to the open one. Imported PDFs are rendered on device at 150 DPI.
- **Automatic page detection** with OpenCV: finds the document edges,
  perspective-warps to a flat page, and falls back to the full frame when no
  quad is found.
- **Manual crop** with draggable corner handles plus an **Auto** button that
  re-runs page detection on the original image.
- **Enhancement filters**: Magic (adaptive contrast), Grayscale, Black & White,
  and Original, with a live preview that updates immediately.
- **Editing**: rotate, reorder, delete, re-crop, and apply filters per page.
- **Signatures and image layers**: draw a signature or insert any image, then
  drag and resize it anywhere on the page. Layers stay editable until you flatten
  them; adding an image never modifies the original scan.
- **OCR** on device with Tesseract 5 (LSTM): English, German, French, Spanish,
  Italian, Portuguese, Russian, Simplified Chinese, Arabic, and Hindi bundled.
  Any `*.traineddata` file can be imported at runtime; OCR runs page by page or
  for a whole document.
- **Searchable PDFs**: image plus invisible, selectable text positioned from
  hOCR word boxes. Optional PDF password and JPEG quality control.
- **PDF tools**: merge documents (from the library or the open document), split
  a document into single-page files, export multi-page PDFs, and print via
  Android's print framework.
- **Offline full-text search**: search document names and every page that has
  already been recognized with OCR, with highlighted snippets. No network, no
  index server.
- **Export**: PDF, DOCX (basic WordprocessingML), XLSX and CSV (OCR text split
  into columns), plain text, and JPEG images; save to `Downloads/shfrascan` or
  share through the system share sheet.
- **Privacy Shield**: scans stored OCR text for emails, phone numbers, card
  numbers (Luhn-checked) and IBANs, shows what it found, and can permanently burn
  black redaction boxes while deleting the original image and OCR text.
- **Offline by construction**: all processing is local. No network access is
  possible because the permission is not declared.

## Requirements

- Android 8.0 (API 26) or newer.
- A device with a camera for capture. Everything else works without one.

## Install

Download the debug APK from a CI run or build it yourself:

```
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk   # most phones
app/build/outputs/apk/debug/app-universal-debug.apk   # every supported ABI
```

Debug builds are signed with the Android debug key and are directly
installable. Per-ABI APKs are much smaller; the universal APK bundles OpenCV
native libraries for all supported ABIs together with Tesseract and ten OCR
language packs.

## Build

### Android Studio

Open the repository root and let Gradle sync. Use JDK 17 with Android SDK 35.

### Command line

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug
```

The Gradle wrapper pins Gradle 8.9. AGP is 8.7.3, Kotlin 1.9.24, Compose BOM
2024.06.00.

### Building in Termux (aarch64, no Android Studio)

A native aarch64 toolchain can build this project with a minimal SDK:

```bash
pkg install openjdk-17 aapt2
mkdir -p ~/android-sdk/{platforms,build-tools/35.0.0,licenses}
# download platform-35_r02.zip from dl.google.com and extract android-35 into
# ~/android-sdk/platforms/
ln -s "$(command -v aapt2)" ~/android-sdk/build-tools/35.0.0/aapt2
printf 'Pkg.Desc=Android SDK Build-Tools\nPkg.Revision=35.0.0\n' \
  > ~/android-sdk/build-tools/35.0.0/source.properties
printf 'sdk.dir=%s\n' "$HOME/android-sdk" > local.properties

./gradlew :app:assembleDebug \
  -Pandroid.aapt2FromMavenOverride="$(command -v aapt2)" \
  -Dorg.gradle.jvmargs=-Xmx3072m
```

AGP otherwise downloads an x86_64 AAPT2 that cannot run on a phone; the
override makes it use the Termux `aapt2`. If `aapt2 version` fails with a
missing `libabsl` or `libfmt` symbol, upgrade those packages (`pkg upgrade
abseil-cpp libprotobuf fmt`).

## OCR language packs

Ten `tessdata_fast` models are committed under `app/src/main/assets/tessdata`.
To fetch more (or refresh them) with network access:

```bash
scripts/fetch-tessdata.sh eng deu fra
```

At runtime, Settings → Text recognition → **Import .traineddata** installs any
Tesseract model into app-private storage; no rebuild is needed. Settings also
chooses the default OCR language and the default enhancement filter.

## How it works

- `app/src/main/java/app/openscan/domain/ImageProcessor.kt` — OpenCV grayscale,
  blur, Canny, contour search, convex quad selection, perspective warp,
  adaptive threshold, and signature compositing.
- `domain/OcrEngine.kt` — one `TessBaseAPI` per call, LSTM engine, hOCR output.
- `domain/PdfTools.kt` — PDFBox-Android image PDFs, optional invisible text
  layer from hOCR word boxes, compression, and `StandardProtectionPolicy`.
- `domain/DocxExporter.kt` — minimal WordprocessingML document with page breaks.
- `domain/XlsxExporter.kt` — dependency-free SpreadsheetML (XLSX) and CSV writer;
  OCR lines become rows and tab/wide-space gaps become columns.
- `data/DocumentRepository.kt` — JSON metadata per document; page images,
  originals, and hOCR are stored in app-private storage.
- `ui/` — Jetpack Compose screens: library, capture, editor, document viewer,
  settings. A single activity-scoped `MainViewModel` owns the state.
- `util/Exporter.kt` — MediaStore downloads, `FileProvider` sharing, printing.

Page images are stored at capture resolution. PDF pages are sized at 150 DPI
(1 px = 0.48 pt) so hOCR boxes map directly onto the invisible text layer.

## Known limitations

- DOCX export preserves line breaks and page breaks, not fonts or layout.
- OCR quality depends on the `tessdata_fast` models; the `tessdata_best` models
  can be imported for higher accuracy at lower speed.
- No cloud sync or account features by design.
- Full-text search covers stored OCR results; run OCR on a page first if you
  want its text to be searchable.

## Author

Made by shfra — https://github.com/shfra249

## License

Apache License 2.0. See [LICENSE](LICENSE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
