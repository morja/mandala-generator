import SwiftUI

/// Small icon buttons in card headers (dice, duplicate, fold, delete …): the glyph stays small, but
/// the button takes a 24 pt square that answers to clicks anywhere in it and shows a light
/// background under the pointer, so it is easy to hit.
struct CardIconButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        CardIconButton(configuration: configuration)
    }

    private struct CardIconButton: View {
        let configuration: ButtonStyleConfiguration
        @State private var isHovered = false

        var body: some View {
            configuration.label
                .frame(minWidth: 24, minHeight: 24)
                .contentShape(Rectangle())
                .background(
                    RoundedRectangle(cornerRadius: 5)
                        .fill(Color.primary.opacity(configuration.isPressed ? 0.14 : isHovered ? 0.07 : 0))
                )
                .onHover { isHovered = $0 }
        }
    }
}

extension ButtonStyle where Self == CardIconButtonStyle {
    static var cardIcon: CardIconButtonStyle { CardIconButtonStyle() }
}

/// The empty space in a card header between the title and the buttons: a click there folds or
/// unfolds the card, so the small chevron is not the only target.
struct CardHeaderFoldArea: View {
    let action: () -> Void

    var body: some View {
        Color.clear
            .frame(maxWidth: .infinity, minHeight: 24)
            .contentShape(Rectangle())
            .onTapGesture(perform: action)
    }
}
