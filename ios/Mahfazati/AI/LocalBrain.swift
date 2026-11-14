import Foundation

enum LocalTopic {
    case total, safe, spend, category, save, forecast, salary, income, compare, advice, help
}

/// المحرك المحلي: يعمل على الجهاز بدون إنترنت وبدون مفتاح، ولا يعتذر أبدًا.
enum LocalBrain {

    static func detectTopic(_ question: String) -> LocalTopic {
        let q = ArText.normalize(question)
        if ArText.hasAny(q, ["كم عندي", "عندي كم", "مجموع", "مجموعي", "الرصيد", "رصيدي",
                             "فلوسي", "كم معي", "معي كم", "كم فلوس"]) { return .total }
        if ArText.hasAny(q, ["اقدر اصرف", "باقي لي", "باقي", "متاح", "المتاح", "امان", "بأمان"]) { return .safe }
        if ArText.hasAny(q, ["صرفت", "صرفي", "مصروفي", "مصاريفي", "كم صرف", "كم طلع", "كم كلفني"]) { return .spend }
        if ArText.hasAny(q, ["وين", "اكثر", "فين", "على شنو", "على ايش", "تصنيف", "بند", "بنود"]) { return .category }
        if ArText.hasAny(q, ["اوفر", "ادخر", "توفير", "كيف اوفر"]) { return .save }
        if ArText.hasAny(q, ["توقع", "نهايه", "اخر الشهر", "بتوصل", "معدل"]) { return .forecast }
        if ArText.hasAny(q, ["راتب", "معاش", "الدوره", "متى ينزل"]) { return .salary }
        if ArText.hasAny(q, ["دخل", "وارد", "استلمت", "كسبت"]) { return .income }
        if ArText.hasAny(q, ["مقارنه", "قارن", "الشهر الماضي", "السابق"]) { return .compare }
        if ArText.hasAny(q, ["نصيحه", "تنصح", "رايك", "شنو اسوي", "شسوي", "كيف احسن"]) { return .advice }
        return .help
    }

    static func answer(_ question: String, snapshot: MoneySnapshot) -> String {
        let trimmed = question.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return summary(snapshot) }
        func money(_ value: Int64) -> String { Currency.format(value) }

