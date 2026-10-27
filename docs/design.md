# JavaPrac design

## Goal

Drill Java *syntax* the way chess puzzles drill tactics: short, timed, randomly dealt, instantly judged. Not algorithms. The
starting content is drawn from a personal Java 21 coding reference (collections, streams, strings, numbers, records and
enums, exceptions, modern Java).

## Decisions

| Question | Decision | Why |
|----------|----------|-----|
| Surface | Terminal UI | No IDE aids is the point. An IDE-plugin mode is deferred. |
| Grading | Compile with `javac`, check the syntax tree, run in a child JVM | Many solutions are correct and none look alike. |
| Structure checks | `com.sun.source` AST, never regex | `for` in a comment or string must not count as a loop. |
| Sandbox | Child JVM, 5 s timeout, `-Xmx256m` | Infinite loops and `System.exit` in a submission must not take down the TUI. |
| Snippet shape | `expression`, `statements`, `declarations` | Covers "return this value", "write these statements" and "declare this type". |
| Build | Gradle, Java 21, JLine 3 for the terminal | |
| Selection | Shuffled bag, no repeats until all seen | Weighted/spaced repetition is deferred. The history log has the data for it. |

## Architecture

```
model/   Challenge, ChallengeLoader        YAML -> immutable records
grade/   SourceBuilder                     splices user code into a generated Attempt.java
         Grader                            compile -> structure -> run, returns a Verdict
         StructureChecker                  AST feature extraction (call:x, loop, lambda, class:record, ...)
         Verifier                          reference passes, `rejects` fail
tui/     Editor, KeyDecoder                pure and unit tested
         Screen                            pure renderer: state -> ANSI string + cursor position
         Tui                               the event loop (the only class that touches the terminal)
store/   History                           append-only JSONL
```

`Screen`, `Editor` and `KeyDecoder` have no terminal dependency, which is what makes them testable.

## Challenge schema

```yaml
- id: streams.limit-first-n          # topic.name, unique
  title: First five
  tags: [streams]
  difficulty: 1                      # 1 to 3
  kind: expression                   # expression | statements | declarations
  prompt: Use a stream to return the first 5 elements of nums as a List<Integer>.
  given: |                           # shown to the user, placed before their code
    List<Integer> nums = List.of(5, 3, 8, 1, 8, 2, 9, 4);
  starter: ""                        # optional initial editor text
  asserts:                           # Java boolean expressions; `result` is the user's expression value
    - result.equals(List.of(5, 3, 8, 1, 8))
  # or several cases, each optionally with its own hidden `given`:
  # cases:
  #   - given: ...
  #     asserts: [...]
  structure:
    require: [call:stream, call:limit]   # a|b means either
    forbid: [loop]
  hints:                             # shown one per 2 failed attempts, or on ^K
    - "Short-circuiting operations stop a stream early."
    - "Stream has a method named limit(n)."
  reference: nums.stream().limit(5).toList();
  rejects:                           # optional: known-wrong answers that must fail
    - nums.subList(0, 4)
```

How `kind` is spliced:

- `expression`: `var result = ( <user code> );` after `given`.
- `statements`: `<user code>` after `given`, so the asserts can see variables the user declared.
- `declarations`: user code becomes top-level types next to the harness. A leading `public` is stripped.

Harness helpers available inside asserts: `out()` (captured stdout) and `throwsA(Class, Runnable)`.

Structure features: `call:NAME`, `new:TYPE`, `for`, `foreach`, `while`, `do-while`, `loop`, `lambda`, `method-ref`, `switch`,
`switch-expr`, `try`, `try-with-resources`, `catch`, `multi-catch`, `finally`, `throw`, `throws`, `if`, `ternary`, `instanceof`,
`cast`, `var`, `class:class|record|enum|interface`, `modifier:NAME`.

## Feedback loop

- Fail: terminal bell, system sound on macOS, red border for 1.2 s, and the compile error or failed assertion in red.
- After every 2 failures one more hint is revealed. `^K` reveals one on demand.
- The clock starts on the first real keystroke and pauses while grading.
- Pass or skip shows the reference solution next to the player's code.

## Deferred

- IDE plugin / scratch-file mode.
- Spaced repetition: weight topics by recent failure rate and slowness (data already in `history.jsonl`).
- `member` snippet kind (methods inside an existing class).
- A user-supplied challenge directory (`~/.javaprac/challenges`).
- Wide-character (emoji) width handling in the renderer; hint rows are clipped by cell count, not display width.
