import Foundation

/// العملة الثابتة: الريال القطري.
enum Currency {
    static let code = "QAR"
    static let label = "ر.ق"

    /// المبالغ تُخزَّن بالهللة (Int64) — نفس قاعدة نسخة أندرويد،
    /// لأن الفاصلة العائمة تُنتج فروقًا في الأرصدة.
    static func format(_ minor: Int64, withCurrency: Bool = true) -> String {
        let value = Double(minor) / 100.0
        let hasFraction = minor % 100 != 0
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: "en_US")
        formatter.minimumFractionDigits = hasFraction ? 2 : 0
        formatter.maximumFractionDigits = 2
        let text = formatter.string(from: NSNumber(value: value)) ?? "\(value)"
        return withCurrency ? "\(text) \(label)" : text
    }
}

/// تحويل الأرقام العربية-الهندية إلى لاتينية وتوحيد الفاصلة العشرية.
enum DigitNormalizer {
    static func normalize(_ input: String) -> String {
        var result = input
        let arabic = "٠١٢٣٤٥٦٧٨٩"
        let eastern = "۰۱۲۳۴۵۶۷۸۹"
        for (i, ch) in arabic.enumerated() {
            result = result.replacingOccurrences(of: String(ch), with: String(i))
        }
        for (i, ch) in eastern.enumerated() {
            result = result.replacingOccurrences(of: String(ch), with: String(i))
        }
        return result
            .replacingOccurrences(of: "٫", with: ".")
            .replacingOccurrences(of: "،", with: ",")
    }

    static func parseMoney(_ input: String) -> Int64? {
        let clean = normalize(input)
            .replacingOccurrences(of: ",", with: "")
            .replacingOccurrences(of: " ", with: "")
        guard !clean.isEmpty, let decimal = Decimal(string: clean, locale: Locale(identifier: "en_US")) else {
            return nil
        }
        var scaled = decimal * 100
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        return NSDecimalNumber(decimal: rounded).int64Value
    }
}
