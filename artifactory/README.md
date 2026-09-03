Note that the basic setup of Argo will take care of everything Artifactory so long as the application sync is triggered.

Default user/pass: admin/password - will immediately be asked to change as part of the first sign-in process.

The setup in this directory will bring up an empty Artifactory, the one-time migration of the Terasology Artifactory from legacy to Kubernetes will be covered in passing but as a one-time event not be considered fully within scope of solid details - hopefully we'll never need to upgrade 3-4 major versions up on a completely different infrastructure paradigm again!

For Helm chart details see https://github.com/jfrog/charts/blob/master/stable/artifactory/values.yaml or https://artifacthub.io/packages/helm/jfrog/artifactory and keep in mind there are several flavors of the Artifactory chart which themselves are sub-charts that we then subchart and deploy via Argo ...

See also https://jfrog.com/help/r/jfrog-installation-setup-documentation/auto-generated-passwords-internal-postgresql which was a bit of an initial oops to miss. The following command will retrieve the password: `kubectl get secret terartifactory-artifactory-postgresql -n artifactory -o jsonpath="{.data.postgresql-password}" | base64 --decode` - however if the password isn't hardwired on initial install it may regenerate and go out of sync vs the DB pod. Since Postgresql is entirely internal to the cluster for the moment a hardwired plaintext password is included in the values file. The Artifactory chart does not yet feel very cloud native, as there appears to be no way to indicate getting secrets from existing k8s secrets and it is just overall awkward..

## Varying the target Artifactory or individual registries

TODO: Probably move this to the root readme, which needs other tidying itself

Our main line of Gradle scripting tries to add some flexibility around feature development and infrastructure changes in the following ways:

* You can vary the Artifactory URL to test a different instance as part of doing upgrades or migrations to new infrastructure.
* You can vary the _registry_ within Artifactory you publish new artifacts to, when we're making potentially disruptive changes to dependencies
* You can also vary the GitHub _organization_ some of our utility targets, although that's generally more about the `groovyw` utility scripting (fetching from a test org)

The `gradle.properties` that ships in the `templates` directory in the Terasology workspace is the main way to do this. It used to copy into the root of the workspace on setup, which as of this writing appears to not happen, so you'd need to copy it manually. See the file comments for further details.

Alternatively if you want to redirect dependency resolution via Gradle at a system level locally rather than per project try out tweaking this snippet, which should be placed in %USERPROFILE%\.gradle\init.gradle (assuming on Windows):

```groovy
allprojects {
    repositories {
        maven {
            // It would be nice to just use "<SERVER_NAME>" like usual but Java's won't use SNI with domains lacking a dot.
            // Source: https://gamlor.info/posts-output/2019-09-05-java-client-sni/en/
            url "https://<MY_TERASOLOGY_MIRROR>/reposilite/terasology-mirror"
            credentials {
                username "<USERNAME>"
                password "<PASSWORD>"
            }
            content {
                  // This repository only contains artifacts with group "org.terasology" or "org.destinationsol"
                  includeGroup "org.terasology"
                  includeModule "brianbb", "jpastebin"
                  includeGroupByRegex "org\\.terasology\\..*"
                  includeGroup "org.destinationsol"
                  includeGroupByRegex "org\\.destinationsol\\..*"
            }
        }
    }
}
```

## Artifactory migration 2023

This was a one-time event to carry content from our old artifactory.terasology.org forward from v4.3.3 to 7.55 in one go, in about the lowest findable amount of effort. "Proper" migration would have included one major version at a time and other headaches, but it turned out that just enough stuff worked if we did an export of system settings + individual repos we wanted to carry forward (in part as there was simply not enough space on the system to try a larger export - attempts were even made to incrementally zip the whole file store over a lengthy journey involving lots of manual downloads and uploads to to no avail)

In the end there was a `20230518.235707.zip` containing system settings and `tar.gz` files for the following named repositories deemed worthwhile to transfer:

