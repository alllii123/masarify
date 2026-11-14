import SwiftUI

struct AssistantView: View {
    @EnvironmentObject private var store: AppStore
    @State private var input = ""
    @State private var thinking = false
    @State private var pending: FinCommand?
    /// يُنشأ مرة واحدة بعد توفر store في onAppear.
    @State private var engine: AssistantEngine?

    private var messages: [ChatMessage] {
        store.chat.filter { !$0.text.isEmpty || $0.error != nil }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                modeChips

                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            if store.chat.isEmpty {
                                intro
                                quickActions
                            }
                            ForEach(messages) { message in
                                BubbleView(message: message,
                                           onConfirm: confirmPending,
                                           onCancel: cancelPending)
                                    .id(message.id)
                            }
                            if thinking {
                                HStack(spacing: 8) {
                                    ProgressView().controlSize(.small)
                                    Text("أفكر في أرقامك…")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                }
                            }
                        }
                        .padding(.horizontal, Space.page)
                        .padding(.vertical, 8)
                    }
                    .onChange(of: store.chat.count) {
                        if let last = messages.last {
                            withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                        }
                    }
                }

                inputBar
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("مساعد محفظتي")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("مسح") { store.clearChat() }
                        .disabled(store.chat.isEmpty)
                }
            }
        }
        .onAppear {
            if engine == nil { engine = AssistantEngine(store: store) }
        }
    }

    // ── مبدّل الوضع ──────────────────────────────────────────────────────
    private var modeChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(AiMode.allCases, id: \.self) { mode in
                    Button {
                        var settings = store.ai
                        settings.mode = mode
                        store.updateAi(settings)
                    } label: {
                        Text(mode.arabicLabel)
                            .font(.caption.weight(.medium))
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(store.ai.mode == mode ? store.theme.container : Color(.secondarySystemGroupedBackground),
                                        in: Capsule())
                            .foregroundStyle(store.ai.mode == mode ? store.theme.onContainer : .secondary)
                    }
                }
            }
            .padding(.horizontal, Space.page)
            .padding(.vertical, 6)
        }
    }

    // ── المقدمة ──────────────────────────────────────────────────────────
    private var intro: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("هلا 👋 أنا مساعدك المالي").font(.title3.weight(.bold))
            Text("أحلل عملياتك وأرصدة حساباتك على جهازك. ما أرسل شي لأي جهة إلا إذا فعّلت الوضع السحابي بمفتاحك.")
                .font(.caption)
                .foregroundStyle(.secondary)

            VStack(alignment: .leading, spacing: 5) {
                Text("أقدر أنفّذ مو بس أجاوب").font(.caption.weight(.semibold))
                Text("قل «أضف مصروف 50 بقالة» أو «عدّل آخر عملية إلى 70» أو «احذف آخر عملية» — أنفّذها فورًا في بياناتك. التنفيذ يشتغل حتى بدون إنترنت.")
                    .font(.caption2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(store.theme.container, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .foregroundStyle(store.theme.onContainer)
        }
    }

    private var quickActions: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("جرّب بسرعة").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            ForEach(["أضف مصروف 50 قهوة", "كم باقي لي أقدر أصرف؟",
                     "وين أصرف أكثر؟", "احذف آخر عملية"], id: \.self) { text in
                Button { send(text) } label: {
                    Text(text)
                        .font(.caption)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 11)
                        .background(Color(.secondarySystemGroupedBackground),
                                    in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
    }

    // ── شريط الإدخال ────────────────────────────────────────────────────
    private var inputBar: some View {
        HStack(spacing: 8) {
            TextField("اسألني: كم أقدر أصرف؟", text: $input, axis: .vertical)
                .lineLimit(1...4)
                .padding(.horizontal, 16)
                .padding(.vertical, 11)
                .background(Color(.secondarySystemGroupedBackground), in: Capsule())

            Button { send(input) } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 34))
                    .foregroundStyle(input.trimmingCharacters(in: .whitespaces).isEmpty
                                     ? Color.secondary : store.theme.primary)
            }
            .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .padding(.horizontal, Space.page)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial)
    }

    // ── الإرسال ─────────────────────────────────────────────────────────
    private func send(_ raw: String) {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !thinking else { return }
        input = ""
        store.appendMessage(ChatMessage(role: "user", text: text))
        thinking = true

        Task {
            let engine = self.engine ?? AssistantEngine(store: store)
            let replies = await engine.send(text)
            await MainActor.run {
                for reply in replies where reply.role != "user" {
                    store.appendMessage(reply)
                    if reply.pending {
                        pending = CommandParser.parse(text)?.command
                    }
                }
                thinking = false
            }
        }
    }

    private func confirmPending() {
        guard let command = pending, let engine else { return }
        let reply = engine.execute(command)
        store.chat.removeAll { $0.pending }
        store.appendMessage(reply)
        pending = nil
    }

    private func cancelPending() {
        store.chat.removeAll { $0.pending }
        store.appendMessage(ChatMessage(role: "model", text: "طيّب، ما سجلت شي.", source: "local"))
        pending = nil
    }
}

private struct BubbleView: View {
    let theme: ThemeColor
    let message: ChatMessage
    let onConfirm: () -> Void
    let onCancel: () -> Void

    private var isUser: Bool { message.role == "user" }

    var body: some View {
        HStack {
            if isUser { Spacer(minLength: 40) }
            VStack(alignment: isUser ? .trailing : .leading, spacing: 4) {
                Text(message.text)
                    .font(.footnote)
                    .foregroundStyle(isUser ? .white : .primary)
                    .padding(.horizontal, 15)
                    .padding(.vertical, 11)
                    .background(isUser ? AnyShapeStyle(theme.primary)
                                       : AnyShapeStyle(Color(.secondarySystemGroupedBackground)),
                                in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                HStack(spacing: 6) {
                    Text(message.at.formatted(.dateTime.hour().minute()))
                    if let source = message.source {
                        Text("•")
                        Text(source == "cloud" ? "سحابي" : "محلي")
                    }
                }
                .font(.system(size: 9))
                .foregroundStyle(.secondary)

                if message.pending {
                    HStack(spacing: 8) {
                        Button("تأكيد ✓", action: onConfirm)
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 16).padding(.vertical, 7)
                            .background(theme.primary, in: Capsule())
                            .foregroundStyle(.white)
                        Button("إلغاء", action: onCancel)
                            .font(.caption)
                            .padding(.horizontal, 16).padding(.vertical, 7)
                            .background(Color(.tertiarySystemFill), in: Capsule())
                            .foregroundStyle(.secondary)
                    }
                }

                if let action = message.action {
                    Text("✓ \(action) في التطبيق")
                        .font(.system(size: 10, weight: .medium))
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(Color.green.opacity(0.16), in: Capsule())
                        .foregroundStyle(.green)
                }

                if let error = message.error {
                    Text(error)
                        .font(.system(size: 9))
                        .foregroundStyle(.red)
                }
            }
            if !isUser { Spacer(minLength: 40) }
        }
    }
}
