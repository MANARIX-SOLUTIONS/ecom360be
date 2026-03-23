# Branch Protection Rules — Setup Guide

Configure these in GitHub → Settings → Branches → Branch protection rules.

## `main` branch

| Setting | Value |
|---------|-------|
| Require pull request before merging | ✅ |
| Required approvals | 1+ |
| Dismiss stale reviews | ✅ |
| Require review from Code Owners | ✅ |
| Require status checks to pass | ✅ |
| Required checks | `Build & Test`, `CodeQL Analysis` |
| Require branches to be up to date | ✅ |
| Require signed commits | Recommended |
| Require linear history | ✅ (squash merge) |
| Do not allow bypassing | ✅ |
| Restrict who can push | Only release automation |

## `develop` branch

| Setting | Value |
|---------|-------|
| Require pull request before merging | ✅ |
| Required approvals | 1 |
| Require status checks to pass | ✅ |
| Required checks | `Build & Test` |

## Git Flow

```
feature/* → develop → main → release tag (vX.Y.Z)
hotfix/*  → main (fast-track with approval)
```

- `develop`: integration branch, auto-deploys to **staging**
- `main`: production-ready, auto-deploys to **production** (with manual approval gate)
- `vX.Y.Z` tags: create GitHub Release + prod deploy