* `ext-release-local` x - i
* `ext-snapshot-local` x - i
* `libs-release-local` x - i
* `libs-snapshot-local` x - i
* `nanoware-release-local`x - i
* `nanoware-snapshot-local` x - i
* `terasology-release-local`x - i
* `terasology-snapshot-local`x - i

All zips were uploaded to `/tmp` using `kubectl cp` to the new Artifactory pod in Kubernetes, meant to back artifactory.terasology.io

The system zip was unzipped and fed into a system-level import with default settings (including content and metadata, none of which was exported anyway) which restores old user accounts as well

Each repository then was uploaded and extracted one by one which put files into `/tmp/repositories/<name>` which was then fed into a repository import with `<name>` of the repository picked manually (again not adjusting any checkboxes)

## Artifactory migration 2024

Oh hey here we are again already anyway. But luckily this time the updated version target is just something like 7.98.9 which is the same major version.

### Known issues

An apparent bug causes a regular error prompt about federation. It doesn't appear to cause any trouble and will be fixed in an upcoming release. See https://stackoverflow.com/questions/79192477/artifactory-cpp-ce-unsupported-operation-federated-after-upgrade-to-latest

## Artifactory migration 2026 — 7.98.9 to 7.161.20, and the database moves out

### Why this is not a version bump

