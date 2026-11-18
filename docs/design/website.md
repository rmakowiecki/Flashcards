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
- The pages crossfade over about 300ms over one shared backdrop. The graph morphs to the new page's
  shape: the hero's on the landing page, the closing page's on the legal pages.
- Email links open the mail app. The deletion page prefills the subject.
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

### Hero

- The tagline and a one-line description under the logo, a scroll hint, and quiet legal links at the
  bottom. Soft dark halos keep the text legible over the graph.
- The scroll hint, the links and their halo fade with the scroll and are gone once it is halfway to
  the next page.
- On first load the graph fades in, then the logo, the text, the hint and the links reveal in turn.
  Returning from a legal page replays only the logo, hint and links.

### Feature pages

- A headline, one supporting paragraph of about 30 words that keeps the page's core message in a
  call-to-action voice, and a framed screenshot of the app.
- A window at least 0.9 times as wide as tall shows text and phone side by side. The phone alternates
  sides and takes at most 45% of the row. A narrower window stacks the text above the phone.
- The phone casts a soft shadow and moves with its page. The text lags slightly and fades in.
- A soft dark halo with no visible edge sits behind the text. The phone always sits above the halo and
  the graph, so the halo never dims it, and the pinned logo and link sit above everything.
- The screenshot follows `prefers-color-scheme` live. It loads only when its page is on or next to the
  screen.

### Closing page

- The large logo, the tagline, a call to join and start learning right now, and the links under one
  halo, in the order Privacy Policy, Contact, Delete your account. The links sit well below the text,
  a share of the window's height, not at the bottom edge.

### Small windows

A window narrower than 600dp (a phone in portrait) or shorter than 480dp (a phone in landscape) is
compact. Larger windows are unchanged apart from the dots not being clickable.

- The page dots are smaller, and every page keeps its content clear of them.
- Feature text uses smaller type, so the screenshot gets the room, but it keeps clear gaps between the
  headline, the paragraph and the phone. Side by side, the text is narrow (about 380dp) and sits close
  to the phone, and the two are centred as one group.
- The page follows the browser's visible height. When a mobile browser hides or shows its address bar,
  the page grows or shrinks with it and settles on the nearest page once the scrolling stops and no
  finger is down. No empty bar is left at the bottom, and no part of the previous page shows.
- In landscape the pinned logo is small, with a small margin, so the phone gets the height. The hero
  and the closing page show a smaller logo near the top and smaller text. The hero has no scroll
  hint, and the links stay in one row.

### Components appearing

- A component fades in while rising about 16dp, over about 450ms. Components on a page start about
  80ms apart. The large logo also grows slightly.
- The hero's and closing page's logo and links reveal as the pages head for their page. They replay
  after the page has fully left the window.
- Text, phones and the pinned logo do not reveal, because they move with the scroll.

### Reduced motion

Under `prefers-reduced-motion`, text moves with its page, nothing fades or reveals, the first-load
entrance does not play, hover changes at once, and pages switch without a crossfade.

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
- On the landing page, the type grows past a 1280dp reference, up to a cap. It grows by the smaller
  of the window's width and its height at 16:9.
- Landing paragraphs wrap into lines of equal width, like CSS `text-wrap: balance`. The legal pages
  wrap as usual.
- Legal text is selectable. Landing text is not, and the landing page shows no context menu.
- Quiet links, the scroll hint and the legal logo show a pointer and brighten over about 150ms on
  hover. Page dots grow on hover.

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
