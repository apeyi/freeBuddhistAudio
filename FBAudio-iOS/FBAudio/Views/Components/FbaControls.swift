import SwiftUI

/// The design's filled button: 48 high, radius 12 (iOS), accent-deep fill (gold on remastered
/// pages, Order blue for Order-only actions, both via the page theme), bold white label.
/// Mirrors Android `FbaPrimaryButton`.
struct FbaPrimaryButton: View {
    let title: String
    var systemImage: String? = nil
    let action: () -> Void
    @Environment(\.pageColors) private var page

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage {
                    Image(systemName: systemImage).font(.system(size: 15, weight: .bold))
                }
                Text(title).font(.body.weight(.bold))
            }
            .foregroundStyle(FbaColors.white)
            .frame(maxWidth: .infinity, minHeight: FbaDimens.buttonHeight)
            .background(page.deep, in: RoundedRectangle(cornerRadius: FbaDimens.radiusButton))
            .contentShape(RoundedRectangle(cornerRadius: FbaDimens.radiusButton))
        }
        .buttonStyle(.plain)
    }
}

/// "Support FBA": the last item on scrolling pages.
struct SupportFbaButton: View {
    let action: () -> Void
    var body: some View { FbaPrimaryButton(title: "Support FBA", action: action) }
}

/// A tool in the grey-circle icon row (talk page, player): 52 circle in grey-100, 24 icon,
/// 12 Medium label underneath. Pass `content` to replace the icon (e.g. a progress ring).
struct ToolCircle<Content: View>: View {
    let label: String
    var accessibilityText: String? = nil
    let action: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                ZStack { content() }
                    .frame(width: FbaDimens.toolCircle, height: FbaDimens.toolCircle)
                    .background(FbaColors.circle, in: Circle())
                Text(label)
                    .font(.caption.weight(.medium))   // 12
                    .foregroundStyle(Color(hex: 0x3D3D3D))
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText ?? label)
        .accessibilityAddTraits(.isButton)
    }
}

extension ToolCircle where Content == ToolIcon {
    init(_ label: String, systemImage: String, tint: Color = FbaColors.black,
         accessibilityText: String? = nil, action: @escaping () -> Void) {
        self.label = label
        self.accessibilityText = accessibilityText
        self.action = action
        self.content = { ToolIcon(systemImage: systemImage, tint: tint) }
    }
}

struct ToolIcon: View {
    let systemImage: String
    let tint: Color
    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 20, weight: .regular))
            .foregroundStyle(tint)
    }
}

/// Text tabs under a page's description ("Tracks (n) | Related talks"): equal widths, a grey
/// hairline under the row, and a 3pt underline in the page colour under the active tab.
struct FbaTextTabs: View {
    let titles: [String]
    @Binding var selected: Int
    @Environment(\.pageColors) private var page

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(titles.enumerated()), id: \.offset) { index, title in
                let isSelected = index == selected
                Button { selected = index } label: {
                    Text(title)
                        .font(.body.weight(isSelected ? .bold : .medium))
                        .foregroundStyle(isSelected ? FbaColors.black : Color(hex: 0x737373))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .overlay(alignment: .bottom) {
                            if isSelected { page.deep.frame(height: 3) }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
            }
        }
        .overlay(alignment: .bottom) { FbaColors.divider.frame(height: 1) }
    }
}
