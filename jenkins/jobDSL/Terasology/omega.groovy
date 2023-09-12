multibranchPipelineJob('Terasology/Omega') {
    description("Repackages the engine zip for Terasology to include the Omega lineup of modules")
    branchSources {
        github {
            id('omegamultiengineterasology') // IMPORTANT: use a constant and unique identifier
            repoOwner('Terasology')
            repository('Index')
            checkoutCredentialsId('gh-app-terasology')
            scanCredentialsId('gh-app-terasology')
            buildOriginPRMerge(true) // We want to build PRs in the origin, merge with base branch
            buildOriginBranchWithPR(false) // We don't keep both an origin branch and a PR from it
            buildForkPRHead(false) // We only build forks merged into the base branch
        }
    }
    factory {
        workflowBranchProjectFactory {
            scriptPath('distros/Jenkinsfile')
        }
    }
    orphanedItemStrategy {
        defaultOrphanedItemStrategy {
            pruneDeadBranches(true)
            daysToKeepStr("1") // Only keep orphaned PRs and branches for a day (yes, requires a String)
            numToKeepStr("1") // Only keep 1 of the orphans? Unsure what this means exactly
        }
    }
    triggers {
        periodicFolderTrigger {
            interval("1440")  // Scan for folder updates once a day, otherwise may only trigger when jobs are re-saved
        }
    }

    // The strategy block can be hard to hit on a GitHub branch source. This seems to do the trick
    // The trick specifically: Do *not* Git Trigger (but *do* create a job) for the master branch, exclusively
    configure {
        it / sources / 'data' / 'jenkins.branch.BranchSource' << {

            strategy(class: 'jenkins.branch.NamedExceptionsBranchPropertyStrategy') {
                defaultProperties(class: 'empty-list')
                namedExceptions(class: 'java.util.Arrays\$ArrayList') {
                    a(class: 'jenkins.branch.NamedExceptionsBranchPropertyStrategy\$Named-array') {
                        'jenkins.branch.NamedExceptionsBranchPropertyStrategy_-Named'() {
                            props(class: 'java.util.Arrays\$ArrayList') {
                                a(class: 'jenkins.branch.BranchProperty-array') {
                                    'jenkins.branch.NoTriggerBranchProperty'()
                                }
                            }
                            name('master')
                        }
                    }
                }
            }
        }
    }
}
