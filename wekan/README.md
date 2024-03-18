# WeKan

This is effectively an open source Trello clone, to allow easy self-hosted kanban.

* https://wekan.github.io/
* https://artifacthub.io/packages/helm/wekan/wekan
* https://github.com/wekan/wekan

To apply: `helm upgrade --install wekan wekan/wekan --namespace wekan --create-namespace -f values.yaml`

Interesting issue: After trying to upgrade with a legit change the new pod could not come online as it tried to mount the existing PV - but the old point still had it mounted (until the new pod would be ready to taken over). Probably an issue with the type of deployment / rolling settings

## Keycloak integration

This was set up simply following instructions at https://github.com/wekan/wekan/wiki/Keycloak which worked together with some ChatGPT suggestions after one bogus path was detected and fixed. See the values file for how to configure WeKan to use it. On the Keycloak side the following steps were taken:

* Create a new client on a given realm (a new realm was made as a good practice - do not use the default "master" realm for anything other than admin)
* Enter the client id (which actually becomes the client id seen in normal OAuth apps - you don't end up with a generated gibberish key)
* The root URL, home URL, and web origin can all be https://wekan.terasology.io
* The Valid redirect and logout URIs should be https://wekan.terasology.io/*
* The access type _must_ be set to confidential, not public (which enables the credentials tab later needed for configure the 3rd party client OAuth connection)
