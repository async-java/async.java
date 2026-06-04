# AGENTS.md

## Git Workflow

Work directly on `master` for this repository. If the default branch is ever
renamed, work directly on `main` instead.

Do not create `codex/*`, `feat/*`, `fix/*`, or other feature branches for
routine agent work unless the user explicitly asks for a branch.

Before changing files:

* confirm the checkout is on `master` or `main`;
* pull from `origin` with fast-forward-only updates;
* keep commits focused and push back to the same branch after tests pass.

If local Git index writes are blocked in the shared checkout, use a clean
temporary clone to commit and push to `master` or `main`, then report the temp
path and commit SHA.
