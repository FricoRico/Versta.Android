## Summary

<!-- What changes and why. Link the issue. -->

## Test plan

<!-- How this was verified. Commands run, device/emulator, flows exercised. -->

## Checklist

- [ ] `./gradlew :app:ktlintCheck` passes
- [ ] `./gradlew :app:detekt` passes (no new findings beyond `detekt-baseline.xml`)
- [ ] `./gradlew :app:lintArm64-v8aDebug` passes (no new findings beyond `lint-baseline.xml`)
- [ ] Tests updated for behavior changes (`app/src/androidTest`)
- [ ] `AGENTS.md` / `README.md` updated when conventions, build, or features change
- [ ] Still works fully offline; no new network calls or telemetry
- [ ] No secrets, keystores, or generated model JSON committed
