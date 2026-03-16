import SwiftUI
import AppKit

// MARK: - Custom palette data model

struct PaletteStop: Identifiable, Equatable, Codable {
    var id: UUID = UUID()
    var position: Double   // 0–1
    var hue: Double        // 0–1
    var saturation: Double // 0–1
    var brightness: Double // 0–1

    var nsColor: NSColor {
        NSColor(hue: CGFloat(hue), saturation: CGFloat(saturation),
                brightness: CGFloat(brightness), alpha: 1)
    }
}

struct CustomPalette: Identifiable, Equatable, Codable {
    var id: String = UUID().uuidString
    var name: String
    var stops: [PaletteStop]

    var colorPalette: ColorPalette {
        let sorted = stops.sorted(by: { $0.position < $1.position })
        return ColorPalette(id: id, name: name,
                            stops: sorted.map { ($0.position, $0.nsColor) })
    }
}

// MARK: - Palette editor sheet

struct PaletteEditorSheet: View {
    @EnvironmentObject private var appState: AppState
    @Binding var isPresented: Bool
    /// nil = create new palette; non-nil = edit existing custom palette by id
    var editingId: String? = nil

    @State private var name: String = "My Palette"
    @State private var stops: [PaletteStop] = [
        PaletteStop(position: 0.0, hue: 0.6,  saturation: 1.0, brightness: 0.2),
        PaletteStop(position: 0.5, hue: 0.75, saturation: 1.0, brightness: 0.8),
        PaletteStop(position: 1.0, hue: 0.9,  saturation: 0.8, brightness: 1.0),
    ]
    @State private var selectedStopId: UUID? = nil

    private var previewPalette: ColorPalette {
        CustomPalette(id: editingId ?? "preview", name: name, stops: stops).colorPalette
    }

