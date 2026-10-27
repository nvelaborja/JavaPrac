# JavaPrac

A terminal practice tool for Java *syntax* (not algorithms), inspired by chess puzzles. It deals small, timed challenges
(streams, collections, exceptions, records, and so on), grades your answer with a real Java compiler, and buzzes with a
red border when you're wrong. There's no autocomplete, on purpose.

```
 JavaPrac · First five · streams · ★☆☆                         ⏱ 00:42   tries 1

  Use a stream to return the first 5 elements of nums as a List<Integer>.

  Given
    List<Integer> nums = List.of(5, 3, 8, 1, 8, 2, 9, 4);

┌─ Your code ───────────────────────────────────────────────────────────────┐
│  1 nums.stream().limit(4).toList();                                       │
└───────────────────────────────────────────────────────────────────────────┘
  ✗ Check failed: result.equals(List.of(5, 3, 8, 1, 8))
```

## Run it

Needs a JDK 21 (not just a JRE) and [gum](https://github.com/charmbracelet/gum) for the launcher.

```
brew install openjdk@21 gum
./play.sh                              # random challenges
./play.sh --tag streams --max-difficulty 2
./play.sh --id streams.limit-first-n   # one specific challenge
./play.sh list                         # what's available
./play.sh stats                        # your history by topic
```

| Key | Action |
|-----|--------|
| `^D` | submit |
| `^K` | reveal the next hint (hints also appear automatically after every 2 failed attempts) |
| `^N` | skip and show the reference solution |
| `^Q` | quit |
| arrows, Home/End, Backspace/Delete, Tab (4 spaces) | edit |

The clock starts on your first keystroke and pauses while the compiler runs. A buzzer (terminal bell, plus a system sound on
macOS) plays on a failed attempt; pass `--mute` to silence it. History is appended to `~/.javaprac/history.jsonl`
(override the directory with `JAVAPRAC_HOME`).

## How grading works

Every submission goes through three stages:

1. **Compile** with the real `javac`. Errors are reported against the line you typed.
2. **Structure** check on the parsed syntax tree (never regex), for challenges that must use or avoid a construct, such as
   "use `.stream()`, no loops".
3. **Behavior**: the code runs in a child JVM (5 second timeout, so infinite loops and `System.exit` can't hurt the tool),
   and Java assertions about the result are evaluated. Any correct solution passes, however it's written.

## Adding challenges

Challenges are YAML in `src/main/resources/challenges/`, listed in `manifest.txt`. See `docs/design.md` for the schema.
Every challenge needs a `reference` solution and at least one hint; `./gradlew test` compiles and runs every reference (and
every `rejects` entry, which must fail) so a broken challenge can't ship.

```
./gradlew test             # unit tests + verify every bundled challenge
./play.sh verify           # the same challenge check, from the CLI
```
