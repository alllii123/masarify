import Foundation

/// لقطة مالية واحدة تُبنى من الحسابات والحركات والخطة.
/// الحساب مطابق تمامًا لـ MoneySnapshot في نسخة أندرويد حتى تتطابق الأرقام بين المنصتين.
struct MoneySnapshot {
    let totalBalance: Int64
    let commitments: Int64
    let savingsTarget: Int64
    let salaryDay: Int
    let daysLeft: Int
    let cycleDays: Int
    let cycleElapsedDays: Int
    let cycleExpense: Int64
    let cycleIncome: Int64
    let records: [SpendRecord]
    let cycleStart: Date

    /// المتاح بأمان = الرصيد − الالتزامات − الادخار، ولا ينزل تحت الصفر.
    var safe: Int64 { max(0, totalBalance - commitments - savingsTarget) }

    /// المصروف اليومي المسموح حتى نهاية الدورة.
    var dailyAllowance: Int64 { daysLeft > 0 ? safe / Int64(daysLeft) : safe }

    var expenses: [SpendRecord] { records.filter(\.isExpense) }
    var income: [SpendRecord] { records.filter { !$0.isExpense } }

    var spendByCategory: [(category: String, amount: Int64)] {
        Dictionary(grouping: expenses) { $0.category.isEmpty ? "أخرى" : $0.category }
            .map { (category: $0.key, amount: $0.value.reduce(0) { $0 + $1.amount }) }
            .sorted { $0.amount > $1.amount }
    }

    var averageDailySpend: Int64 {
        cycleElapsedDays > 0 ? cycleExpense / Int64(cycleElapsedDays) : cycleExpense
    }

    /// كم سيبقى من المتاح لو استمر بنفس معدل الصرف.
    var projectedLeftover: Int64 {
        max(0, safe - averageDailySpend * Int64(daysLeft))
    }

    /// نسبة استهلاك المتاح على الأيام المتبقية.
    var burnRatio: Int {
        guard safe > 0 else { return 0 }
        return Int((averageDailySpend * Int64(daysLeft)) * 100 / safe)
    }

    var projectedCycleExpense: Int64 {
        guard cycleElapsedDays > 0 else { return cycleExpense }
        return cycleExpense * Int64(cycleDays) / Int64(cycleElapsedDays)
    }

    static let empty = MoneySnapshot(
        totalBalance: 0, commitments: 0, savingsTarget: 0, salaryDay: 27,
        daysLeft: 0, cycleDays: 30, cycleElapsedDays: 1,
        cycleExpense: 0, cycleIncome: 0, records: [], cycleStart: Date()
    )
}

struct SpendRecord: Identifiable {
    let id = UUID()
    let category: String
    let merchant: String
    let amount: Int64
    let isExpense: Bool
}

enum CycleMath {
    /// بداية دورة الراتب: أقرب يوم راتب مضى (أو الشهر الماضي).
    static func cycleStart(salaryDay: Int, now: Date = Date(), calendar: Calendar = .current) -> Date {
        let day = min(max(salaryDay, 1), 28)
        var comps = calendar.dateComponents([.year, .month], from: now)
        comps.day = day
        comps.hour = 0
        comps.minute = 0
        comps.second = 0
        guard let thisMonth = calendar.date(from: comps) else { return now }
        if thisMonth <= now { return thisMonth }
        return calendar.date(byAdding: .month, value: -1, to: thisMonth) ?? thisMonth
    }

    static func daysLeft(salaryDay: Int, now: Date = Date(), calendar: Calendar = .current) -> Int {
        let day = min(max(salaryDay, 1), 28)
        var comps = calendar.dateComponents([.year, .month], from: now)
        comps.day = day
        comps.hour = 0
        comps.minute = 0
        comps.second = 0
        guard var next = calendar.date(from: comps) else { return 0 }
        if next <= now {
            next = calendar.date(byAdding: .month, value: 1, to: next) ?? next
        }
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: now),
                                          to: calendar.startOfDay(for: next)).day ?? 0
        return max(0, days)
    }
}

extension MoneySnapshot {
    /// يبني اللقطة من بيانات المستخدم الفعلية.
    init(accounts: [Account], transactions: [Transaction], plan: PlanSettings, now: Date = Date()) {
        let total = accounts.reduce(Int64(0)) { $0 + $1.currentBalance }
        let start = CycleMath.cycleStart(salaryDay: plan.salaryDay, now: now)
        let elapsed = max(1, Calendar.current.dateComponents([.day], from: start, to: now).day ?? 1)
        let left = CycleMath.daysLeft(salaryDay: plan.salaryDay, now: now)

        let cycleTx = transactions.filter { $0.timestamp >= start }
        let expense = cycleTx.filter { $0.type.isExpense }.reduce(Int64(0)) { $0 + $1.amount }
        let income = cycleTx.filter { !$0.type.isExpense }.reduce(Int64(0)) { $0 + $1.amount }

        let records = cycleTx.map {
            SpendRecord(category: $0.category, merchant: $0.merchant,
                        amount: $0.amount, isExpense: $0.type.isExpense)
        }

        self.init(
            totalBalance: total,
            commitments: plan.upcomingCommitments,
            savingsTarget: plan.savingsTarget,
            salaryDay: plan.salaryDay,
            daysLeft: left,
            cycleDays: elapsed + left,
            cycleElapsedDays: elapsed,
            cycleExpense: expense,
            cycleIncome: income,
            records: records,
            cycleStart: start
        )
    }
}
