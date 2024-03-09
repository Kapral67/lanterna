# Lanterna 595

## Intro

The `AnimatedLabel` class allows for a series of frames to be collected within a single Component. These frames are then cycled through indefinitely via a separate Animation Thread (AnimationTask). This is useful as we can simplify what would otherwise be manually adding and removing individual Label components as frames onto some parent component which requires significant boilerplate code and is error-prone as such an implementation would require knowledge of lanterna's internal operation to setup properly. The `AnimatedLabel` allows developers to create animations with simple public api calls and rightfully abstracts away implementation details, simplifying the experience.

## Problem Statement

The indefinitely running Animation Thread poses the issue that it could prevent JVM shutdown and thus hang an application indefinitely. Currently, the solution to that is for the Animation Thread to retrieve the AnimationLabel it is tied to each iteration via a Weak-Reference. That way, the Animation Thread would not block Garbage Collection of the AnimationLabel object. However, this strong dependency upon Garbage Collection for a critical part of this class's logic is faulty since the Garbage Collector's implementation is not deterministic and most certainly behaves differently depending upon the Java implementation and System. Another contention is that there are cases where Strong-References to the `AnimationLabel` may still exist, but the Animation Thread should still be killed. Take the following for example:

Say we have `Window w`, `w` is then given a `Panel p` to serve as the container for its display elements. We add an `AnimatedLabel a` to `p` via `p.addComponent(a)`, then we set `w` component to `p` like `w.setComponent(p)`. In this common scenario (see `WaitingDialog`), a Strong-Reference to `a` will always exist as long as a Strong-Reference to `w` is maintained. Furthermore, even if `w.showDialog(gui)` and `w.close()` are called, these would only add and remove `w` to and from the `gui` respectively and the component chain `w <- p <- a` is still intact.

In the above scenario, we would instead want the Animation to begin when displayed (its parent has-a/is-a `@NotNull TextGUI`) and stopped when no longer displayed.

Lastly, having the Animation Thread execute continuously regardless if the `AnimatedLabel` is displayed on a GUI or not is wasteful, especially if there are multiple `AnimatedLabel` objects. The developers who depend upon lanterna should not need to be concerned about the lifecycle of their `AnimatedLabel` objects as if they were Threads because (a) then usage of the `AnimatedLabel` as a public api would require developers to have implementation knowledge of the `AnimatedLabel` class which defeats the purpose of having this api and (b) `AnimatedLabel`'s public interface does not indicate (through `extends` or `implements`) its usage/creation of Thread(s) or that it is a Thread, so there is zero intuition for any developer to treat instantiations of `AnimatedLabel` as Threads.

## Solution 1

### Abstract

This solution proposes modifications to the `AnimatedLabel` class so that it monitors the presence/abscence of a TextGUI displaying it via the `Component::getTextGUI` method it inherits. This way the animation can be started when a TextGUI is first detected and stopped once the TextGUI is gone.

### Requirements

1. Determining when to start and stop animations must be done asynchronously (monitor task)

2. Waiting & detecting prescence/abscence of a TextGUI must be done busilessly

  - otherwise, we might as well start the animation on instantiation as is currently implemented

3. Window objects can be reused (added to gui, removed from gui, added back to gui); `AnimatedLabel` objects should be reusable too

  - i.e. calling `AnimatedLabel::startAnimation` after a previous call to `AnimatedLabel::stopAnimation` on that same object is a valid use-case

### Limitations

- Monitor task needs to begin upon instantiation (in ctor) otherwise we could create a race condition between starting the monitor task and the `AnimatedLabel` being displayed on a TextGUI

### To Investigate

How can we busilessly detect when `AnimatedLabel::getTextGUI` changes?