    var body: some View {
        VStack(spacing: 0) {
            // Title bar
            HStack {
                Text(editingId == nil ? "New Palette" : "Edit Palette")
                    .font(.headline)
                Spacer()
                Button("Cancel") { isPresented = false }
                    .buttonStyle(.plain)
                    .foregroundColor(.secondary)
            }
            .padding(16)

            Divider()

            ScrollView {
                VStack(spacing: 16) {
                    // Name
                    HStack {
                        Text("Name").font(.system(size: 11)).foregroundColor(.secondary).frame(width: 50, alignment: .leading)
                        TextField("Palette name", text: $name)
                            .textFieldStyle(.roundedBorder)
                            .font(.system(size: 11))
                    }
                    .padding(.horizontal, 16)

                    // Gradient preview
                    gradientPreview
                        .padding(.horizontal, 16)

                    Divider()

                    // Stops list
                    VStack(spacing: 4) {
                        HStack {
                            Text("STOPS")
                                .font(.system(size: 9, weight: .semibold))
                                .foregroundColor(.secondary).kerning(1.0)
                            Spacer()
                            Button(action: addStop) {
                                Label("Add", systemImage: "plus")
                                    .font(.system(size: 10))
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        }
                        .padding(.horizontal, 16)

                        ForEach($stops) { $stop in
                            StopRow(stop: $stop,
                                    isSelected: selectedStopId == stop.id,
                                    canDelete: stops.count > 2,
                                    onSelect: { selectedStopId = stop.id },
                                    onDelete: { stops.removeAll { $0.id == stop.id } })
                                .padding(.horizontal, 16)
                        }
                    }

                    Divider()
                }
                .padding(.vertical, 12)
            }

            Divider()

            // Action buttons
            HStack(spacing: 8) {
                if editingId != nil {
                    Button("Delete", role: .destructive) {
                        if let id = editingId {
                            appState.customPalettes.removeAll { $0.id == id }
                            appState.saveCustomPalettes()
                        }
                        isPresented = false
                    }
                    .buttonStyle(.bordered)
                }
                Spacer()
                Button(editingId == nil ? "Create Palette" : "Save Changes") {
                    savePalette()
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(16)
        }
        .frame(width: 380, height: 560)
        .onAppear { loadForEditing() }
    }

    private var gradientPreview: some View {
        let sorted = stops.sorted(by: { $0.position < $1.position })
        let gradStops = sorted.map {
            Gradient.Stop(color: Color(nsColor: $0.nsColor), location: $0.position)
        }
        return LinearGradient(gradient: Gradient(stops: gradStops),
                              startPoint: .leading, endPoint: .trailing)
            .frame(height: 32)
            .cornerRadius(8)
            .overlay(
                // Stop position markers
                GeometryReader { geo in
                    ForEach(stops) { stop in
                        Circle()
                            .fill(Color(nsColor: stop.nsColor))
                            .frame(width: 12, height: 12)
                            .overlay(Circle().stroke(selectedStopId == stop.id ? Color.white : Color.white.opacity(0.5), lineWidth: selectedStopId == stop.id ? 2 : 1))
                            .position(x: geo.size.width * stop.position, y: geo.size.height / 2)
                            .onTapGesture { selectedStopId = stop.id }
                    }
                }
            )
    }

    private func addStop() {
        // Insert at the midpoint of the largest gap
        let sorted = stops.sorted(by: { $0.position < $1.position })
        var bestPos = 0.5
        var bestGap = 0.0
        for i in 0..<(sorted.count - 1) {
            let gap = sorted[i + 1].position - sorted[i].position
            if gap > bestGap {
                bestGap = gap
                bestPos = (sorted[i].position + sorted[i + 1].position) / 2
            }
        }
        let interpolated = previewPalette.color(at: bestPos)
        var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        let hsbColor = interpolated.usingColorSpace(.deviceRGB) ?? interpolated
        hsbColor.getHue(&h, saturation: &s, brightness: &b, alpha: &a)
        let newStop = PaletteStop(position: bestPos,
                                  hue: Double(h), saturation: Double(s), brightness: Double(b))
        stops.append(newStop)
        selectedStopId = newStop.id
    }

    private func savePalette() {
        let pal = CustomPalette(id: editingId ?? UUID().uuidString, name: name.isEmpty ? "Untitled" : name, stops: stops)
        if let id = editingId, let idx = appState.customPalettes.firstIndex(where: { $0.id == id }) {
            appState.customPalettes[idx] = pal
        } else {
            appState.customPalettes.append(pal)
        }
        appState.saveCustomPalettes()
        isPresented = false
    }

    private func loadForEditing() {
        guard let id = editingId,
              let pal = appState.customPalettes.first(where: { $0.id == id }) else { return }
        name = pal.name
        stops = pal.stops
    }
}

// MARK: - Individual stop row

private struct StopRow: View {
    @Binding var stop: PaletteStop
    let isSelected: Bool
    let canDelete: Bool
    let onSelect: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(spacing: 4) {
            HStack(spacing: 6) {
                // Colour swatch
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(nsColor: stop.nsColor))
                    .frame(width: 24, height: 24)
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(
                        isSelected ? Color.accentColor : Color.white.opacity(0.15), lineWidth: isSelected ? 2 : 1))
                    .onTapGesture { onSelect() }

                VStack(spacing: 2) {
                    stopSlider(label: "Pos", value: $stop.position, color: .white)
                }

                if canDelete {
                    Button(action: onDelete) {
                        Image(systemName: "minus.circle")
                            .font(.system(size: 11))
                            .foregroundColor(.red.opacity(0.7))
                    }
                    .buttonStyle(.plain)
                }
            }
            if isSelected {
                VStack(spacing: 2) {
                    stopSlider(label: "Hue", value: $stop.hue, color: .purple)
                    stopSlider(label: "Sat", value: $stop.saturation, color: .pink)
                    stopSlider(label: "Bri", value: $stop.brightness, color: .yellow)
                }
                .padding(.leading, 30)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.easeInOut(duration: 0.15), value: isSelected)
        .padding(6)
        .background(isSelected ? Color.accentColor.opacity(0.08) : Color.clear)
        .cornerRadius(6)
    }

    private func stopSlider(label: String, value: Binding<Double>, color: Color) -> some View {
        HStack(spacing: 6) {
            Text(label)
                .font(.system(size: 9)).foregroundColor(.secondary)
                .frame(width: 22, alignment: .leading)
            Slider(value: value, in: 0...1).accentColor(color)
            Text(String(format: "%.2f", value.wrappedValue))
                .font(.system(size: 9)).foregroundColor(.secondary).monospacedDigit()
                .frame(width: 28, alignment: .trailing)
        }
    }
}
