# Launch screens share one backdrop

> Status: accepted

## Context

Splash, Onboarding and Login each drew their own gradient and Morpheus graph. A graph's clock starts
when it is first drawn, so the next screen's graph always jumped instead of continuing the last one.
Splash also needed a graph that scales and turns into place, which Morpheus specs cannot express.

## Decision

**One backdrop behind the nav host.** `LaunchBackdropHost` draws the gradient and one graph behind the
`NavHost` while a launch screen is shown or leaving. Launch screens draw only their content over it.
The graph's clock keeps running across screens, and an arriving screen morphs it to its own spec. A
launch screen opened from Main, with no graph shown, fades its spec in.

**Scale and rotation live in the app.** The backdrop state carries the graph's alpha, scale and rotation
as a graphics layer, plus the gradient's slide. Only Splash animates them, and they settle before it
hands over, so every handoff is a pure spec morph. They move into Morpheus once it supports them.

**Launch screens fade out instead of being cut.** The outgoing screen shows under the transparent
incoming one, so its content fades fast while it stays composed until the shared logo lands. A screen
outside the launch flow leaving for one is removed at once, or it would show through.

**Fallback state.** Without a host (previews, the showcase), a launch screen draws its own backdrop.

## Considered options

- **Per-screen graphs with a clock handoff** (an injectable clock plus a last-frame holder). Rejected:
  needs new Morpheus API, two renderers overlap during the logo's flight, and any mismatch shows as a jump.
- **Scale and rotation in the Morpheus spec.** Strokes would stay crisp and the morph would unwind the
  transform. Deferred until Morpheus specs support a transform.
