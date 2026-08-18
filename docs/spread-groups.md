# Spread groups — keeping rivals off the same node

Some workloads must not share a node. Not because they conflict logically, but because they compete for the same CPU at the same moment, and the loser takes something visible down with it.

This repo expresses that with a **spread group**: a label that pods carry, plus an anti-affinity rule saying "never share a node with anything else wearing this label".

## How pod anti-affinity works

Scheduling normally answers one question: *does this pod fit here?* Anti-affinity adds a second: *what else is already here?*

A pod anti-affinity rule has three parts:

| Part | Meaning |
|---|---|
| `labelSelector` | which **other pods** we are avoiding |
| `topologyKey` | what counts as "the same place" |
| `required…` / `preferred…` | whether it is a hard rule or a tiebreaker |

`topologyKey: kubernetes.io/hostname` means the node is the unit — each node has a distinct value for that label, so "same topology" means "same node". Setting it to `topology.kubernetes.io/zone` would spread across zones instead.

`requiredDuringSchedulingIgnoredDuringExecution` is a hard constraint at **scheduling** time. The two halves of that name both matter:

- **required during scheduling** — the scheduler will refuse to place the pod rather than violate it. If no node qualifies, the pod stays `Pending`.
- **ignored during execution** — once placed, the pod is never evicted for violating it later. A rule that becomes untrue after the fact does not move anything.

The alternative, `preferredDuringSchedulingIgnoredDuringExecution`, is a weighted preference: the scheduler tries, and places the pod anyway if it cannot. That sounds safer and is usually the wrong choice here, because it fails silently — you get the packing you were trying to avoid, with nothing to tell you.

## Why a shared label instead of each workload avoiding itself

The obvious rule is "ingress replicas avoid other ingress replicas", selecting on the ingress controller's own labels. That solves replica-vs-replica and nothing else — the ingress can still land on the node already running the Jenkins controller.

A shared group label inverts it. Every member declares the *same* selector, matching the group rather than itself, so the constraint holds **across workloads**:

```yaml
podLabels:
  siliconsaga.org/spread-group: frontline

affinity:
  podAntiAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchExpressions:
            - key: siliconsaga.org/spread-group
              operator: In
              values: [frontline]
        topologyKey: kubernetes.io/hostname
```

Adding a member is adding those two blocks. No existing member changes.

## The `frontline` group

| Member | Where it is configured |
|---|---|
| ingress-nginx controller (2 replicas) | `ingress-control/values.yaml` |
| Jenkins controller | `jenkins/values.yaml` |

**Three pods, so three nodes.** The default pool runs a minimum of 3 and usually 4–5, so this fits with headroom.

### Why these three

GKE's `optimize-utilization` autoscaling profile deliberately bin-packs onto the fewest nodes so it can scale down. Left alone it put both ingress replicas, the Jenkins controller and Artifactory on a single 2-core node. When a build started, all of them contended; the ingress lost, failed its own health probe, and restarted — which showed up as `artifactory.terasology.io` being down while the Artifactory pod itself was perfectly healthy.

A second ingress replica bought nothing against that, because it was packed onto the same node as the first.

## Costs, stated plainly

- **The group needs at least as many schedulable nodes as it has members.** At 3 members and 3 nodes there is no slack: lose a node and one member sits `Pending` until capacity returns. That is the intended signal, but it is a real constraint on shrinking the pool.
- **It fights the autoscaler's packing goal**, which is the point, but it means scale-down cannot consolidate these nodes as tightly.
- **Membership is opt-in and easy to forget.** A new front-door workload does not join by accident.

## Adding a member

1. Add the `siliconsaga.org/spread-group: frontline` pod label.
2. Add the anti-affinity block above.
3. Check the node count — members must be ≤ schedulable nodes, or something will not schedule.

**Artifactory is the obvious fourth candidate**, since it is the service whose availability keeps being affected. It is deliberately left out for now: a fourth member would require a permanent floor of four nodes, and the ingress fix alone may be enough. Revisit if it keeps landing beside the Jenkins controller.

## Checking it worked

```bash
kubectl get pods -A -l siliconsaga.org/spread-group=frontline -o wide
```

Every listed pod should show a different `NODE`. If one is `Pending`, read its events — `didn't match pod anti-affinity rules` means there is no node left without a group member, which is a capacity answer, not a bug.
