import Foundation

/// أمر مالي مستخرج من كلام المستخدم.
enum FinCommand: Equatable {
    case add(type: TxType, amount: Int64, category: String, merchant: String)
    case edit(matchAmount: Int64?, matchMerchant: String?, newAmount: Int64, newCategory: String?)
    case delete(matchAmount: Int64?, matchMerchant: String?)

    var summary: String {
        switch self {
        case let .add(type, amount, category, _):
            return "\(type.arabicLabel) \(Currency.format(amount)) — \(category)"
        case let .edit(_, _, newAmount, _):
            return "تعديل العملية إلى \(Currency.format(newAmount))"
        case .delete:
            return "حذف عملية"
        }
    }
}

struct ParsedCommand {
    let command: FinCommand
    /// صريح = فعل أمر واضح ⇒ ينفّذ فورًا. غير صريح ⇒ يُطلب تأكيد بضغطة.
    let explicit: Bool
}

/// محلل الأوامر — نسخة iOS من CommandParser في أندرويد بنفس المفردات والقواعد.
enum CommandParser {

    private static let addVerbs = [
        "اضف", "اضيف", "ضيف", "ضيفي", "سجل", "سجللي", "سجلها", "حط", "حطي", "حطلي",
        "صرفت", "صرف", "صرفته", "دفعت", "دفع", "دفعته", "شريت", "اشتريت", "شريتها",
        "اخذت", "اخذ", "خذ", "خذلك", "خذه", "طلعت", "طلع",
        "كلفني", "كلفتني", "بليت", "اكلت", "تعشيت", "تغديت", "فطرت", "شربت",
        "استلمت", "قبضت", "حولت", "وصلني", "نزل", "خصم", "نقص"
    ]

    private static let editVerbs = [
        "عدل", "عدله", "عدلها", "تعديل", "غير", "غيره", "غيرها", "صحح", "بدل"
    ]

    private static let deleteVerbs = [
        "احذف", "حذف", "امسح", "مسح", "الغ", "الغي", "شيل", "شيلها", "ازل", "كنسل"
    ]

    private static let futureWords = [
        "بصرف", "بشتري", "بروح", "باخذ", "بدفع", "بعدين", "بكره", "باجر",
        "ناوي", "ناويه", "نفسي", "ودي", "ابي", "ابغي", "افكر", "لازم",
        "محتاج", "احتجت", "يمكن", "لو", "اذا", "سوف"
    ]

    private static let questionWords = [
        "كم", "بكم", "جم", "قديش", "شكثر", "شحال", "وين", "ليش", "ليه", "متى", "هل",
        "شلون", "كيف", "اقدر", "باقي", "بقى", "عندي", "معي", "رصيدي", "فلوسي",
        "المتاح", "مصروفي", "صرفي", "تقدر", "تعرف", "تتوقع", "توقع", "رايك", "تنصح",
        "نصيحه", "وش", "ايش", "شنو", "شو"
    ]

    private static let incomeWords = ["راتب", "معاش", "دخل", "استلمت", "قبضت", "وارد", "مكافاه", "منحه"]
    private static let expenseWords = ["مصروف", "مصاريف", "فاتوره", "قيمه", "دفعت", "صرفت", "شريت", "كلفني"]

    private static let categoryRules: [(words: [String], category: String)] = [
        (["بقاله", "جمعيه", "سوبر", "كارفور", "لولو", "مونوبري", "سبينس", "خضار", "فواكه",
          "لحم", "دجاج", "سمك", "بيض", "بضاعه", "سوق"], "بقالة"),
        (["مطعم", "مطاعم", "قهوه", "كوفي", "كافيه", "ستاربكس", "ماكدونالدز", "برجر",
          "بيتزا", "غدا", "عشا", "فطور", "اكل", "شاي", "كيك", "حلويات", "شاورما", "كنتاكي"], "مطاعم"),
        (["بنزين", "وقود", "بترول", "تاكسي", "اوبر", "كريم", "مواصلات", "باص", "مترو",
          "صيانه", "سياره", "تواير", "غسيل"], "مواصلات"),
        (["صيدليه", "دكتور", "مستشفى", "عياده", "تحليل", "دوا", "اسنان", "علاج", "كشف"], "صحة"),
        (["ملابس", "ثوب", "غتره", "حقيبه", "ساعه", "امازون", "نون", "تسوق", "جوال",
          "ايفون", "لابتوب", "سماعه", "عطر", "احذيه"], "تسوق"),
        (["كهرباء", "كهرماء", "ماي", "ماء", "ايجار", "بيت", "منزل", "اثاث", "انترنت",
          "اتصالات", "فودافون", "اوريدو", "سباك", "كهربجي", "تنظيف"], "منزل"),
        (["سينما", "لعبه", "العاب", "ترفيه", "نزهه", "رحله", "تذاكر", "بلايستيشن", "حفله"], "ترفيه"),
        (["راتب", "معاش"], "راتب"),
        (["مكافاه", "دخل اضافي", "بيع", "منحه", "هديه"], "دخل إضافي")
    ]

    private static let numberRegex = try! NSRegularExpression(pattern: "\\d+(?:[.,]\\d{1,2})?")
    private static let editMarkers = ["الى", "الي", "خله", "خلها", "يصير", "تكون"]

