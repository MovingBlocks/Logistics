# The Terasology Foundation Infrastructure

This repository documents the overall infrastructure used by The Terasology Foundation to manage the development of its various projects. It is paired with a few secrets stored in password safes among the community or otherwise tied up somewhere private in our services - if you think you need something just ask around :-)

## Pre-requsites

* A decently recent Kubernetes cluster with enough access to apply cluster-wide stuff via `kubectl` and `helm` (v3, not v2)
* This Git repository updated and available to have Kubernetes commands executed against it
* A domain with DNS record management available - will be adding A records for `*` and `@` pointing at an ingress controller IP

### Kubernetes

After the age of Digital Ocean came the age of Google Kubernetes Engine, with its speed and sleek cloud tools. While the setup of a cluster can be automated it is so fast and easy along with only being needed very rarely so simple manual steps are included here. They should be convertable to different cloud hosting supporting Kubernetes fairly readily if ever needed. A few variants of our GKE setup has existed over time.

* Get ready to work on GKE on Google Cloud in whatever account is available to us (get k8s API enabled, etc). Get into a cloud shell or local `kubectl` session
* Create the cluster and default node pool:

```
gcloud beta container --project "teralivekubernetes" clusters create "ttf-cluster" \
    --zone "us-east1-d" \
    --machine-type "e2-highmem-2" \
    --disk-size "50" \
    --num-nodes "2" \
    --enable-autoscaling \
    --total-min-nodes "2" \
    --total-max-nodes "6" \
    --location-policy "ANY" \
    --enable-autoupgrade \
    --enable-autorepair \
    --autoscaling-profile optimize-utilization \
    --node-locations "us-east1-d" \
    --enable-legacy-authorization # Enable legacy authorization (we likely have old stuff needing it)
```

* Create a secondary node pool that starts empty (read below for more details):

```
gcloud beta container --project "teralivekubernetes" node-pools create "heavy-builder-pool" \
    --cluster "ttf-cluster" \
    --zone "us-east1-d" \
    --machine-type "e2-standard-4" \
    --disk-size "50" \
    --node-labels builder=heavy \
    --node-taints heavy-builder-only=true:NoSchedule \
    --num-nodes "0" \
    --enable-autoscaling \
    --total-min-nodes "0" \
    --total-max-nodes "3" \
    --location-policy "ANY" \
    --enable-autoupgrade \
    --enable-autorepair
```

#### Node pools

As of late 2024 a new setup supporting multiple node pools is being implemented. This is partly from an old setup relying on beefier nodes (4 cpu, 32 GB ram) being consistently bad at downscaling to just one node, which would be plenty for all our long term stuff. Instead there is a default node pool with smaller machines offering 2 CPUs and 16 GM of ram, with a minimum pool size of 2 - same total as before, but now split in two. This reduces efficiency a bit (less ability to maximize usage, more overhead per node) but seemingly solves the auto-scaling as going down to 2 nodes appears easy enough. Specialized node pools can then be added with a minimum size of 0, that downscale just fine so long as the 2-size default pool is happy. An initial 2nd pool for heavy Jenkins builders is included, at 4 CPU + 16 GB of RAM, enough to run two engine builds (that can sometime approach 4 CPU and more than 8 GB of ram each)

Controlling what goes where is done via labels (node selectors), node taints, and workload tolerances. The default node pool has no related config and will work as generic k8s. The included example heavy builder pool has a NoSchedule taint called `heavy-builder-only=true` that prevents any workload from starting there unless it explicitly tolerates that taint. Additionally the builder pool has a node label of `builder: heavy` that can be used to force a workload to go to such a node. With both tolerance and label on engine Jenkins build agents the heavy nodes would spin up, yet with _just_ the tolerance a _module_ builder would be able to run in either the default pool or the heavy pool based on availability. Affinity and other advanced topics are not likely relevant yet.

An example test pod yaml is included in `heavy-pool-pod.yaml` that will only work in the heavy pool. See more related details in the jenkins directory, including the `TECHNICAL.md`

Another likely useful pool would be one with unusually powerful CPUs, likely one of the `C` series (rather than `E2` for cheap general purpose compute). Code builds will just take longer on a weak CPU, but _game servers_ might get outright laggy if they don't have enough CPU to get everything done fast enough with players connected. This depends a fair bit on the game, of course.

Tip: GKE offers a _Labels_ field during node pool config - those are _not_ **NODE** labels (despite the UI helpfully suggesting that they're "applied to all nodes") and won't be visible within Kubernetes, they're just for GCP things. This is confusing enough for chatbots to be well-aware of how much of a pitfall it is.

#### Committed use discounts

Another new thing with late 2024 is the goal of using a committed use discount to cover the two long term default nodes for a full year at a time. This should in theory get the cost down to just below $100/month for a somewhat "idle" cluster, which isn't bad at all (assuming a sole cluster which has its $70 admin fee covered by the free tier's single cluster allowance). Commitments are managed under Computer engine / Committed Use Discounts.

Example monthly costs (before discounts) in late 2024 during evaluation for `us-east-1`):

* $66 `e2-highmem-2` - default pool nodes (2 CPU, 16 GB)
* $98 `e2-standard-4` - heavy builder nodes (4 CPU, 16 GB)
* $179 `c3d-highmem-4` - strong CPU nodes (4 CPU, 32 GB) - enough to for instance run several ARK servers (the Genesis 2 map was brutal on CPU and could go beyond 16 GB RAM) along with who knows how many Terasology servers

Generally for the `E2` server types a 1 year committed discount takes off about 37% of the on-demand cost. 3 year would be 55% but is a long time.

#### Adding utility admin access

For getting into the cluster without having to deal with GKE, `gcloud` and so on see the included `kubeconfig-sa-token.yaml` file and the step-by-step instructions via commented out lines. In short it creates a utility admin service account, binds the right access, and creates a token for logging in with said service account. Then that just needs to be put into a typical kube config. Inspired by https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contengaddingserviceaccttoken.htm

Detailed IAM is a bit much for our needs but users can also be added this way if needed.

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
1. With the domain confirmed go edit `argocd/templates/ingress-argocd.yaml` and comment out the `letsencrypt-staging` line in favor of the `letsencrypt-prod` line to allow Lets Encrypt to pull a fully valid certificate next try. Commit & push to Git.
  * Naturally if we've already done that this repo will show current state not the pristine ready to work from scratch state - if redoing you'll have to swap from prod back to stage first.
  * Swapping the TLS config between Stage and Prod may go weird if Kubernetes tries to "reuse" part of the secrets. Applying in Argo may be enough to reset this properly? Seems to be ...
1. Click the button to synchronize Argo CD _inside_ Argo CD - yep it manages itself! You may get funny behavior for a moment as Argo redeploys itself.
  * Note that the Argo app for Argo itself may show as out of sync or otherwise weird even before updating Git - when the app is established an extra k8s label may be affixed to the related resources plus Argo hasn't really tried to Argo itself yet so ...
1. Sync other apps as desired, such as Artifactory - other apps should already be configured to start with production-level Lets Encrypt, you only need the extra step the first time (with Argo)
1. Over time simply modify Git as needed then go sync the relevant app in Argo (or YOLO into enabling auto-sync!)
