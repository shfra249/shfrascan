<div dir="rtl">

# shfrascan 

ماسح مستندات خاص يعمل بالكامل دون اتصال بالإنترنت على أندرويد. shfrascan تنفيذ
مستقل للميزات التي يدفع الناس عادةً اشتراكاً مقابلها: الكشف التلقائي عن الحواف
وتصحيح المنظور، وفلاتر المستندات، والتعرف الضوئي على الحروف (OCR) على الجهاز
بلغات متعددة، وملفات PDF قابلة للبحث دون علامة مائية، ودمج ملفات PDF وتقسيمها،
والتصدير إلى DOCX/XLSX/CSV/TXT، والبحث النصي الكامل دون اتصال، والتوقيعات
وطبقات الصور القابلة للتحريك، والتنقيح الخصوصي بلمسة واحدة، والمشاركة أو الطباعة.

لا حسابات، لا إعلانات، لا قياسات، لا علامة مائية، ولا سحابة. التطبيق لا يطلب
إذن `INTERNET` أصلاً.



## الميزات

- **الالتقاط** عبر CameraX: التقاط متعدد الصفحات، تبديل الفلاش، والتركيز باللمس.
- **الاستيراد**: صور موجودة (اختيار متعدد) وملفات PDF، إما كمستند جديد أو ملحقة
  بالمستند المفتوح. تُعرض صفحات PDF على الجهاز بدقة 150 DPI.
- **الكشف التلقائي عن الصفحة** عبر OpenCV: يجد حواف المستند، ويصحح المنظور إلى
  صفحة مسطحة، ويعود إلى الإطار الكامل إذا لم يجد رباعياً.
- **الاقتصاص اليدوي** بمقابض زوايا قابلة للسحب، مع زر **تلقائي** يعيد تشغيل كشف
  الصفحة على الصورة الأصلية.
- **فلاتر التحسين**: سحر (تباين تكيفي)، تدرج رمادي، أبيض وأسود، وأصلي، مع معاينة
  حية تتحدث فوراً.
- **التحرير**: تدوير، إعادة ترتيب، حذف، إعادة اقتصاص، وتطبيق الفلاتر لكل صفحة.
- **التوقيعات وطبقات الصور**: ارسم توقيعاً أو أدرج أي صورة، ثم اسحبها وغيّر
  حجمها في أي مكان على الصفحة. تبقى الطبقات قابلة للتحرير حتى تسطّحها؛ وإضافة
  صورة لا تعدّل المسح الأصلي أبداً.
- **OCR** على الجهاز باستخدام Tesseract 5 (LSTM): الإنجليزية والألمانية والفرنسية
  والإسبانية والإيطالية والبرتغالية والروسية والصينية المبسطة والعربية والهندية
  مضمّنة. يمكن استيراد أي ملف `*.traineddata` أثناء التشغيل؛ ويعمل OCR صفحة
  بصفحة أو للمستند كاملاً.
- **ملفات PDF قابلة للبحث**: صورة مع نص غير مرئي قابل للتحديد وُضع من صناديق
  الكلمات في hOCR. كلمة مرور PDF اختيارية وتحكم بجودة JPEG.
- **أدوات PDF**: دمج المستندات (من المكتبة أو المستند المفتوح)، تقسيم المستند إلى
  ملفات صفحات مفردة، تصدير PDF متعدد الصفحات، والطباعة عبر إطار الطباعة في
  أندرويد.
- **بحث نصي كامل دون اتصال**: ابحث في أسماء المستندات وكل صفحة تم التعرف عليها
  بـ OCR، مع مقتطفات مظللة. لا شبكة ولا خادم فهرسة.
- **التصدير**: PDF وDOCX (WordprocessingML أساسي) وXLSX وCSV (نص OCR مقسم إلى
  أعمدة) ونص عادي وصور JPEG؛ احفظ في `Downloads/shfrascan` أو شارك عبر ورقة
  المشاركة في النظام.
- **Privacy Shield (درع الخصوصية)**: يفحص نص OCR المخزّن بحثاً عن رسائل البريد
  الإلكتروني وأرقام الهواتف وأرقام البطاقات (بتحقق Luhn) وIBANs، ويعرض ما وجده،
  ويمكنه حرق صناديق تنقيح سوداء بشكل دائم مع حذف الصورة الأصلية ونص OCR.
- **دون اتصال بطبيعته**: كل المعالجة محلية. لا يمكن الوصول إلى الشبكة لأن الإذن
  غير معلن.

## المتطلبات

- أندرويد 8.0 (API 26) أو أحدث.
- جهاز مزود بكاميرا للالتقاط. كل الميزات الأخرى تعمل بدونه.

## التثبيت

نزّل APK التصحيح من تشغيل CI أو ابنِه بنفسك:

```
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk   # معظم الهواتف
app/build/outputs/apk/debug/app-universal-debug.apk   # كل الأبنية المدعومة
```

