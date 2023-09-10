Note that the basic setup of Argo will take care of everything Artifactory so long as the application sync is triggered.

Default user/pass: admin/password - will immediately be asked to change as part of the first sign-in process.

The setup in this directory will bring up an empty Artifactory, the one-time migration of the Terasology Artifactory from legacy to Kubernetes will be covered in passing but as a one-time event not be considered fully within scope of solid details - hopefully we'll never need to upgrade 3-4 major versions up on a completely different infrastructure paradigm again!

## Artifactory migration 2023

This was a one-time event to carry content from our old artifactory.terasology.org forward from v4.3.3 to 7.55 in one go, in about the lowest findable amount of effort. "Proper" migration would have included one major version at a time and other headaches, but it turned out that just enough stuff worked if we did an export of system settings + individual repos we wanted to carry forward (in part as there was simply not enough space on the system to try a larger export - attempts were even made to incrementally zip the whole file store over a lengthy journey involving lots of manual downloads and uploads to to no avail)

In the end there was a `20230518.235707.zip` containing system settings and `tar.gz` files for the following repositories deemed worthwhile to transfer:

* `ext-release-local`
* `ext-snapshot-local`
* `libs-release-local`
* `libs-snapshot-local`
* `nanoware-release-local`
* `nanoware-snapshot-local`
* `terasology-release-local`
* `terasology-snapshot-local`

All zips were uploaded to `/tmp` using `kubectl cp` to the new Artifactory pod in Kubernetes, meant to back artifactory.terasology.io

The system zip was unzipped and fed into a system-level import with default settings (including content and metadata, none of which was exported anyway)

Each repository was extracted which put files into `/tmp/repositories` which was then fed into a repository import with the name of the repository picked manually (again not adjusting any checkboxes)
