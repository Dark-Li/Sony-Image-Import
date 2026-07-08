# Branching Workflow

SonyEdge uses a simple Git Flow style branch policy.

## Branches

- `master`: stable, usable builds only. Code merged here should already be tested on a phone and should be suitable for real camera import use.
- `develop`: daily integration branch. Finished fixes and features land here before being promoted to `master`.
- `feature/<name>`: isolated feature or experiment branches, created from `develop`.
- `fix/<name>`: targeted bug-fix branches, created from `develop` unless the fix must patch `master` immediately.

## Default Flow

```powershell
git checkout develop
git pull origin develop
git checkout -b feature/<short-name>
```

After implementation and local verification:

```powershell
git checkout develop
git merge feature/<short-name>
git push origin develop
```

After phone testing confirms camera connection, browsing, preview, and download still work:

```powershell
git checkout master
git merge develop
git push origin master
```

## Project Rule

New app functionality and UI experiments should not be developed directly on `master`.
Use `develop` and feature branches first, then promote only verified builds to `master`.
