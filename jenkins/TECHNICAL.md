# Technical Jenkins

Documentation here covers the more optional tasks of making Jenkins run _nicely_ - which may or may not be needed depending on context. Some historical observations are also included.

## Developing the dev tools

If debugging for development reasons you can work via Helm CLI manually, rather than let Argo CD handle it after a Git push. This can be easier when just changing individual or a few files in a tool like Monokle.io

* helm repo add jenkins https://charts.jenkins.io
* helm install terajenkins jenkins/jenkins -f values.yaml -f values-agents.yaml -f values-plugins.yaml -f values-jcasc-general.yaml -n jenkins
  * (or "upgrade" instead of "install")
* Get initial admin password with `kubectl get secret terajenkins -n jenkins -o jsonpath="{.data.jenkins-admin-password}" | base64 --decode && echo`
  * This won't matter if fully provisioning with JCasC already configuring GitHub integration

## SSD storage

Jenkins works best on SSD, even the controller, especially when it comes to dealing with lots of little files involved in analytics uploading and so forth. An extra storage class is set up via pre-install Helm hook in `pre-install-ssd-storage-class.yaml` and that in turn is used in the main `values.yaml`

## JCasC

We use a recommended approach from the official Helm image to [split config-as-code into multiple files](https://github.com/jenkinsci/helm-charts/blob/main/charts/jenkins/README.md#breaking-out-large-config-as-code-scripts) which then originate from multiple Helm values files.

Files with JCasC sections result in files being created at `/var/jenkins_home/casc_configs` in the Jenkins pod, where they are then loaded as multiple sources for config. Each file is named after its arbitrary key before the `|` such as `general.yaml` from the following snippet:

```
jenkins:
  controller:
    JCasC:
      configScripts:
        general: |
          jenkins: ...
```

### JCasC Gotchas

* You don't want to overlap between non-JCasC and JCasC config for the _same_ setting, as noted in the documentation (for instance indicating the Jenkins URL itself both ways)
* If you change the arbitrary key for a JCasC snippet Jenkins will happily create a _new_ file under `/var/jenkins_home/casc_configs` - without deleting the _old_ file which still loads with its outdated values, including the potential for clashes! Unsure if there is an easy fix for this other than getting into the file system and deleting the old file directly (or just doing a full wipe but..)
  * Even though the Jenkins pod's _main_ container will end up in an inaccessible crash state if it fails to start you can cheat and use the sidecar meant to reload jcasc with the following: `kubectl exec -it terajenkins-0 -n jenkins --container config-reload -- /bin/sh` then `cd /var/jenkins_home/casc_configs` to get to the good stuff. Then delete away and retry!
* JCasC can be _very_ picky when it comes to parameter lists. A valid config in the UI may produce an "export" JCasC that will not work (as the docs indicate) because some empty field wasn't included in the config snippet.
  * One thing that was missing for GitHub Authorization setup was the "organizationNames" entry - it may well work if left as an empty string rather than undefined (leading to a null, causing param issues?)
* If working locally with any sort of Helm templating approach followed by deploying individual resources (easy to do in Monokle) be mindful that adding new JCasC keys to a values file may also remove them from the default file - which if not redeployed will clash with the new values. Deploy both or the whole thing if in doubt

## Job DSL

The jobDSL directory contains the `BaseSeedJob.groovy` script which creates a set of top-level folders in Jenkins (see it for details)

Each folder thanks to the base seed job also gets an associated nested seed job, hidden away in the "Utilities" folder (to keep infra complexity out of the main folders)

That seed job in turn will load any `*.groovy` files in an associated directory in this repository. So anything in the "Terasology" directory will be  processed by "Utilities/TerasologySeedJob"

It isn't automated (yet at least) that all scripts under that directory will naturally appear inside the Terasology directory itself - that has to be indicated in the DSL script itself.

Job DSL itself is a large topic but is also _intensely well documented_ both at its [plugin page](https://plugins.jenkins.io/job-dsl/) in its [wiki](https://github.com/jenkinsci/job-dsl-plugin/wiki) and there's even a full API viewer customized to a given Jenkins [like ours](http://jenkins.terasology.io/testmaster/plugin/job-dsl/api-viewer/index.html) - there are also oodles of articles, guides, tutorials, etc online.

An important part of Job DSL and jobs in general is assigning stuff to the right node. All seed jobs are tied to the `master` label as that's where they need to run. For jenkins.terasology.io multiple build agents / labels will be available. In a scripted Jenkinsfile you can target multiple labels like so: `node ("default-java || heavy-java") { ...`

*Note:* If jobs are created via DSL that include system Groovy scripts (able to interact with Jenkins itself at an admin level, so hugely powerful) they'll need to be manually approved under Manage Jenkins once - this is an annoying "security" feature there doesn't appear to be an easy way to greenlight ahead of time despite the Job DSL stuff itself being at the admin-only level and OKed by being written by, well, admins.

### Job DSL Syntax Highlighting

Gradle bits have been included in an attempt to allow recognition of the Groovy DSL for Job DSL - see https://github.com/jenkinsci/job-dsl-plugin/wiki/IDE-Support

Import the Gradle project within this directory and cross your fingers! Introduced via https://github.com/MovingBlocks/InfraPlayground/pull/24

## Kaniko building

To run Docker builds within Jenkins (which spends up build agents using Docker) one approach is using Kaniko and one such agent has been prepared to build our _other_ custom agent images.

Note that to direct execution to the `kaniko` container you need a bit more Jenkinsfile magic:

```groovy
node("kaniko") {
    stage('Test Kaniko') {
        container('kaniko') {
            stage('Output') {
                sh '''
                df -ah
                ls -la /kaniko
                /kaniko/executor version
                '''
            }
        }
    }
}
```

Pre-cached Kaniko-based agents are built using https://github.com/MovingBlocks/JenkinsAgentPrecachedJava and have jobs made via DSL in the Utilities folder. See also the https://github.com/MovingBlocks/JenkinsAgentAndroid agent which is a little simpler (no parallel)
