# Carrot scalafmt fork

This is Carrot's fork of [scalafmt](https://github.com/scalameta/scalafmt),
maintained on branch `carrot/3.11.3` (upstream tag `v3.11.3` + fork commits).
It exists because Carrot formats under `newlines.source = keep` with a
source-driven house style that stock scalafmt cannot express: hand-written
breaks, glue, and alignment carry intent and are preserved (or repaired
toward the house style), instead of being recomputed.

The canonical consumer is `carrot-platform/.scalafmt.conf`.

## Versioning

- The version is **pinned** in `build.sbt` (`version := "3.11.3+CARROT.N"`);
  upstream's sbt-dynver is bypassed.
- The suffix must use `+`, not `-`: IntelliJ's bundled scalafmt-dynamic only
  parses `+`-suffixed custom versions and silently falls back to scalafmt
  1.5.1 otherwise. (`+` must be quoted in HOCON, so `.scalafmt.conf` writes
  `version = "3.11.3+CARROT.N"`.)
- The CLI runs in-process only when the config version equals the FULL build
  version, so a version bump is a **one-commit lockstep change** across:
  fork `build.sbt` → publish → `carrot-platform/.scalafmt.conf` (+ golden
  files if formatting changed).
- The Nexus releases repository disallows redeploy: every publish needs a
  fresh `CARROT.N`.

## Build, test, publish

```sh
sbt --batch cli/assembly    # → scalafmt-cli/jvm/target/scala-2.13/scalafmt.jar
sbt --batch tests/test      # full suite, ~25s
```

Publish (16 artifacts, credentials required — see the platform runbook):

```sh
CARROT_ARTIFACTS_USER=… CARROT_ARTIFACTS_PASSWORD=… \
  sbt --batch "interfaces/publish; dynamicCore/publish; cli/publish; +coreJVM/publish"
```

Artifacts go to `https://artifacts.getcarrot.io/repository/maven-releases/`
(anonymous reads). sbt 1 hosts fetch `scalafmt-core_2.12`, IntelliJ fetches
`_2.13`, sbt 2 plugins fetch `_3`; the standalone CLI needs the repo in
`COURSIER_REPOSITORIES`.

## Regression tests

- `scalafmt-tests/shared/src/test/resources/newlines/source_keep_carrot.stat`
  is the fork's regression corpus. Its header MUST mirror the carrot-platform
  config (minus the version line) — a drifted header silently tests the wrong
  behavior.
- `FormatTests.scala` pins the explored-states counter; bump it when adding
  stat cases (the failure message prints the new value).
- carrot-platform additionally pins two **golden pairs**
  (`scalafmt/FormattingTestOriginal.{scala,sbt}` →
  `FormattingTestGolden.{scala,sbt}`): formatting Original must yield Golden
  byte-exactly, and Golden must be a fixed point. CI checks this.

## Option surface (fork additions)

The umbrella flag is the top-level **`carrotKeep`** (Boolean): under
`newlines.source = keep` it gates the whole family of source-driven
behaviors — kept breaks after defn `=`/`yield`/infix operators/lambda and
case arrows/`(`-before-lambda/enumerator `=`/before postfix `match`, glued
heads (`{ x =>`, `{ case … =>`, `(using` clauses, `=> for`), glued closes
(`yield x }`, `then` after multiline conditions), call-site "rule B"
normalization (any clause-level break ⇒ canonical config style; inline
clauses stay inline; single-arg glue; tuples/patterns as-source), and the
overflow trigger (only the outermost clause of a >maxColumn line converts).

Named options:

| Option | Meaning |
| --- | --- |
| `align.carrotMaxShift` (Int, 0 = off) | reject aligner merges that would pad any row by more than N columns |
| `align.acrossBlankLines` | blank lines never split an alignment block |
| `align.closeParenSite` | defn dangling close paren sits at the open paren's column |
| `align.multilineMembers` | continuation lines of multiline members join the enclosing table |
| `AlignToken.onlyIfClauseBroken` | align token participates only when its clause is broken in the output |
| `AlignToken.onlyIfCaseClassParam` | ctor `:` columns only for case-class/enum-case params (annotation-only mods included) |
| `AlignToken.onlyIfOwnerStartsLine` | output line must start with the owner statement's first token |
| `spaces.preserveBefore` / `spaces.preserveAfter` | source runs of 2+ spaces before/after listed tokens are preserved verbatim (comments keyed as `"//"`) |
| `spaces.afterAccessModifier` | enforce `private [scope]` (space before the qualifier bracket) |
| `indent.preserveAssignIndent` | a source break before a defn/named-arg/enumerator `=` keeps the `=` at its source offset |
| `indent.preserveInfixIndent` | leading infix operators keep their source column |
| `indent.preserveParamClauseIndent` | subsequent defn param clauses keep their source break and column |
| `indent.preservePatAltIndent` | pattern-alternative `\|` keeps its source column |
| `newlines.inInterpolation = keep` | splice breaks kept as written, never inserted |
| `newlines.configStyle.<site>.preferBreakBeforeClose` | a source break before `)` alone implies config style |

All fork changes in core are marked with `// CARROT fork:` comments.

## Debugging recipe

Gate temporary printlns on `sys.env.contains("CARROT_DEBUG")`:
`Router.getSplits` (raw splits per token), `PolicySummary.execute`
(before/after per policy), and a winning-route walk in
`BestFirstSearch.getBestPath`. Remove before committing. Known trap: a
`killOnFail` optimal-token run follows only cost-0 splits — any cost-1 split
on the path silently kills the parent branch.
