import SwiftUI

/// شاشة الترحيب — نظير SplashScreen في نسخة أندرويد.
struct SplashView: View {
    @EnvironmentObject private var store: AppStore
    let onFinished: () -> Void

    @State private var logoScale: CGFloat = 0.72
    @State private var logoOpacity: Double = 0
    @State private var textOpacity: Double = 0
    @State private var progress: CGFloat = 0
    @State private var pulse = false

    private var greeting: (String, String) {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5...11: return ("صباح الخير ☀️", "خلّنا نبدأ يومك بفلوس واضحة")
        case 12...16: return ("مساء النور 🌤️", "شوف وين وصلت اليوم")
        case 17...21: return ("مساء الخير 🌙", "ملخصك المالي جاهز")
        default: return ("سهرة موفقة 🌙", "أرقامك محفوظة وآمنة")
        }
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(.systemBackground),
                         store.theme.primaryLight.opacity(0.14),
                         Color(.systemBackground)],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                ZStack {
                    Circle()
                        .fill(store.theme.primaryLight.opacity(0.22))
                        .frame(width: 108, height: 108)
                        .scaleEffect(pulse ? 1.06 : 0.9)

                    RoundedRectangle(cornerRadius: 28, style: .continuous)
                        .fill(LinearGradient(colors: store.theme.heroGradient,
                                             startPoint: .topLeading,
                                             endPoint: .bottomTrailing))
                        .frame(width: 88, height: 88)
                        .overlay {
                            Image(systemName: "wallet.bifold.fill")
                                .font(.system(size: 40, weight: .medium))
                                .foregroundStyle(.white)
                        }
                        .scaleEffect(logoScale)
                        .opacity(logoOpacity)
                }

                Spacer().frame(height: 26)

                VStack(spacing: 6) {
                    Text("محفظتي")
                        .font(.system(size: 34, weight: .bold))
                    Text(greeting.0)
                        .font(.headline)
                        .foregroundStyle(store.theme.primary)
                    Text(greeting.1)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .opacity(textOpacity)
            }

            VStack(spacing: 10) {
                Spacer()
                ProgressView(value: progress)
                    .tint(store.theme.primaryLight)
                    .frame(width: 200)
                Text("نجهّز أرقامك…")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .padding(.bottom, 64)
            .opacity(textOpacity)
        }
        .onAppear { run() }
    }

    private func run() {
        withAnimation(.easeOut(duration: 0.32)) { logoOpacity = 1 }
        withAnimation(.easeOut(duration: 0.42)) { logoScale = 1 }
        withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true }
        withAnimation(.easeOut(duration: 0.38).delay(0.15)) { textOpacity = 1 }
        withAnimation(.linear(duration: 0.9).delay(0.2)) { progress = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.55) { onFinished() }
    }
}