        switch detectTopic(trimmed) {
        case .total:
            return """
            مجموعك الحالي: \(money(snapshot.totalBalance)) في كل الحسابات.
            بعد حجز الالتزامات (\(money(snapshot.commitments))) والادخار (\(money(snapshot.savingsTarget)))، \
            المتاح بأمان: \(money(snapshot.safe)).
            """

        case .safe:
            var text = "المتاح بأمان: \(money(snapshot.safe)).\n"
            if snapshot.daysLeft > 0 {
                text += "يعني \(money(snapshot.dailyAllowance)) باليوم لين الراتب (باقي \(snapshot.daysLeft) يوم).\n"
            }
            text += "الحساب: الرصيد \(money(snapshot.totalBalance)) − الالتزامات \(money(snapshot.commitments)) − الادخار \(money(snapshot.savingsTarget))."
            return text

        case .spend:
            var text = "صرفت هذي الدورة \(money(snapshot.cycleExpense))."
            if snapshot.cycleIncome > 0 {
                let pct = Int(Double(snapshot.cycleExpense) / Double(snapshot.cycleIncome) * 100)
                text += " (\(pct)% من دخلك)"
            }
            text += "\n"
            if snapshot.cycleElapsedDays > 0 {
                text += "متوسط اليوم: \(money(snapshot.averageDailySpend)) على مدى \(snapshot.cycleElapsedDays) يوم."
            }
            if let top = snapshot.spendByCategory.first {
                text += "\nأكبر بند: \(top.category) (\(money(top.amount)))."
            }
            return text

        case .category:
            let top = Array(snapshot.spendByCategory.prefix(3))
            guard !top.isEmpty else { return "ما فيه مصاريف مسجلة هذي الدورة بعد. سجّل أول عملية وأنا أحللها لك." }
            var text = "أكبر بنود الصرف هذي الدورة:\n"
            for (index, item) in top.enumerated() {
                let pct = snapshot.cycleExpense > 0 ? Int(Double(item.amount) / Double(snapshot.cycleExpense) * 100) : 0
                text += "\(index + 1). \(item.category) — \(money(item.amount)) (\(pct)%)\n"
            }
            text += "\nلو خفّضت البند الأول 10% توفر \(money(top[0].amount / 10))."
            return text

        case .save:
            let leftover = snapshot.cycleIncome - snapshot.cycleExpense
            if snapshot.cycleIncome <= 0 {
                return "ما عندي دخل مسجل لهذي الدورة. سجّل الراتب أو حدده في الخطة وأحسب لك قدرة الادخار بدقة."
            }
            if leftover <= 0 {
                let top = snapshot.spendByCategory.first?.category ?? "المصاريف اليومية"
                return "مصروفك \(money(snapshot.cycleExpense)) يوازي أو يتعدى الدخل. أول خطوة: قلل أكبر بند (\(top)) بمبلغ بسيط كل أسبوع."
            }
            let pct = Int(Double(leftover) / Double(snapshot.cycleIncome) * 100)
            return """
            تقدر تدخر حتى \(money(leftover)) هذي الدورة (\(pct)% من دخلك).
            لو حوّلت \(money(leftover / 2)) لهدف واضح، الباقي يكفيك بسهولة.
            """

        case .forecast:
            var text = "لو كملت بنفس المعدل (\(money(snapshot.averageDailySpend)) يوميًا)، متوقع تصرف \(money(snapshot.projectedCycleExpense)) بنهاية الدورة.\n"
            if snapshot.cycleIncome > 0 {
                text += "الفرق عن دخلك: \(money(snapshot.cycleIncome - snapshot.projectedCycleExpense))."
            }
            return text

        case .salary:
            return "باقي \(snapshot.daysLeft) يوم على الراتب (يوم \(snapshot.salaryDay)). مصروف الدورة \(money(snapshot.cycleExpense))."

        case .income:
            guard snapshot.cycleIncome > 0 else { return "ما فيه دخل مسجل هذي الدورة." }
            return "دخلك المسجل هذي الدورة \(money(snapshot.cycleIncome))، وصرفت \(money(snapshot.cycleExpense))، الباقي \(money(snapshot.cycleIncome - snapshot.cycleExpense))."

        case .compare:
            guard let top = snapshot.spendByCategory.first else {
                return "ما فيه بيانات كافية للمقارنة. سجّل عمليات أكثر وأقدر أقارن لك."
            }
            let pct = snapshot.cycleExpense > 0 ? Int(Double(top.amount) / Double(snapshot.cycleExpense) * 100) : 0
            return "\(top.category) هو أعلى بند عندك (\(money(top.amount))، \(pct)% من صرفك). هنا أول مكان تقدر توفّر منه."

        case .advice:
            var text = "أفضل ثلاث خطوات من أرقامك:\n"
            if let top = snapshot.spendByCategory.first {
                text += "1. خفّض \(top.category) 10% ⇒ توفير \(money(top.amount / 10)).\n"
            }
            if snapshot.safe > 0 && snapshot.daysLeft > 0 {
                text += "2. التزم بـ \(money(snapshot.dailyAllowance)) يوميًا لين الراتب.\n"
            }
            if snapshot.commitments > 0 {
                text += "3. خلّ \(money(snapshot.commitments)) محجوزة للالتزامات، لا تلمسها."
            }
            return text

        case .help:
            return summary(snapshot)
        }
    }

    /// ملخص دائم بدل الاعتذار — أي سؤال غير مفهوم يُجاب بهذا.
    static func summary(_ snapshot: MoneySnapshot) -> String {
        var text = "ملخصك الآن 👇\n"
        text += "• مجموعك: \(Currency.format(snapshot.totalBalance))\n"
        text += "• المتاح بأمان: \(Currency.format(snapshot.safe))\n"
        if snapshot.daysLeft > 0 {
            text += "• يوميًا: \(Currency.format(snapshot.dailyAllowance)) (باقي \(snapshot.daysLeft) يوم)\n"
        }
        text += "• صرف الدورة: \(Currency.format(snapshot.cycleExpense))"
        if let top = snapshot.spendByCategory.first {
            text += "\n• أكبر بند: \(top.category) (\(Currency.format(top.amount)))"
        }
        text += "\n\nاسألني عن أي رقم، أو قل «أضف مصروف 50 بقالة» ونفّذها لك."
        return text
    }

    /// ملاحظات جاهزة تظهر في الرئيسية والمساعد.
    static func insights(_ snapshot: MoneySnapshot) -> [Insight] {
        var out: [Insight] = []
        if snapshot.expenses.isEmpty && snapshot.cycleIncome <= 0 {
            return [Insight(title: "خلّنا نبدأ بخطوة وحدة",
                            body: "أضف رصيد حسابك، وبعدها أول عملية. من أول عملية راح أقدر أحسب المتاح بأمان وتوقع نهاية الشهر.",
                            tone: .neutral)]
        }

        if snapshot.cycleIncome > 0 {
            let share = Int(Double(snapshot.projectedCycleExpense) / Double(snapshot.cycleIncome) * 100)
            if share >= 100 {
                out.append(Insight(title: "معدل الصرف يتجاوز دخلك",
                                   body: "على هذا المعدل بتصرف \(Currency.format(snapshot.projectedCycleExpense)) بنهاية الدورة، وهذا \(share)% من دخلك.",
                                   tone: .warn))
            } else if share >= 80 {
                out.append(Insight(title: "الصرف قريب من الدخل",
                                   body: "متوقع تصرف \(Currency.format(snapshot.projectedCycleExpense)) (\(share)% من الدخل).",
                                   tone: .warn))
            } else {
                out.append(Insight(title: "وضعك مستقر",
                                   body: "متوقع تصرف \(Currency.format(snapshot.projectedCycleExpense)) (\(share)% من دخلك).",
                                   tone: .good))
            }
        }

        if snapshot.daysLeft > 0 && snapshot.safe > 0 {
            let burn = snapshot.burnRatio
            if burn >= 100 {
                out.append(Insight(title: "المعدل الحالي أعلى من المتاح",
                                   body: "بنفس الوتيرة (\(Currency.format(snapshot.averageDailySpend)) يوميًا) تحتاج أكثر من المتاح (\(Currency.format(snapshot.safe))). حاول تنزل ليومي \(Currency.format(snapshot.dailyAllowance)).",
                                   tone: .warn))
            } else if burn >= 80 {
                out.append(Insight(title: "قريب من الحد",
                                   body: "صرفت \(burn)% من متاحك على الأيام المتبقية.",
                                   tone: .warn))
            } else {
                out.append(Insight(title: "عندك مساحة",
                                   body: "باقي \(Currency.format(snapshot.safe)) لـ \(snapshot.daysLeft) يوم — يعني \(Currency.format(snapshot.dailyAllowance)) باليوم.",
                                   tone: .good))
            }
        }

        if let top = snapshot.spendByCategory.first, snapshot.cycleExpense > 0 {
            let pct = Int(Double(top.amount) / Double(snapshot.cycleExpense) * 100)
            out.append(Insight(title: "أكبر بند: \(top.category)",
                               body: "\(Currency.format(top.amount)) — \(pct)% من صرفك. لو خفّضته 10% توفر \(Currency.format(top.amount / 10)).",
                               tone: .neutral))
        }
        return Array(out.prefix(4))
    }
}

struct Insight: Identifiable {
    enum Tone { case good, warn, neutral }
    let id = UUID()
    let title: String
    let body: String
    var tone: Tone = .neutral
}

struct ChatMessage: Identifiable {
    let id = UUID()
    let role: String            // "user" أو "model"
    var text: String
    var source: String?         // "local" أو "cloud"
    var error: String?
    var action: String?         // شارة «تم التنفيذ»
    var pending: Bool = false
    var at: Date = Date()
}
