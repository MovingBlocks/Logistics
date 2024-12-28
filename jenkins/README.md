# Jenkins

This directory controls and documents our CI/CD setup within Jenkins. Efforts have been made to automate as much as possible and document all of the things to the best of our ability.

See [[TECHNICAL.md]] for deeper details, this readme will attempt to remain high level focusing on the steps to get started and common maintenance.

Argo CD largely runs the deployment of Jenkins and other things - see the root readme and the one in the argocd dir. See also the secrets section here first just in case.

Deployment of Jenkins and about everything else is done using Helm. See https://github.com/jenkinsci/helm-charts/blob/main/charts/jenkins/README.md for more details on the Jenkins setup and available variables.


## GitHub OAuth

The "live" https://jenkins.terasology.io instance is paired with https://github.com/organizations/MovingBlocks/settings/applications/132034

To experiment with a new/temporary one consider creating a new OAuth application in a place like https://github.com/organizations/Terasology/settings/applications and write down the client id and secret.

* Homepage URL should be something like https://jenkins.terasology.io
* Make sure the Authorization callback URL is something like https://jenkins.terasology.io/securityRealm/finishLogin
* Description can be anything, like "Jenkins for The Terasology Foundation"

For the sake of local development ease you can use `jenkins-secret-do-not-recomment.yaml` to prepare the secrets for Kubernetes, just enter the right values as instructed by comments and run `kubectl apply -f jenkins-secret-do-not-recommit.yaml -n jenkins` - you may need to create the namespace first. HOWEVER you do of course not want to commit the actual values, and we should aim to use proper external secrets manager like Vault or some Argo-flavored thing (which does also have a plugin for Vault)

## GitHub API via GitHub App

Jenkins can have increased access to the GitHub API by authenticating as a _GitHub App._ - and the setup can be reused between Jenkins builds, just may have to generate a new secret on the existing app.

