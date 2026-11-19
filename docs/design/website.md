# Website

The public website on Firebase Hosting has three parts: the app's landing page, the Privacy Policy,
and the steps for Account Deletion without the app. The Play Console listing and the app's Account
screen link to the legal pages, so their addresses are part of the app's contract. The site uses the
app's design tokens ([ADR-0068](../adr/0068-website-is-a-compose-multiplatform-wasm-app.md)). Its
landing page is a snapping pager over one animated graph
([ADR-0079](../adr/0079-website-landing-page-is-a-snapping-pager-over-one-morpheus-graph.md)).

## Pages and addresses

| Address | Page |
|---|---|
| `/` | The landing page: a hero, one page per feature, and a closing page |
| `/privacy` | The Privacy Policy |
| `/delete-account` | How to delete the account in the app or by email, and what is deleted or kept |

- Every address serves the same page, which reads the path. Any other address shows the landing page.
- A trailing slash or an `.html` ending is accepted. It is then replaced in place by the page's own
  address, with no new history entry.
- The landing page's pages have no addresses of their own. A reload returns to the hero.

## Navigation

- A link to another page switches without reloading and adds a history entry.
- The pages crossfade briefly over one shared backdrop. The graph morphs to the new page's
  shape: the hero's on the landing page, the closing page's on the legal pages.
- While the landing page fades out, it takes no keys or wheel, no longer moves the graph and gives up
  the browser's page snapping: the page coming in owns them.
- Email links open the mail app. The deletion page prefills the subject.
- Links that leave the site, the GitHub button and the author byline, open in a new tab, so the
  landing page stays open behind them.
- The tab title names the page shown.

## Landing page

Ten full-window pages that snap: the hero, eight feature pages and the closing page.

### Moving between pages

- The browser scrolls and snaps the pages with its own feel for trackpad, wheel, touch and momentum.
- Once the input ends, the pages snap to the nearest page. A snap never starts under resting fingers.
- One swipe stops at the next page (`scroll-snap-stop: always`). Late trackpad momentum may carry one
  page further.
- There is no visible scrollbar and no bounce at either end.
- Arrow up and down and Page Up and Down move one page smoothly. Pressed again mid-scroll, they go on
  from the target page. Home and End go to the first and last page. Keys with Ctrl, Alt or Meta stay
  with the browser.
- Page dots on the right edge sit on a frosted pill. A dot grows and brightens as the pages near its
  page. The dots only indicate: they take no click, touch or hover. The hero's scroll hint opens the
  first feature.

### Backdrop

- The brand gradient fills the window, with one fixed graph in front of it.
- Each page has its own shape of ribbons and corner blooms. The hero's is brightest. The closing
  page's is a calm band with no bloom.
- While scrolling, the graph morphs between page shapes with nodes in place. It follows a slow scroll
  exactly. A fast scroll morphs at most one shape per second, so the graph catches up after the page
  lands.

### Local tuner

- On `localhost` or `127.0.0.1` only, T toggles Morpheus's tuning sheet for the page on screen. The
  wheel over it scrolls the sheet.
- Layer edits apply to every page's graph. Shape edits apply to the page on screen.
- Extra controls set the minimum morph time, one page per swipe, the side-by-side aspect ratio, and
  the reveal time, rise and stagger. A button copies the values as Kotlin.
- Edits live in memory until reload.

### Logo

- The hero and the closing page show a large logo, about half the window's width, at the top of the
  text block. It moves and fades with the text.
- Feature pages pin a small logo at the top start corner and the Privacy Policy link at the top end
  corner. Both slide in from their edge with the scroll and fade in as they slide. The hero and the
  closing page have no pinned links: they carry their own. Neither logo is a link. The pinned link
  is the same size as the other links and sits as far from the end edge as from the top edge.
- The text under a large logo sits slightly closer to it than the logo's own bounds, and the tagline
  and its description are set well apart.

### GitHub and Google Play buttons

The landing page doubles as a portfolio link, so the hero and the closing page show two buttons side
by side, GitHub first. Feature pages show none.

- **View on GitHub**: a white pill with dark text and the GitHub mark. It opens the repository
  (`github.com/rmakowiecki/Flashcards`) in a new tab, which shows the README of its default branch.
  It brightens on hover.
- **Google Play**: while the app is in closed testing, an outlined, muted pill with a play triangle.
  It shows a not-allowed cursor, never brightens and opens nothing.
  - Hover, a tap or keyboard focus shows a hint under the buttons: "In closed testing on Google Play.
    Public release coming soon." on the page dots' frosted backing. Its space under the buttons stays reserved while
    it is hidden, so it never moves the page or covers anything. The next press anywhere hides a
    tapped hint.
  - It takes keyboard focus so the hint is reachable, but Enter does nothing.
- A compact window shows both buttons smaller, still side by side.
- One flag (`ProjectLinks.PLAY_STORE_AVAILABLE`) and the store address live together. Turning the
  flag on makes the Google Play button an outlined link to the store listing, opened in a new tab,
  with no hint.

At public launch a separate change turns the flag on, restores the closing page's original call to
action, swaps the pill for Google's official badge (which may not be altered), and puts it first.
That build goes to a `launch` preview channel and is promoted with
`firebase hosting:clone flashcards-8ad6d:launch flashcards-8ad6d:live` once the app is live on Google
Play.

### Hero

- The tagline and a one-line description under the logo, the GitHub and Google Play buttons under
  the text, a scroll hint, and quiet legal links at the bottom. The buttons move with the text. Soft
  dark halos keep the text legible over the graph.
