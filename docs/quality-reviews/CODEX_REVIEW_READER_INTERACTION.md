# Reader interaction arbiter review

## Verdict: REQUEST CHANGES

### P0

None.

### P1

- `app/src/main/java/com/beautifulquran/ui/reader/ReaderInteraction.kt:55` — `UserMovedPage` disables follow but deliberately preserves `pendingJumpAyah`; `SearchNavigated` does the same at line 69. Because the jump effect is keyed only by `requestedJumpAyah` (`ReaderScreen.kt:582`), neither event cancels the in-flight jump. A hand drag can therefore still be pulled by `focusController.focus()`, and a search glide can race/serialize with the older rail jump and land on the wrong intent. The newer direct-manipulation/search intent must supersede and clear the pending jump (which cancels that effect); add transition tests for hand-during-jump and search-during-jump. The existing test at `ReaderInteractionTest.kt:139` currently codifies the incorrect precedence.

- `app/src/main/java/com/beautifulquran/ui/reader/ReaderScreen.kt:723` — display-reflow focus still gates on raw `followEnabled` at lines 723 and 737 rather than `ReaderInteraction.shouldFollowPlayback(interaction)`. A settings/layout change can consequently issue playback focus while annotation or a jump owns focus, bypassing the arbiter and competing through the focus-controller mutex. Use the arbiter predicate consistently (and cover annotating/pending-jump reflow ownership at the nearest practical pure or wiring test boundary).

### P2

- `app/src/test/java/com/beautifulquran/ui/reader/ReaderInteractionTest.kt:54` — search and chapter advance are tested only from idle, so the suite does not establish the critical supersession table it claims to own. Add sequence cases for search/hand/annotation/chapter advance during a pending jump, a newer jump followed by a stale `JumpSettled`, and annotation close preserving the prior follow choice. The reducer itself is otherwise appropriately small; no additional abstraction is warranted.

Ship recommendation: Fix the two P1 ownership leaks and add transition-sequence coverage before shipping; no PlayerCommandGate or ReaderSessionGate regression was found in the reviewed paths.
