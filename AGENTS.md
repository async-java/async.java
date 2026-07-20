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

## Syncing with the remote

"Sync with the remote" (or just "sync") is **bidirectional and always contacts
the remote** — it pulls *and* pushes. It is never push-only, and a clean local
working tree does **not** by itself mean "synced": a sync is not finished until
local and the remote have exchanged commits in both directions.

The steps for a sync:

1. `git fetch --all --prune` — see what the remote has.
2. `git pull` (which merges) — or `git merge` the upstream tracking branch —
   to integrate the remote's commits into your local branch **first**.
3. `git add` / `git commit` any local work.
4. `git push` — publish your commits.

Always integrate with **`git merge`** (and plain `git pull`, which merges).
**Do not `git rebase`** to sync — rebasing rewrites history and breaks shared
branches; keep the merge history instead.
