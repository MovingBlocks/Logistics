# The Terasology Foundation Infrastructure

This repository documents the overall infrastructure used by The Terasology Foundation to manage the development of its various projects. It is paired with a few secrets stored in password safes among the community or otherwise tied up somewhere private in our services - if you think you need something just ask around :-)

## Pre-requsites

* A decently recent Kubernetes cluster with enough access to apply cluster-wide stuff via `kubectl` and `helm` (v3, not v2)
* This Git repository updated and available to have Kubernetes commands executed against it
* A domain with DNS record management available - will be adding A records for `*` and `@` pointing at an ingress controller IP

### Kubernetes

After the age of Digital Ocean came the age of Google Kubernetes Engine, with its speed and sleek cloud tools. While the setup of a cluster can be automated it is so fast and easy along with only being needed very rarely so simple manual steps are included here. They should be convertable to different cloud hosting supporting Kubernetes fairly readily if ever needed.

* Start creating a new cluster via GKE on Google Cloud in whatever account is available to us
* Enable auto-scaling but try to set the minimum/default node count to 1, maybe max at 5 - we're not trying to create a high availability setup, just an efficient and easy way to balance resources.
  * We _could_ go crazier here, and have done so in the past, could even include a secondary preemptible node pool for ephemereal build agents - but that's likely not worth it (yet). See other readmes for example  TECHNICAL.md in the Jenkins directory for more
* Create a node pool with 4 cpu + 32 gb high memory compute nodes (easier to share CPU than memory, and with high mem we can usually just live on a single node yet grow elastically during build spikes)
* Consider enabling basic/legacy auth to get easily sharable user/pass for access or a cluster certificate (simple user/pass may be legacy only by now) - IAM is needlessly complicated for our needs
* Look for toggles to _minimize_ any sort of high availability or other doodads we likely can go without, like multi-region etc. Aim for optimizing utilization for auto-scaling, not resiliency.
* Let the cluster finish creating and grab anything useful like the API endpoint for the cluster, a cert useful locally, etc. There are usually buttons for generating kube configs for whichever tools.

### Git and local dev

Initial setup from scratch takes starting locally with `kubectl` and `helm` although this could be done within a Google Cloud Shell. After Argo CD is online the setup can take care of itself reading everything out of Git. However you might still want to clone this repo locally to do any sort of Helm previewing, bits of testing not in the live cluster, and so on. Can recommend grabbing a nice IDE or two such as IntelliJ, VSCode, Git Kraken, and/or Monokle.io for Kubernetes.

## Steps

See the individual directories for more technical details on each topic - this part covers the very beginning which starts with Argo CD. Over time secrets management might mature further and change these steps, so do have a look around for any other secrets-looking things first.

1. Prepare the Jenkins OAuth secret - this could be done more automated but was bypassed during main migration due to expediency. For the moment we hook up and apply a secret to Kubernetes from a local file (so naturally do _not_ recommit the file that says to not recommit it after putting secrets in there). This _could_ be done after Argo, but before running a sync on Jenkins.
  *  Go to https://github.com/organizations/MovingBlocks/settings/applications/132034 and make sure you have the client id and the secret (make a new one if needed).
  *  Base64 encode them like noted in `jenkins-secret-do-not-recommit.yaml` and enter the values there.
  *  Create the `jenkins` namespace early (Argo is otherwise configured to make it if needed): `kubectl create ns jenkins`
  *  Apply the secret: `kubectl apply -f jenkins-secret-do-not-recommit.yaml -n jenkins`
1. Kick off the process with `helm install terargo-argocd . -f values.yaml --namespace argocd --create-namespace` in the "argocd" directory (cd there with a terminal or use something like monokle.io's IDE)
1. Stuff should spin up in a few minutes, both Argo CD and an initial application "ingress-control" that sets up an ingress controller (and more - there may be a several minute pause between Argo appearing and it acting on ingress stuff) - get its IP and apply that to the domain (likely hosted on namecheap.com for Terasology stuff)
  * You may have to wait a bit here depending on the mood of DNS replication and/or try in a new browser or incognito mode, maybe after running something like `ipconfig /flushdns`
1. Verify that `https://argocd.[yourdomain]` pulls up, log in with `admin` to validate further (see dedicated readme) (you'll have to OK an invalid cert - plain http will hit a redirect loop on login)
  * Get the password with `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d && echo` (works readily within a Google Cloud Shell, less so on Windows)
1. With the domain confirmed go edit `argocd/templates/argocd-ingress.yaml` and comment out the `letsencrypt-staging` line in favor of the `letsencrypt-prod` line to allow Lets Encrypt to pull a fully valid certificate next try. Commit & push to Git.
  * Naturally if we've already done that this repo will show current state not the pristine ready to work from scratch state - if redoing you'll have to swap from prod back to stage first.
  * Swapping the TLS config between Stage and Prod may go weird if Kubernetes tries to "reuse" part of the secrets. Applying in Argo may be enough to reset this properly? Seems to be ...
1. Click the button to synchronize Argo CD _inside_ Argo CD - yep it manages itself! You may get funny behavior for a moment as Argo redeploys itself.
  * Note that the Argo app for Argo itself may show as out of sync or otherwise weird even before updating Git - when the app is established an extra k8s label may be affixed to the related resources plus Argo hasn't really tried to Argo itself yet so ...
1. Sync other apps as desired, such as Artifactory - other apps should already be configured to start with production-level Lets Encrypt, you only need the extra step the first time (with Argo)
1. Over time simply modify Git as needed then go sync the relevant app in Argo (or YOLO into enabling auto-sync!)
