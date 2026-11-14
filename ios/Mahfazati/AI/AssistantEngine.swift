import Foundation

/// منسّق المساعد: يقرر ينفّذ أو يجاوب، ويضمن أن المحلي يعمل دائمًا.
@MainActor
final class AssistantEngine {
    private let store: AppStore
    private let client = GeminiClient()

    init(store: AppStore) {
        self.store = store
    }

    /// ينفّذ الأوامر أولًا (قبل أي اتصال شبكي) فلا تحتاج إنترنت.
    /// يعيد رسالة إن كان الأمر مفهومًا، أو nil ليكمل المسار العادي.
    func handleCommand(_ text: String) -> ChatMessage? {
        guard let parsed = CommandParser.parse(text) else { return nil }

        if !parsed.explicit {
            // صياغة مفهومة بلا فعل صريح: لا نسجّل من تلقاء أنفسنا
            return ChatMessage(role: "model",
                               text: proposalText(parsed.command) + "\nاضغط «تأكيد» وأسجلها فورًا.",
                               source: "local", pending: true)
        }
        return execute(parsed.command)
    }

    private func proposalText(_ command: FinCommand) -> String {
        switch command {
        case let .add(type, amount, category, merchant):
            var text = "أسجل \(type.arabicLabel) \(Currency.format(amount)) — \(category)"
            if !merchant.isEmpty { text += " (\(merchant))" }
            return text + "؟"
        case let .edit(_, _, newAmount, _):
            return "أعدّل العملية إلى \(Currency.format(newAmount))؟"
        case .delete:
            return "أحذف العملية؟"
        }
    }

    @discardableResult
    func execute(_ command: FinCommand) -> ChatMessage {
        switch command {
        case let .add(type, amount, category, merchant):
            guard store.addTransaction(type: type, amount: amount, category: category,
                                                merchant: merchant, note: "من المساعد",
                                                source: "ASSISTANT") else {
                return ChatMessage(role: "model", text: "ما فيه حساب مسجل. أضف حسابًا أولًا.",
                                   source: "local", error: "لا يوجد حساب")
            }
            let total = store.snapshot.totalBalance
            return ChatMessage(
                role: "model",
                text: "تم ✅ سجلت \(type.arabicLabel) \(Currency.format(amount)) — \(category)\(merchant.isEmpty ? "" : "، \(merchant)").\nمجموعك الحين: \(Currency.format(total))",
                source: "local",
                action: type.isExpense ? "أُضيفت عملية مصروف" : "أُضيف دخل"
            )

        case let .edit(matchAmount, matchMerchant, newAmount, newCategory):
            guard let target = findMatch(amount: matchAmount, merchant: matchMerchant) else {
                return ChatMessage(role: "model",
                                   text: "ما لقيت عملية مطابقة. اذكر المبلغ أو اسم المكان.",
                                   source: "local", error: "لم تُعثر على العملية")
            }
            store.updateTransaction(target, newAmount: newAmount, newCategory: newCategory)
            return ChatMessage(role: "model",
                               text: "تم ✅ عدّلت العملية إلى \(Currency.format(newAmount)).\nمجموعك الحين: \(Currency.format(store.snapshot.totalBalance))",
                               source: "local", action: "عُدّلت العملية")

        case let .delete(matchAmount, matchMerchant):
            guard let target = findMatch(amount: matchAmount, merchant: matchMerchant) else {
                return ChatMessage(role: "model",
                                   text: "ما لقيت العملية. اذكر المبلغ أو اسم المكان.",
                                   source: "local", error: "لم تُعثر على العملية")
            }
            store.deleteTransaction(target)
            return ChatMessage(role: "model",
                               text: "تم ✅ حذفت العملية ورجعت أثرها على الرصيد.\nمجموعك الحين: \(Currency.format(store.snapshot.totalBalance))",
                               source: "local", action: "حُذفت العملية")
        }
    }

    private func findMatch(amount: Int64?, merchant: String?) -> Transaction? {
        let all = store.transactions
        guard !all.isEmpty else { return nil }
        let byAmount = amount.map { value in all.filter { $0.amount == value } } ?? []
        let byMerchant = merchant.map { name in
            all.filter { $0.merchant.localizedCaseInsensitiveContains(name)
                || $0.note.localizedCaseInsensitiveContains(name) }
        } ?? []

        if !byAmount.isEmpty && !byMerchant.isEmpty {
            return byMerchant.first { $0.amount == amount } ?? byAmount.first
        }
        if let first = byAmount.first { return first }
        if let first = byMerchant.first { return first }
        return amount == nil && (merchant?.isEmpty ?? true) ? all.first : nil
    }

    /// الإرسال الكامل: أمر ← محلي ← سحابي (لو مفعّل) مع رجوع للمحلي عند الفشل.
    func send(_ text: String) async -> [ChatMessage] {
        var produced: [ChatMessage] = []
        produced.append(ChatMessage(role: "user", text: text))

        if let commandReply = handleCommand(text) {
            produced.append(commandReply)
            return produced
        }

        let snapshot = store.snapshot
        let localReply = LocalBrain.answer(text, snapshot: snapshot)
        let settings = store.ai

        let useCloud: Bool
        switch settings.mode {
        case .local: useCloud = false
        case .cloud, .auto: useCloud = settings.cloudReady
        }

        guard useCloud else {
            var note = ""
            if settings.mode == .cloud && !settings.cloudReady {
                note = "الوضع السحابي مختار لكن المفتاح غير مضاف. رديت عليك محليًا:\n\n"
            }
            produced.append(ChatMessage(role: "model", text: note + localReply, source: "local"))
            return produced
        }

        let history = store.chat.prefix(10).map {
            GeminiClient.Message(role: $0.role, text: $0.text)
        } + [GeminiClient.Message(role: "user", text: text)]

        let context = cloudContext(snapshot: snapshot, shareAmounts: settings.shareAmounts)
        let style = settings.style.arabicLabel

        let result = await client.ask(apiKey: settings.apiKey, model: settings.model,
                                      style: style, context: context, history: Array(history))

        switch result {
        case .success(let reply):
            produced.append(ChatMessage(role: "model", text: reply, source: "cloud"))
        case .failure(let error):
            produced.append(ChatMessage(role: "model", text: localReply, source: "local",
                                        error: "تعذّر الوصول للسحابة: \(error.message)"))
        }
        return produced
    }

    /// سياق مختصر يُرسل للسحابة فقط عند موافقة المستخدم على مشاركة الأرقام.
    private func cloudContext(snapshot: MoneySnapshot, shareAmounts: Bool) -> String {
        var text = "snapshot: currency=QAR"
        if shareAmounts {
            text += ", safe=\(Double(snapshot.safe) / 100), balance=\(Double(snapshot.totalBalance) / 100)"
            text += ", commitments=\(Double(snapshot.commitments) / 100)"
            text += ", cycleExpense=\(Double(snapshot.cycleExpense) / 100)"
            text += ", cycleIncome=\(Double(snapshot.cycleIncome) / 100)"
        } else {
            text += ", amountsWithheld=true, expenseCount=\(snapshot.expenses.count)"
        }
        text += ", daysLeft=\(snapshot.daysLeft), salaryDay=\(snapshot.salaryDay)"
        return text
    }
}
