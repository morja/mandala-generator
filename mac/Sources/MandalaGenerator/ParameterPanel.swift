import SwiftUI

// MARK: - Shared UI components

struct SectionCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.uppercased())
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(.secondary).kerning(1.2)
                .padding(.horizontal, 16)
            VStack(alignment: .leading, spacing: 0) {
                content().padding(12)
            }
            .background(Color(NSColor.windowBackgroundColor).opacity(0.5))
            .cornerRadius(8).padding(.horizontal, 12)
        }
    }
}

struct LabeledSlider: View {
    let label: String
    @Binding var value: Double
    var color: Color = .blue

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label).font(.subheadline).foregroundColor(.secondary)
                Spacer()
                Text(String(format: "%.2f", value)).font(.caption).foregroundColor(.secondary).monospacedDigit()
            }
            Slider(value: $value, in: 0...1).accentColor(color)
        }
    }
}

struct PaletteSwatch: View {
    let palette: ColorPalette
    let isSelected: Bool
    var isBlend: Bool = false

    var body: some View {
        VStack(spacing: 2) {
            LinearGradient(
                gradient: Gradient(stops: palette.stops.map {
                    Gradient.Stop(color: Color(nsColor: $0.1), location: $0.0)
                }),
                startPoint: .leading, endPoint: .trailing
            )
            .frame(height: 18)
            .cornerRadius(3)
            .overlay(RoundedRectangle(cornerRadius: 3)
                .stroke(isSelected ? Color.white : isBlend ? Color.orange : Color.clear, lineWidth: 2))

            Text(palette.name)
                .font(.system(size: 8))
                .foregroundColor(isSelected || isBlend ? .primary : .secondary)
                .lineLimit(1)
        }
        .padding(2)
        .background(isSelected ? Color.blue.opacity(0.2) : isBlend ? Color.orange.opacity(0.15) : Color.clear)
        .cornerRadius(5)
        .contentShape(Rectangle())
    }
}
