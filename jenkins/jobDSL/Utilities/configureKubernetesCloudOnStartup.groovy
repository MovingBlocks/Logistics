job("Utilities/ConfigureKubernetesCloudOnStartup") {
    description('TODO! Configures a Kubernetes cloud with some pod templates for build agents. Meant to run on startup to make sure the config in Git is always current')
    label("main")
    scm {
        git {
            remote {
              url("...")
              credentials("...")
              branch("...")
            }
        }
    }
    triggers {
        // Jenkins restart trigger
        hudsonStartupTrigger {
            // Trigger based on the master
            label("main")
            // Seconds before running
            quietPeriod("10")
            // Not used but required as part of dynamic DSL (I think)
            nodeParameterName("main")
            // Run on initial connection
            runOnChoice("true")
        }
    }
    steps {
        systemGroovyCommand ('''
            ...
        ''')
    }
}
