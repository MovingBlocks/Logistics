# Jenkins

This directory controls and documents our CI/CD setup within Jenkins. Efforts have been made to automate as much as possible and document all of the things to the best of our ability.

See [[TECHNICAL.md]] for deeper details, this readme will attempt to remain high level focusing on the steps to get started and common maintenance.

Argo CD largely runs the deployment of Jenkins and other things - see the root readme and the one in the argocd dir. See also the secrets section here first just in case.

Deployment of Jenkins and about everything else is done using Helm. See https://github.com/jenkinsci/helm-charts/blob/main/charts/jenkins/README.md for more details on the Jenkins setup and available variables.


## GitHub OAuth

Create a new OAuth application in a place like https://github.com/organizations/Terasology/settings/applications and write down the client id and secret.

* Homepage URL should be something like https://jenkins.terasology.io
* Make sure the Authorization callback URL is something like https://jenkins.terasology.io/securityRealm/finishLogin
* Description can be anything, like "Jenkins for The Terasology Foundation"

For the sake of local development ease you can use `jenkins-secret-do-not-recomment.yaml` to prepare the secrets for Kubernetes, just enter the right values as instructed by comments and run `kubectl apply -f jenkins-secret-do-not-recommit.yaml -n jenkins` - you may need to create the namespace first. HOWEVER you do of course not want to commit the actual values, and we should aim to use proper external secrets manager like Vault or some Argo-flavored thing (which does also have a plugin for Vault)

