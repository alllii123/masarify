import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    let onClose: () -> Void

    @State private var keyInput = ""
    @State private var keyStatus = ""
    @State private var modelInput = ""
    @State private var models: [String] = []
    @State private var loadingModels = false
    @State private var showImporter = false
    @State private var backupURL: URL?
    @State private var status = ""

    private let client = GeminiClient()

    var body: some View {
        NavigationStack {
            Form {
                appearanceSection
                accountsSection
                aiSection
                if store.ai.cloudReady { cloudSection }
                backupSection
                aboutSection
            }
            .navigationTitle("الإعدادات")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("تم", action: onClose)
                }
            }
            .onAppear {
                keyInput = store.ai.apiKey
                modelInput = store.ai.model
            }
            .fileImporter(isPresented: $showImporter,
                          allowedContentTypes: [.json],
                          allowsMultipleSelection: false) { result in
                handleImport(result)
            }
        }
    }

    // ── المظهر ───────────────────────────────────────────────────────────
    private var appearanceSection: some View {
        Section {
            Picker("لون التطبيق", selection: Binding(
                get: { store.theme },
                set: { store.setTheme($0) }
            )) {
                ForEach(ThemeColor.allCases) { theme in
                    Text(theme.arabicLabel).tag(theme)
                }
            }
            .pickerStyle(.segmented)

            Picker("الوضع", selection: Binding(
                get: { store.appearance },
                set: { store.setAppearance($0) }
            )) {
                ForEach(AppearanceMode.allCases) { mode in
                    Text(mode.arabicLabel).tag(mode)
                }
            }
            .pickerStyle(.segmented)
        } header: {
            Text("المظهر")
        }
    }

    // ── الحسابات ─────────────────────────────────────────────────────────
    private var accountsSection: some View {
        Section {
            ForEach(store.accounts) { account in
                HStack {
                    Text(account.name)
                    Spacer()
                    Text(Currency.format(account.currentBalance))
                        .fontWeight(.semibold)
                        .foregroundStyle(.secondary)
                }
            }
        } header: {
            Text("الحسابات")
        } footer: {
            Text("مجموعك = مجموع أرصدة كل الحسابات.")
        }
    }

    // ── الذكاء الاصطناعي ─────────────────────────────────────────────────
    private var aiSection: some View {
        Section {
            Picker("الوضع", selection: Binding(
                get: { store.ai.mode },
                set: { var ai = store.ai; ai.mode = $0; store.updateAi(ai) }
            )) {
                ForEach(AiMode.allCases, id: \.self) { Text($0.arabicLabel).tag($0) }
            }

            Picker("الأسلوب", selection: Binding(
                get: { store.ai.style },
                set: { var ai = store.ai; ai.style = $0; store.updateAi(ai) }
            )) {
                ForEach(AiStyle.allCases, id: \.self) { Text($0.arabicLabel).tag($0) }
            }

            Toggle("مشاركة الأرقام مع السحابة", isOn: Binding(
                get: { store.ai.shareAmounts },
                set: { var ai = store.ai; ai.shareAmounts = $0; store.updateAi(ai) }
            ))
        } header: {
            Text("المساعد")
        } footer: {
            Text("المساعد المحلي يعمل دائمًا بدون إنترنت. مشاركة الأرقام تُرسل مجاميع فقط ولا تُرسل نص أي رسالة بنكية.")
        }
    }

    private var cloudSection: some View {
        Section {
            SecureField("مفتاح Gemini (يُحفظ في Keychain)", text: $keyInput)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()

            HStack {
                Button("حفظ المفتاح") { saveKey() }
                Spacer()
                Button("فحص") { testKey() }
                    .disabled(keyInput.isEmpty)
            }

            if !keyStatus.isEmpty {
                Text(keyStatus)
                    .font(.caption)
                    .foregroundStyle(keyStatus.contains("✅") ? .green : .red)
            }

            TextField("الموديل", text: $modelInput)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()

            if loadingModels {
                HStack { ProgressView().controlSize(.small); Text("أجلب الموديلات…").font(.caption) }
            } else if !models.isEmpty {
                Picker("موديل مقترح", selection: Binding(
                    get: { modelInput },
                    set: { modelInput = $0; saveModel($0) }
                )) {
                    ForEach(models, id: \.self) { Text($0).tag($0) }
                }
            }

            Button("تحديث قائمة الموديلات") { refreshModels() }
                .disabled(keyInput.isEmpty)
        } header: {
            Text("السحابة")
        } footer: {
            Text("المفتاح لا يُكتب في أي ملف ولا يُضمَّن في التطبيق. يُخزَّن في Keychain الجهاز فقط.")
        }
    }

    // ── النسخ الاحتياطي ──────────────────────────────────────────────────
    private var backupSection: some View {
        Section {
            Button("تصدير نسخة احتياطية") { exportBackup() }
            Button("استيراد نسخة") { showImporter = true }
            if let url = backupURL {
                ShareLink(item: url) {
                    Label("مشاركة الملف (Drive / AirDrop)", systemImage: "square.and.arrow.up")
                }
            }
            if !status.isEmpty {
                Text(status).font(.caption).foregroundStyle(.secondary)
            }
        } header: {
            Text("النسخ الاحتياطي")
        } footer: {
            Text("الصيغة متوافقة مع نسخة أندرويد، فتقدر تنقل بياناتك بين الجوالات. المفتاح مستبعد من النسخة.")
        }
    }

    private var aboutSection: some View {
        Section {
            HStack {
                Text("الإصدار")
                Spacer()
                Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.4.8")
                    .foregroundStyle(.secondary)
            }
            HStack {
                Text("العملة")
                Spacer()
                Text("الريال القطري (ر.ق)").foregroundStyle(.secondary)
            }
        } header: {
            Text("عن التطبيق")
        }
    }

    // ── الأفعال ──────────────────────────────────────────────────────────
    private func saveKey() {
        var ai = store.ai
        ai.apiKey = AiSettings.sanitized(keyInput)
        store.updateAi(ai)
        keyStatus = ai.apiKey.isEmpty ? "المفتاح فارغ" : "تم الحفظ في Keychain ✅"
    }

    private func saveModel(_ model: String) {
        var ai = store.ai
        ai.model = model
        store.updateAi(ai)
    }

    private func testKey() {
        keyStatus = "جاري الفحص…"
        Task {
            let result = await client.ask(apiKey: AiSettings.sanitized(keyInput),
                                          model: modelInput.isEmpty ? "gemini-flash-latest" : modelInput,
                                          style: "مختصر", context: "snapshot: currency=QAR",
                                          history: [.init(role: "user", text: "قل: جاهز")])
            await MainActor.run {
                switch result {
                case .success(let reply):
                    keyStatus = "المفتاح يعمل ✅ — \(reply.prefix(30))"
                case .failure(let error):
                    keyStatus = error.message
                }
            }
        }
    }

    private func refreshModels() {
        loadingModels = true
        Task {
            let result = await client.listModels(apiKey: AiSettings.sanitized(keyInput))
            await MainActor.run {
                loadingModels = false
                switch result {
                case .success(let list):
                    models = list
                    keyStatus = list.isEmpty ? "المفتاح يعمل لكن ما رجعت موديلات" : "تم: \(list.count) موديل ✅"
                case .failure(let error):
                    keyStatus = error.message
                }
            }
        }
    }

    private func exportBackup() {
        do {
            let data = try store.exportBackup()
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("mahfazati-backup.json")
            try data.write(to: url, options: .atomic)
            backupURL = url
            status = "تم إنشاء الملف — استخدم زر المشاركة."
        } catch {
            status = "فشل التصدير: \(error.localizedDescription)"
        }
    }

    private func handleImport(_ result: Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            do {
                let data = try Data(contentsOf: url)
                try store.importBackup(data, replaceExisting: false)
                status = "تم الاستيراد ✅"
            } catch {
                status = error.localizedDescription
            }
        case .failure(let error):
            status = error.localizedDescription
        }
    }
}
