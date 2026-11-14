# iOS — أول بناء ناجح ✅

## النتيجة
CI على `macos-15` بنى نسخة iOS كاملة وأنتج:
`Mahfazati-v0.4.8-unsigned.ipa` — 449 KB — Artifact محفوظ 90 يومًا.

تم التحقق من محتوى الملف بعد التنزيل:
| الحقل | القيمة |
|---|---|
| CFBundleIdentifier | com.mahfazati.app |
| CFBundleDisplayName | محفظتي |
| ShortVersion | 0.4.8 |
| Build | 10 |
| MinimumOSVersion | 17.0 |
| UIDeviceFamily | [1, 2] (iPhone + iPad) |
| Binary | Mach-O arm64 |
| AppIcon | 60x60@2x + 76x76@2x~ipad |

## أخطاء حقيقية اكتُشفت بالبناء وأُصلحت
البناء الأول فشل بثلاثة أخطاء ترجمة:
1. **`foregroundStyle(cond ? .primary : .green)`** — النوعان مختلفان
   (`HierarchicalShapeStyle` مقابل `Color`). الحل: `Color.primary` / `Color.green`.
2. **`guard store.addTransaction(...)`** — الدالة تعيد `Transaction?` لا `Bool`.
   الحل: `let created = ...; guard created != nil`.
3. **`unable to type-check in reasonable time`** في `AssistantView` — التعريف
   كان كبيرًا جدًا. الحل: تقسيمه إلى `messageList` و`messageRows` و`thinkingRow`
   و`scrollToBottom`.

ثم خطأ رابع في الحزم:
4. **`Archive Missing Bundle Identifier`** — الـ Info.plist كان ينقصه
   `CFBundleIdentifier` و`CFBundleExecutable` و`CFBundlePackageType`.
   أضيفت بمتغيرات البناء `$(PRODUCT_BUNDLE_IDENTIFIER)` إلخ.

## ملاحظة على منهجية التحقق
شيفرة المنطق تحقّقت مسبقًا بـ 38 اختبار Swift. أما **شيفرة الواجهة فلا يمكن
التحقق منها إلا بترجمة Apple الحقيقية** — ولذلك أُجري البناء على macOS قبل الإعلان
عن أي شيء. الادعاء بأن الواجهة تعمل بدون هذا البناء كان سيكون تخمينًا.

## التوزيع
- Release: `ios-v0.4.8` يحمل دليل التثبيت.
- الـ IPA نفسه يبقى Artifact (لا يُرفع كملف في Releases) لأنه غير موقّع
  وغير قابل للاستخدام مباشرة.
