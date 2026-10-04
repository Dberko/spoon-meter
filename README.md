# Spoon Meter

Rates how **spooned** or **cursed** your account is, from collection log kill counts and uniques.

For each collection log page it works out how many uniques that KC should have produced, then reports
where you sit against it — an account-wide score plus a per-boss breakdown, and for items you are
missing, the share of players who would already have one.

```
             Hard Cursed
          Spoon score 3 / 100
   [======|===============================]
      14 uniques vs 21.3 expected  (0.7x)
```

## Using it

Open your collection log and click through the pages you care about. Each page is read while it is on
screen and remembered; nothing is gathered in the background. The sidebar panel fills in behind you.

- **`::spoon`** prints the verdict as a client message.
- **`!spoon`** rewrites your own message where it is displayed, the way `!kc` does. `!spoon araxxor`
  rates one page and includes its KC; names match loosely (`arax`, `the gauntlet`).
- **Copy** / **Chat** copy the full breakdown, or a line short enough for a game chat message.

```
Spoon Meter: A Bit Dry (27/100) - 108 uniques vs 114.7 expected, 0.9x
Spoon Meter Araxxor: Spooned (92/100) - 468 kc, 9 uniques vs 5.5 expected, 1.6x
```

`!spoon` renders for you alone — everyone else receives the literal text. It only ever rewrites the
local player's own message, because no other client holds your collection log.

## Behaviour worth knowing

- **No network.** The plugin makes no requests and uploads nothing.
- **No input.** Nothing is written to the client; the only `invokeLater` is `SwingUtilities`, for the
  panel.
- **Storage.** All file access goes through RuneLite's `Filepath`, confined to the plugin directory.
  Read pages are saved to `plugin-data/spoon-meter/<rs profile key>.json`.
- **Parsing.** The collection log is matched on structure — the `Obtained: x/y` line and the widget
  holding the most item children — rather than hardcoded child ids.

## The maths

Each unique is an independent trial per kill, so the count you own is a sum of binomials with small
`p`, which is Poisson with `lambda = sum(kc x drop chance)`. The rating is the mid-p percentile of
your observed count, `P(X < k) + 0.5 * P(X = k)`, evaluated through the regularized incomplete gamma
function so it holds up at raid-sized lambdas. Per-item dryness is `1 - (1 - p)^kc`.

## Drop rates

Rates live in [`drop_rates.json`](src/main/resources/com/spoonmeter/drop_rates.json), from the OSRS
Wiki. Drop a `drop_rates.json` into the plugin directory to override it without rebuilding.

Pages that count several activities separately declare `variants`, each naming its counter, and
expectations are summed across them — so 11 normal and 1,487 corrupted Gauntlet runs score at 1/120
and 1/50 rather than one blended rate. Same for the three Dagannoth Kings on one page and the raid
modes. Rates that move with team size, points or invocation are flagged `approx` and shown with a
`*`; the raid unique chances are config values instead. Items that drop in **stacks** are
deliberately excluded, since the log records items received rather than drops.

## Known limitations

- **Shared items are pooled.** A page shows your *total* quantity of an item, not what that source
  gave you, so one Virtus robe top appears on all four Desert Treasure pages and one uncut onyx on
  Zulrah, Zalcano and Skotizo. Such items are counted once and split between their sources by
  expectation share, so a boss with one kill cannot claim a drop another earned over hundreds. The
  residue: if a source is missing from the drop table — Slayer tasks, Zalcano — its drops still land
  on whichever rated page lists the item.
- KC is a proxy for rolls, and loot left on the floor was never seen by the log.
- Duplicate protection (Araxxor halberd pieces, DT2 vestiges) is modelled as independent rolls.
- A single rare drop swings one page hard at low KC; the account figure is far steadier.
- A page whose counters match no variant is skipped rather than guessed at. The panel footer reports
  how many pages went unrated.

## Building

Standard RuneLite plugin build: `gradle build`. [`build.ps1`](build.ps1) is a convenience for
building without Gradle, compiling against the jars RuneLite already caches; `-Verify` runs the test
suite as plain assertions when no test framework is on the classpath.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
