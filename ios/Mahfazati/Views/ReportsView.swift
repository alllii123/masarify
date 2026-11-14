import SwiftUI
import Charts

struct ReportsView: View {
    @EnvironmentObject private var store: AppStore

    private var snapshot: MoneySnapshot { store.snapshot }

    private var monthTransactions: [Transaction] {
        store.transactions.filter { $0.timestamp >= snapshot.cycleStart }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Space.gap) {
                    summary
                    categoryChart
                    dailyChart
                    topMerchants
                }
                .padding(.horizontal, Space.page)
                .padding(.bottom, 30)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("التقارير")
        }
    }

    // ── الملخص ───────────────────────────────────────────────────────────
    private var summary: some View {
        HStack(spacing: 10) {
            summaryTile("مصروف", Currency.format(snapshot.cycleExpense), .red)
            summaryTile("دخل", Currency.format(snapshot.cycleIncome), .green)
            summaryTile("عدد العمليات", "\(monthTransactions.count)", store.theme.primary)
        }
        .padding(.top, 8)
    }

    private func summaryTile(_ title: String, _ value: String, _ tint: Color) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.footnote.weight(.bold))
                .foregroundStyle(tint)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(title).font(.system(size: 9)).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 13)
        .background(Color(.secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // ── مخطط التصنيفات ───────────────────────────────────────────────────
    private var categoryChart: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("الصرف حسب التصنيف").font(.subheadline.weight(.semibold))

            if snapshot.spendByCategory.isEmpty {
                Text("ما فيه مصاريف لهذي الدورة بعد.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 24)
            } else {
                Chart {
                    ForEach(Array(snapshot.spendByCategory.prefix(6).enumerated()), id: \.offset) { _, item in
                        SectorMark(
                        angle: .value("المبلغ", Double(item.amount)),
                        innerRadius: .ratio(0.55),
                            angularInset: 2
                        )
                        .cornerRadius(5)
                        .foregroundStyle(by: .value("التصنيف", item.category))
                        .annotation(position: .overlay) {
                            if Double(item.amount) / Double(max(1, snapshot.cycleExpense)) > 0.12 {
                                Text("\(Int(Double(item.amount) / Double(max(1, snapshot.cycleExpense)) * 100))%")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                        }
                    }
                }
                .chartLegend(position: .bottom, spacing: 10)
                .frame(height: 230)
            }
        }
        .padding(Space.card)
        .background(Color(.secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    // ── مخطط الأيام ──────────────────────────────────────────────────────
    private var dailyTotals: [(day: Date, amount: Int64)] {
        let calendar = Calendar.current
        let groups = Dictionary(grouping: monthTransactions.filter { $0.type.isExpense }) {
            calendar.startOfDay(for: $0.timestamp)
        }
        return groups
            .map { (day: $0.key, amount: $0.value.reduce(Int64(0)) { $0 + $1.amount }) }
            .sorted { $0.day < $1.day }
    }

    private var dailyChart: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("الصرف اليومي").font(.subheadline.weight(.semibold))
            if dailyTotals.isEmpty {
                Text("لا توجد بيانات.")
                    .font(.caption).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity).padding(.vertical, 18)
            } else {
                Chart {
                    ForEach(dailyTotals, id: \.day) { item in
                        BarMark(
                            x: .value("اليوم", item.day, unit: .day),
                            y: .value("المبلغ", Double(item.amount) / 100)
                        )
                        .foregroundStyle(store.theme.primaryLight.gradient)
                        .cornerRadius(4)
                    }
                }
                .chartYAxis {
                    AxisMarks(position: .leading)
                }
                .frame(height: 170)
            }
        }
        .padding(Space.card)
        .background(Color(.secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    // ── أكثر الأماكن ─────────────────────────────────────────────────────
    private var topMerchants: some View {
        let merchants = Dictionary(grouping: monthTransactions.filter { $0.type.isExpense }) {
            $0.merchant.isEmpty ? $0.category : $0.merchant
        }
        .map { (name: $0.key, total: $0.value.reduce(Int64(0)) { $0 + $1.amount }) }
        .sorted { $0.total > $1.total }
        .prefix(5)

        return VStack(alignment: .leading, spacing: 12) {
            Text("أكثر الأماكن صرفًا").font(.subheadline.weight(.semibold))
            if merchants.isEmpty {
                Text("لا توجد بيانات.").font(.caption).foregroundStyle(.secondary)
            } else {
                ForEach(Array(merchants.enumerated()), id: \.element.name) { index, item in
                    HStack {
                        Text("\(index + 1).")
                            .font(.caption2).foregroundStyle(.secondary)
                        Text(item.name).font(.caption)
                        Spacer()
                        Text(Currency.format(item.total))
                            .font(.caption.weight(.semibold))
                    }
                    if index < merchants.count - 1 { Divider() }
                }
            }
        }
        .padding(Space.card)
        .background(Color(.secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}
