# User Flow — Main

Authentication entry/exit and the navigation shell. Each tab's internal flows are in their own file.

```mermaid
flowchart TD

    %% Legend: (Screen)  [/Action/]  {Decision}  ([Entry/Exit])  {{NYI}}

    %% ── App Launch ────────────────────────────────────────────────
    AppLaunch([App Launch]) --> Splash(SPLASH SCREEN)
    Splash --> SeenCheck{Onboarding seen?}
    SeenCheck -->|no| Onboarding(ONBOARDING\n8-step pager · Skip jumps to the final step)
    SeenCheck -->|yes| AuthCheck{Signed in?\nGuest counts as no}
    AuthCheck -->|no| Login(LOGIN SCREEN\nGoogle or GitHub)
    AuthCheck -->|yes| NavRoot

    Onboarding -->|Start studying, signed in| NavRoot
    Onboarding -->|Start studying, not signed in| Login
    Login -->|sign-in success, onboarding seen| NavRoot
    Login -->|sign-in success, not seen| Onboarding

    %% ── Navigation Root ───────────────────────────────────────────
    NavRoot(MAIN SCREEN\nbottom nav)
    NavRoot --> HomeTab[🏠 Home — see home.md]
    NavRoot --> StudyTab[📚 Study — see study.md]
    NavRoot --> SettingsTab[⚙️ Settings — see settings.md]
    NavRoot -.->|debug builds only| DebugTab[🐞 Debug hub]
    ProgressTab{{📊 Progress — not built, see progress.md}}

    %% ── Cross-tab ─────────────────────────────────────────────────
    HomeTab -.->|empty state CTA\ntab switch · no stack push| StudyTab

    %% ── Account and sign-out ──────────────────────────────────────
    SettingsTab --> Account(ACCOUNT SCREEN)
    Account --> ReportBug(REPORT A BUG)
    Account --> Licenses(OPEN-SOURCE LICENCES)
    Account -->|Sign out · Delete account| Login
```
