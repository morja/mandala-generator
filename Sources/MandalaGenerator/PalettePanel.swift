import SwiftUI

struct PalettePanel: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(spacing: 14) {

                // PRIMARY
                paletteGrid(
                    title: "Primary",
                    selectedIndex: appState.parameters.paletteIndex,
                    isBlendStyle: false
                ) { index in
                    appState.parameters.paletteIndex = index
                }

                // MIX SLIDER
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("MIX")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(.secondary)
                            .kerning(1.2)
                        Spacer()
                        Text(String(format: "%.0f%%", appState.parameters.paletteBlend * 100))
                            .font(.caption).foregroundColor(.secondary).monospacedDigit()
                    }
                    Slider(value: $appState.parameters.paletteBlend, in: 0...1)
                        .accentColor(.orange)
                    Text("Blend primary with a second palette")
                        .font(.system(size: 9)).foregroundColor(.secondary)
                }
                .padding(.horizontal, 12)

                // BLEND TARGET (visible only when mix > 0)
                if appState.parameters.paletteBlend > 0.01 {
                    paletteGrid(
                        title: "Blend With",
                        selectedIndex: appState.parameters.blendPaletteIndex,
                        isBlendStyle: true
                    ) { index in
                        appState.parameters.blendPaletteIndex = index
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            .padding(.vertical, 12)
            .animation(.easeInOut(duration: 0.2), value: appState.parameters.paletteBlend > 0.01)
        }
        .background(Color(NSColor.controlBackgroundColor))
    }

    @ViewBuilder
    private func paletteGrid(title: String, selectedIndex: Int, isBlendStyle: Bool,
                              onSelect: @escaping (Int) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title.uppercased())
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(isBlendStyle ? .orange : .secondary)
                .kerning(1.2)
                .padding(.horizontal, 16)

            VStack(alignment: .leading, spacing: 0) {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 6) {
                    ForEach(Array(ColorPalettes.all.enumerated()), id: \.offset) { index, palette in
                        PaletteSwatch(palette: palette,
                                      isSelected: selectedIndex == index,
                                      isBlend: isBlendStyle)
                            .onTapGesture { onSelect(index) }
                    }
                }
                .padding(10)
            }
            .background(Color(NSColor.windowBackgroundColor).opacity(0.5))
            .cornerRadius(8)
            .padding(.horizontal, 12)
        }
    }
}
