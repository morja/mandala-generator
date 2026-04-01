import Foundation

struct Quote: Equatable {
    let text: String
    let author: String
}

// MARK: - Quote Database

enum QuoteDatabase {
    static let quotes: [Quote] = [

        // ── Ramana Maharshi ──────────────────────────────────────────────────
        Quote(text: "The state we call realization is simply being oneself.", author: "Ramana Maharshi"),
        Quote(text: "Your duty is to be and not to be this or that. 'I am that I am'.", author: "Ramana Maharshi"),
        Quote(text: "'I exist' is the only permanent self-evident experience of everyone.", author: "Ramana Maharshi"),
        Quote(text: "Take no notice of the ego and its activities, but see the light behind.", author: "Ramana Maharshi"),
        Quote(text: "Watch the mind. You must stand aloof from it. You are not the mind.", author: "Ramana Maharshi"),
        Quote(text: "Unbroken 'I, I' is the infinite ocean.", author: "Ramana Maharshi"),
        Quote(text: "All that is needed is to give up thinking of objects other than the Self.", author: "Ramana Maharshi"),
        Quote(text: "Self is not to be reached. Be as you are.", author: "Ramana Maharshi"),
        Quote(text: "You are That, here and now.", author: "Ramana Maharshi"),
        Quote(text: "The real 'I' is silent. Only 'I am' is the truth.", author: "Ramana Maharshi"),
        Quote(text: "The lazy state of just being and shining is the highest state.", author: "Ramana Maharshi"),
        Quote(text: "Who is the meditator? Ask the question first. Remain as the meditator.", author: "Ramana Maharshi"),

        // ── Nisargadatta Maharaj ─────────────────────────────────────────────
        Quote(text: "Let go your attachment to the unreal and the real will step into its own.", author: "Nisargadatta Maharaj"),
        Quote(text: "Return again and again to 'I am' until it is your only abode.", author: "Nisargadatta Maharaj"),
        Quote(text: "Everything that exists, exists as my Self. There is no duality.", author: "Nisargadatta Maharaj"),
        Quote(text: "Leave your mind alone. Don't go along with it.", author: "Nisargadatta Maharaj"),
        Quote(text: "Desire is the memory of pleasure and fear is the memory of pain.", author: "Nisargadatta Maharaj"),
        Quote(text: "Your mind is all with things, people and ideas, never with your Self.", author: "Nisargadatta Maharaj"),
        Quote(text: "All you can say is: 'I am not this, I am not that'.", author: "Nisargadatta Maharaj"),

        // ── Annamalai Swami ──────────────────────────────────────────────────
        Quote(text: "If you cultivate indifference towards the mind, you'll cease to identify with it.", author: "Annamalai Swami"),

        // ── Rumi ─────────────────────────────────────────────────────────────
        Quote(text: "The wound is the place where the light enters you.", author: "Rumi"),
        Quote(text: "You are not a drop in the ocean. You are the entire ocean, in a drop.", author: "Rumi"),
        Quote(text: "What you seek is seeking you.", author: "Rumi"),
        Quote(text: "Stop acting so small. You are the universe in ecstatic motion.", author: "Rumi"),
        Quote(text: "Love is the bridge between you and everything.", author: "Rumi"),
        Quote(text: "Your heart knows the way. Run in that direction.", author: "Rumi"),
        Quote(text: "There is a candle in your heart, ready to be kindled.", author: "Rumi"),
        Quote(text: "Let yourself be silently drawn by the stronger pull of what you really love.", author: "Rumi"),
        Quote(text: "When I am silent, I fall into the place where everything is music.", author: "Rumi"),
        Quote(text: "Do not feel lonely, the entire universe is inside you.", author: "Rumi"),
        Quote(text: "The desire to know your own soul will end all other desires.", author: "Rumi"),
        Quote(text: "Sell your cleverness and buy bewilderment.", author: "Rumi"),
        Quote(text: "Let the beauty we love be what we do.", author: "Rumi"),
        Quote(text: "Be empty of worrying. Think of who created thought.", author: "Rumi"),
        Quote(text: "Wherever you stand, be the soul of that place.", author: "Rumi"),
        Quote(text: "You were born with wings. Why prefer to crawl through life?", author: "Rumi"),
        Quote(text: "Set your life on fire. Seek those who fan your flames.", author: "Rumi"),
        Quote(text: "Close your eyes, fall in love, stay there.", author: "Rumi"),
        Quote(text: "There is a voice that doesn't use words. Listen.", author: "Rumi"),
        Quote(text: "I am not this hair, I am not this skin, I am the soul that lives within.", author: "Rumi"),
        Quote(text: "When you do things from your soul, you feel a river moving in you, a joy.", author: "Rumi"),
        Quote(text: "Raise your words, not voice. It is rain that grows flowers, not thunder.", author: "Rumi"),
        Quote(text: "These pains you feel are messengers. Listen to them.", author: "Rumi"),
        Quote(text: "Only from the heart can you touch the sky.", author: "Rumi"),
        Quote(text: "Wherever you are, and whatever you do, be in love.", author: "Rumi"),
        Quote(text: "Beyond the rightness or wrongness of things there is a field — I'll meet you there.", author: "Rumi"),
        Quote(text: "In silence there is eloquence. Stop weaving and see how the pattern improves.", author: "Rumi"),
        Quote(text: "Your heart is the size of an ocean. Go find yourself in its hidden depths.", author: "Rumi"),
        Quote(text: "Let silence take you to the core of life.", author: "Rumi"),
        Quote(text: "If light is in your heart, you will find your way home.", author: "Rumi"),
        Quote(text: "We carry inside us the wonders we seek outside us.", author: "Rumi"),
        Quote(text: "My soul is from elsewhere, I'm sure of that, and I intend to end up there.", author: "Rumi"),
        Quote(text: "The time has come to turn your heart into a temple of fire.", author: "Rumi"),
        Quote(text: "The soul is here for its own joy.", author: "Rumi"),
        Quote(text: "Open your hands if you want to be held.", author: "Rumi"),
        Quote(text: "Be melting snow. Wash yourself of yourself.", author: "Rumi"),
        Quote(text: "Why do you stay in prison when the door is so wide open?", author: "Rumi"),
        Quote(text: "When will you begin that long journey into yourself?", author: "Rumi"),
        Quote(text: "Respond to every call that excites your spirit.", author: "Rumi"),
        Quote(text: "Patience is the key to joy.", author: "Rumi"),
        Quote(text: "Gratitude is the wine for the soul. Go on. Get drunk.", author: "Rumi"),
        Quote(text: "The soul has been given its own ears to hear things the mind does not understand.", author: "Rumi"),
        Quote(text: "Be soulful. Be kind. Be in love.", author: "Rumi"),
        Quote(text: "Dance until you shatter yourself.", author: "Rumi"),
        Quote(text: "Concentrate on the Essence, concentrate on the light.", author: "Rumi"),
        Quote(text: "Seek the sound that never ceases. Seek the sun that never sets.", author: "Rumi"),
        Quote(text: "Let your teacher be love itself.", author: "Rumi"),
        Quote(text: "Sit, be still, and listen.", author: "Rumi"),
        Quote(text: "Move, but don't move the way fear makes you move.", author: "Rumi"),
        Quote(text: "As you start to walk on the way, the way appears.", author: "Rumi"),
        Quote(text: "Don't wait any longer. Dive in the ocean, leave and let the sea be you.", author: "Rumi"),

        // ── Zen & Tao ─────────────────────────────────────────────────────────
        Quote(text: "When you realize nothing is lacking, the whole world belongs to you.", author: "Lao Tzu"),
        Quote(text: "Do you have the patience to wait until your mud settles and the water is clear?", author: "Lao Tzu"),
        Quote(text: "Act without expectation.", author: "Lao Tzu"),
        Quote(text: "If you are depressed, you are living in the past. If you are anxious, you are living in the future.", author: "Lao Tzu"),
        Quote(text: "Let go, or be dragged.", author: "Zen proverb"),
        Quote(text: "No clinging, no seeking.", author: "Zen proverb"),
        Quote(text: "Before enlightenment; chop wood, carry water. After enlightenment; chop wood, carry water.", author: "Zen proverb"),
        Quote(text: "Empty your mind, be formless. Shapeless, like water.", author: "Bruce Lee"),
        Quote(text: "In the beginner's mind there are many possibilities, but in the expert's mind there are few.", author: "Shunryu Suzuki"),
        Quote(text: "The mind of the beginner is empty, free of the habits of the expert.", author: "Shunryu Suzuki"),
        Quote(text: "Treat every moment as your last. It is not preparation for something else.", author: "Shunryu Suzuki"),
        Quote(text: "Do not seek the truth, only cease to cherish your opinions.", author: "Seng-ts'an"),
        Quote(text: "Do not dwell in the past, do not dream of the future, concentrate the mind on the present moment.", author: "Buddha"),
        Quote(text: "Wherever you are, be there totally.", author: "Eckhart Tolle"),
        Quote(text: "To seek is to suffer. To seek nothing is bliss.", author: "Bodhidharma"),
        Quote(text: "The essence of the Way is detachment.", author: "Bodhidharma"),
        Quote(text: "Nothing ever goes away until it has taught us what we need to know.", author: "Pema Chödrön"),
        Quote(text: "The ability to observe without evaluating is the highest form of intelligence.", author: "Jiddu Krishnamurti"),
        Quote(text: "I don't mind what happens. That is the essence of inner freedom.", author: "Jiddu Krishnamurti"),
        Quote(text: "Man suffers only because he takes seriously what the gods made for fun.", author: "Alan Watts"),
        Quote(text: "Zen is a liberation from time.", author: "Alan Watts"),
        Quote(text: "Only the hand that erases can write the true thing.", author: "Meister Eckhart"),
        Quote(text: "Rest and be kind, you don't have to prove anything.", author: "Jack Kerouac"),
        Quote(text: "Have the fearless attitude of a hero and the loving heart of a child.", author: "Soyen Shaku"),
        Quote(text: "When thoughts arise, then do all things arise. When thoughts vanish, then do all things vanish.", author: "Huang Po"),
        Quote(text: "I live by letting things happen.", author: "Dogen"),
        Quote(text: "A Zen master's life is one continuous mistake.", author: "Dogen"),
        Quote(text: "Those who seek the easy way do not seek the true way.", author: "Dogen"),
        Quote(text: "Zen teaches nothing; it merely enables us to wake up and become aware.", author: "D.T. Suzuki"),
        Quote(text: "The beauty of Zen is found in simplicity and tranquility.", author: "Thich Thien-An"),
        Quote(text: "Wise men don't judge — they seek to understand.", author: "Wei Wu Wei"),
        Quote(text: "My daily affairs are quite ordinary; but I'm in total harmony with them.", author: "Layman Pang"),
        Quote(text: "In the midst of chaos, there is also opportunity.", author: "Sun Tzu"),
        Quote(text: "The noble-minded are calm and steady. Little people are forever fussing and fretting.", author: "Confucius"),
        Quote(text: "What the superior man seeks is in himself; what the small man seeks is in others.", author: "Confucius"),
        Quote(text: "Still your waters.", author: "Josh Waitzkin"),
        Quote(text: "Be present above all else.", author: "Naval Ravikant"),
        Quote(text: "Don't be satisfied with stories, how things have gone with others.", author: "Rumi"),

        // ── Eric Baret ────────────────────────────────────────────────────
        Quote(text: "There is no path to silence. Silence is the path.", author: "Eric Baret"),
        Quote(text: "When the observer disappears, what remains is observation.", author: "Eric Baret"),
        Quote(text: "You are not the one who experiences — you are the experiencing itself.", author: "Eric Baret"),
        Quote(text: "Nothing needs to be added. Nothing needs to be removed.", author: "Eric Baret"),
        Quote(text: "Presence is not something you can achieve. It is what you are.", author: "Eric Baret"),
        Quote(text: "The search for peace is the only obstacle to peace.", author: "Eric Baret"),
        Quote(text: "When the mind is no longer trying to grasp, life reveals itself.", author: "Eric Baret"),
        Quote(text: "Welcoming is the end of becoming.", author: "Eric Baret"),

        // ── Eckhart Tolle ─────────────────────────────────────────────────
        Quote(text: "Realize deeply that the present moment is all you ever have.", author: "Eckhart Tolle"),
        Quote(text: "The primary cause of unhappiness is never the situation but your thoughts about it.", author: "Eckhart Tolle"),
        Quote(text: "You are the sky. Everything else is just the weather.", author: "Eckhart Tolle"),
        Quote(text: "Life is the dancer and you are the dance.", author: "Eckhart Tolle"),
        Quote(text: "The power for creating a better future is contained in the present moment.", author: "Eckhart Tolle"),
        Quote(text: "Awareness is the greatest agent for change.", author: "Eckhart Tolle"),
        Quote(text: "Being at ease with not knowing is crucial for answers to come to you.", author: "Eckhart Tolle"),
        Quote(text: "Sometimes letting things go is an act of far greater power than defending or hanging on.", author: "Eckhart Tolle"),
        Quote(text: "The past has no power over the present moment.", author: "Eckhart Tolle"),
        Quote(text: "Accept — then act. Whatever the present moment contains, accept it as if you had chosen it.", author: "Eckhart Tolle"),
        Quote(text: "To be aware of little, quiet things, you need to be quiet inside.", author: "Eckhart Tolle"),
        Quote(text: "Stillness is where creativity and solutions to problems are found.", author: "Eckhart Tolle"),

        // ── Adyashanti ────────────────────────────────────────────────────
        Quote(text: "The truth is that you already are what you are seeking.", author: "Adyashanti"),
        Quote(text: "Stop trying to get somewhere. You are already here.", author: "Adyashanti"),
        Quote(text: "Enlightenment is a destructive process. It has nothing to do with becoming better.", author: "Adyashanti"),
        Quote(text: "True meditation has no direction or goal. It is pure openness.", author: "Adyashanti"),
        Quote(text: "If you are sincere, sincerity itself will guide you.", author: "Adyashanti"),
        Quote(text: "Everything is already assembled. There is nothing to add.", author: "Adyashanti"),
        Quote(text: "Silence is not the absence of sound, but the absence of self.", author: "Adyashanti"),
        Quote(text: "When you inquire 'Who am I?' the answer is not found — it is the finder that dissolves.", author: "Adyashanti"),
        Quote(text: "Love is not something you find. Love is something that finds you.", author: "Adyashanti"),
        Quote(text: "The willingness to be exactly as you are, right now, is the doorway.", author: "Adyashanti"),
        Quote(text: "You will always be free in this moment if you are not trying to be free.", author: "Adyashanti"),

        // ── Gangaji ───────────────────────────────────────────────────────
        Quote(text: "Stop. Be still. That's all. That's enough.", author: "Gangaji"),
        Quote(text: "You are the awareness in which everything appears and disappears.", author: "Gangaji"),
        Quote(text: "The longing for peace is peace itself, calling you home.", author: "Gangaji"),
        Quote(text: "What you are looking for is what is looking.", author: "Gangaji"),
        Quote(text: "Suffering comes from the idea that something is missing. Nothing is missing.", author: "Gangaji"),
        Quote(text: "The willingness to meet yourself fully, just as you are, is radical love.", author: "Gangaji"),
        Quote(text: "True freedom is not the freedom to get what you want. It is freedom from wanting.", author: "Gangaji"),
        Quote(text: "In this moment, let everything be as it is.", author: "Gangaji"),
        Quote(text: "You are not in a process of becoming. You are already that which you seek.", author: "Gangaji"),
        Quote(text: "The deepest rest is found not in sleep but in the recognition of your own being.", author: "Gangaji"),

    ]

    static func randomQuote() -> Quote {
        quotes.randomElement() ?? quotes[0]
    }

    // Fonts well-suited for spiritual/contemplative text overlays
    static let availableFonts: [(name: String, display: String)] = [
        ("Zapfino",            "Zapfino"),
        ("Palatino-Roman",     "Palatino"),
        ("Didot",              "Didot"),
        ("Baskerville",        "Baskerville"),
        ("Georgia",            "Georgia"),
        ("Optima-Regular",     "Optima"),
        ("GillSans",           "Gill Sans"),
        ("Futura-Medium",      "Futura"),
        ("HoeflerText-Regular","Hoefler Text"),
        ("Copperplate",        "Copperplate"),
        ("AmericanTypewriter", "Typewriter"),
        ("Papyrus",            "Papyrus"),
        ("TimesNewRomanPSMT",  "Times New Roman"),
        ("HelveticaNeue",      "Helvetica Neue"),
    ]
}
