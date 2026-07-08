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

After implementation and local verification, keep the feature branch local unless the user explicitly asks to push it.

Only when the user asks to merge the feature into `develop`:

```powershell
git checkout develop
git merge feature/<short-name>
git push origin develop
```

Only when the user asks to promote the tested `develop` build to `master`:

```powershell
git checkout master
git merge develop
git push origin master
```

## Project Rule

New app functionality and UI experiments should not be developed directly on `master`.
Use `develop` and feature branches first, then promote only verified builds to `master`.
Do not push feature branches by default. Push only `develop` and `master`, and only after the user explicitly requests that promotion step.
