# MKT-1 · Reddit and launch plan for Dahlia

23 October 2026. Low-budget promotion for the Android app, Reddit first. Companion to [STORE-1](store/STORE-1-play-listing.md) (listing) and BLOG-1 (`docs/BLOG-1-blog-concept.md` in the tearoom.one website repository, the blog).

## 1. Rules that apply everywhere

1. Show, don't sell: one image or a short clip, two or three plain sentences, the link in a comment or where the subreddit's rules put it. No "revolutionary", no feature lists.
2. Say what it is in the first line: free, open source (GPL), no account, no ads, optional one-time Pro. These four facts are what Reddit rewards.
3. Answer every comment within the first two hours; that is when a post lives or dies. Thank people for bug reports and say when they are fixed.
4. One subreddit per day at most, never the same image twice in a week. Cross-posting the same text to five subreddits in one hour gets flagged as spam.
5. Post from an account with some history. If Mathis' account is new or empty, spend a week commenting in the target subreddits first.
6. Read each subreddit's rules on the day of posting; self-promotion rules change.

## 2. Where, what and when

| Order | Subreddit | Rule to watch | What to post | Day |
|---|---|---|---|---|
| 1 | r/androidapps | "Self Promo Saturday" megathread only; direct posts are removed | Text post: what it is, the four facts, Play link, one screenshot | Saturday |
| 2 | r/fossdroid | Open-source apps only; link to source welcome | Link post to GitHub + Play link in comment; mention GPL, no analytics, Play Billing as the only proprietary piece | Any weekday |
| 3 | r/AndroidWallpaper and r/wallpaper | Wallpapers themselves, not app ads; OC tag | The rendered mandala as a 1080×2160 image, title names the style ("Hypocycloid over Universe, made in Dahlia"), app named in a comment only | Weekend |
| 4 | r/mandala | Art community; be a member, not a vendor | One finished mandala with the emblem (Om or Sri Yantra), ask what motif they would want next | Any day |
| 5 | r/sacredgeometry | Same as r/mandala | Flower of Life or Sri Yantra render | Two weeks later |
| 6 | r/Android_Apps and r/androiddev | r/androiddev is for developers: post about building, not about the app | "Ported a macOS renderer to Kotlin with a 256 MB heap budget" (ADR-0002) with a link to the repository | After the first blog post exists |
| 7 | r/opensource | Show the project | Short, same as r/fossdroid | Month two |

Skip: r/Android (no app promotion at all), r/apps (dead), paid "app review" subreddits.

## 3. Post texts

**r/androidapps, Self Promo Saturday (comment in the megathread):**

> Dahlia – Mandala Maker (free, open source). Build mandalas from up to five layers with 36 drawing styles, put a symbol in the centre (Om, Tree of Life, Flower of Life, Sri Yantra …), add a quote, and save them or set them as wallpaper. No account, no ads, no tracking; everything stays on the device. Pro is a one-time 2.99 € purchase for more layers, emblems, 2048 px, a live wallpaper and daily wallpapers; you can try the editor features before buying. Source under GPL-3.0: github.com/morja/mandala-generator. Play: play.google.com/store/apps/details?id=one.tearoom.mandala. Happy to hear what is missing.

**r/fossdroid (link post to the repository, first comment):**

> Android app for making mandalas, GPL-3.0, Kotlin, no analytics or network use of its own. The one proprietary part is Google Play Billing for the optional Pro purchase; the free build without it is in the repository. Favourites are plain JSON and open in the macOS app too. Play link: … Questions about the rendering (a 256 MB heap budget for 1024 px exports) welcome.

**r/AndroidWallpaper (image post):**

> Title: "Phyllotaxis over a Universe layer [OC, 1080×2160]"
> Comment: "Made with Dahlia, a free mandala app I wrote; link in profile if anyone wants to make their own."

**r/mandala (image post):**

> Title: "Sri Yantra with a hand-drawn ring, generated from curves"
> Comment: what the layers are, which style, and that the app is free; link only if asked or in profile.

## 4. Assets to prepare before the first post

1. Six wallpaper-sized renders (1080×2160) from the starter favourites, exported from the app at screen size: two with emblems, one with a quote, three pure layers. Keep them in `docs/marketing/` (not yet created).
2. One 10-second clip of the live wallpaper turning, as MP4 and GIF (Reddit plays MP4; GIF for comments). The promo video on YouTube is not good enough for this; record the phone screen directly.
3. The Play link with a campaign parameter so installs from Reddit show up in the Play Console: `https://play.google.com/store/apps/details?id=one.tearoom.mandala&referrer=utm_source%3Dreddit%26utm_medium%3Dpost` (Play Console → Grow → Store performance → Acquisition reports; the "referrer" parameter is Google's own).
4. Profile text on Reddit with the Play link, so image posts never need a link in the comments.

## 5. After Reddit

- Reviews: the first five to ten come from friends who use the app (Mathis). An in-app review prompt (Play In-App Review API after the third saved mandala) is a code change for the next release.
- Pinterest: a board "Mandala wallpapers" with the renders from point 4.1, each pin linking to the Play page. Mathis has an old account; revive it when the blog has its first post, so pins can point to a how-to page as well.
- Product Hunt: only together with a Mac release under the Dahlia name (ADR-0012, point 5).
- Blog posts and the rest of the content plan: BLOG-1.

## 6. What to measure

Play Console → Statistics: store listing visitors, installs per country (the five new listing languages should show up within two weeks), acquisition by UTM source. Review after four weeks; drop channels that brought nothing.
