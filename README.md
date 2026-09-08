# Spoon Meter

A RuneLite plugin that reads your collection log and tells you, with actual statistics, how
**spooned** or **cursed** your account is.

It takes the kill count and the uniques on each collection log page, works out how many uniques that
KC *should* have produced, and turns the gap into a percentile: the share of accounts with your
exact KC who would be doing worse than you.

```
             Hard Cursed
          Spoon score 3 / 100
   [======|===============================]
      14 uniques vs 21.3 expected  (0.7x)
   Luckier than 3% of accounts at your KC.
```

## What it shows

- **An account-wide verdict** — one of nine bands from *Spooned by the Gods* down to *RNG Hates You*,
  with a 0–100 spoon score.
- **A per-boss breakdown** — every rated page with its KC, uniques obtained versus expected, and its
  own rating. Sort by cursed first, spooned first, KC, or name.
- **The item breakdown** — click any boss to see each unique, and for the ones you are missing, the
  percentage of players who would already have it at your KC. That is the number that tells you
  whether you are genuinely dry or just impatient.
- **`::spoon`** — prints the one-line verdict to your game chat as a client message.
- **`!spoon`** — typed into public, clan or friends chat, replaces your own message with the rating,
  the way `!kc` does. `!spoon gauntlet` rates a single page.
- **Copy** / **Say** — the full breakdown to the clipboard, or the one-line version typed straight
  into your chatbox, ready to send with Enter.

### What other people see

`!spoon` renders **for you only**. Everyone else sees the literal text `!spoon`. This is not a
limitation worth working around — it is how the client works:

- `ChatboxInput` is immutable and `ChatInput` exposes only `consume()`/`resume()`, so a plugin can
  cancel your message but cannot change the text that is sent. The core emoji plugin has the same
  property: it rewrites `MessageNode`s locally, which is why non-RuneLite players see `:)`.
- `!kc` looks like it works across clients because each viewer's own client fetches that player's
  stats from RuneLite's chat-stats API. Spoon Meter has no server and uploads nothing, so no one
  else's client can look your numbers up.

So to actually show a clan, the text has to end up in your own chat input. There is no paste into
the game chatbox either — the only Ctrl+V handler in the client is the login screen's. Instead the
panel's **Say** button writes the line into the chat input for you, and you press Enter:

```java
client.setVarcStrValue(335, line);       // chatbox typed text
client.runScript(222, "");               // redraw the input line
```

Both ids come from core RuneLite rather than guesswork — `KeyRemappingPlugin` writes varcstr 335 to
clear what you have typed, and `ChatHistoryPlugin` runs script 222 after filling in a reply. The
line is prefixed with `/` by default so Enter sends it to your clan; turn that off in the config for
public chat. The plugin never sends the message itself — you press Enter.

## Feeding it

Open your collection log and click through the pages you care about. Every page you view is read and
saved; nothing is collected in the background, because the client only knows what the log shows
while it is on screen. Re-open a page after a drop to update it.

Data is stored per account in `~/.runelite/spoon-meter/<profile key>.json`, keyed by RuneLite's own
RS profile key so switching accounts switches the saved log. It never leaves your machine.

## The maths

Each unique is an independent Bernoulli trial rolled once per kill, so the number of uniques you own
is a sum of independent binomials with small `p` and large `n`. That sum is a Poisson variable with

```
lambda = sum over items of (kc x drop chance)
```

Your rating is the mid-p percentile of your observed unique count under that Poisson — `P(X < k) +
0.5 * P(X = k)` — which is centred on 0.5 for a perfectly average account. The Poisson CDF is
evaluated through the regularized incomplete gamma function, so it stays accurate at raid-sized
lambdas where summing terms would fall apart.

The per-item "83% have it" figure is simply `1 - (1 - p)^kc`.

## Drop rates

Rates live in [`drop_rates.json`](src/main/resources/com/spoonmeter/drop_rates.json) — around 30
sources, from the OSRS Wiki. `rate` is a denominator (`1/rate` per kill); `weight` is used with the
variant's `totalWeight` and your configured raid unique chance.

### Split pages

Several collection log pages cover more than one activity, each with its own counter and its own
rates. Those sources declare `variants`, and each variant names the counter it is rolled by:

```json
{
  "name": "The Gauntlet",
  "variants": [
    { "counter": "Gauntlet completion count",
      "items": [ { "name": "Crystal armour seed", "rate": 120 } ] },
    { "counter": "Corrupted Gauntlet completion count",
      "items": [ { "name": "Crystal armour seed", "rate": 50 } ] }
  ]
}
```

Expectations are summed across variants, so 11 normal runs and 1,487 corrupted runs are scored at
1/120 and 1/50 respectively rather than being lumped together at one rate. This covers the Gauntlet,
the Dagannoth Kings (one page, three kings), and the normal/entry/hard/expert modes of the three
raids. Counter names are matched exactly enough that `(CM)` and `(Hard)` stay distinct — a page whose
counters do not match any variant is skipped rather than guessed at, and the panel footer tells you
how many pages went unrated.

To correct or extend the table without rebuilding the plugin, copy that file to
`~/.runelite/spoon-meter/drop_rates.json` and edit it. If that file exists it is loaded instead of
the bundled one.

Sources marked `approx: true` show a `*` in the panel — their real rates move with team size, points
or invocation level, so they are a best-effort estimate:

| Source | Why it's approximate |
| --- | --- |
| Chambers of Xeric, Theatre of Blood, Tombs of Amascut | Unique chance depends on points, team size and raid level — set yours in the plugin config |
| The Nightmare | Rates improve with party size; the bundled values are the solo rates |
| Grotesque Guardians, Thermonuclear smoke devil, DT2 bosses | Individual rates are less well documented than the rest |

