# Publishing

Publishing is handled by the manual GitHub Actions workflow in `.github/workflows/publish-mods.yml`.

## One-time setup

Add these repository secrets in GitHub:

- `MODRINTH_TOKEN`
- `CURSEFORGE_TOKEN`

Add these repository variables, or fill them as workflow inputs every time:

- `MODRINTH_PROJECT_ID`, default slug: `playereactions`
- `CURSEFORGE_PROJECT_ID`

Real tokens should never be committed. `.env.publish` and `publish.local.properties` are ignored for local notes.

## Before publishing

1. Open GitHub Actions and run `Publish Mods` on the branch you want to publish.
2. Paste the changelog text into the `changelog` input when you are ready to publish. Dry runs can leave it empty.
3. Keep `dry_run` enabled first. It builds the jars, prints the planned Modrinth/CurseForge metadata, and uploads the jars as an artifact without publishing.
4. Run it again with `dry_run` disabled when the preview is correct.

The workflow skips `paper-plugin` and only builds:

- `fabric`
- `neoforge`
- `fabric-server-relay`

## Publish metadata

Modrinth:

- Fabric: version `${mod_version}`, beta, Fabric loader, client only.
- NeoForge: version `${mod_version}`, beta, NeoForge loader, client and server.
- Fabric server relay: version `${mod_version}-server`, alpha, Fabric loader, server only.

CurseForge:

- Fabric: name `Fabric ${mod_version}`, release, Fabric loader, client only.
- NeoForge: name `Neoforge ${mod_version}`, release, NeoForge loader, client and server.
- Fabric server relay: name `Fabric Server Relay ${mod_version}`, release, Fabric loader, server only.

If a platform rejects duplicate version numbers for Fabric and NeoForge, rerun with adjusted workflow inputs or publish one of them manually from the dry-run artifacts.
