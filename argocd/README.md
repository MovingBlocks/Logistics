# Argo CD

This app is a continuous deployment (CD) tool meant to help deploy other apps.

For initial setup see the root readme as it assumes everything starts with Argo. This readme will focus on reasoning and later steps.

## Approach - Helm

The goal by using Argo CD is having as much config and setup present in this readme's Git repo as possible, with minimal effort needed to maintain or even rebuild our infrastructure as needed.

You can see more about Argo and Helm at https://github.com/argoproj/argo-helm/tree/main/charts/argo-cd

Argo CD is a Kubernetes-native CD operator meant to help maintain applications hosted within its cluster (or other clusters). It can manage apps (including itself) in a variety of ways, including via _Helm_ - a well-known orchestration tool that relies on charts, which are template files that can depend on other charts and load values from given files to replace within the chart. You deploy a given chart as a "release" which you can list with `helm ls` (you need to supply a namespace to go beyond the default)

For an easy way to figure out which values can be supplied run something like `helm show values artifactory-oss --repo https://charts.jfrog.io` - however there are two common gotchas:

* A chart may be based on another chart, in which case you'd need to repeat the command on the other chart. This is the case with Artifactory which is actually a small _tree_ of charts. The OSS chart depends on the base chart and the above command only shows the values within the OSS chart, not the ones from "upstream"
* The suggested way to use Helm charts within Argo is to make your _own_ chart that is based on your desired chart, so you can supply your own values file and version track your chart release level. This gets fun in two ways: version numbers become like nesting dolls and when you wrap a chart you also have to wrap your values - see the Artifactory example which ends up being `artifactory-oss.artifactory.artifactory`
  * Your chart's version is the release you're deploying to your Kubernetes cluster
  * The target chart's version is the release you're actually using
  * The application _inside_ the target chart also has its own version number - sometimes numbers get partially or wholly duplicated between layers

You can also "render" a completed chart showing your values supplied by using `helm template -f "values.yaml" jfrog/artifactory-oss` but you do tend to get a _lot_ of YAML thrown in your face. GUI tooling may be advised, such as https://monokle.io/

Note: While Helm is used to both bootstrap Argo CD itself and later manage various applications inside Argo the original Helm install will become orphaned when Argo takes over (Argo actually just uses Helm to render the resource files, but not to install charts) - this can include a name clash so be sure to use "terargo" as the initial release name for Argo itself.

## Manage Argo CD via Argo CD

An Argo installation can be managed by itself - that is, after the initial installation you can see Argo CD as an app inside it, even tweak it there or make adjustments in Git that can then sync either automatically or via manual sync operation within Argo. For more details see https://argo-cd.readthedocs.io/en/stable/operator-manual/declarative-setup/#manage-argo-cd-using-argo-cd - although note that the setup there uses Kustomize rather than Helm. After a fair amount of experimentation Helm became the way for us instead.

The applications defined _inside_ Argo CD (including Argo CD itself!) are themselves defined as Argo CD applications, which is a Custom Resource Definition (CRD) provided by Argo CD. Resource files for our apps live in the templates directory here, although as config maps as by the time the initial Helm run executes Kubernetes doesn't know about the Argo CRDs and will blow up. Delaying the use of the CRDs to a Helm post-install hook applying them as a Job gets around this and lets everything work in one smooth action.

## Steps for after initial setup

When you have a fresh new Argo CD it should come with bunch of stuff preconfigured thanks to the tree of YAML in this repo, you should be able to retrieve the initial admin password either directly as a secret or by using the Argo CLI which you can install [as per the below instructions](https://argo-cd.readthedocs.io/en/stable/cli_installation/)

For the easier secret approach simply execute `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d`

For the full CLI setup follow these steps, with the last one allowing you to change the admin password (which User Info / Update Password will also let you do in the GUI)

* `curl -sSL -o argocd-linux-amd64 https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64`
* `sudo install -m 555 argocd-linux-amd64 /usr/local/bin/argocd`
* `rm argocd-linux-amd64`
* `argocd admin initial-password -n argocd`
* `argocd account update-password`

### Dex

Adding Dex into the mix backed by a GitHub OAuth application takes a few steps. This allows auth via GitHub rather than just the predefined Argo admin account _but_ if you log in via GitHub you may not see any apps! So there's still a bit more to do there ...

Register a new GitHub OAuth app and record the client id and client secret. Put the id into `values.yaml` and update the secret in `github-oauth-secret-do-not-recommit.yaml` as per the instructions but of course _do not_ commit it again.

Apply it: `kubectl apply -f github-oauth-secret-do-not-recommit.yaml -n argocd`

Enable some additional config to `values.yaml` - see the dex tree in the included file (still TODO)

### Add Credentials Template

If there is a desire to use Argo CD with private Git repos you need to prepare a credential that'll work. This can be done for individual Git repos under Argo's Settings - Repositories - Connect Repo

If wanting a credential that'll work for a _range_ of repositories still fill out a regular repo but use a prefix of the URL that'll match multiple repos, for instance https://github.com/CervTest would match any actual repository defined under that user account.

There is no need to do anything special for any related Argo apps, they should work immediately and automatically so long as they can match to such a credentials template. This includes apps created via yaml and even an application set.

See https://argo-cd.readthedocs.io/en/stable/user-guide/private-repositories/ for further details.

### ApplicationSet support

This is not really needed for normal operations, but if a series of Argo-managed apps managed via new Git repos would be handy (like for spinning up Terasology game servers) then the Argo CD Application Set Controller can be enabled as well and configured with GitHub. See main doc at https://argocd-applicationset.readthedocs.io/en/stable/Generators-Git/

* Include a second ingress to allow a Git webhook to target an API inside the controller (not exposed by default)
* Make a webhook as per the doc link (make sure especially to set it to JSON not the x-www-form stuff)
* Take the secret from the webhook page and edit it into the `argocd-secret` object via `kubectl edit secret argocd-secret -n argocd`
* The regular Argo CD process also has the same hook but meant for refreshing application state, not the existance of applications. One of these may also be needed, will use same secret

The above should prepare the ApplicationSet support to receive hook events - however, you may also need to configure Argo CD _itself_ to do the same. See https://argo-cd.readthedocs.io/en/stable/operator-manual/webhook/

TODO: Testing so far has met with mixed success. Needs more work.

## Later maybes

* There are plenty of plugins and other extensions to Argo CD we could look at. The suggested template `kustomization.yaml` came with a little component section related to https://github.com/argoproj-labs/argocd-extensions which may be worth checking out. Would enable by adding a `components:` block with `- https://github.com/argoproj-labs/argocd-extensions/manifests` in it
* We could also potential _remove_ pieces we do not need - like Dex is an included component dealing with certificates or secrets or something, but does our instance actually use it? Notifications?
