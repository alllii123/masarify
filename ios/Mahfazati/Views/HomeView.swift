import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: AppStore
    let onAssistant: () -> Void
    let onSettings: () -> Void

    private var snapshot: MoneySnapshot { store.snapshot }
    private var insights: [Insight] { LocalBrain.insights(snapshot) }

    var body: some View {
        ScrollView {
            VStack(spacing: Space.gap) {
                header
                heroCard

                HStack(spacing: 12) {
                    MetricTile(title: "مصروف الدورة",
                               value: Currency.format(snapshot.cycleExpense),
                               caption: snapshot.cycleIncome > 0
                                   ? "من \(Currency.format(snapshot.cycleIncome))"
                                   : "دون دخل مسجل",
                               icon: "arrow.up.right",
                               tint: .red)
                    MetricTile(title: "الالتزامات القادمة",
                               value: Currency.format(store.plan.upcomingCommitments),
                               caption: "محجوزة من رصيدك",
                               icon: "arrow.down.left",
                               tint: .orange)
                }

                if !insights.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("ملاحظات من أرقامك")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.secondary)
                        ForEach(insights) { InsightCard(insight: $0) }
                    }
                }

                assistantCard

                VStack(alignment: .leading, spacing: 10) {
                    Text("خطتك").font(.headline)
                    VStack(spacing: 12) {
                        PlanRow(label: "هدف الادخار",
                                value: Currency.format(store.plan.savingsTarget),
                                icon: "banknote")
                        Divider()
                        PlanRow(label: "الالتزامات المحجوزة",
                                value: Currency.format(store.plan.upcomingCommitments),
                                icon: "calendar.badge.clock")
                        if store.plan.salaryAmount > 0 {
                            Divider()
                            PlanRow(label: "الراتب",
                                    value: Currency.format(store.plan.salaryAmount),
                                    icon: "creditcard")
                        }
                    }
                    .padding(Space.card)
                    .background(Color(.secondarySystemGroupedBackground),
                                in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
            }
            .padding(.horizontal, Space.page)
            .padding(.bottom, 28)
        }
        .background(Color(.systemGroupedBackground))
    }

    // ── الترويسة ─────────────────────────────────────────────────────────
    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text("أهلًا فيك 👋").font(.title2.weight(.bold))
                Text("ملخص مالك لهذي الدورة")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button(action: onSettings) {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 17))
                    .foregroundStyle(store.theme.primary)
                    .frame(width: 40, height: 40)
                    .background(store.theme.container, in: Circle())
            }
        }
        .padding(.top, 8)
    }

    // ── بطاقة المجموع ─────────────────────────────────────────────────────
    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("مجموعك")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.92))
                Spacer()
                Text(snapshot.daysLeft == 0 ? "الراتب اليوم" : "باقي \(snapshot.daysLeft) يوم")
                    .font(.caption2)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 11)
                    .padding(.vertical, 6)
                    .background(.white.opacity(0.18), in: Capsule())
            }

            Text(Currency.format(snapshot.totalBalance))
                .font(.system(size: 38, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .padding(.top, 6)

            Text("في كل حساباتك")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.68))

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("المتاح بأمان")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.white.opacity(0.85))
                    Text("بعد حجز الالتزامات وهدف الادخار")
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.55))
                }
                Spacer()
                Text(Currency.format(snapshot.safe))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.white)
            }
            .padding(.top, 14)

            HStack(spacing: 10) {
                heroPill(label: "تقدر تصرف يوميًا",
                         hint: snapshot.daysLeft > 0 ? "المتاح ÷ \(snapshot.daysLeft) يوم" : "",
                         value: Currency.format(snapshot.dailyAllowance))
                heroPill(label: "مصروف الدورة",
                         hint: snapshot.cycleIncome > 0 ? "من \(Currency.format(snapshot.cycleIncome))" : "دون دخل مسجل",
                         value: Currency.format(snapshot.cycleExpense))
            }
            .padding(.top, 16)

            if snapshot.cycleIncome > 0 {
                let ratio = min(1, Double(snapshot.cycleExpense) / Double(snapshot.cycleIncome))
                ProgressView(value: ratio)
                    .tint(.white)
                    .background(.white.opacity(0.22))
                    .clipShape(Capsule())
                    .padding(.top, 16)
                Text("صرفت \(Int(ratio * 100))% من دخل الدورة")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.78))
                    .padding(.top, 6)
            }
        }
        .padding(22)
        .background(
            LinearGradient(colors: store.theme.heroGradient,
                           startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 26, style: .continuous)
        )
    }

    private func heroPill(label: String, hint: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.caption2).foregroundStyle(.white.opacity(0.78))
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(.white)
            if !hint.isEmpty {
                Text(hint).font(.system(size: 9)).foregroundStyle(.white.opacity(0.5))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(.white.opacity(0.14), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // ── بطاقة المساعد ─────────────────────────────────────────────────────
    private var assistantCard: some View {
        Button(action: onAssistant) {
            HStack(spacing: 12) {
                Image(systemName: "sparkles")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(store.theme.primary)
                    .frame(width: 42, height: 42)
                    .background(store.theme.container, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text("مساعد محفظتي").font(.subheadline.weight(.semibold))
                    Text("اسأل عن أرقامك أو قل «أضف مصروف 50 بقالة»")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.leading)
                }
                Spacer()
                Image(systemName: "chevron.left")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .padding(Space.card)
            .background(Color(.secondarySystemGroupedBackground),
                        in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

struct MetricTile: View {
    let title: String
    let value: String
    let caption: String
    let icon: String
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: icon)
                .font(.caption.weight(.bold))
                .foregroundStyle(tint)
                .frame(width: 26, height: 26)
                .background(tint.opacity(0.12), in: Circle())
            Text(value)
                .font(.subheadline.weight(.bold))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(title).font(.caption2).foregroundStyle(.secondary)
            Text(caption).font(.system(size: 9)).foregroundStyle(.secondary.opacity(0.8))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct InsightCard: View {
    let insight: Insight

    private var tint: Color {
        switch insight.tone {
        case .good: return .green
        case .warn: return .orange
        case .neutral: return .blue
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: insight.tone == .warn ? "exclamationmark.triangle.fill" : "lightbulb.fill")
                .font(.caption)
                .foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 3) {
                Text(insight.title).font(.footnote.weight(.semibold))
                Text(insight.body)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(13)
        .background(Color(.secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct PlanRow: View {
    let label: String
    let value: String
    let icon: String

    var body: some View {
        HStack {
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(width: 24)
            Text(label).font(.footnote)
            Spacer()
            Text(value).font(.footnote.weight(.semibold))
        }
    }
}
