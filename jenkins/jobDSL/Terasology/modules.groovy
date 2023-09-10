// Make a folder for the module jobs
def baseModuleFolderName = "Terasology/Modules"
folder(baseModuleFolderName) {
    description("These folders are letter-based groupings of module jobs")
}

// Make a folder for the module *seed* jobs
def seedModuleFolderName = baseModuleFolderName + "SeedJobs"
folder(seedModuleFolderName) {
    description("These seed jobs are to allow for more granular throttling of module job creation and maintenance")
}

// Iterate over the standard latin alphabet to make letter-based seed jobs. Could perhaps expand later if needed
// Reason is too many jobs in one folder slows things down. If needed ABCmCzDE... could probably split several per letter (may lose job history tho)
char[] letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
for (char letter : letters) {
    def seedModuleFolderNameLetter = seedModuleFolderName + "/seed$letter"
    println "Making seed job for $letter at $seedModuleFolderNameLetter"

    // We actually make a seed job for each of the actual jobs, to allow throttling (don't create all and org-scan at once)
    job(seedModuleFolderNameLetter) {
        description("This seed job creates one associated letter-based org folders containing game modules. By making one seed job for each letter we can throttle the amount of jobs being created a bit")
        label("master")
        steps {
            dsl {
                // The embedded job below is a bit more complicated due to the usage of the Jenkins Templating Engine, which may not be explicitly supported by Job DSL yet
                // Essentially we replicate the XML structure of a manually made job using a configure block. To get raw xml append /config.xml at the end of a job
                def seedJobDSL = """
githubOrg = "Terasology"
credId = "gh-app-terasology"
githubApiUri = "https://api.github.com"
orgPipelineRepo = "https://github.com/MovingBlocks/ModuleJteConfig.git"
libraryRepo = "https://github.com/MovingBlocks/ModuleJteLibraries.git"

organizationFolder "$baseModuleFolderName/$letter", {
    // TODO: May need to tweak the cleanup config
    description "Build jobs for Terasology modules"
    displayName "$letter"

    triggers {
        periodic(24 * 60)  // Scan for folder updates once a day, otherwise may only trigger when jobs are re-saved
    }
    
    organizations {
        github {
            credentialsId credId
            repoOwner githubOrg
            apiUri githubApiUri

            traits {
                gitHubBranchDiscovery {
                    // Build branches from origin
                    strategyId(1)
                }
                gitHubPullRequestDiscovery {
                    // Build PRs merged into HEAD first
                    strategyId(1)
                }
                // Subtle note: "source" here relates to filtering the project repos. The "head" variant apply to *within* the repo and doesn't filter repos
                sourceRegexFilter {
                    // Filter repos covered via letter in regex
                    regex("${letter}.*")
                }
            }
        }
    }
    
    // See https://issues.jenkins-ci.org/browse/JENKINS-60874 - the fork trait doesn't work yet due to issues. So we just add it in this way
    configure {
        it / 'navigators' / 'org.jenkinsci.plugins.github__branch__source.GitHubSCMNavigator' / 'traits' << {
            'org.jenkinsci.plugins.github__branch__source.ForkPullRequestDiscoveryTrait' {
                strategyId(1)
                // We trust ALL forks for modules - because the build system doesn't trust modules anyway and imports build scripty bits from elsewhere
                trust(class: 'org.jenkinsci.plugins.github_branch_source.ForkPullRequestDiscoveryTrait\$TrustEveryone')
            }
        }
    }

    // We fall back on using a configure job for the JTE pieces as those aren't fully ready for DSL yet
    configure {
        // This block configures the JTE specific resources - the pipeline config + library config
        it / 'properties' << 'org.boozallen.plugins.jte.init.governance.TemplateConfigFolderProperty' {
            tier {
                configurationProvider (class: 'org.boozallen.plugins.jte.init.governance.config.ScmPipelineConfigurationProvider') {
                    scm(class: 'hudson.plugins.git.GitSCM') {
                        configVersion 2
                        userRemoteConfigs {
                            'hudson.plugins.git.UserRemoteConfig' {
                                url orgPipelineRepo
                                credentialsId credId
                            }
                        }
                        branches {
                            'hudson.plugins.git.BranchSpec' {
                                name "develop"
                            }
                        }
                    }
                }
            }
        }
    }
    
    // These two blocks awkwardly deal with 'projectFactories' { whatever } failing to an ambiguous Node/String thing, despite that working elsewhere
    configure {
        // This blanks out the existing 'projectFactories' node (with Jenkinsfile default) since we for some reason can't replace it via normal syntax
        def oldFactoryNode = it / 'projectFactories' / 'org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectFactory'
        oldFactoryNode.replaceNode {}
    }
  
    configure {
        // This then adds the JTE factory, as for some reason the << {...} to append works just fine, but {} to replace doesn't
        it / 'projectFactories' << 'org.boozallen.plugins.jte.job.TemplateMultiBranchProjectFactory' {
            filterBranches(false)
        }
    }
}
                """
                text(seedJobDSL)
            }
        }
    }
}
