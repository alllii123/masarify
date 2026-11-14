import SwiftUI

struct PlanningView: View {
    @EnvironmentObject private var store: AppStore
    @State private var salaryText = ""
    @State private var savingsText = ""
    @State private var commitmentsText = ""
    @State private var salaryDay = 27
    @State private var saved = false

    private var snapshot: MoneySnapshot { store.snapshot }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        Text("الراتب الشهري")
                        Spacer()
                        TextField("0", text: $salaryText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                    }
                    HStack {
                        Text("يوم الراتب")
                        Spacer()
                        Picker("", selection: $salaryDay) {
                            ForEach(1...28, id: \.self) { Text("\($0)").tag($0) }
                        }
                        .labelsHidden()
                    }
                } header: {
                    Text("الدخل")
                }

                Section {
                    HStack {
                        Text("هدف الادخار")
                        Spacer()
                        TextField("0", text: $savingsText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                    }
                    HStack {
                        Text("الالتزامات القادمة")
                        Spacer()
                        TextField("0", text: $commitmentsText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                    }
                } header: {
                    Text("الحجز")
                } footer: {
                    Text("الالتزامات وهدف الادخار يُخصمان من رصيدك لحساب «المتاح بأمان».")
                }

                Section {
                    HStack {
                        Text("المتاح بأمان")
                        Spacer()
                        Text(Currency.format(snapshot.safe)).fontWeight(.semibold)
                    }
                    HStack {
                        Text("تقدر تصرف يوميًا")
                        Spacer()
                        Text(Currency.format(snapshot.dailyAllowance)).fontWeight(.semibold)
                    }
                    HStack {
                        Text("باقي للراتب")
                        Spacer()
                        Text("\(snapshot.daysLeft) يوم")
                    }
                } header: {
                    Text("النتيجة")
                }

                Section {
                    Button {
                        save()
                    } label: {
                        HStack {
                            Spacer()
                            Label(saved ? "تم الحفظ ✓" : "حفظ الخطة", systemImage: "checkmark.circle")
                                .fontWeight(.semibold)
                            Spacer()
                        }
                    }
                    .disabled(saved)
                }
            }
            .navigationTitle("الخطة")
            .onAppear(perform: fill)
        }
    }

    private func fill() {
        salaryText = store.plan.salaryAmount == 0 ? "" : String(Double(store.plan.salaryAmount) / 100)
        savingsText = store.plan.savingsTarget == 0 ? "" : String(Double(store.plan.savingsTarget) / 100)
        commitmentsText = store.plan.upcomingCommitments == 0 ? "" : String(Double(store.plan.upcomingCommitments) / 100)
        salaryDay = store.plan.salaryDay
    }

    private func save() {
        let plan = PlanSettings(
            salaryAmount: DigitNormalizer.parseMoney(salaryText) ?? 0,
            salaryDay: salaryDay,
            savingsTarget: DigitNormalizer.parseMoney(savingsText) ?? 0,
            upcomingCommitments: DigitNormalizer.parseMoney(commitmentsText) ?? 0
        )
        store.updatePlan(plan)
        saved = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { saved = false }
    }
}
