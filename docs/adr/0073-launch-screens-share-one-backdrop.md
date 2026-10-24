# Launch screens share one backdrop

> Status: accepted

## Context

Splash, Onboarding and Login each drew the brand gradient and their own Morpheus graph. A graph's clock
starts when its background is first drawn, so two backgrounds showing the same spec with the same seed
still place their nodes differently. Handing one screen's graph to the next as a morph was therefore
impossible: the incoming graph always jumped. Each screen faded its graph in from nothing instead, and
Splash was cut in a single frame under the incoming opaque screen once the shared logo had landed.

The splash also needed a graph that scales up and turns into place while it fades in. Morpheus specs
have no scale or rotation, and Morpheus is changing heavily in its own repository, so a transform could
not be added to the library yet.

## Decision

**One backdrop behind the nav host serves every launch screen.** `LaunchBackdropHost` draws the base
color, the gradient and one graph behind the `NavHost` while Splash, Onboarding or Login is shown or
still leaving, and is disposed once none is. The launch screens draw only their content over it and
reach its state through a CompositionLocal. Moving between them never swaps the graph: each arriving
screen morphs it from the screen as it is to its own spec, and the clock keeps running. With nothing
shown yet, as when Onboarding or Login opens from Main, the arriving screen shows its spec invisible and
fades it in, as before.

**Splash's scale and rotation live in the app, on the graph's layer.** The backdrop state carries the
graph's opacity, scale and rotation as animated values, applied as a graphics layer over the drawn
graph, plus how far the gradient has slid in. Only Splash animates scale and rotation, and they settle
before it hands over, so every handoff is a pure spec morph. The splash spec draws nothing outside its
bloom, so the rotation never shows the graph's edge. The transform moves into Morpheus once the library
can take it.

**Launch screens leave by fading their content, not by being cut.** The screen going out stays visible
under the transparent one coming in, so it fades out quickly while staying composed until the shared
logo lands. A screen leaving for a launch screen from outside the launch flow is removed at once, since
it would otherwise show through.

**Screens keep a fallback backdrop of their own.** Without a host, as in previews and the screen
showcase, a launch screen makes and draws its own backdrop state, so it stays self-contained.

## Considered options

- **Per-screen graphs with a handoff.** Each screen keeps its graph, and an injectable clock plus a
  holder of the last frame lets the incoming screen start where the outgoing one stopped. Rejected: it
  needs new Morpheus API, two renderers overlap during the logo's flight, and any mismatch shows as a
  jump.
- **Scale and rotation in the Morpheus spec.** Strokes would stay crisp and the morph would unwind the
  transform. Deferred: the library is mid-rework, so the app applies the transform until it lands there.