- The large logo sits at its usual height when everything fits. In a shorter window the logo, text
  and buttons move up, then the logo shrinks, so they never reach the scroll hint and links. The
  closing page does the same with its links and byline and the window's bottom edge.
- The scroll hint, the links and their halo fade with the scroll and are gone once it is halfway to
  the next page. Once gone they take no click and no focus.
- On first load the graph fades in, then the logo, the text, the buttons, the hint and the links
  reveal in turn. Returning from a legal page replays only the logo, buttons, hint and links.

### Feature pages

- A headline, one supporting paragraph of about 30 words that keeps the page's core message in a
  call-to-action voice, and a framed screenshot of the app.
- A window that is wide enough for its height, and wide enough for a readable text column beside the
  phone, shows text and phone side by side. The local tuner sets the shape threshold. The phone
  alternates sides and takes under half of the row. Any other window stacks the text above the phone,
  and the phone fits the column's width and the height left.
- The phone casts a soft shadow and moves with its page. The text lags slightly and fades in.
- A soft dark halo with no visible edge sits behind the text. The phone always sits above the halo and
  the graph, so the halo never dims it, and the pinned logo and link sit above everything.
- The screenshot follows `prefers-color-scheme` live. It loads only when its page is on or next to the
  screen.

### Closing page

- The large logo, the tagline, the line "Coming soon to Google Play. Follow along on GitHub.", the
  GitHub and Google Play buttons, and the links under one halo, in the order Privacy Policy, Contact,
  Delete your account. The links sit below the buttons and their hint, a small share of the window's
  height further down, not at the bottom edge.
- A quiet byline under the links, "Built by rmakowiecki", links to the author's GitHub profile.

### Small windows

A window narrower than the phone-portrait breakpoint or shorter than the phone-landscape one is
compact (`LandingScale`). Larger windows are unchanged apart from the dots not being clickable.

- The page dots are smaller, and every page keeps its content clear of them.
- Feature text uses smaller type, so the screenshot gets the room, but it keeps clear gaps between the
  headline, the paragraph and the phone. Side by side, the text column is narrow and sits close to the
  phone, and the two are centred as one group.
- The page follows the browser's visible height. When a mobile browser hides or shows its address bar,
  the page grows or shrinks with it and settles on the nearest page once the scrolling stops and no
  finger is down. No empty bar is left at the bottom, and no part of the previous page shows.
- In landscape the pinned logo is small, with a small margin, so the phone gets the height. The hero
  and the closing page show a smaller logo near the top and smaller text. Any window under 700dp tall
  uses that smaller text on the hero and the closing page, so the logo keeps its room. The hero has no scroll
  hint, and the links stay in one row.

### Components appearing

- A component fades in while rising a short distance. Components on a page start one after another. The
  large logo also grows slightly. The tuner sets the time, rise and stagger.
- The hero's and closing page's logo, buttons and links reveal as the pages head for their page. They
  replay after the page has fully left the window. On the closing page the buttons, links and byline
  reveal together.
- Text, phones and the pinned logo do not reveal, because they move with the scroll.

### Reduced motion

Under `prefers-reduced-motion`, text moves with its page, nothing fades or reveals, the first-load
entrance does not play, hover changes at once, and pages switch without a crossfade. Keys and the
scroll hint jump to their page instead of scrolling through the pages between. The pinned logo and
link are there or not, with no slide or fade, and the graph switches shape at once, also between the
landing page and a legal page. A legal section shows at once. The preference is followed live.

### Keyboard focus

The links, the GitHub and Google Play buttons, the legal pages' logo and the hero's scroll hint show a
visible focus mark. A control that
is faded out, such as the pinned link on the hero, cannot be focused.

## Legal pages

- A large centered logo at the top scrolls with the page and opens the landing page. It brightens on
  hover.
- The logo, card and links reveal in turn as the page opens. Each section's divider and heading
  reveals the first time it scrolls into view. Body text never moves.
- The text sits in one readable column on an opaque card in the app's card colors. The card is light
  or dark by `prefers-color-scheme`, switching live. Links use the app's primary color.
- The closing page's calm graph sits behind the card.
- The closing page's links follow the text.

## Look

- Every page sits on the brand screen gradient, which never flips between light and dark mode.
- Text is the on-gradient white. Secondary lines use reduced opacity. Type follows the app's Roboto
  scale.
- On the landing page, the type grows once the window passes a reference size, up to a cap. It grows
  by the smaller of the window's width and its height at 16:9 (`LandingScale`).
- Landing paragraphs wrap into lines of equal width, like CSS `text-wrap: balance`. The legal pages
  wrap as usual.
- Legal text is selectable. Landing text is not, and the landing page shows no context menu.
- Quiet links, the scroll hint, the GitHub button and the legal logo show a pointer and brighten over
  about 150ms on hover. The closed-testing hint fades in over the same time, at once under reduced
  motion. Page dots grow on hover.

## Loading and limits

- The brand gradient shows before the site loads, so there is no white flash.
- With JavaScript off, a fallback names the contact address.
- Search engines and screen readers read little beyond the title and description.
- The local preview serves every path the same page, on its own port. The default port belongs to the
  Firestore emulator.

## Related

- [ADR-0068](../adr/0068-website-is-a-compose-multiplatform-wasm-app.md): why the site is built this way.
- [ADR-0079](../adr/0079-website-landing-page-is-a-snapping-pager-over-one-morpheus-graph.md): the landing page's pager, graph and input.
- [account.md](account.md): the Account screen's privacy policy row and in-app Account Deletion.
