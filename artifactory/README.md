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
