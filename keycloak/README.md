# Keycloak

Open Source Identity and Access Management app

* https://www.keycloak.org/
* https://github.com/keycloak/keycloak
* https://artifacthub.io/packages/helm/bitnami/keycloak (packaged by Bitnami which tends to be solid)


To apply: `helm upgrade --install keycloak bitnami/keycloak --namespace keycloak --create-namespace -f values.yaml`

Oddly the ingress resource was not deployed, despite showing fine in a Helm dry-run. Applied it manually with Monokle, targeting the keycloak namespace

Never mind, it was the usual quirkiness with the subcharting impacting the values file yet not installing via Argo meant that Chart.yaml wasn't being used etc ...
