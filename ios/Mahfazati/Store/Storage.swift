import Foundation
import Security

/// تخزين JSON على القرص في مجلد Application Support.
struct Storage {
    private let folder: URL

    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory,
                                            in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        folder = base.appendingPathComponent("Mahfazati", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    }

    private func url(_ name: String) -> URL { folder.appendingPathComponent("\(name).json") }

    private func read<T: Decodable>(_ name: String, as type: T.Type) -> T? {
        guard let data = try? Data(contentsOf: url(name)) else { return nil }
        return try? JSONDecoder.iso.decode(type, from: data)
    }

    private func write<T: Encodable>(_ name: String, _ value: T) {
        guard let data = try? JSONEncoder.iso.encode(value) else { return }
        try? data.write(to: url(name), options: .atomic)
    }

    func loadAccounts() -> [Account]? { read("accounts", as: [Account].self) }
    func saveAccounts(_ value: [Account]) { write("accounts", value) }

    func loadTransactions() -> [Transaction]? { read("transactions", as: [Transaction].self) }
    func saveTransactions(_ value: [Transaction]) { write("transactions", value) }

    func loadPlan() -> PlanSettings? { read("plan", as: PlanSettings.self) }
    func savePlan(_ value: PlanSettings) { write("plan", value) }

    func loadAi() -> AiSettings? { read("ai", as: AiSettings.self) }
    func saveAi(_ value: AiSettings) { write("ai", value) }

    func loadTheme() -> ThemeColor {
        let raw = UserDefaults.standard.string(forKey: "theme") ?? ThemeColor.blue.rawValue
        return ThemeColor(rawValue: raw) ?? .blue
    }

    func saveTheme(_ value: ThemeColor) {
        UserDefaults.standard.set(value.rawValue, forKey: "theme")
    }

    func loadAppearance() -> AppearanceMode {
        let raw = UserDefaults.standard.string(forKey: "appearance") ?? AppearanceMode.system.rawValue
        return AppearanceMode(rawValue: raw) ?? .system
    }

    func saveAppearance(_ value: AppearanceMode) {
        UserDefaults.standard.set(value.rawValue, forKey: "appearance")
    }
}

/// مفتاح API يُحفظ في Keychain فقط — لا يظهر في أي ملف أو نسخة احتياطية.
struct KeychainStore {
    private let service = "com.mahfazati.app.apikey"
    private let account = "gemini"

    func write(_ value: String) throws {
        guard !value.isEmpty else {
            delete()
            return
        }
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
        var attributes = query
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw NSError(domain: "Keychain", code: Int(status))
        }
    }

    func read() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func delete() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}

extension JSONEncoder {
    static var iso: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }
}

extension JSONDecoder {
    static var iso: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}
