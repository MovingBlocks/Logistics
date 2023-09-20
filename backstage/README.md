# Backstage

Internal Developer Portals (IDPs) or frequently simply DevOps Platforms (arguable if there are subtle differences) have become all the rage. The [Backstage](https://backstage.io/) project offers a framework to build such an IDP.

This directory contains _configuration_ for setting up an instance of Backstage in Kubernetes, although you also separately need a customized Docker image to get much of anywhere with it.

So far deploying this has been done locally as a Helm chart repeatedly, with the `backstage-secrets-do-not-recommit.yaml` filled out and left in the `templates` dir so it'll auto-deploy with Helm. If deploying secrets manually (to for the moment let Argo start handling the app) then the secrets should _not_ be in templates since otherwise Argo will keep overwriting it.

## Configuration and secrets

Unlike Artifactory Backstage is _very_ [Kubernetes-native](https://backstage.io/docs/deployment/k8s/) with almost _too_ many different ways to configure things and supply secrets. An overview:

* Backstage has [local YAML configuration files](https://backstage.io/docs/conf/) (much like Artifactory), these are good for static information that may change between environments (easy ways to vary based on deployment context)
* You can override config file values using environment variables with the same name prefixed with `APP_CONFIG_` and dots replaced with underscores such as `APP_CONFIG_app_baseUrl` - and you can supply such [extra environment variables](https://github.com/backstage/charts/tree/main/charts/backstage#configure-your-backstage-instance) via Helm values in the `backstage.extraEnvVars` array
  * Unsure if the general variables should be used to this approach rather than piggybacking on the secrets CM (somewhat handy that secrets == .env locally)
* You can furthermore embed such a YAML config file as a Config Map in Kubernetes (much like Jenkins), which is good for dynamic stuff like catalog content you can then change without a Docker rebuild or Helm redeploy
  * For this to work the `extraAppConfig` block needs to be configured in the values file
* The Helm chart has an extensive set of things to configure via [values file](https://github.com/backstage/charts/tree/main/charts/backstage), which is good to for instance set up a Postgresql instance for the earlier config to point at.
  * The Helm chart specifically has a way to load the config map or [sensitive values from Kubernetes secrets](https://github.com/backstage/charts/tree/main/charts/backstage#sensitive-environment-variables) (`backstage.extraEnvVarsSecrets`) that'll be added to your environment and thus assessible for use within the main config files
* The Helm chart additionally can point directly at a secret for some things like [Postgresql auth](https://github.com/backstage/charts/tree/main/charts/backstage#configuring-chart-postgresql) - which coincidentally uses the same Bitnami image as Artifactory's Postgresql (so set that password!)
* Backstage can itself be configured to point at various Git repositories for more fun, although usually in the sense of catalog content (templates etc)

The `cm-app-config.extra.yaml` Config Map holds some of our details with a TODO to move all the unchanging bits into a Docker image when a custom one is in use. Also consider what should be in the Helm values file vs the CM. Rough reasoning:

1. (Rebuild) Docker holds actual changed Backstage code tweaks and any related config it makes no sense to change without rebuilding Docker anyway. And maybe really boring config file stuff that'll never change
1. (Redeploy) Helm holds very Kubernetes-specific stuff that would need a refresh of the pod anyway (like resource allocations and Postgres setup) - this _can_ hold config file stuff but favor the CM for that
1. (Restart) The CM holds main "live" config that we might want to change without a rebuild or a redeploy. Expect this to take a restart (recreation in place of the pod)
1. (Refresh) Any Backstage defined "Locations" for catalog data, usually Git repos, will routinely get checked for updates without a need to restart the app - roughly every 2 minutes by default or when you go through a flow that checks anew (like the Create page using a Template from Git)

The `backstage-secrets-do-not-recommit.yaml` file holds the Kubernetes secrets that `extraEnvVarsSecrets` will make available as environment variables. They can be referenced in the CM in the form of `${KEY}` (not necessarily just `$key`)

If secrets are copied from its yaml file to a `.env` file in the Backstage workspace be wary that `user-password` and `admin-password` work within YAML to be interpreted via Kubernetes, but possibly not so much in a Linux env declaration. May need to convert to underscores - those two keys are only used for Postgres anyway which isn't live for regular local dev.

## Useful initial tweaks

Several bits to get started

* Add integrations & auth for something like GitHub, including tweaking the sign-in page as per https://backstage.io/docs/auth/#sign-in-configuration
* Fix the weird API auth issue noted at https://github.com/backstage/backstage/blob/master/contrib/docs/tutorials/authenticate-api-requests.md or templates won't work?
  * API seems wide open even without specifying the backend custom port (not exposed by default)

## Docker

Backstage the app is shipped as a stateless Docker image and needs to be rebuilt fairly regularly due to the immaturity of Backstage's plugin ecosystem, which still needs to be hand-wired directly into related code within the Backstage app code.

If running on older Docker installs the Backstage-provided `Dockerfile` may need to be built with `DOCKER_BUILDKIT=1 ` preceding the `build-image` command in `package.json` (and can have its default sqlite3 piece removed - but TODO that then may break something else from lacking Python)

### Publishing images to GitHub

Say we have a `Dockerfile` in a directory ready to build as "some-image-name" and use somewhere. It must include either following snippet to map seamlessly to a GitHub Packages repo:

* `LABEL org.opencontainers.image.source=https://github.com/MovingBlocks/Logistics`
* `LABEL org.opencontainers.image.source=https://github.com/Cervator/ArtifactoryFuns`

From a directory with Docker installed and a (classic) personal access token (PAT) generated assuming you are "Cervator":

* `export CR_PAT=YOUR_TOKEN`
* `echo $CR_PAT | docker login ghcr.io -u Cervator --password-stdin`
* `docker build -t backstage .`
* `docker tag backstage ghcr.io/cervator/backstage:latest`
* `docker push ghcr.io/cervator/backstage:latest`

Note that while a first push of a new package this way will publish & link it to the desired Git repo it will [start private](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#pushing-container-images) - you have to go into settings and swap it to public to make it readable in general. Hopefully that'll only be needed once and/or maybe it will work as documented if you're using an org repo? It went private even from a public repo on Cervator's account.

To pull:

* `docker pull ghcr.io/cervator/backstage` - basic
* `docker pull ghcr.io/cervator/backstage:latest` - specific tag
* `docker pull ghcr.io/cervator/backstage@sha256:1234567890abcdefghijklmn` - specific digest from `docker inspect` for the image (`docker images` will show the starting letters - may be enough?)

You can also include the following in the `Dockerfile` that will cause added details on the package page on GitHub (description is limited to 512 chars, license 256 chars, example "MIT"):

```
LABEL org.opencontainers.image.description="My container image"
LABEL org.opencontainers.image.licenses=MIT
```

## Gotchas

When using `yarn dev` the front-end and backend are started in parallel. It looks likely that this may cause one to try reaching the other before it is ready, looking much like the following - but it doesn't appear to happen again so it is probably safe to ignore:

```
error Collating documents for software-catalog failed: FetchError: request to http://localhost:7007/api/catalog/entities?offset=0&limit=500 failed, reason: connect ECONNREFUSED 127.0.0.1:7007 type=plugin documentType=software-catalog
[1] 2023-09-18T03:52:04.941Z backstage error request to http://localhost:7007/api/catalog/entities?offset=0&limit=500 failed, reason: connect ECONNREFUSED 127.0.0.1:7007 type=system task=search_index_software_catalog errno=ECONNREFUSED code=ECONNREFUSED stack=FetchError: request to http://localhost:7007/api/catalog/entities?offset=0&limit=500 failed, reason: connect ECONNREFUSED 127.0.0.1:7007
```



## TODO

NEXT - try the docker build again, now that all secrets are out of `app-config.yaml` in the Vagrant workspace. See if it runs on backstage.terasology.io after tweaking image in yaml - YEP!
- when deployed via Helm to GKE the CM must essentially match what would be the client yaml config file for local dev
- in theory when deployed to AWS you just have the values in a param store instead? Could have yarn scripts to convert / prepare things ...
- Note that ${CATALOG_FILE_PATH} has been added and needs to vary between local from-source and within-docker

* See what's up with `// @ts-ignore` - some linting thing? Maybe less aggressive? Prettifier?
* Improve the GitHub auth setup
  * New school offers a default GitHub setup, will probably auth but not associate with GitHub profile (just some guest thing)
  * Can enable a simple new GitHub identity thing _but_ it requires https://backstage.io/docs/integrations/github/org/ to be enabled with users ingested
  * There is also a toggle for accepting in users from multiple orgs (may be desired)
  * Hopefully all that will work - the old custom snippet is inlined below
* Test that the Ghost template will result in working Ghost blogs
  * Last time touched it seemed like a DB issue kept Ghost from starting up
  * The ApplicationSet within Argo proved highly annoying to get going (for Venue apparently we skipped it and just wrote Applications to throw at k8s)
* Improve config further for a reusable and cross-purpose approach
  * Retrieve the lost stuff from the Adaptavist flavor base like how it handled config files with a multi-client approach
  * Apply the best practices from https://github.com/backstage/charts/tree/main/charts/backstage#configure-your-backstage-instance - at least partially done



Old GitHub client conflict

```
import {
  DEFAULT_NAMESPACE,
  stringifyEntityRef
} from '@backstage/catalog-model';
import {
  createGithubProvider,
  createRouter
} from '@backstage/plugin-auth-backend';
import { Router } from 'express';
import { PluginEnvironment } from '../types';

export default async function createPlugin({
  logger,
  database,
  config,
  discovery,
  tokenManager,
}: PluginEnvironment): Promise<Router> {
  return await createRouter({
    logger,
    config,
    database,
    discovery,
    tokenManager,
    providerFactories: {
      github: createGithubProvider({
        signIn: {
          resolver: async (
            {
              result: {
                fullProfile: { username },
              },
            },
            ctx,
          ) => {
            const id = username as any;

            const userEntityRef = stringifyEntityRef({
              kind: 'User',
              namespace: DEFAULT_NAMESPACE,
              name: id,
            });

            const fullEnt =
              await ctx.catalogIdentityClient.resolveCatalogMembership({
                entityRefs: [userEntityRef],
                logger: ctx.logger,
              });

            const token = await ctx.tokenIssuer.issueToken({
              claims: { sub: userEntityRef, ent: fullEnt },
            });
            return { id, token };
          },
        },
      }),
    },
  });
}
```
