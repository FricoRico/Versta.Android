# Branch protection proposal

Applies to `develop` (the active development branch) and `main`.

| Setting | Proposed value | Why |
| --- | --- | --- |
| Require a pull request before merging | on | Founder reviews every PR; no direct pushes. |
| Required approvals | 1 | Solo founder today; keep it cheap. |
| Require status checks to pass | on | The three CI jobs below. |
| Required checks | `static-analysis`, `android-lint`, `assemble` | These are the job names in `.github/workflows/ci.yml`. |
| Require branches to be up to date before merging | on | Guarantees the merged result was actually tested by CI. |
| Require conversation resolution | on | Review comments don't get lost. |
| Require signed commits | off | Not worth the setup cost at this stage. |
| Allow force pushes / deletions | off | Protect history. |
| Bypass for admins | off | The gate only works if it also gates its owner. |

Apply via: repo **Settings → Branches → Add rule** for `develop`, repeat for `main`.

Notes for the founder (one-time actions):
1. GitHub Actions is enabled for the repo (default for public repos).
2. Add the three check names above as *required* only after the first workflow
   run has completed, otherwise GitHub cannot find them in the picker.
3. Secrets for signed release builds and Play/TestFlight upload are added
   later, under the beta-automation work — debug CI needs none.
