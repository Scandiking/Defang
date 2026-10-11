# Defang

## Weblate PRs (branch `weblate`)

- **Never squash-merge** a PR from the `weblate` branch. Use `gh pr merge <n> --merge` (or `--rebase`).
  Squashing rewrites Weblate's commits, so Weblate's next rebase conflicts ("Could not merge repository"),
  locks the components, and blocks Libre hosting approval.
- Other PRs may be squash-merged as usual.
- If it happens anyway: Weblate → Operations → Repository maintenance → Danger zone →
  **Discard local changes** (only when "pending units" is 0, or after committing/pushing them).
  Do not use "Update with merge/rebase" — they replay the same conflict.
