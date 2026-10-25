# Marketing

Share images for Dahlia and the websites, and the record of what went out. The plan with dates is [MKT-3](../MKT-3-editorial-plan.md); the Reddit plan is [MKT-1](../MKT-1-reddit-and-launch-plan.md). Technical details (template, script options, safe zones, how the backgrounds were rendered) are in [DETAILS.md](DETAILS.md).

## Where things are

| Folder or file | What |
|---|---|
| `share/friends-story/stories/story-<n>-en/` | Ready-made story sequences, five images numbered in posting order, plus `00-overview.jpg`. **Start here when posting a story.** |
| `share/friends-story/portrait/`, `square/`, `landscape/` | Single images for chats and feeds (A, D, C and F variants) |
| `share/sites/` | Feed cards for the websites (Mother Meera, Advaita Hub) |
| `posted/` | What was actually posted or scheduled, with captions (in git) |
| `texts/<name>.js` | The texts of one set: `friends` (plain lines), `friends-story` (the story series) |
| `MKT-2-share-images.html` | The template and viewer: open it in the browser, switch variant, image set, language and text set |

`share/` is not in git; `posted/` is.

## Change the texts

1. Edit `texts/friends-story.js` (or copy it to `texts/<name>.js` for a new audience).
2. Proof first, about half a minute:
   ```
   docs/marketing/render-share-images.sh friends-story proof
   ```
3. When it looks right, the full run, about four minutes:
   ```
   docs/marketing/render-share-images.sh friends-story
   ```

## Add a mandala as story background

1. Drop it as JPEG, 1080 × 1920, into `renders/story/` (a line goes under it) or `renders/story-quote/` (the image carries its own text, so it never gets a line). The file name becomes part of the image names, so name it something like `lotus.jpg`.
2. Proof, then full run, as above. Every image in those folders is used; the sequences spread them out by themselves. `SHUFFLE=1 docs/marketing/render-share-images.sh friends-story` puts them in random order for that run.

Or ask Claude to render a starter favourite in that format; that is how the current ones were made.

## Add an app screenshot

1. Save it as PNG, 1080 × 2160, to `../store/screenshots/phone-<n>-<name>.png`.
2. In `MKT-2-share-images.html`, add it to `SETS` under `story` (app card) or `wallpaper` (lock screen card).
3. Proof, then full run.

## Rules of thumb

- Instagram captions: "link in bio", hashtags. Facebook captions: first person, full link in the text, no hashtags.
- English copy: two main clauses need a full stop, never a comma. No evaluative words ("calmer, friendlier"), say what is there.
- Stories: four slides are enough (teaser, quote or quiet teaser, lock screen, app); the overview image shows the whole sequence.
- Never post the same mandala twice in a week.
