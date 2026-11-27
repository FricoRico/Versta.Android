# Versta.Android verification

Runs belong on the x86 Paseo host, including Android SDK/NDK builds. Follow
the root and affected nested `AGENTS.md` files. Choose the relevant ABI-flavoured
Gradle compile, assemble, lint and connected-device tests; review
`.github/workflows/ci.yml` for required gates. Respect the host's Gradle memory
ceilings. Record commands and results; never claim unrun tests passed. For
cross-repository contracts or submodules, record and verify exact source commits.
