# AGENTS.md

## Git Workflow

Work directly on `main` for this repository.

Do not create `codex/*`, `feat/*`, `fix/*`, or other feature branches for
routine agent work unless the user explicitly asks for a branch.

Before changing files:

* confirm the checkout is on `main`;
* pull from `origin` with fast-forward-only updates;
* keep commits focused and push back to the same branch after tests pass.

If local Git index writes are blocked in the shared checkout, use a clean
temporary clone to commit and push to `main`, then report the temp path and
commit SHA.
