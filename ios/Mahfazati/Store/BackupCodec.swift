import Foundation

/// صيغة النسخ الاحتياطي — متوافقة مع نسخة أندرويد (`masarify-backup` v1)
/// حتى يستطيع المستخدم نقل بياناته بين الأجهزة بملف واحد.
///
/// مفتاح API مستبعد دائمًا: لا يُكتب في أي ملف يُشارَك.
enum BackupCodec {

    struct Parsed {
        let accounts: [Account]
        let transactions: [Transaction]
        let plan: PlanSettings
        let theme: ThemeColor?
    }

    private struct Payload: Codable {
        let format: String
        // ملاحظة: لا يوجد حقل للمفتاح هنا بقصد — المفتاح لا يُصدَّر أبدًا.
        let schemaVersion: Int
        let exportedAt: String
        let accounts: [AccountDTO]
        let transactions: [TransactionDTO]
        let plan: PlanDTO
        let theme: String?
    }

    private struct AccountDTO: Codable {
        let id: String
        let name: String
        let currentBalance: Int64
        let createdAt: String?
    }

    private struct TransactionDTO: Codable {
        let id: String
        let type: String
        let amount: Int64
        let accountId: String
        let category: String
        let merchant: String?
        let note: String?
        let timestamp: String?
        let source: String?
    }

    private struct PlanDTO: Codable {
        let salaryAmount: Int64
        let salaryDay: Int
        let savingsTarget: Int64
        let upcomingCommitments: Int64
    }

    // ── التصدير ──────────────────────────────────────────────────────────
    static func encode(accounts: [Account], transactions: [Transaction],
                       plan: PlanSettings, theme: ThemeColor) throws -> Data {
        let iso = ISO8601DateFormatter()
        let payload = Payload(
            format: "masarify-backup",
            schemaVersion: 1,
            exportedAt: iso.string(from: Date()),
            accounts: accounts.map {
                AccountDTO(id: $0.id.uuidString, name: $0.name,
                           currentBalance: $0.currentBalance,
                           createdAt: iso.string(from: $0.createdAt))
            },
            transactions: transactions.map {
                TransactionDTO(id: $0.id.uuidString, type: $0.type.rawValue,
                               amount: $0.amount, accountId: $0.accountID.uuidString,
                               category: $0.category, merchant: $0.merchant,
                               note: $0.note, timestamp: iso.string(from: $0.timestamp),
                               source: $0.source)
            },
            plan: PlanDTO(salaryAmount: plan.salaryAmount, salaryDay: plan.salaryDay,
                          savingsTarget: plan.savingsTarget,
                          upcomingCommitments: plan.upcomingCommitments),
            theme: theme.rawValue
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(payload)
    }

    // ── الاستيراد ────────────────────────────────────────────────────────
    static func decode(_ data: Data) throws -> Parsed {
        let payload = try JSONDecoder().decode(Payload.self, from: data)
        guard payload.format == "masarify-backup" else {
            throw NSError(domain: "Backup", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "الملف ليس نسخة احتياطية من محفظتي"])
        }
        let iso = ISO8601DateFormatter()
        let accounts = payload.accounts.compactMap { dto -> Account? in
            guard let id = UUID(uuidString: dto.id) else { return nil }
            return Account(id: id, name: dto.name, currentBalance: dto.currentBalance,
                           createdAt: dto.createdAt.flatMap(iso.date(from:)) ?? Date())
        }
        let transactions = payload.transactions.compactMap { dto -> Transaction? in
            guard let id = UUID(uuidString: dto.id),
                  let accountID = UUID(uuidString: dto.accountId),
                  let type = TxType(rawValue: dto.type) else { return nil }
            return Transaction(id: id, type: type, amount: dto.amount, accountID: accountID,
                               category: dto.category,
                               merchant: dto.merchant ?? "", note: dto.note ?? "",
                               timestamp: dto.timestamp.flatMap(iso.date(from:)) ?? Date(),
                               source: dto.source ?? "MANUAL")
        }
        let plan = PlanSettings(salaryAmount: payload.plan.salaryAmount,
                                salaryDay: payload.plan.salaryDay,
                                savingsTarget: payload.plan.savingsTarget,
                                upcomingCommitments: payload.plan.upcomingCommitments)
        return Parsed(accounts: accounts, transactions: transactions, plan: plan,
                      theme: payload.theme.flatMap { ThemeColor(rawValue: $0) })
    }
}
