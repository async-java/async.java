# Releasing `async-java`

This document is for maintainers cutting a new release. End users just need
the snippet in [README](readme.md#installation).

## Where the artifact lives

| Coordinate                                         | What it is                                | Notes                                       |
| -------------------------------------------------- | ----------------------------------------- | ------------------------------------------- |
| `io.github.oresoftware:async-java:<version>`        | This release line, on **Maven Central**.  | Current `<version>` lives in `pom.xml`.     |
| `io.github.oresoftware:async-java:<version>`        | Same artifact on **GitHub Packages**.     | Published by the release workflow with `GITHUB_TOKEN`. |
| `com.oresoftware:async.0.1:0.1.1012`               | The legacy artifact published in 2019.    | Frozen — kept on Central for compatibility. |
| `com.github.async-java:async.java:<git-tag>`       | Same source, served by **JitPack**.       | Built on-demand from any git ref.           |

JitPack is automatic — every git tag is buildable as soon as it's pushed
(see [`jitpack.yml`](jitpack.yml)). GitHub Packages is automatic from the
release workflow. The only manual credential setup is for the Maven Central /
Sonatype path.

## One-time setup

You need three things to publish to Maven Central:

### 1. A Sonatype Central Portal account + the `io.github.oresoftware` namespace

* Sign up at <https://central.sonatype.com>.
* Verify the `io.github.oresoftware` namespace. The Portal will tell you to
  either:
  * confirm the automatically provisioned GitHub namespace for
    [ORESoftware](https://github.com/ORESoftware), or
  * add a DNS TXT record.
  If the Portal shows a verification key instead, create a temporary public
  GitHub repository under [ORESoftware](https://github.com/ORESoftware) whose
  name is exactly that key, then click **Verify** in the Portal.
* Once verified, generate a **User Token** at
  <https://central.sonatype.com/account>. You'll get a *username* string and a
  *password* string. These are *not* your Portal login.

### 2. A GPG key registered with the Portal

* Generate (or reuse) a GPG key:

  ```bash
  gpg --full-generate-key            # 4096-bit RSA, no expiry, real-name = your portal account name
  gpg --list-secret-keys --keyid-format LONG
  ```

* Upload the **public** key to one of the keyservers the Portal trusts:

  ```bash
  gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
  gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
  ```

* Export the **private** key as ASCII-armored text (this goes into a CI secret,
  not into git):

  ```bash
  scripts/export-release-gpg-key.sh <KEY_ID>
  ```

### 3. Repo secrets for the release workflow

In <https://github.com/async-java/async.java/settings/secrets/actions> add:

| Name                       | Value                                                        |
| -------------------------- | ------------------------------------------------------------ |
| `CENTRAL_USERNAME`         | User Token username from the Portal.                         |
| `CENTRAL_PASSWORD`         | User Token password from the Portal.                         |
| `MAVEN_GPG_PRIVATE_KEY`    | Full contents of `/tmp/maven-gpg-private-key.asc`.           |
| `MAVEN_GPG_PASSPHRASE`     | Passphrase that unlocks the GPG key.                         |

Or set them from a checked-out repo with:

```bash
gh auth login -h github.com -p ssh --skip-ssh-key -w -s repo,workflow

export CENTRAL_USERNAME='...'
export CENTRAL_PASSWORD='...'
export MAVEN_GPG_PRIVATE_KEY="$(cat /tmp/maven-gpg-private-key.asc)"
export MAVEN_GPG_PASSPHRASE='...'

scripts/set-release-secrets.sh
scripts/check-release-readiness.sh --pre-tag
```

Use `check-release-readiness.sh --pre-tag` before tagging to catch missing
GitHub auth, missing repo secrets, and missing local GPG setup. Use
`check-release-readiness.sh` after the release workflow runs; the post-tag
check fails until the remote tag exists and Maven Central metadata shows the
release.

## Cutting a release (automated path)

```bash
# 1. Bump the version (drop the -SNAPSHOT suffix).
#    Edit pom.xml: <version>0.2.11-SNAPSHOT</version> -> <version>0.2.11</version>
git commit -am "Release 0.2.11"

# 2. Confirm release prerequisites before tagging.
scripts/check-release-readiness.sh --pre-tag

# 3. Tag the commit. The release workflow only fires on `v*` tags.
git tag v0.2.11
git push origin main --tags

# 4. (Optional) Open development on the next version.
#    Edit pom.xml: <version>0.2.11</version> -> <version>0.2.12-SNAPSHOT</version>
git commit -am "Begin 0.2.12 development"
git push
```

The `release` workflow ([.github/workflows/release.yml](.github/workflows/release.yml))
then:

1. Verifies the tag matches `pom.xml`'s `<version>`.
2. Runs `mvn test`.
3. Imports the GPG key into the runner's agent.
4. Runs `mvn -P publish-artifacts,release deploy`, which:
   * Builds the jar.
   * Builds `-sources.jar` and `-javadoc.jar` (Maven Central requires both).
   * GPG-signs every artifact (jar / sources / javadoc / pom).
   * Uploads to the Central Portal's staging API.
   * Auto-publishes the staged release (because `<autoPublish>true</autoPublish>`).
5. Verifies the public Maven Central URLs for the POM, jar, sources jar, and
   Javadoc jar.
6. Reconfigures Maven credentials for GitHub Packages (GitHub's
   `setup-java` action rewrites `~/.m2/settings.xml` each time it runs).
7. Runs `mvn -P publish-artifacts,github-packages deploy`, which publishes the same version to
   `https://maven.pkg.github.com/async-java/async.java` using `GITHUB_TOKEN`.
8. Creates a GitHub Release with the jars attached.

The new version shows up on Maven Central within ~30 minutes of the workflow
finishing.

## Cutting a release (manual / local path)

For emergencies when CI is down. You need GPG and Sonatype credentials on the
local machine.

Add this to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
      <password>YOUR_CENTRAL_TOKEN_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>local-gpg</id>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.keyname>YOUR_KEY_ID</gpg.keyname>
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>local-gpg</activeProfile>
  </activeProfiles>
</settings>
```

Then:

```bash
# Verify everything builds and signs locally first.
mvn -P publish-artifacts,release -DskipTests verify

# Deploy.
mvn -P publish-artifacts,release deploy
```

## Publishing to GitHub Packages locally

CI uses the built-in `GITHUB_TOKEN`. For a local deploy, create a classic
GitHub personal access token with `write:packages`, then add a `github` server
to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_CLASSIC_PAT_WITH_WRITE_PACKAGES</password>
    </server>
  </servers>
</settings>
```

Then:

```bash
mvn -P publish-artifacts,github-packages -DskipTests -Dgpg.skip=true deploy
```

## Snapshot releases

Snapshots (`x.y.z-SNAPSHOT`) can be pushed to the Central Portal's snapshot
endpoint the same way; the `central-publishing-maven-plugin` figures it out
from the version suffix. Snapshots can be consumed via:

```xml
<repositories>
  <repository>
    <id>central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <snapshots><enabled>true</enabled></snapshots>
    <releases><enabled>false</enabled></releases>
  </repository>
</repositories>
```

## Yanking a bad release

You can't delete a Central Portal release once it's published — Sonatype
treats the artifact graph as immutable. The standard workaround is:

1. Cut a new patch release (`0.2.1`) that fixes the bug.
2. Mark the broken release as deprecated in a GitHub Release note.
3. If the broken release is dangerous (security CVE), open a separate
   advisory at <https://github.com/async-java/async.java/security/advisories>.
