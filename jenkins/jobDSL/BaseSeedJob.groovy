folder("Terasology") {
    description("These jobs are for the Terasology game project itself, rather than The Terasology Foundation org level stuff")
}

folder("DestinationSol") {
    description("This folder holds jobs for our Destination Sol video game")
}

folder("Nanoware") {
    description("This is a test folder / org for experimenting on our infrastructure like Jenkins itself. It has special props set on the Folder that apply to all jobs under it.")
    properties {
        envVarsFolderProperty {
            // These redirect Nanoware jobs to its variant repos in Artifactory, if the build scripts go looking for these vars anyway. TODO: Switch to .io and fix https://github.com/MovingBlocks/Logistics/issues/7
            properties("PUBLISH_ORG=nanoware\nRESOLUTION_REPO=http://artifactory.terasology.org/artifactory/virtual-nanoware-and-remote")
        }
    }
}

folder("Libraries") {
    description("This folder contains game-agnostic libraries, frameworks, and the like")
}

folder("Utilities") {
    description("This folder contains general utilities, stuff used for configuring Jenkins itself and other infrastructure, etc")
}

folder("Utilities/Docker") {
    description("This folder contains utility jobs related to Docker")
}

folder("Experimental") {
    description("Temporary testing and other hijinks live here. Nothing should be intended as permanent")
}

// For all the non-experimental folders we make seed jobs inside the Utilities folder
def folderList = ["Terasology", "DestinationSol", "Nanoware", "Libraries", "Utilities"]
folderList.each { folderName ->
    println "Working on folder $folderName"
    job("Utilities/${folderName}SeedJob") {
        description("This seed job manages its folder based on an infra repo with DSL scripts")
        label("master")
        scm {
            git {
                remote {
                    url("https://github.com/MovingBlocks/InfraPlayground.git")
                    credentials("github-app-terasology-jenkins-io")
                }
                branch("master")
            }
        }
        steps {
            dsl {
                external("jenkins/jobDSL/$folderName/*.groovy")
            }
        }
    }
}
