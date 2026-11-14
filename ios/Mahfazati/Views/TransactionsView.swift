import SwiftUI

struct TransactionsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var filter: TxType? = nil

    private var filtered: [Transaction] {
        guard let filter else { return store.transactions }
        return store.transactions.filter { $0.type == filter }
    }

    private var grouped: [(title: String, items: [Transaction])] {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ar_QA")
        formatter.dateFormat = "d MMMM yyyy"
        let groups = Dictionary(grouping: filtered) { formatter.string(from: $0.timestamp) }
        return groups
            .map { (title: $0.key, items: $0.value.sorted { $0.timestamp > $1.timestamp }) }
            .sorted { ($0.items.first?.timestamp ?? .distantPast) > ($1.items.first?.timestamp ?? .distantPast) }
    }

    var body: some View {
        NavigationStack {
            Group {
                if store.transactions.isEmpty {
                    ContentUnavailableView(
                        "ما فيه عمليات بعد",
                        systemImage: "tray",
                        description: Text("أضف أول عملية من الزر تحت، أو قل للمساعد «أضف مصروف 50 بقالة».")
                    )
                } else {
                    List {
                        ForEach(grouped, id: \.title) { section in
                            Section(section.title) {
                                ForEach(section.items) { tx in
                                    TransactionRow(transaction: tx)
                                        .swipeActions(edge: .trailing) {
                                            Button(role: .destructive) {
                                                store.deleteTransaction(tx)
                                            } label: {
                                                Label("حذف", systemImage: "trash")
                                            }
                                        }
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("العمليات")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("الكل") { filter = nil }
                        Button("مصروف") { filter = .expense }
                        Button("دخل") { filter = .income }
                    } label: {
                        Image(systemName: "line.3.horizontal.decrease.circle")
                    }
                }
            }
        }
    }
}

struct TransactionRow: View {
    let transaction: Transaction

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: transaction.type.isExpense ? "arrow.up.right" : "arrow.down.left")
                .font(.caption.weight(.bold))
                .foregroundStyle(transaction.type.isExpense ? .red : .green)
                .frame(width: 34, height: 34)
                .background((transaction.type.isExpense ? Color.red : Color.green).opacity(0.12),
                            in: Circle())

            VStack(alignment: .leading, spacing: 2) {
                Text(transaction.merchant.isEmpty ? transaction.category : transaction.merchant)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
                HStack(spacing: 5) {
                    Text(transaction.category)
                    Text("•")
                    Text(transaction.timestamp.formatted(.dateTime.hour().minute()))
                    if transaction.source == "ASSISTANT" {
                        Text("•").foregroundStyle(.blue)
                        Text("مساعد")
                    }
                }
                .font(.caption2)
                .foregroundStyle(.secondary)
            }

            Spacer()

            Text("\(transaction.type.isExpense ? "−" : "+")\(Currency.format(transaction.amount))")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(transaction.type.isExpense ? .primary : .green)
        }
        .padding(.vertical, 3)
    }
}