Time to move off 7.98.9 and onto a currently-supported release. Check
[JFrog's security advisories](https://jfrog.com/help/r/jfrog-security-advisories)
for the specifics that motivated the timing.

The catch: **there is no newer version that is only a tag change.** Every
currently-supported branch — 7.111.x, 7.117.x, 7.125.x, 7.133.x, 7.146.x,
7.161.x — ships a chart that differs from 107.98.9 in two breaking ways.

1. **The bundled PostgreSQL jumps a major version.** This install runs 15.6. The
   lowest patched chart bundles 16.6; the latest bundles 17.10, and the image
   itself moved from `bitnami/postgresql` to `echohq/postgres` in Bitnami's
   registry exodus. The container refuses to start on a PGDATA directory
   initialised by an older major, so a dump/restore is unavoidable either way.
2. **The values schema changed.** `postgresqlPassword` / `postgresqlUsername` /
   `postgresqlDatabase` became `auth.*`, and `persistence` / `resources` moved
   under `primary`. Every override in the old `values.yaml` sat at a path the new
   chart does not read, so they would be **silently ignored** — including the
   database password. That failure looks like a healthy sync followed by an
   Artifactory that cannot reach its own database.

Since a database migration is forced regardless, the database moves to Mimir's
shared PostgreSQL cluster rather than into a newer bundled one. That cluster is
also PostgreSQL 15, so this becomes a **same-major dump/restore** with no
version upgrade at all — and afterwards the database is covered by pgBackRest
(repo1 local, repo2 in GCS) instead of a crash-consistent Velero disk snapshot.

Artifactory 7.161 supports PostgreSQL 13–17, so 15 stays supported.

### Also re-check the probe overrides when bumping

The `access` and `router` `startupProbe.config` blocks in `values.yaml` are
copies of upstream defaults with only `failureThreshold` raised to 90. Upstream
has since **changed the router probe from an `exec` curl to an `httpGet`**, so
carrying the old block forward would pin a stale probe shape onto a much newer
router image. Re-copy both blocks from the new chart and re-apply only the
`failureThreshold` change. The `access` block is otherwise unchanged.

### The database wiring is all-or-nothing — verify the render, don't trust it

This one caused a real outage during the cutover, so it is worth stating flatly.

The chart writes `db-url` into its unified secret **only when `database.secrets`
is entirely empty**. Supplying `url` as a plain value while sourcing `user` and
`password` from a Secret — the obvious arrangement, since the URL is not
sensitive and the password must not be in Git — silently skips that block, while
the StatefulSet references `db-url` regardless. Result:

```
Error: couldn't find key db-url in Secret ...-unified-secret
```

Every container in the pod fails to start, with `CreateContainerConfigError`.
So: url, user **and** password all via `database.secrets`, or all three as plain
values. The URL therefore lives in its own Secret, templated in
`templates/db-url-secret.yaml`.

**`helm template` will not catch this.** It renders cleanly — a dangling
`secretKeyRef` is perfectly valid YAML. The check that catches it is comparing
every rendered `secretKeyRef` name/key pair against the keys the rendered (and
pre-existing) Secrets actually provide:

```bash
helm template <release> . --output-dir /tmp/r
grep -rh -A2 secretKeyRef /tmp/r --include='*statefulset*' --include='*deployment*'
```

Then account for every pair. A clean render is not evidence.

### The shared cluster's pgBouncer requires TLS — and psql hides that

The JDBC URL must carry `?sslmode=require`. That pgBouncer runs with
`client_tls_sslmode = require`, so a plaintext connection is refused outright:

```
FATAL: SSL required (SQLSTATE 08P01)
```

Every JDBC-based service — metadata, access, artifactory, topology, jfconfig —
then retries 120 times and exits, and the pod restart-loops.

The trap is in how this gets tested. A `psql` login through the very same
endpoint **succeeds without any sslmode set**, because libpq defaults to
`sslmode=prefer` and quietly negotiates TLS. The PostgreSQL JDBC driver does
not. So a green psql check proves the credentials, the route and the database —
and proves nothing whatsoever about how the application connects. During this
migration exactly that check was used as evidence, and it passed while the app
could not connect at all.

**Verify with the client the application actually uses**, or at minimum force
`sslmode=disable` in a psql check to confirm what the server demands rather than
letting the client paper over it.

`require` rather than `verify-full`: the operator issues its own CA, so full
verification would mean distributing that CA to every consumer for no real gain
on an in-cluster hop. No client certificate is needed — pgBouncer requires TLS,
not client auth.

### Two more things the new chart changes

**Master and join keys are now mandatory.** 107.98.9 generated them itself and
kept the master key on the PVC. The new chart refuses to template without both,
and — this is the dangerous part — **the master key must be the existing one**.
It is what Artifactory's stored configuration is encrypted with: repository
credentials, proxy passwords, signing keys. Given a fresh random key it does not
fail loudly; it starts and cannot decrypt its own config. Step 3a below pulls the
real key off the running instance.

**Frontend and JFBus are now independent Deployments**, not containers in the
StatefulSet. Two new pods, and upstream ships their main containers with
`resources: {}` — BestEffort. This repo already learned that BestEffort plus a
health probe is a restart loop under contention, and the cluster is currently
tight on CPU requests. Left as upstream ships it rather than guessing numbers;
size them from observation once they have run for a day, the same way the
`artifactory` container's requests were derived.

### Cutover runbook

Deliberately split across two merges. The `DataService` is additive and safe to
land at any time; the chart bump is what actually re-points Artifactory, so it
must not be synced until the data is already in place.

**This app is on the legacy ArgoCD in the `argocd` namespace, and it does NOT
auto-sync** — its `syncPolicy` has no `automated` block, so a merge only makes it
`OutOfSync` and waits. The cutover is therefore the *manual sync*, not the merge.
That is a safety margin, not a licence to be casual: sync at a moment you have
chosen, with the data already restored.

A **selective sync** is worth preferring. `Secret/terartifactory-artifactory-postgresql`
carries long-standing drift — `postgresql-password` is hardwired in values but
`postgresql-postgres-password` is auto-generated and cycles — so a blanket sync
rewrites a credential that has nothing to do with this migration. It is annotated
`argocd.argoproj.io/sync-options: Prune=false` so that a later prune cannot take
it: that Secret holds the only copy of the old database's superuser password, and
losing it would leave a rollback you cannot administer.

1. **Merge the `DataService`** (this PR). It provisions an empty `artifactory`
   database in the shared cluster plus a Secret named
   `artifactory-db-dataservice` carrying `host`, `port`, `database`, `username`,
   `password` and `uri`. Nothing about the running Artifactory changes.

   ```bash
   kubectl get dataservice artifactory-db -n artifactory
   # PHASE must be Ready before going further.
   ```

2. **Quiesce Artifactory.** The dump has to be of a database nobody is writing to;
   a hot dump plus a later flip means every write in between is lost silently.

   ```bash
   kubectl scale statefulset terartifactory-artifactory -n artifactory --replicas=0
   ```

3. **Dump and restore.** `--no-owner` matters: the dump's objects are owned by the
   old `artifactory` role, which does not exist in the shared cluster. Without it
   the restore fails partway and leaves a half-populated database that still
   looks present.

   ```bash
   kubectl exec -n artifactory terartifactory-artifactory-postgresql-0 -- \
     pg_dump -U artifactory -d artifactory --no-owner --no-acl -Fc > /tmp/artifactory.dump

   # Credentials for the target come from the vended Secret, not from anywhere
   # in this repo — that is the point of moving.
   kubectl get secret artifactory-db-dataservice -n artifactory \
     -o jsonpath='{.data.password}' | base64 --decode
   ```

   Restore it through the shared cluster's pgBouncer
   (`mimir-postgres-pgbouncer.mimir.svc.cluster.local:5432`, database
   `artifactory`) as the vended user, then confirm the row counts survived rather
   than trusting a clean exit code.

3a. **Capture the existing master key and create the keys Secret.** Do this
   while the pod is still scaled down but *before* merging the bump — the new
   chart will not render without it, and a wrong key is worse than a missing one.

   ⚠️ **Do not copy `join.key` off the PVC.** It begins with `JE`, JFrog's
   *encrypted-value* prefix — it is the join key encrypted with the master key,
   not the join key. Putting that blob in the Secret injects ciphertext where
   plaintext is expected, and it fails at service registration, long after the
   sync looks successful. Take the join key from the UI (Administration →
   Security → General → Connection Details), or generate a fresh one: with a
   single node, OSS (no Xray), and `event`/`integration` disabled, nothing is
   joined to this instance and all the platform services pick up the new value
   together. The **master key** has no such freedom — it must be the existing one.

   Copy rather than echo, so no key material lands in a shell history:

   ```bash
   kubectl cp -c artifactory \
     artifactory/terartifactory-artifactory-0:/var/opt/jfrog/artifactory/etc/security/master.key \
     ./master-key

   # Fresh join key. `openssl rand -hex` appends a newline, which --from-file
   # would store verbatim inside the key; dd trims it back to exactly 64 bytes.
   openssl rand -hex 32 -out ./join-key.raw
   dd if=./join-key.raw of=./join-key bs=1 count=64 status=none

   kubectl create secret generic artifactory-mandatory-keys -n artifactory \
     --from-file=master-key=./master-key \
     --from-file=join-key=./join-key

   # Both entries must read as exactly 64 bytes; anything larger means a stray newline.
   kubectl describe secret artifactory-mandatory-keys -n artifactory
   rm -f ./master-key ./join-key ./join-key.raw
   ```

   The Secret is created by hand rather than templated so neither key is ever
   committed to this repo.

4. **Merge the chart bump.** Sets `postgresql.enabled: false`, points
   `database.url` at the shared cluster and sources credentials from the vended
   Secret. ArgoCD syncs and Artifactory comes back on the new database.

5. **Verify, then reclaim.** Once Artifactory is healthy and serving artifacts,
   the old 20Gi `data-terartifactory-artifactory-postgresql-0` PVC can go. Leave
   it until you are sure — it is the only rollback that does not involve a
   restore.

### Rollback

Before step 2, revert the merge. After step 3, the old bundled database is still
intact and untouched — reverting the chart bump points Artifactory back at it,
losing only writes made since the dump. That is why step 5 waits.