    // ── الواجهة ──────────────────────────────────────────────────────────
    static func parse(_ raw: String) -> ParsedCommand? {
        let text = ArText.normalize(raw)
        guard !text.isEmpty else { return nil }

        let amount = findAmount(text)
        let isQuestion = looksLikeQuestion(text) || ArText.hasAny(text, futureWords)

        if ArText.hasAny(text, deleteVerbs) {
            return ParsedCommand(
                command: .delete(matchAmount: amount,
                                 matchMerchant: merchantOf(raw).isEmpty ? nil : merchantOf(raw)),
                explicit: true
            )
        }

        if ArText.hasAny(text, editVerbs) {
            guard let newAmount = amountAfterEditMarker(text) ?? amount else { return nil }
            let markerIndex = editMarkers.compactMap { text.range(of: $0)?.lowerBound }.min() ?? text.endIndex
            let oldAmount = findAmount(String(text[text.startIndex..<markerIndex]))
            return ParsedCommand(
                command: .edit(matchAmount: oldAmount == newAmount ? nil : oldAmount,
                               matchMerchant: merchantOf(raw).isEmpty ? nil : merchantOf(raw),
                               newAmount: newAmount,
                               newCategory: categoryOf(raw)),
                explicit: true
            )
        }

        guard let value = amount, value > 0, !isQuestion else { return nil }

        let isIncome = ArText.hasAny(text, incomeWords) && !ArText.hasAny(text, expenseWords)
        let type: TxType = isIncome ? .income : .expense
        let merchant = merchantOf(raw)
        let category = categoryOf(raw) ?? (isIncome ? "أخرى" : "أخرى")

        let command = FinCommand.add(type: type, amount: value, category: category, merchant: merchant)

        if ArText.hasAny(text, addVerbs) {
            return ParsedCommand(command: command, explicit: true)
        }
        // بلا فعل صريح: نقترح فقط، ولا نسجّل من تلقاء أنفسنا
        if !merchant.isEmpty || category != "أخرى" {
            return ParsedCommand(command: command, explicit: false)
        }
        return nil
    }

    // ── الأرقام ──────────────────────────────────────────────────────────
    static func findAmount(_ raw: String) -> Int64? {
        let text = ArText.normalize(raw)
        let range = NSRange(text.startIndex..., in: text)
        guard let match = numberRegex.firstMatch(in: text, range: range),
              let matchRange = Range(match.range, in: text) else { return nil }

        let rawNumber = String(text[matchRange]).replacingOccurrences(of: ",", with: ".")
        guard let base = Double(rawNumber) else { return nil }

        let after = String(text[matchRange.upperBound...]).trimmingCharacters(in: .whitespaces).prefix(14)
        var multiplier = 1.0
        if after.hasPrefix("الف") || after.hasPrefix("الاف") || after.hasPrefix("الفين") {
            multiplier = 1000
        } else if after.hasPrefix("k") {
            multiplier = 1000
        } else if after.hasPrefix("مليون") {
            multiplier = 1_000_000
        }
        return Int64((base * multiplier * 100).rounded())
    }

    private static func amountAfterEditMarker(_ text: String) -> Int64? {
        let indexes = editMarkers.compactMap { text.range(of: $0)?.lowerBound }
        guard let first = indexes.min() else { return nil }
        return findAmount(String(text[first...]))
    }

    // ── التصنيف واسم المكان ───────────────────────────────────────────────
    static func categoryOf(_ raw: String) -> String? {
        let text = ArText.normalize(raw)
        for rule in categoryRules where ArText.hasAny(text, rule.words) {
            return rule.category
        }
        return nil
    }

    private static func isFiller(_ word: String) -> Bool {
        var bare = word
        if bare.hasPrefix("ال") { bare.removeFirst(2) }
        if bare.hasPrefix("و") { bare.removeFirst() }
        if bare.count <= 2 { return true }
        let fillers: Set<String> = [
            "مصروف", "مصاريف", "دخل", "راتب", "قيمه", "مبلغ", "عمليه", "عمليات", "ريال",
            "يومي", "يوميه", "شهري", "تقريبا", "حوالي", "يمكن", "بس", "الحين", "اليوم",
            "امس", "هذا", "هذي", "كان", "صار", "بعد", "قبل", "على", "من", "في", "الي",
            "الى", "مع", "عن", "كل", "شي", "شوي", "كثير", "قليل", "والله", "الله",
            "طيب", "زين", "اوكي", "شكرا", "ياخي", "تكفى"
        ]
        return fillers.contains(bare) || fillers.contains(word)
    }

    static func merchantOf(_ raw: String) -> String {
        var text = ArText.normalize(raw)
        text = numberRegex.stringByReplacingMatches(in: text, range: NSRange(text.startIndex..., in: text),
                                                    withTemplate: " ")
        for verb in addVerbs + editVerbs + deleteVerbs + editMarkers {
            text = text.replacingOccurrences(of: verb, with: " ")
        }
        text = text.unicodeScalars
            .map { CharacterSet.letters.contains($0) || $0 == " " ? String($0) : " " }
            .joined()
        let words = text.split(separator: " ").map(String.init)
            .filter { $0.count > 1 && !isFiller($0) }
        return words.prefix(3).joined(separator: " ")
    }

    private static func looksLikeQuestion(_ text: String) -> Bool {
        if text.contains("؟") || text.contains("?") { return true }
        return ArText.hasAny(text, questionWords)
    }
}
