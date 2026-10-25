// Text set "default": the narrative series for WhatsApp and Instagram stories (24 October 2026).
// The sequence tells it in order: building an app, what it draws, a pause, the lock screen, the reveal.
// Same structure as friends.js: copy, change the lines, run render-share-images.sh (it is the default).
// Fixed lines since 24 October 2026 (combination 1), so images from different sequences can be mixed freely;
// a list may still hold alternatives (story 1 takes the first, story 2 the second, and so on).
window.MKT_TEXT = {
  stories: { count: 5, steps: ["teaser:1", "teaser:2", "teaser:0", "wallpaper", "app"] },
  en: {
    message: "It's called Dahlia, out now for Android. Try it. I'd love your feedback and a review.",
    facts: "Free and open source, no ads, no account.",
    link: "tearoom.one/dahlia",
    mandala: "Mandalas from curves, light and symbols. My new Android app, free to try.",
    teaser: [
      ["Look what I've been working on."],
      ["Another one. Made on my phone."]
    ],
    wallpaper: ["Decorate your lock screen."]
  },
  de: {
    message: "Sie heißt Dahlia, jetzt für Android. Probier sie aus, ich freue mich über Feedback und eine Bewertung.",
    facts: "Kostenlos und Open Source, keine Werbung, kein Konto.",
    link: "tearoom.one/dahlia",
    mandala: "Mandalas aus Kurven, Licht und Symbolen. Meine neue Android-App, kostenlos zum Ausprobieren.",
    teaser: [
      ["Schau, woran ich gearbeitet habe."],
      ["Noch eins. Auf dem Handy gemacht."]
    ],
    wallpaper: ["Verschönere deinen Sperrbildschirm."]
  }
};
