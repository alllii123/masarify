import SwiftUI

enum ThemeColor: String, Codable, CaseIterable, Identifiable {
    case blue
    case green

    var id: String { rawValue }

    var arabicLabel: String {
        switch self {
        case .blue: return "أزرق"
        case .green: return "أخضر"
        }
    }

    /// اللون الأساسي للثيم — مطابق لقيم نسخة أندرويد.
    var primary: Color {
        switch self {
        case .blue: return Color(hex: 0x124A86)
        case .green: return Color(hex: 0x0E6B52)
        }
    }

    var primaryLight: Color {
        switch self {
        case .blue: return Color(hex: 0x2E70FF)
        case .green: return Color(hex: 0x14A67C)
        }
    }

    /// تدرّج بطاقة «مجموعك».
    var heroGradient: [Color] {
        switch self {
        case .blue:
            return [Color(hex: 0x347AFF), Color(hex: 0x1B4FC4), Color(hex: 0x0B2A72)]
        case .green:
            return [Color(hex: 0x18A87D), Color(hex: 0x0E7A5C), Color(hex: 0x07503C)]
        }
    }

    var container: Color {
        switch self {
        case .blue: return Color(hex: 0xD9E7FA)
        case .green: return Color(hex: 0xD8F0E6)
        }
    }

    var onContainer: Color {
        switch self {
        case .blue: return Color(hex: 0x06203F)
        case .green: return Color(hex: 0x06301F)
        }
    }
}

enum AppearanceMode: String, Codable, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var arabicLabel: String {
        switch self {
        case .system: return "النظام"
        case .light: return "فاتح"
        case .dark: return "داكن"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1.0
        )
    }
}

/// مسافات موحّدة لكل الشاشات.
enum Space {
    static let page: CGFloat = 18
    static let gap: CGFloat = 14
    static let card: CGFloat = 16
}