### Raid config

Under **Raid drop rates**, set your average chance of a purple per raid. Each mode is separate,
because each is counted separately on the log page:

- **CoX** — `points / 8675` as a percent. 30k points is `3.5`.
- **CoX (CM)** — same rule on the points a Challenge Mode raid earns you.
- **ToB** — the team rate is 11% (1/9.1) split across the team, so a trio is about `3.7`.
- **ToB (HM)** — 13% (1/7.7) split across the team, so a trio is about `4.4`.
- **ToA** — roughly `points / (10500 - 20 x raid level)` as a percent; about `12` for a 400 solo.
- **ToA (Expert)** — higher raid levels raise both points and rate; about `15` for a 500.

## Known limitations

These are inherent to reading a collection log rather than a drop history, and are worth knowing
before you take a verdict personally:

- **KC is a proxy for rolls.** Pages counting chests, permits or completions are treated the same as
  kill counts.
- **Uncollected loot is invisible.** If you left drops on the floor, the log never saw them.
- **Duplicate protection is ignored.** A few tables (Araxxor halberd pieces, DT2 vestiges) hold back
  duplicates, which the model treats as independent rolls.
- **Items obtained before a page existed** still count, but any KC earned before Jagex started
  tracking a boss does not.
- **Very rare items dominate small samples.** A single pet at low KC can swing one page's rating
  hard. The account-wide number is much steadier.
- **Renamed counters drop a page silently.** Split pages match their counters by name, so if Jagex
  renames one the page stops being rated instead of being rated wrongly. Watch the "N rated of M
  pages read" line in the footer.

## The panel is 225px

RuneLite's sidebar is a fixed width — `PluginPanel.PANEL_WIDTH` is a constant and `ClientUI` has no
divider to drag — so a plugin cannot widen it or make it resizable. Every row therefore puts its long
text in `BorderLayout.CENTER` and its short value in `EAST`: the value keeps its full width and the
long text ellipsises into whatever is left, instead of the two overlapping. Full text is on the
tooltip, and counter names, which run to things like "Corrupted Gauntlet completion count", are shown
as a plain `1,498 kc` with the breakdown on hover.

## Building from the command line

No IDE and no Gradle needed. RuneLite already downloaded every jar this plugin compiles against into
`~/.runelite/repository2`, so [`build.ps1`](build.ps1) compiles straight against those. The only
extra tool is a JDK - RuneLite ships a JRE, which has no compiler:

```powershell
scoop bucket add java; scoop install temurin11-jdk
```

(or `winget install --id EclipseAdoptium.Temurin.11.JDK -e`). The script locates the JDK itself, so
no `PATH` or `JAVA_HOME` setup is needed.

| Command | What it does |
| --- | --- |
| `.\build.ps1` | Compile, package `build\spoon-meter.jar`, install it to `~\.runelite\sideloaded-plugins` |
| `.\build.ps1 -Verify` | Compile and run the self-check: 42 assertions over the maths, the drop table and the rating pipeline |
| `.\build.ps1 -Run` | Compile and launch RuneLite with the plugin loaded, using RuneLite's own JRE |
| `.\build.ps1 -Clean` | Wipe `build\` first |

To uninstall, delete `~\.runelite\sideloaded-plugins\spoon-meter.jar`.

### Getting it to actually load

Side-loading works only in a client that was **not** started by the official launcher. Two gates in
the client, both verifiable in the shipped bytecode:

```java
// PluginManager.loadSideLoadPlugins() — returns before reading the directory
if (!developerMode) return;

// RuneLite.main() — how developerMode is set
developerMode = options.has("developer-mode") && RuneLiteProperties.getLauncherVersion() == null;
```

`getLauncherVersion()` is just `System.getProperty("runelite.launcher.version")`, and the official
launcher always passes `-Drunelite.launcher.version=<version>`. So under the RuneLite launcher — and
therefore under the Jagex Launcher, which starts that same launcher — developer mode is forced off
and `--developer-mode` in the launcher's *Client arguments* has no effect. This is deliberate: the
Plugin Hub is RuneLite's sanctioned channel for third-party plugins, and hub jars are signature
checked against `externalplugins.crt`.

That leaves two options:

- **`.\build.ps1 -Run`** — starts a client outside the launcher, so the plugin loads. It has no
  Jagex account session though, so you get the old-style login screen. Fine for working on the
  plugin, not for playing on a Jagex account.
- **[Plugin Hub](https://github.com/runelite/plugin-hub)** — the only way to run this alongside the
  Jagex Launcher. Push this directory to a public GitHub repo, then open a PR against
  `runelite/plugin-hub` adding `plugins/spoon-meter` containing the repo URL and a commit hash.
  Once merged it installs from the in-client Plugin Hub like any other plugin.

### With Gradle instead

[`build.gradle`](build.gradle) is a standard RuneLite plugin build if you would rather use Gradle
(`gradle build`). It runs the JUnit versions of the same checks from [`src/test/java`](src/test/java).
No wrapper is checked in; `gradle wrapper` will generate one.

## Verification

`build.ps1 -Verify` runs [`SelfCheck`](src/test/java/com/spoonmeter/SelfCheck.java), a JUnit-free
version of the test suite so it works with nothing but a JDK. It covers the Poisson/gamma maths
against known values (including large-lambda stability, where a naive sum falls apart), the
integrity of the drop table (every item resolves to a real probability, every weight set sums to its
declared total), and the rating pipeline end to end.