تُوقّع نسخ التصحيح بمفتاح أندرويد للتصحيح ويمكن تثبيتها مباشرة. ملفات APK حسب
ABI أصغر بكثير؛ أما APK الشامل فيضم مكتبات OpenCV الأصلية لكل الأبنية المدعومة
مع Tesseract وعشر حزم لغات OCR.

## البناء

### Android Studio

افتح جذر المستودع واترك Gradle يزامن. استخدم JDK 17 مع Android SDK 35.

### سطر الأوامر

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug
```

يثبّت Gradle wrapper الإصدار 8.9. وAGP هو 8.7.3، وKotlin 1.9.24، وCompose BOM
2024.06.00.

### البناء في Termux (aarch64، دون Android Studio)

يمكن لسلسلة أدوات aarch64 الأصلية بناء هذا المشروع باستخدام SDK بسيط:

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

وإلا فإن AGP ينزّل AAPT2 بنسخة x86_64 لا تعمل على الهاتف؛ يجعل هذا الخيار
المشروع يستخدم `aapt2` الخاص بـ Termux. إذا فشل `aapt2 version` بسبب رمز
`libabsl` أو `libfmt` مفقود، فحدّث تلك الحزم (`pkg upgrade abseil-cpp libprotobuf
fmt`).

## حزم لغات OCR

عشر نماذج `tessdata_fast` مضمّنة تحت `app/src/main/assets/tessdata`. لجلب المزيد
(أو تحديثها) مع توفر اتصال بالشبكة:

```bash
scripts/fetch-tessdata.sh eng deu fra
```

أثناء التشغيل، الإعدادات ← التعرف على النص ← **Import .traineddata** يثبّت أي
نموذج Tesseract في التخزين الخاص بالتطبيق؛ دون إعادة بناء. تختار الإعدادات أيضاً
لغة OCR الافتراضية وفلتر التحسين الافتراضي.

## كيف يعمل

- `app/src/main/java/app/openscan/domain/ImageProcessor.kt` — تحويل OpenCV إلى
  تدرج رمادي، وتمويه، وCanny، والبحث عن الكونتورات، واختيار الرباعي المحدب،
  وتصحيح المنظور، والتباين التكيفي، وتركيب التوقيعات.
- `domain/OcrEngine.kt` — `TessBaseAPI` واحد لكل عملية، محرك LSTM، ومخرجات hOCR.
- `domain/PdfTools.kt` — ملفات PDF صورية عبر PDFBox-Android، وطبقة نص غير مرئية
  اختيارية من صناديق كلمات hOCR، وضغط، و`StandardProtectionPolicy`.
- `domain/DocxExporter.kt` — مستند WordprocessingML بسيط مع فواصل صفحات.
- `domain/XlsxExporter.kt` — كاتب SpreadsheetML (XLSX) وCSV دون تبعيات؛ أسطر OCR
  تصبح صفوفاً، والفواصل بمسافات جدولة أو واسعة تصبح أعمدة.
- `data/DocumentRepository.kt` — بيانات JSON وصفية لكل مستند؛ تُخزّن صور الصفحات
  والأصول وhOCR في التخزين الخاص بالتطبيق.
- `ui/` — شاشات Jetpack Compose: المكتبة، والالتقاط، والمحرر، وعارض المستندات،
  والإعدادات. يملك `MainViewModel` واحد مرتبط بالنشاط الحالة كلها.
- `util/Exporter.kt` — تنزيلات MediaStore، ومشاركة `FileProvider`، والطباعة.

تُخزّن صور الصفحات بدقة الالتقاط. وتُحدّد أحجام صفحات PDF بدقة 150 DPI
(1 px = 0.48 pt) بحيث تتطابق صناديق hOCR مباشرةً مع طبقة النص غير المرئية.

## القيود المعروفة

- تصدير DOCX يحافظ على فواصل الأسطر والصفحات، وليس الخطوط أو التخطيط.
- تعتمد جودة OCR على نماذج `tessdata_fast`؛ ويمكن استيراد نماذج `tessdata_best`
  لدقة أعلى بسرعة أقل.
- لا مزامنة سحابية أو ميزات حسابات، وهذا مقصود.
- يغطي البحث النصي الكامل نتائج OCR المخزّنة؛ شغّل OCR على الصفحة أولاً إذا أردت
  أن يكون نصها قابلاً للبحث.

## المؤلف

صنعه shfra — https://github.com/shfra249

## الترخيص

رخصة Apache 2.0. راجع [LICENSE](LICENSE) و
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

</div>

---

# shfrascan

A private, fully offline document scanner for Android. shfrascan is a clean-room
implementation of the features people usually pay a subscription for: automatic
edge detection and perspective correction, document filters, on-device OCR in
many languages, watermark-free searchable PDFs, PDF merging and splitting,
DOCX/XLSX/CSV/TXT export, offline full-text search, movable signatures and
image layers, one-tap privacy redaction, and sharing or printing.

There is no account, no ads, no telemetry, no watermark, and no cloud. The app
does not even request the `INTERNET` permission.



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