Note that the secret added there is for OAuth and would live GitHub-side at https://github.com/organizations/MovingBlocks/settings/applications/132034

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
    * [ ] owner=`MovingBlocks`, id=`github-app-terasology-jenkins-io`
    * [ ] owner=`Terasology`, id=`gh-app-terasology`
    * [ ] owner=`Nanoware`, id=`gh-app-nanoware`
  - (This is true of August 2021. Check [JENKINS-62220](https://issues.jenkins.io/browse/JENKINS-62220) to see if they've fixed things to require less duplication. Later update: They did, but need to figure out what needs to change before updating the setup and this documentation, if we should even bother)
  - The `id` strings are used by the JobDSL scripts.

[github-app]: https://github.com/jenkinsci/github-branch-source-plugin/blob/master/docs/github-app.adoc (GitHub App Authentication Guide)

The key generated from the GitHub application was included in this repo as `terasology-jenkins-io.github-app.private-key.pkcs8` in the format needed by Jenkins, that has been moved to a password safe.

## Various secrets

Jenkins has built up a lot of credentials over the years, and all the original instructions are in the https://github.com/MovingBlocks/InfraPlayground repo - for this rejuvenation attempt let us see how few we can get away with:

* (Username with password) user `gooey` the Artifactory user (id `artifactory-gooey`)
* (Username with password) user and id `GooeyHub` the GitHub user - used as our primary robot account for anything automation.
* (Secret text) id `GooeyHubAccessToken` with the personal access token for GooeyHub again (different credential types may be needed in some contexts)
* (Username with password) user `gooeyhub` on Docker Hub with id `docker-hub-terasology-token`
* (Secret text) id `destsolDiscordWebhook` with the webhook URL to our Discord (viewable via servevr settings / integrations - although there are a _lot_ of webhooks in there at this point ... maybe all the others are for GitHub direct rather than Jenkins and we stopped using the Jenkins one?) - so this one might be TODO - test
  * The original specific webhook intended here is the one for `#destsol-auto` and the value can be copied from there

## More config

* For backwards compatibility may want to attach the `master` (and `built-in` ?) label to the Jenkins controller until `main` is in use everywhere. Set under "Build Executor Status" link - Built-In Node - Configure (space separate multiple labels). Initial batch of renames have been done for DSL seed jobs since they're the main ones needing the controller.
* There is a `content.terasology.io` (or whichever domain) defined for Jenkins as a secondary URL beyond the base jenkins subdomain. This is to help host certain other kinds of content from Jenkins like javadoc. Its ingress should spin up automatically as part of our setup, unsure if we need any other toggles or if this even has or will go out of date at some point.
  * Set **Resource Root URL** to https://content.terasology.io/ under Manage Jenkins / general to enable this within Jenkins
  * See See https://www.jenkins.io/doc/book/security/user-content/#resource-root-url for details
* In Jenkins main config look for and adjust GitHub API usage from "Normalize API requests" to "Throttle at/near rate limit" (see todo-swap-this.png)
* Also in Jenkins main config look for "GitHub Servers" and add one, leaving it on defaults (public cloud) and use the GooeyHub PAT secret text credential
  * When ready for this Jenkins to take control of all the things make sure "Manage Hooks" is checked - but consider cleaning old obsolete hooks (and apps for that sake) on busy repos.
* If jobs are created via DSL that include system Groovy scripts (able to interact with Jenkins itself at an admin level, so hugely powerful) they may need to be manually approved under Manage Jenkins once - this is an annoying "security" feature there doesn't appear to be an easy way to greenlight ahead of time despite the Job DSL stuff itself being at the admin-only level and OKed by being written by, well, admins.
  * The https://github.com/MovingBlocks/JenkinsAgentPrecachedJava/blob/main/Jenkinsfile job seems to provoke this over a simply use of encoding in Groovy and will need manual approval - running the job once to let it fail will then pop the approval request into the admin section (which requires purposefully failing a job once which feels dirty)

## Plugins and upgrades

For ease we are simply indicating version-pinned plugins via Helm values file, instead using a custom built image with specific plugins might be _slightly_ more efficient but hardly worth it.

The pinned-list can be generated and maintained by an included "PluginAuditizer" utility job that writes out plugin version lists in a few different formats. Typical approach:

* Run the job to see it print out the lists, two last ones in particular
  * "plugins.txt style" - includes version pins for _currently installed plugins_
  * "plugins.txt style - latest" - simply makes a list variant with `:latest` everywhere
* To prepare for an upgrade take the "latest" list contents and paste them into `values-plugins.yaml` to replace the pinned versions
  * Alternatively and maybe easier: simply go update all available plugins manually and add in any others you want then run the job and grab the version pins for an IaC update
* Apply the updated config (Helm/Argo) - possibly to a test Jenkins with an already-updated controller version (may still work on a pre-upgraded controller but some plugins may complain)
* Do any testing to see if the newer plugin versions cause trouble
* Run the "PluginAuditizer" again and grab the _pinned_ list this time and paste it into `values-plugins.yaml` - note that new dependencies may appear in the list.
* Re-apply the config (should result in no change but pins to plugins to avoid surprises later)

To upgrade the Jenkins controller itself simply update the version tag in `values.yaml` under the `controller` section. Jenkins' admin section will suggest when a new LTS is available, you can confirm via https://hub.docker.com/r/jenkins/jenkins/tags?page=1&name=lts - you may want to do this before or after plugins being installed, such first updating plugins, getting the new pinned plugin list, updating IaC along with the Jenkins controller version then sync in Argo and you should be set.

## JCasC and Job DSL

See the [[TECHNICAL.md]] for extensive details on these topics but in short JCasC powers configuration-as-code for _Jenkins itself_ like system settings while Job DSL powers a hierarchy of jobs that define other jobs to make the majority of Jenkins' content fully generated by automation.

JCasC is mixed in with regular Helm config in the values files included in this directory with each values file aggregated by the setup in Argo CD. There is some special care & handling needed in some cases when making certain changes that may clash with previous changes. No added config or action by a human is needed to get going with JCasC.

Job DSL on the other hand requires an initial seed job be created manually (it could also be automated, really, but it is a one-time tiny action) then ideally shepherded a bit as all the seed job spins up hopefully without choking the entire instance (we generate _a lot_ of jobs which can trigger a "build storm" of sorts that'll go for a while)

* Create a freestyle job at the Jenkins root named "BaseSeedJob", restrict it to the `main` label (standard for all Job DSL seed jobs), perform "Process Job DSLs" via "Use provided script" then pasting in contents from `jobdsl/BaseSeedJob.groovy`
* Run the base seed job once (it may fail the first time insisting on first being approved under Manage Jenkins / ScriptApproval - do so then rerun)
* There now should be folder-specific seed jobs inside the Utilities folder. Trigger them as needed and actual build jobs will be created. Note that multi-branch pipeline jobs will trigger _all their qualified branches and PRs_ for immediate builds, so don't fire everything off at once
* Module mega-jobs are special and have one more layer of seed jobs (one per letter, to better organize the larger number of jobs). Find them either in the Utilities folder or in the Nanoware case inside its folder (testing focus)
  * Note that no module jobs should be triggered (their DSL-generated Organization Folder jobs won't auto-build on their own) until their associated engine job has built the primary branch - the module build harness needs to be copied from there

At this point you should be in business and able to watch the unfolding build storm.

Note that as the cluster auto-scales to fit more builders a few may fail with a timeout message to Kubernetes. This is likely because a new node didn't come up in time to allow that particular builder to be fully born. Don't worry - more should follow.

If you see a *bunch* of failed builders the cluster may have maxed out. Either just wait for the available build capacity to grind down the queue or consider whether the cluster needs a higher auto-scaling limit

## Left to do

* Add remaining build agents - done but not tested
