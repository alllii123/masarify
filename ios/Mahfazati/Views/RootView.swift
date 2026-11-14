import SwiftUI

struct RootView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showSplash = true

    var body: some View {
        ZStack {
            if showSplash {
                SplashView { showSplash = false }
                    .transition(.opacity)
            } else {
                MainTabView()
                    .transition(.opacity)
            }
        }
        .tint(store.theme.primary)
        .preferredColorScheme(store.appearance.colorScheme)
        .animation(.easeInOut(duration: 0.28), value: showSplash)
    }
}

struct MainTabView: View {
    @EnvironmentObject private var store: AppStore
    @State private var tab: Int = 0
    @State private var showAdd = false
    @State private var showSettings = false

    var body: some View {
        TabView(selection: $tab) {
            HomeView(onAssistant: { tab = 2 }, onSettings: { showSettings = true })
                .tabItem { Label("الرئيسية", systemImage: "house.fill") }
                .tag(0)

            TransactionsView()
                .tabItem { Label("العمليات", systemImage: "list.bullet.rectangle") }
                .tag(1)

            AssistantView()
                .tabItem { Label("المساعد", systemImage: "sparkles") }
                .tag(2)

            PlanningView()
                .tabItem { Label("الخطة", systemImage: "target") }
                .tag(3)

            ReportsView()
                .tabItem { Label("التقارير", systemImage: "chart.pie.fill") }
                .tag(4)
        }
        .safeAreaInset(edge: .bottom) {
            if tab != 2 {
                AddButtonBar { showAdd = true }
            }
        }
        .sheet(isPresented: $showAdd) {
            AddTransactionView { showAdd = false }
                .presentationDetents([.large])
        }
        .sheet(isPresented: $showSettings) {
            SettingsView { showSettings = false }
        }
    }
}

/// زر «إضافة عملية» العائم فوق شريط التبويبات.
private struct AddButtonBar: View {
    let onTap: () -> Void
    @EnvironmentObject private var store: AppStore

    var body: some View {
        Button(action: onTap) {
            Label("إضافة عملية", systemImage: "plus.circle.fill")
                .font(.system(size: 15, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(store.theme.primary, in: Capsule())
                .foregroundStyle(.white)
        }
        .padding(.horizontal, Space.page)
        .padding(.bottom, 4)
    }
}
