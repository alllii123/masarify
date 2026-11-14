import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// عميل Gemini — نفس سلّم التوافق وسياسة إعادة المحاولة في نسخة أندرويد.
///
/// الدروس المنقولة:
/// 1) `thinkingConfig` غير مدعوم في كل الموديلات ⇒ لا نرسله أبدًا.
/// 2) 400 ⇒ ننزل درجة في السلّم (FULL → NO_SYSTEM → MINIMAL).
/// 3) 429/5xx ⇒ نعيد ثم ننتقل لموديل آخر (لا نستسلم عند أول خطأ عابر).
/// 4) 401/403 فقط حتمية ⇒ نتوقف برسالة واضحة.
actor GeminiClient {

    struct Message {
        let role: String
        let text: String
    }

    private let host = "https://generativelanguage.googleapis.com/v1beta"
    private let session: URLSession

    /// الموديل الأول alias لا يتقاعد، بدل اسم قاعدة بيانات قد يُغلق.
    static let candidateModels = ["gemini-flash-latest", "gemini-2.5-flash-lite", "gemini-2.5-flash"]
    static let suggestedModels = [
        "gemini-flash-latest", "gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-pro"
    ]

    private enum BodyLevel: CaseIterable {
        case full, noSystem, minimal
    }

    init() {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 45
        config.httpAdditionalHeaders = ["Accept": "application/json"]
        session = URLSession(configuration: config)
    }

    // ── الطلب ────────────────────────────────────────────────────────────
    func ask(apiKey: String, model: String, style: String,
             context: String, history: [Message]) async -> Result<String, AIError> {
        let models = ([model.isEmpty ? "gemini-flash-latest" : model] + Self.candidateModels)
            .reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }

        var lastError = AIError.network("تعذّر الاتصال بالمساعد.")
        var tried: [String] = []

        for candidate in models {
            for retry in 0..<2 {
                if retry > 0 { try? await Task.sleep(nanoseconds: 700_000_000) }

                for level in BodyLevel.allCases {
                    guard let url = URL(string: "\(host)/models/\(candidate):generateContent") else {
                        continue
                    }
                    var request = URLRequest(url: url)
                    request.httpMethod = "POST"
                    request.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")
                    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    request.httpBody = try? JSONSerialization.data(
                        withJSONObject: body(style: style, context: context, history: history, level: level)
                    )

                    do {
                        let (data, response) = try await session.data(for: request)
                        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                        let payload = String(data: data, encoding: .utf8) ?? ""

                        switch code {
                        case 200...299:
                            if let text = Self.extractText(payload), !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                return .success(text)
                            }
                            lastError = .empty(String(describing: Self.finishReason(payload) ?? "فارغ"))
                            continue
                        case 400:
                            // حقل غير مدعوم: ننزل درجة
                            tried.append("\(candidate)/\(level)")
                            lastError = .invalidArgument(Self.errorMessage(payload))
                            continue
                        case 401, 403:
                            return .failure(.unauthorized(Self.errorMessage(payload)))
                        case 429:
                            lastError = .busy("تجاوزت الحصة مؤقتًا")
                            break
                        case 404:
                            lastError = .notFound(candidate)
                            break
                        case 500...599:
                            lastError = .busy("خدمة Google مشغولة (\(code))")
                            continue
                        default:
                            lastError = .network("فشل الاتصال (رمز \(code))")
                            continue
                        }
                    } catch {
                        lastError = .network("انقطاع الشبكة: \(error.localizedDescription)")
                        continue
                    }
                }
            }
        }

        let note = tried.isEmpty ? "" : "\n\nجرّبت: \(tried.prefix(4).joined(separator: "، "))"
        return .failure(.chain(lastError.message + note))
    }

    func listModels(apiKey: String) async -> Result<[String], AIError> {
        guard let url = URL(string: "\(host)/models?pageSize=200") else {
            return .failure(.network("رابط غير صالح"))
        }
        var request = URLRequest(url: url)
        request.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")
        do {
            let (data, response) = try await session.data(for: request)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            guard code == 200 else {
                return .failure(.unauthorized(Self.errorMessage(String(data: data, encoding: .utf8) ?? "")))
            }
            guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let models = root["models"] as? [[String: Any]] else {
                return .success([])
            }
            let names = models.compactMap { item -> String? in
                guard let raw = item["name"] as? String else { return nil }
                let name = raw.hasPrefix("models/") ? String(raw.dropFirst(7)) : raw
                let unusable = ["tts", "image", "embedding", "aqa"].contains { name.contains($0) }
                    || name.hasPrefix("gemma")
                guard !unusable else { return nil }
                if let methods = item["supportedGenerationMethods"] as? [String],
                   !methods.contains(where: { $0.contains("generateContent") }) {
                    return nil
                }
                return name
            }
            return .success(names.sorted())
        } catch {
            return .failure(.network(error.localizedDescription))
        }
    }

    // ── بناء الجسم ───────────────────────────────────────────────────────
    private func body(style: String, context: String, history: [Message], level: BodyLevel) -> [String: Any] {
        let system = systemPrompt(style: style, context: context)

        var contents: [[String: Any]] = history.prefix(20).map { message in
            [
                "role": message.role == "user" ? "user" : "model",
                "parts": [["text": message.text]]
            ]
        }
        if contents.isEmpty {
            contents = [["role": "user", "parts": [["text": "أعطني ملخصًا سريعًا لوضعي المالي."]]]]
        }

        if level != .full, var first = contents.first,
           let parts = first["parts"] as? [[String: Any]],
           let original = parts.first?["text"] as? String {
            first["parts"] = [["text": "\(system)\n\nسؤال المستخدم: \(original)"]]
            contents[0] = first
        }

        var payload: [String: Any] = ["contents": contents]
        if level == .full {
            payload["system_instruction"] = ["parts": [["text": system]]]
            payload["generationConfig"] = ["temperature": 0.4, "maxOutputTokens": 2048]
        }
        return payload
    }

    private func systemPrompt(style: String, context: String) -> String {
        """
        أنت مساعد مالي شخصي داخل تطبيق «محفظتي» في قطر. \
        استخدم الريال القطري (ر.ق) في كل الأرقام. \
        أجب بالعربية العامية الخليجية المبسطة، باختصار وبدون مقدمات طويلة. \
        اعتمد فقط على الأرقام الموجودة في السياق ولا تخترع أرقامًا. \
        لو طُلب منك نصيحة استثمار أو قرض، وضّح أن هذا ليس استشارة مالية رسمية. \
        الأسلوب المطلوب: \(style). سياق بيانات المستخدم: \(context)
        """
    }

    // ── الاستخراج ────────────────────────────────────────────────────────
    static func extractText(_ payload: String) -> String? {
        guard let root = try? JSONSerialization.jsonObject(with: Data(payload.utf8)) as? [String: Any],
              let candidates = root["candidates"] as? [[String: Any]],
              let content = candidates.first?["content"] as? [String: Any],
              let parts = content["parts"] as? [[String: Any]] else { return nil }
        let text = parts.compactMap { $0["text"] as? String }.joined()
        return text.isEmpty ? nil : text
    }

    private static func finishReason(_ payload: String) -> String? {
        guard let root = try? JSONSerialization.jsonObject(with: Data(payload.utf8)) as? [String: Any],
              let candidates = root["candidates"] as? [[String: Any]] else { return nil }
        return candidates.first?["finishReason"] as? String
    }

    private static func errorMessage(_ payload: String) -> String {
        guard let root = try? JSONSerialization.jsonObject(with: Data(payload.utf8)) as? [String: Any],
              let error = root["error"] as? [String: Any],
              let message = error["message"] as? String else { return "" }
        return message
    }
}

enum AIError: Error {
    case network(String)
    case unauthorized(String)
    case invalidArgument(String)
    case busy(String)
    case notFound(String)
    case empty(String)
    case chain(String)

    var message: String {
        switch self {
        case .network(let text): return text
        case .unauthorized(let detail):
            let suffix = detail.isEmpty ? "" : ": \(detail)"
            return "المفتاح غير مقبول\(suffix). تأكد من نسخه كاملًا من AI Studio."
        case .invalidArgument(let detail):
            return "الطلب غير مدعوم من الموديل: \(detail)"
        case .busy(let detail): return "\(detail). جرّب بعد قليل أو موديلًا آخر."
        case .notFound(let model): return "الموديل «\(model)» غير متاح لمفتاحك."
        case .empty(let reason): return "رد فارغ (\(reason)). جرّب موديل lite أو سؤالًا أقصر."
        case .chain(let text): return text
        }
    }
}
