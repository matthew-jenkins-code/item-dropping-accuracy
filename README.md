# Item Dropping Accuracy

A RuneLite plugin that tracks click accuracy and speed while you drop or open a full inventory
(for example fish while barbarian fishing, or Empty bucket packs), and guides you with a click-pattern overlay.

- **Pattern overlay:** the next item pulses, the following ones are numbered, and skipped or failed items turn red and are queued at the end.
- **Patterns:** row by row, column by column, snake rows/columns, DU/UD Snake Rows (right or left pair of columns first, down then up or up then down), or a custom slot list.
- **Accuracy:** hits out of all clicks in the run. A click only counts if the item actually left the inventory. Clicking out of order, or an item opening by itself, fails the run.
- **Stats:** run time, best time per item count, perfect-run counter and perfect-run streak, and session totals.
- **Settings:** items per run, only/excluded items (names, `*` wildcard, or ids), exclude noted items, click actions.

The plugin never clicks or sends input for you; it only observes your clicks and draws overlays.

Run a development client with `./gradlew run`.
