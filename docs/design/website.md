# Website

The public website served by Firebase Hosting: the app's landing page, the Privacy Policy and the
steps for Account Deletion without the app. The Play Console listing and the app's Account screen
link to the legal pages, so their addresses are part of the app's contract. The site is drawn with
the same design tokens as the app ([ADR-0068](../adr/0068-website-is-a-compose-multiplatform-wasm-app.md)),
and its landing page is a snapping pager over one animated graph
([ADR-0079](../adr/0079-website-landing-page-is-a-snapping-pager-over-one-morpheus-graph.md)).

## Pages and addresses

| Address | Page |
|---|---|
| `/` | The landing page: a hero, one page per feature, and a closing page |
| `/privacy` | The Privacy Policy |
| `/delete-account` | How to delete the account in the app or request Account Deletion by email, what is deleted and what is kept |

- Every address is served the same page; the site reads the path and shows the matching page.
- A trailing slash and the `.html` ending of the original static pages are accepted.
- Any other address shows the landing page.
- When the address is not a page's own (a trailing slash, an `.html` ending, an unknown path), it is
  replaced in place with the page's own address, without a new history entry.
- The landing page's pages have no addresses of their own; a reload returns to the hero.

## Navigation

- A link to another page of the site switches the page without reloading and adds a browser history
  entry, so Back and Forward move between pages.
- Email links open the visitor's mail app, with the subject prefilled on the deletion page.
- The browser tab title names the page shown.

## Landing page

Ten full-window pages, one at a time, that snap into place: the hero, eight feature pages and the
closing page.

### Moving between pages

- The page scrolls like any page: a trackpad moves it by exactly its own movement, momentum included,
  a mouse-wheel notch eases it by the notch's distance, and a touch fling travels as far as its speed
  carries it. The page can stop anywhere between two pages while the input continues.
- Snapping only assists. Once scrolling has come to rest, the page settles on the nearest page, leaning
  toward the one it was heading for, so a deliberate scroll lands on the next page and a small nudge
  settles back. The settle is short and decisive, without a bounce, and any new input takes over from
  where the page is.
- The pages respond in the same frame as the input, with nothing smoothed or queued.
- Arrow up and down and Page Up and Down move one page; Home goes to the hero and End to the closing
  page. Keys with Ctrl, Alt or Meta held stay with the browser.
- A column of dots on the window's right edge shows the current page; a dot opens its page.
- The hero's scroll hint opens the first feature.

### Backdrop

- The brand screen gradient fills the window, with one animated graph fixed in front of it, behind all
  pages; the graph does not scroll with them.
- Each page has its own graph shape: wide ribbons sweeping across the whole window at a new angle,
  height and curve, and blooms anchored in its corners. The hero's is brighter, a cover; the closing
  page's is calm. Glowing nodes are an accent, and every graph drifts, waves and pulses gently.
- As the pages scroll, the graph morphs from one page's shape to the next, nodes staying in place,
  with comets carrying the light from the old shape to the new one. The next shape starts to grow from
  the first pixels of a scroll. A slow scroll is followed exactly; a fast scroll or a snap morphs at
  most at a fixed pace, so the graph catches up shortly after the page lands.
- The site's graph family is its own, not the app's.

### Logo

- One logo for the whole landing page. On the hero and the closing page it is large, about half the
  window's width, above the tagline. On every feature page it is small, pinned at the window's top
  start corner.
- Between the hero and the first feature, and between the last feature and the closing page, it moves
  and shrinks continuously with the scroll, so it never leaves the window. A click opens the hero.

### Hero

- The tagline and a one-line description of the app under the large logo, a hint to scroll, and quiet
  links to the two legal pages at the bottom.
- On first load only, the graph fades in, then the logo and text fade up, in about half a second.
  Coming back to the landing page from a legal page does not replay it.

### Feature pages

- Each shows a headline, a subline, and a longer detail paragraph in smaller, quieter type for
  visitors who stop to read, next to a framed screenshot of the app. The page dots show where the
  visitor is; the pages carry no counter of their own.
- In a wide window, text and phone sit side by side, the phone alternating sides from one feature to
  the next. In a narrow window (under the Material expanded width), the text sits on top and the phone
  below; there is still one page per feature.
- Content stays clear of the pinned logo.
- The phone moves with its page. The text moves slightly slower than its page and fades in as its page
  arrives.
- A soft dark halo behind each block of text keeps it legible over the graph; it looks the same at
  every scroll position.
- Phone and text sit together as one group centered in the window, the text in a narrow column.
- Each feature has a light and a dark screenshot. The one shown follows the browser's color scheme and
  switches live when it changes. Besides the legal pages' card, nothing else on the site follows the
  system theme.
- A screenshot is loaded and decoded only when its page is on screen or next to it.

### Closing page

- The tagline again under the large logo, with links to the two legal pages and the contact address.

### Reduced motion

Under the browser's reduced-motion preference, the text moves with its page and does not fade, the
scroll hint stays still, and the first-load entrance does not play.

## Legal pages

- The Flashcards logo, large and centered at the top, scrolling away with the page; it opens the
  landing page.
- The page's text in one readable column with a maximum width, on an opaque card in the app's own
  card colors: light with dark text when the browser asks for a light color scheme, dark grey with
  light text when it asks for dark, switching live. Links take the app's primary color. The page
  scrolls when it is taller than the window.
- The closing page's calm graph sits fixed behind, over the brand gradient.
- Sections are separated by a hairline divider and a section heading.
- The same links as the closing page follow the text.

## Look

- Every page sits on the brand screen gradient, the one behind splash, login and onboarding, in both
  light and dark mode, because the gradient never flips.
- Text is the on-gradient white, with secondary lines (the feature sublines and detail paragraphs, the
  policy date, the deletion page's subtitle, the quiet links) at reduced opacity, in the app's Roboto
  type scale.
- On the landing page the type scale grows with the window beyond a reference width, up to a cap, so
  text fills a large desktop window; narrow windows use smaller slots of the same scale.
- The legal pages' text can be selected and copied. The landing page's cannot, and it shows no
  context menu; its clickable logo, scroll hint and dots show a pointer but no hover highlight.

## Loading and limits

- Until the site has loaded, the window already shows the brand gradient, so there is no white flash.
- With JavaScript off, a short fallback names the contact address for privacy and deletion requests.
- The pages are drawn on a canvas, so search engines and screen readers read little of them beyond
  the page title and description.
- The local preview serves every path the same page, as Hosting does, on a port of its own, since the
  default one belongs to the Firestore emulator.

## Related

- [ADR-0068](../adr/0068-website-is-a-compose-multiplatform-wasm-app.md): why the site is built this way.
- [ADR-0079](../adr/0079-website-landing-page-is-a-snapping-pager-over-one-morpheus-graph.md): the landing page's pager, graph and input.
- [account.md](account.md): the Account screen's privacy policy row and in-app Account Deletion.
