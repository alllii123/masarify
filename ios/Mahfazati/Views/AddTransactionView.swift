import SwiftUI

struct AddTransactionView: View {
    @EnvironmentObject private var store: AppStore
    let onDone: () -> Void

    @State private var type: TxType = .expense
    @State private var amountText = ""
    @State private var category = Categories.expense[0]
    @State private var merchant = ""
    @State private var note = ""

    private var amount: Int64 { DigitNormalizer.parseMoney(amountText) ?? 0 }
    private var canSave: Bool { amount > 0 }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("النوع", selection: $type) {
                        ForEach(TxType.allCases, id: \.self) { item in
                            Text(item.arabicLabel).tag(item)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section {
                    HStack {
                        Text(Currency.label)
                            .foregroundStyle(.secondary)
                        TextField("0.00", text: $amountText)
                            .keyboardType(.decimalPad)
                            .font(.system(size: 30, weight: .bold, design: .rounded))
                    }
                } header: {
                    Text("المبلغ")
                }

                Section {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 78), spacing: 8)], spacing: 8) {
                        ForEach(Categories.list(for: type), id: \.self) { item in
                            Button {
                                category = item
                            } label: {
                                Text(item)
                                    .font(.caption)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 9)
                                    .background(category == item ? store.theme.primary : Color(.tertiarySystemFill),
                                                in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                                    .foregroundStyle(category == item ? .white : .primary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 4)
                } header: {
                    Text("التصنيف")
                }

                Section {
                    TextField("مثال: ستاربكس", text: $merchant)
                    TextField("ملاحظة (اختياري)", text: $note)
                } header: {
                    Text("تفاصيل")
                }

                Section {
                    Button {
                        save()
                    } label: {
                        HStack {
                            Spacer()
                            Text("حفظ العملية").fontWeight(.semibold)
                            Spacer()
                        }
                    }
                    .disabled(!canSave)
                }
            }
            .navigationTitle("عملية جديدة")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("إلغاء", action: onDone)
                }
            }
            .onChange(of: type) {
                category = Categories.list(for: type)[0]
            }
        }
    }

    private func save() {
        store.addTransaction(type: type, amount: amount, category: category,
                             merchant: merchant, note: note)
        onDone()
    }
}