Reference: [GitHub App Authentication Guide][github-app]
Original issue: [InfraPlayground#19](https://github.com/MovingBlocks/InfraPlayground/issues/19)

We have a GitHub app [terasology-jenkins-io](https://github.com/apps/terasology-jenkins-io) which can be navigated to via
* MovingBlocks Organization Settings
* Developer Settings
* [GitHub Apps](https://github.com/organizations/MovingBlocks/settings/apps) (note this is _different_ than OAuth Apps)

That app is owned by [MovingBlocks](https://github.com/MovingBlocks), and is additionally installed to the [Terasology](https://github.com/Terasology) and [Nanoware](https://github.com/Nanoware) organizations.

Follow the [guide][github-app] to create Credentials of type **GitHub App**.

+ Manage Jenkins ⇨ Manage Credentials ⇨ store=_Jenkins_ domain=_(global)_ ⇨ Add "GitHub App" Credentials
+ The **App Id** is `100575` (visible on the [app's settings page](https://github.com/organizations/MovingBlocks/settings/apps/terasology-jenkins-io)).
+ Do open the *Advanced* options when setting up these credentials and fill in the **Owner** field.
  - We need to create one Credentials entry for each GitHub Organization we operate on. These may use the same App ID and secret, but set different Owners.
    * [ ] owner=`MovingBlocks`, id=`github-app-terasology-jenkins-io`, description `GitHub App auth for MovingBlocks org`
    * [ ] owner=`Terasology`, id=`gh-app-terasology`, description `GitHub App auth for Terasology org`
    * [ ] owner=`Nanoware`, id=`gh-app-nanoware`, description `GitHub App auth for Nanoware org`
  - (This is true of August 2021. Check [JENKINS-62220](https://issues.jenkins.io/browse/JENKINS-62220) to see if they've fixed things to require less duplication. Later update: They did, but need to figure out what needs to change before updating the setup and this documentation, if we should even bother)
  - The `id` strings are used by the JobDSL scripts.

[github-app]: https://github.com/jenkinsci/github-branch-source-plugin/blob/master/docs/github-app.adoc (GitHub App Authentication Guide)

The key generated from the GitHub application was included in this repo as `terasology-jenkins-io.github-app.private-key.pkcs8` in the format needed by Jenkins, that has been moved to a password safe.

Note: For testing a new Jenkins the existing GitHub app can simply be used directly - it will work even with a test Jenkins at a different URL.

## Various secrets

Jenkins has built up a lot of credentials over the years, and all the original instructions are in the https://github.com/MovingBlocks/InfraPlayground repo - for this rejuvenation attempt let us see how few we can get away with (passwords can be found in a password safe somewhere or the old repo):

* (Username with password) user `gooey` and id `artifactory-gooey` for the Artifactory user with description "User/pass to publish things to Artifactory"
* (Username with password) user and id `GooeyHub` the main GitHub user - "Primary robot user/pass for anything GitHub".
* (Secret text) id `GooeyHubAccessToken` with the personal access token for GooeyHub again (different credential types may be needed in some contexts) - "Primary robot token for anything GitHub".
* (Username with password) user `gooeyhub` on Docker Hub with id `docker-hub-terasology-token` - "Docker hub user/pass"
* (Secret text) id `destsolDiscordWebhook` with the webhook URL to our Discord (viewable via server settings / integrations - although there are a _lot_ of webhooks in there at this point ... maybe all the others are for GitHub direct rather than Jenkins and we stopped using the Jenkins one?) - so this one might be TODO - test
  * The original specific webhook intended here is the one for `#destsol-auto` and the value can be copied from there. So description "Discord webhook for #destsol-auto"
*  (Secret file) id `utility-admin-kubeconfig-sa-token` to match the service account created via `kubeconfig-sa-token.yaml` if desired, description "kubeconfig file for utility-admin"
*  (Secret file) id `jenkins-gar-sa` for publishing (and retrieving) Docker images from Google Artifact Registry.

### Setting up Google Artifact Repository

Assuming locally configured `gcloud` or use of a Cloud Terminal in GCP:

* `gcloud iam service-accounts create jenkins-gar-sa` to create a service user in GCP (should create as `jenkins-gar-sa@teralivekubernetes.iam.gserviceaccount.com`)
* Grant "Artifact Registry Create-on-Push Writer" to the account via IAM menu on GCP (potentially "Storage Admin" might be needed for some things? Utility bucket access - different SA?)
* Go to IAM / Service Accounts and on the KEYS tab add a new JSON key to the service account - we need this as a Secret File in Jenkins. Keep it somewhere local for a moment.
* Go add a new credential in Jenkins and store the JSON file there (see main secrets section)

See test job execution at https://jenkins.terasology.io/job/Experimental/job/TestGAR/1/console

## More config

* For backwards compatibility we have `master` and `built-in` labels attached to the Jenkins controller until `main` is in use everywhere. Initial batch of renames have been done for DSL seed jobs since they're the main ones needing the controller.
* There is a `content.terasology.io` (or whichever domain) defined for Jenkins as a secondary URL beyond the base jenkins subdomain. This is to help host certain other kinds of content from Jenkins like javadoc. Its ingress should spin up automatically as part of our setup and the related config in Jenkins is automated as well
  * See https://www.jenkins.io/doc/book/security/user-content/#resource-root-url for details
* In Jenkins main config look for and adjust GitHub API usage from "Normalize API requests" to "Throttle at/near rate limit" (see todo-swap-this.png)
* Also in Jenkins main config look for "GitHub Servers" and add one, leaving it on defaults (public cloud) and use the GooeyHub PAT secret text credential
  * When ready for this Jenkins to take control of all the things make sure "Manage Hooks" is checked - but consider cleaning old obsolete hooks (and apps for that sake) on busy repos.
* If jobs are created via DSL that include system Groovy scripts (able to interact with Jenkins itself at an admin level, so hugely powerful) they may need to be manually approved under Manage Jenkins once - this is an annoying "security" feature there doesn't appear to be an easy way to greenlight ahead of time despite the Job DSL stuff itself being at the admin-only level and OKed by being written by, well, admins.
  * The https://github.com/MovingBlocks/JenkinsAgentPrecachedJava/blob/main/Jenkinsfile job seems to provoke this over a simply use of encoding in Groovy and will need manual approval - running the job once to let it fail will then pop the approval request into the admin section (which requires purposefully failing a job once which feels dirty)
  * Actually, if you disable the Groovy sandbox checkbox on a system Groovy step in a new enough version and are logged in as an admin you get a button now to pre-approve the script! At least for some job types.

## Plugins and upgrades

For ease we are simply indicating version-pinned plugins via Helm values file, using a custom built image with specific plugins instead might be _slightly_ more efficient but hardly worth it.

The pinned-list can be generated and maintained by an included "PluginAuditizer" utility job that writes out plugin version lists in a few different formats. Typical approach:

* Run the job to see it print out the lists
  * "plugins.txt style" - includes version pins for _currently installed plugins_
  * "plugins.txt style - latest" - simply makes a list variant with `:latest` everywhere
  * Helm values file likewise for both variants, suitable for pasting into `values-plugins.yaml` so long as indentation is made correct)
* To prepare for an upgrade take the "latest" list contents and paste them into `values-plugins.yaml` to replace the pinned versions
  * Alternatively and maybe easier: simply go update all available plugins manually and add in any others you want then run the job and grab the version pins for an IaC update (last step)
* Apply the updated config (Helm/Argo) - possibly to a test Jenkins with an already-updated controller version (may still work on a pre-upgraded controller but some plugins may complain)
* Do any testing to see if the newer plugin versions cause trouble
* Run the "PluginAuditizer" again and grab the _pinned_ list this time and paste it into `values-plugins.yaml` - note that new dependencies may appear in the list.
* Re-apply the config (should result in no change but pins to plugins to avoid surprises later)

To upgrade the Jenkins controller itself simply update `controller.tag` in `values.yaml`. Jenkins' admin section will suggest when a new LTS is available, you can confirm and grab exact tags via https://hub.docker.com/r/jenkins/jenkins/tags?page=1&name=lts - you may want to do this before or after plugins being installed, such first updating plugins, getting the new pinned plugin list, updating IaC along with the Jenkins controller version then sync in Argo and you should be set.

## JCasC and Job DSL

See the [[TECHNICAL.md]] for extensive details on these topics but in short JCasC powers configuration-as-code for _Jenkins itself_ like system settings while Job DSL powers a hierarchy of jobs that define other jobs to make the majority of Jenkins' content fully generated by automation. Groovy scripts involved with this in a new enough Jenkins can be pre-approved for script security if an admin is editing the job live, but sometimes automatically created stuff will need manual approval and may fail once or twice.

JCasC is mixed in with regular Helm config in the values files included in this directory with each values file aggregated by the setup in Argo CD. There is some special care & handling needed in some cases when making certain changes that may clash with previous changes. No added config or action by a human is needed to get going with JCasC.

Job DSL on the other hand requires an initial seed job be created manually (it could also be automated, really, but it is a one-time tiny action) then ideally shepherded a bit as all the seed job spins up hopefully without choking the entire instance (we generate _a lot_ of jobs which can trigger a "build storm" of sorts that'll go for a while)

* Create a freestyle job at the Jenkins root named "BaseSeedJob", restrict it to the `main` label (standard for all Job DSL seed jobs), perform "Process Job DSLs" via "Use provided script" then pasting in contents from `jobdsl/BaseSeedJob.groovy`
  * *NOTE:* Since this hand is hand-configured if its file is changed in Git the changes have to be pasted into the job in Jenkins as well
* Run the base seed job once (it may fail the first time insisting on first being approved under Manage Jenkins / ScriptApproval - do so then rerun)
* There now should be folder-specific seed jobs inside the Utilities folder. Trigger them as needed and actual build jobs will be created. Note that multi-branch pipeline jobs will trigger _all their qualified branches and PRs_ for immediate builds, so don't fire everything off at once
* Module mega-jobs are special and have one more layer of seed jobs (one per letter, to better organize the larger number of jobs). Find them either in the Utilities folder or in the Nanoware case inside its folder (testing focus)
  * Note that no module jobs should be triggered (their DSL-generated Organization Folder jobs won't auto-build on their own) until their associated engine job has built the primary branch - the module build harness needs to be copied from there

At this point you should be in business and able to watch the unfolding build storm.

Note that as the cluster auto-scales to fit more builders a few may fail with a timeout message to Kubernetes. This is likely because a new node didn't come up in time to allow that particular builder to be fully born. Don't worry - more should follow.

If you see a *bunch* of failed builders the cluster may have maxed out. Either just wait for the available build capacity to grind down the queue or consider whether the cluster needs a higher auto-scaling limit

**NOTE:** There may be quirks about generating _views_ using Job DSL. The Terasology `moduleViews.groovy` script for instance seems to get seen and run by the associated DSL job, but no views show up. Making a manual job to run just that Job DSL copy pasted in seems to make the views show up fine.

## Agents

As of the late 2024 migration and upgrade the Jenkins agent configuration was converted entirely to use the JCasC approach. Additionally the newly added support for multiple k8s node pools was engaged via node labels and tolerances, with an initial example made of the primary Terasology build agent, split into two as follows:

* The existing example had the `ts-engine` _Jenkins_ agent label removed, and a k8s tolerance for the heavier node pool added. This allows these generally module/misc building only agents to use either the standard nodes _or_ the heavy ones, but does not force use of the heavy pool. Whether they'll favor spinning up regular nodes or heavy ones when capacity is short is an open question and not bad either way
* A cloned agent retained the `ts-engine` label and got both the tolerance and a new _k8s node selector_ forcing them to run solely on nodes from the heavy pool. As such they'd never run on the default nodes holding the long term services like the Jenkins controller itself

The motivation here is to cause engine builds to spin up dedicated nodes that could hold two engine builds in parallel or possibly three module builds if optimized further - the new smaller default nodes _could_ hold one engine build at the most, if barely depending on how long term stuff has spread around, but considering how CPU intensive engine builds can get (up to a full 4 CPU) their impact would be better to isolate. Additionally this keeps the heavy nodes exclusive for short term build processes and makes sure to leave nothing on those nodes that need a longer life, and thus could cause those more expensive nodes to not scale down. Finally by leaving the tolerance (but not the selector) on other agents they _can_ use spare capacity on the heavy nodes for "lesser" builds, rather than necessarily spin up more default nodes while leaving capacity unused on the heavy nodes.

Even deeper cost optimization would be doable via preemptible / spot instance nodes, that can be withdrawn on short notice. That would take some serious resillience configuration within Jenkinsfiles, however, with hooks to retry _some_ types of build steps if they fail due to being preempted, and likely more use of parallelism (think chunks of tests running on separate nodes). That's way more than we need right now, however, and just having one additional node pool with special agent setup to use that is enough to prove the potential if needed in the future.

## Left to do

* Add remaining build agents - done but not tested
  * TODO: The Python image does work, as root, but Jenkins logs in as jenkins which gets no easy access to Python. Do we even need this agent? Probably an easy fix if so
  * TODO: I removed the `kubectl-agent-experimental` agent as it was a minimal throwaway image I tinkered with, the newer DinD image has kubectl and other stuff. Could delete the repo/branch
    * One minor advantage: this agent allowed usage of kubectl without being privileged with Docker ...
* Fix https://docs.gradle.org/current/userguide/upgrading_version_7.html#abstractarchivetask_api_cleanup for the Omega job
* More plugin considerations
  * maybe theme-manager - was on old server but unsure. Better icons plz ...
  * in theory startup-trigger-plugin but that was a temp need? Deleted job instead
  * maybe ssh-slaves - unsure if that would power either Mac or Win agents? Prolly not
  * maybe pipeline-milestone-step but unsure if we ever use it
  * thin-plugin could probably be dropped - or use it just in case for regular backups?
