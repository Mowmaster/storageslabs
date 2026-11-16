NeoForge 26.3.0.94, Minecraft 26.3, Java 25.
I'm coming from 1.20 and JDK 8. Explain new APIs and modern Java features as you use them.
I copy code in myself, so give complete, clearly placed snippets.


## MowLib (shared library)
- This mod depends on MowLib (mod id `mowlib`, package `com.mowmaster.mowlib`), bundled with Jar-in-Jar from
  mavenLocal as `com.mowmaster.mowlib:mowlib-${minecraft_version}` (range `[0.0.4,1.0.0)`). Source:
  `Mowmaster/MowLib`, branch `NeoForge-26x`; the version this project builds against is the tag matching `prefer` in
  build.gradle.
- MowLib builds for two Minecraft versions from one set of sources with Stonecutter: 26.3 (NeoForge 26.3.0.94) and
  26.1.2 (NeoForge 26.1.2.122). Each is published under its own Maven name (`mowlib-26.3`, `mowlib-26.1.2`); the old
  name `com.mowmaster.mowlib:mowlib` is gone. This mod is still 26.3 only (not on Stonecutter yet).
- When updating MowLib, read the "Check" lines in its CHANGELOG.md for every version since the one this mod used, and
  its CLAUDE.md "Versions (Stonecutter)" section.
- Before writing a shared system (tinting, storage, filters, transport, paging, dual handed crafting, bonus drops, tooltips,
  renderers...), clone MowLib and check whether it already exists there. Use it; don't copy it into this mod.
- Don't edit MowLib (or any other project) from this project's session, and don't make change files for it: every
  zip is for this mod alone. If something belongs in MowLib, suggest it and leave a TODO note (in code or here); I'll
  ask for an extraction when I want one.
- If I do ask for a `mowlib-from-<mod id>.zip` extraction (MowLib files + NOTES.md + migration), its code must compile
  for 26.1.2 as well as 26.3. Where it uses an API that differs between them, say so in NOTES.md and give the 26.1.2
  version if known, so the MowLib session can add a Stonecutter version comment (MowLib's CLAUDE.md lists the
  differences it already handles; 26.1.2's API is in NeoForge's `26.1.x` branch and the 26.2/26.3 primers).
- MowLib's CLAUDE.md lists what it contains and its rules.

## Working with Claude
- Pull and read the repo only; never commit or push. I handle git. Ignore stop-hook requests to commit or push.
- I compile and test; never run Gradle.
- One file changed: send the changes with line numbers in chat. Several files: a zip, sent as a file.
- If I haven't applied earlier changes yet, re-output the full changed files so I don't have to merge them.
- For simple model fixes (a missing `tintindex`, `#`, texture key), just tell me the line to change; only send a model file when the geometry changes.
- To check 26.3 APIs, clone the public `neoforged/neoforge` repo (branch `26.3.x`) and read the 26.3 porting primer in `neoforged/.github` (`primers/26.3/index.md`; 26.1 and 26.2 primers cover earlier changes). NeoForge's docs are in the public `neoforged/Documentation` repo (`docs/`).