# Keycloak

Open Source Identity and Access Management app

* https://www.keycloak.org/
* https://github.com/keycloak/keycloak
* https://artifacthub.io/packages/helm/bitnami/keycloak (packaged by Bitnami which tends to be solid)

## Initial setup

The shipped values file should be enough to get started

To apply: `helm upgrade --install keycloak bitnami/keycloak --namespace keycloak --create-namespace -f values.yaml`

The default admin username is `user` with the password retrievable via the following:

`kubectl get secret --namespace keycloak keycloak -o jsonpath="{.data.admin-password}" | base64 --decode`

## Initial configuration

* Consider creating a custom realm to set up real or test data not in the default "master" realm
* Go to "Identity providers" and try out a social login, such as GitHub
* Make an OAuth app on GitHub at https://github.com/settings/developers
  * The "Redirect URI" from Keycloak is what to use for the "Authorization callback URL" in the config on GitHub
  * Initial test app: https://github.com/settings/applications/2513482
    * Client ID: `465bb750113880ab272c`
    * Client secret: <secret generated from the OAuth app config page>
* To test a GitHub login the built-in "account" client seems to work, as others may too. It has a home page of https://keycloak.terasology.io/realms/testrealm/account/
  * This appears to indeed be the general account management function within Keycloak itself
* As the theory goes you'd add custom clients to represent apps like Wekan, which has a Keycloak option! Like Backstage.

## Scary security implication

Since you can get mapped to the same account via email it is feasible for an "account takeover through email hijacking" attack to get mapped via unverified email on a different provider a given user hasn't set up yet. It would go like the following:

1. User signs up for a local account with email `a@b.c` (or really any enabled provider so long as there's more than one)
2. User logs in via GitHub OAuth with an account with the same email and gains access to the same Keycloak account thanks to email mapping (however, GitHub verifies emails)
3. A nefarious user notices a different login provider enabled that doesn't verify emails, creates an account with that provider with email `a@b.c` which remains unconfirmed, logs in to the Keycloak-backed site using that provider then can access the hijacked account fully

Lesson: Don't enable providers that cannot be trusted to be 100% authoritative over who has access to the email associated with an account.
