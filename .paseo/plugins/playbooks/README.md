# Kaneo team Playbooks

These hidden phases are **not an autonomous workflow**. They have no trigger:
the planned coordinator must verify its own identity, preserve durable state,
and start each phase explicitly. Until that integration is implemented and
tested, do not start these phases for unattended work.
Every phase is managerless and requires a unique coordinator `dispatch_id` and
the approved origin ref and full commit for the pre-execution guard.
This marker is not an exactly-once guarantee or permission to retry a missing
run. Isolated Playbooks can fall back to workspace definitions; checking
provenance after a run starts is too late to prevent its first action.

- `team-plan` gathers three independent plans, two critiques and two fresh
  council assessments. It does not start implementation. The coordinator
  must treat an open question, `decision_required`, either council's `human`
  verdict, a failed/malformed result or an unknown state as a stop and take
  the matter to the human in Kaneo.
- `team-build` starts a fresh isolated worktree and opens an initial PR. It
  is not a correction or re-review mechanism; do not invoke it to update an
  existing PR. Playbooks chooses the branch name, which may not contain the
  Kaneo ticket ID: the coordinator must verify the PR, branch and head and
  explicitly link the PR to the task rather than assuming automatic linking.
- `team-correct` updates one already verified PR from its exact current head.
  It never creates or merges a PR and never force-pushes; the coordinator must
  verify the resulting PR head and enforce the review-round limit.
- `team-review` asks Terra and Opus to comment independently on one supplied
  head, then asks two fresh council agents to check the direction. The
  coordinator must honor either council's `human` verdict, verify the reported
  sessions, comments, model identities and current head, and enforce the
  three-round limit.

The per-repository `repository.md` records test and host guidance. The shared
Playbook and role files are intended to be kept identical across Versta repos.
Prompts and Playbooks are not a security boundary: branch restrictions must
prevent bot merges, and the coordinator must enforce the decision and evidence
rules before unattended pickup.
