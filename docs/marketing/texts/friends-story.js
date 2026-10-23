// Text set "friends-story": the narrative series for WhatsApp and Instagram stories (24 October 2026).
// The sequence tells it in order: building an app, what it draws, a pause, the lock screen, the reveal.
// Same structure as friends.js: copy, change the lines, run render-share-images.sh friends-story.
// Lines in a list are alternatives; story 1 takes the first, story 2 the second, and so on (repeating).
window.MKT_TEXT = {
  stories: { count: 5, steps: ["teaser:1", "teaser:2", "teaser:0", "wallpaper", "app"] },
  en: {
    message: "It's called Dahlia, out now for Android. Try it. I'd love your feedback and a review.",
    facts: "Free and open source, no ads, no account.",
    link: "tearoom.one/dahlia",
    mandala: "Mandalas from curves, light and symbols. My new Android app, free to try.",
    teaser: [
      ["I've been building a small app.", "I made a small app.", "I've been building an app on the side."],
      ["It draws mandalas from curves and light.", "It draws mandalas from curves, light and symbols.", "Every mandala is drawn from curves and light."]
    ],
    wallpaper: ["Decorate your lock screen.", "Or put one on your lock screen.", "Beautify your lock screen."]
  },
  de: {
    message: "Sie heißt Dahlia und ist für Android da. Probier sie aus, ich freue mich über Feedback und eine Bewertung.",
    facts: "Kostenlos und Open Source, keine Werbung, kein Konto.",
    link: "tearoom.one/dahlia",
    mandala: "Mandalas aus Kurven, Licht und Symbolen. Meine neue Android-App, kostenlos zum Ausprobieren.",
    teaser: [
      ["Ich habe eine kleine App gebaut.", "Ich habe eine kleine App gemacht.", "Nebenbei habe ich eine App gebaut."],
      ["Sie zeichnet Mandalas aus Kurven und Licht.", "Sie zeichnet Mandalas aus Kurven, Licht und Symbolen.", "Jedes Mandala entsteht aus Kurven und Licht."]
    ],
    wallpaper: ["Verschönere deinen Sperrbildschirm.", "Oder leg eins auf den Sperrbildschirm.", "Schmück deinen Sperrbildschirm."]
  }
};
