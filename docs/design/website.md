# Website

The public website served by Firebase Hosting: a home page, the Privacy Policy and the steps for
Account Deletion without the app. The Play Console listing and the app's Account screen link to the
legal pages, so their addresses are part of the app's contract. The site is drawn with the same
design tokens as the app ([ADR-0068](../adr/0068-website-is-a-compose-multiplatform-wasm-app.md)).

## Pages and addresses

| Address | Page |
|---|---|
| `/` | Home: the logo, the app's tagline, and links to the two legal pages |
| `/privacy` | The Privacy Policy |
| `/delete-account` | How to request Account Deletion by email, what is deleted and what is kept |

- Every address is served the same page; the site reads the path and shows the matching page.
- A trailing slash and the `.html` ending of the original static pages are accepted.
- Any other address shows Home.
- When the address is not a page's own (a trailing slash, an `.html` ending, an unknown path), it is
  replaced in place with the page's own address, without a new history entry.

## Navigation

- A link to another page of the site switches the page without reloading and adds a browser history
  entry, so Back and Forward move between pages.
- Each legal page opens with a link back to Home.
- Email links open the visitor's mail app, with the subject prefilled on the deletion page.
- The browser tab title names the page shown.

## Look

- Every page fills the window with the brand screen gradient, the one behind splash, login and
  onboarding, in both light and dark mode, because the gradient never flips.
- Text is the on-gradient white, with secondary lines (the policy date, the deletion page's subtitle)
  at reduced opacity, in the app's Roboto type scale.
- Content sits in one centered column with a maximum width; it scrolls when it is taller than the
  window.
- Legal pages separate their sections with a hairline divider and a section heading.
- All text can be selected and copied.

## Loading and limits

- Until the site has loaded, the window already shows the brand gradient, so there is no white flash.
- With JavaScript off, a short fallback names the contact address for privacy and deletion requests.
- The pages are drawn on a canvas, so search engines and screen readers read little of them beyond
  the page title and description.

## Related

- [ADR-0068](../adr/0068-website-is-a-compose-multiplatform-wasm-app.md): why the site is built this way.
- [account.md](account.md): the Account screen's privacy policy row and in-app Account Deletion.
