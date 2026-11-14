import Foundation

/// نوع الحركة — نفس القيم المستخدمة في نسخة أندرويد حتى تتوافق النسخ الاحتياطية.
enum TxType: String, Codable, CaseIterable {
    case expense = "EXPENSE"
    case income = "INCOME"

    var isExpense: Bool { self == .expense }

    var arabicLabel: String {
        switch self {
        case .expense: return "مصروف"
        case .income: return "دخل"
        }
    }
}

struct Account: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var name: String
    var currentBalance: Int64
    var createdAt: Date = Date()
}

struct Transaction: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var type: TxType
    var amount: Int64
    var accountID: UUID
    var category: String
    var merchant: String = ""
    var note: String = ""
    var timestamp: Date = Date()
    var source: String = "MANUAL"

    var signedAmount: Int64 { type.isExpense ? -amount : amount }
}

/// التصنيفات — مطابقة تمامًا لتلك الموجودة في نسخة أندرويد
/// حتى تبقى التقارير والتحليلات متسقة بين المنصتين.
enum Categories {
    static let expense = ["بقالة", "مطاعم", "مواصلات", "تسوق", "منزل", "صحة", "ترفيه", "أخرى"]
    static let income = ["راتب", "دخل إضافي", "تحويل وارد", "أخرى"]

    static func list(for type: TxType) -> [String] {
        type.isExpense ? expense : income
    }
}

/// إعدادات الخطة المالية.
struct PlanSettings: Codable {
    var salaryAmount: Int64 = 0
    var salaryDay: Int = 27
    var savingsTarget: Int64 = 0
    var upcomingCommitments: Int64 = 0
}

enum AiMode: String, Codable, CaseIterable {
    case local = "LOCAL"
    case auto = "AUTO"
    case cloud = "CLOUD"

    var arabicLabel: String {
        switch self {
        case .local: return "محلي"
        case .auto: return "تلقائي"
        case .cloud: return "سحابي"
        }
    }
}

enum AiStyle: String, Codable, CaseIterable {
    case simple = "SIMPLE"
    case friendly = "FRIENDLY"
    case analyst = "ANALYST"

    var arabicLabel: String {
        switch self {
        case .simple: return "بسيط"
        case .friendly: return "ودّي"
        case .analyst: return "تحليلي"
        }
    }
}

struct AiSettings: Codable {
    var mode: AiMode = .auto
    var style: AiStyle = .friendly
    var apiKey: String = ""
    var model: String = "gemini-flash-latest"
    var cloudEnabled: Bool = true
    var localEnabled: Bool = true
    var shareAmounts: Bool = false

    var cloudReady: Bool { cloudEnabled && !apiKey.trimmingCharacters(in: .whitespaces).isEmpty }

    /// المفتاح يُخزَّن في Keychain لا في UserDefaults.
    static func sanitized(_ key: String) -> String {
        key.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
