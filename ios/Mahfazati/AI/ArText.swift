import Foundation

/// تطبيع النص العربي قبل أي مطابقة.
///
/// المستخدم يكتب بلهجته: «اضف» بلا همزة، «قهوه» بالتاء المربوطة، «الى» بألف مقصورة.
/// لو طابقنا النص كما هو، سنفشل في كل مرة لأن «أضف» ≠ «اضف».
enum ArText {
    static func normalize(_ raw: String) -> String {
        var text = DigitNormalizer.normalize(raw)
        text = text.replacingOccurrences(of: "\u{064B}", with: "")
            .replacingOccurrences(of: "\u{064C}", with: "")
            .replacingOccurrences(of: "\u{064D}", with: "")
            .replacingOccurrences(of: "\u{064E}", with: "")
            .replacingOccurrences(of: "\u{064F}", with: "")
            .replacingOccurrences(of: "\u{0650}", with: "")
            .replacingOccurrences(of: "\u{0651}", with: "")
            .replacingOccurrences(of: "\u{0652}", with: "")
            .replacingOccurrences(of: "\u{0670}", with: "")
            .replacingOccurrences(of: "\u{0640}", with: "")
        for (from, to) in [("أ", "ا"), ("إ", "ا"), ("آ", "ا"), ("ٱ", "ا"),
                           ("ى", "ي"), ("ئ", "ي"), ("ؤ", "و"), ("ة", "ه")] {
            text = text.replacingOccurrences(of: from, with: to)
        }
        return text.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// مطابقة كلمة كاملة لا جزءًا من كلمة أخرى.
    static func hasWord(_ text: String, _ word: String) -> Bool {
        let pattern = "(^|[^\\p{L}])" + NSRegularExpression.escapedPattern(for: word) + "($|[^\\p{L}])"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return false }
        return regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
    }

    static func hasAny(_ text: String, _ words: [String]) -> Bool {
        words.contains { hasWord(text, $0) }
    }
}
