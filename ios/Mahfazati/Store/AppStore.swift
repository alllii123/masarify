import Foundation
import SwiftUI

/// المخزن المركزي: مصدر الحقيقة الوحيد لكل الشاشات.
@MainActor
final class AppStore: ObservableObject {
    @Published private(set) var accounts: [Account] = []
    @Published private(set) var transactions: [Transaction] = []
    @Published var plan = PlanSettings()
    @Published var ai = AiSettings()
    @Published var theme: ThemeColor = .blue
    @Published var appearance: AppearanceMode = .system
    @Published var chat: [ChatMessage] = []

    private let storage = Storage()
    private let keychain = KeychainStore()

    init() {
        load()
    }

    // ── التحميل والحفظ ──────────────────────────────────────────────────
    private func load() {
        if let saved = storage.loadAccounts() { accounts = saved }
        if let saved = storage.loadTransactions() { transactions = saved }
        if let saved = storage.loadPlan() { plan = saved }
        if let saved = storage.loadAi() { ai = saved }
        ai.apiKey = keychain.read() ?? ""
        theme = storage.loadTheme()
        appearance = storage.loadAppearance()
        if accounts.isEmpty {
            accounts = [Account(name: "الحساب الرئيسي", currentBalance: 0)]
        }
    }

    private func persist() {
        storage.saveAccounts(accounts)
        storage.saveTransactions(transactions)
        storage.savePlan(plan)
        var safeAi = ai
        safeAi.apiKey = ""
        storage.saveAi(safeAi)
        do { try keychain.write(ai.apiKey) } catch {
            print("Keychain write failed: \(error)")
        }
    }

    // ── الحساب الحالي ───────────────────────────────────────────────────
    var primaryAccount: Account? { accounts.first }

    var snapshot: MoneySnapshot {
        MoneySnapshot(accounts: accounts, transactions: transactions, plan: plan)
    }

    // ── الحركات ─────────────────────────────────────────────────────────
    @discardableResult
    func addTransaction(type: TxType, amount: Int64, category: String,
                        merchant: String, note: String, accountID: UUID? = nil,
                        date: Date = Date(), source: String = "MANUAL") -> Transaction? {
        guard amount > 0 else { return nil }
        guard let account = accountID.flatMap({ id in accounts.first { $0.id == id } })
                ?? primaryAccount else { return nil }

        let tx = Transaction(type: type, amount: amount, accountID: account.id,
                             category: category, merchant: merchant, note: note,
                             timestamp: date, source: source)
        transactions.insert(tx, at: 0)
        applyBalance(tx, reverse: false)
        persist()
        return tx
    }

    func deleteTransaction(_ tx: Transaction) {
        applyBalance(tx, reverse: true)
        transactions.removeAll { $0.id == tx.id }
        persist()
    }

    func updateTransaction(_ tx: Transaction, newAmount: Int64, newCategory: String?) {
        guard newAmount > 0 else { return }
        applyBalance(tx, reverse: true)
        if let index = transactions.firstIndex(where: { $0.id == tx.id }) {
            transactions[index].amount = newAmount
            if let category = newCategory, !category.isEmpty {
                transactions[index].category = category
            }
            applyBalance(transactions[index], reverse: false)
        }
        persist()
    }

    private func applyBalance(_ tx: Transaction, reverse: Bool) {
        guard let index = accounts.firstIndex(where: { $0.id == tx.accountID }) else { return }
        let delta = tx.signedAmount * (reverse ? -1 : 1)
        accounts[index].currentBalance += delta
    }

    // ── الحسابات ────────────────────────────────────────────────────────
    func addAccount(name: String, openingBalance: Int64) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        accounts.append(Account(name: trimmed, currentBalance: openingBalance))
        persist()
    }

    func setBalance(accountID: UUID, balance: Int64) {
        guard let index = accounts.firstIndex(where: { $0.id == accountID }) else { return }
        accounts[index].currentBalance = balance
        persist()
    }

    // ── الإعدادات ───────────────────────────────────────────────────────
    func updatePlan(_ plan: PlanSettings) {
        self.plan = plan
        persist()
    }

    func updateAi(_ ai: AiSettings) {
        self.ai = ai
        persist()
    }

    func setTheme(_ theme: ThemeColor) {
        self.theme = theme
        storage.saveTheme(theme)
    }

    func setAppearance(_ mode: AppearanceMode) {
        self.appearance = mode
        storage.saveAppearance(mode)
    }

    // ── المحادثة ────────────────────────────────────────────────────────
    func appendMessage(_ message: ChatMessage) {
        chat.append(message)
    }

    func clearChat() {
        chat.removeAll()
    }

    // ── النسخ الاحتياطي ─────────────────────────────────────────────────
    func exportBackup() throws -> Data {
        try BackupCodec.encode(accounts: accounts, transactions: transactions,
                               plan: plan, theme: theme)
    }

    func importBackup(_ data: Data, replaceExisting: Bool) throws {
        let parsed = try BackupCodec.decode(data)
        if replaceExisting {
            accounts = []
            transactions = []
        }
        for account in parsed.accounts where !accounts.contains(where: { $0.id == account.id }) {
            accounts.append(account)
        }
        let validIDs = Set(accounts.map(\.id))
        for tx in parsed.transactions where validIDs.contains(tx.accountID)
            && !transactions.contains(where: { $0.id == tx.id }) {
            transactions.append(tx)
        }
        plan = parsed.plan
        persist()
    }
}
