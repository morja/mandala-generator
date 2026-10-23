// Text set "friends": the share images for friends and family chats.
// Copy this file to texts/<name>.js, change the lines, then run render-share-images.sh <name>.
// Every line is plain text; keep the quotes and commas as they are.
// `teaser` holds the short lines for the teaser stories (variant G), one list per position: the first
// list is "line 1", the second "line 2". Several lines in a list are alternatives; story 1 takes the
// first, story 2 the second, and so on (repeating). Every teaser mandala is also rendered once per
// line and once without text into story/.
// `stories` is the plan for the ready-made story sequences in stories/: `count` sequences, each with
// these steps in order. "teaser:1" and "teaser:2" are a mandala with line 1 or 2, "teaser:0" one
// without text, "app" the app story (variant B). No mandala repeats within a sequence.
window.MKT_TEXT = {
  stories: { count: 5, steps: ["teaser:1", "teaser:2", "teaser:0", "app"] },
  en: {
    message: "Try out my new Android app. I'd love some feedback and reviews.",
    facts: "Free and open source, no ads, no account.",
    link: "tearoom.one/dahlia",
    mandala: "Mandalas from curves, light and symbols. My new Android app, free to try.",
    teaser: [
      ["See what you can make with my new app.", "Look what my new app can do.", "Made this with my new app."],
      ["Another one. Made on my phone.", "One more, straight from my phone.", "And another, a minute on my phone."]
    ]
  },
  de: {
    message: "Meine neue Android-App. Probier sie aus, ich freue mich über Feedback und Bewertungen.",
    facts: "Kostenlos und Open Source, keine Werbung, kein Konto.",
    link: "tearoom.one/dahlia",
    mandala: "Mandalas aus Kurven, Licht und Symbolen. Meine neue Android-App, kostenlos zum Ausprobieren.",
    teaser: [
      ["Schau, was man mit meiner neuen App machen kann.", "Schau, was meine neue App kann.", "Mit meiner neuen App gemacht."],
      ["Noch eins. Auf dem Handy gemacht.", "Noch eins, direkt vom Handy.", "Und noch eins, eine Minute am Handy."]
    ]
  }
};
