// Make a folder for the module jobs - duplicated from `modules.groovy` so whichever gets here first makes the folder
def baseModuleFolderName = "Nanoware/TerasologyModules"
folder(baseModuleFolderName) {
    description("These folders are letter-based groupings of module jobs")
}

// Here then come the views
listView("$baseModuleFolderName/master") {
    description('All master branch jobs for Nanoware test org modules')
    recurse true
    jobs {
        regex('.*master.*')
    }
    /* Needs a plugin, might make some neat views later
    jobFilters {
        status {
            status(Status.FAILED)
        }
    } */
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

listView("$baseModuleFolderName/develop") {
    description('All develop branch jobs for Nanoware test org modules')
    recurse true
    jobs {
        regex('.*develop.*')
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

listView("$baseModuleFolderName/PRs") {
    description('All PR jobs for Nanoware test org modules')
    recurse true
    jobs {
        regex('.*PR.*')
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

listView("$baseModuleFolderName/Tutorials") {
    description('All tutorial modules for Nanoware test org')
    recurse true
    jobs {
        regex('.*Tutorial.*')
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

listView("$baseModuleFolderName/Unhealthy") {
    description('Unhealthy modules')
    recurse true
    jobs {
        regex('.*develop.*')
    }

    jobFilters {
        status {
            matchType(MatchType.EXCLUDE_UNMATCHED)
            status(Status.FAILED, Status.UNSTABLE)
        }
        // TODO: Another status filter that excludes disabled jobs? Probably fairly rare
    }

    columns {
        status()
        name()
        testResult(2)
        // static analysis - TODO, not regularly supported in Job DSL? Name: "# Issues", Type: "Total (severity high only)
        weather()
        lastSuccess()
        lastDuration()
        buildButton()
    }
}

// Omega & Iota dynamic views - TODO: Hook the job up to a trigger from the Index so the view will update each time the repo does?
List<String> moduleNamesForDistribution(distribution) {
    def url = new URL("https://raw.githubusercontent.com/Terasology/Index/master/distros/${distribution.toLowerCase()}/gradle.properties")
    def props = new java.util.Properties()
    url.newInputStream().withStream {
        props.load(it)
    }
    return props.getProperty("extraModules").tokenize(',')
}

void listViewForDistribution(folderName, distribution) {
    listView("$folderName/$distribution") {
        description("All develop branch jobs for modules in the $distribution set.")
        recurse true
        jobs {
            names(*moduleNamesForDistribution(distribution).collect { "${it[0]}/$it/develop" } )
        }
        columns {
            status()
            weather()
            name()
            testResultColumn { testResultFormat(1) }
            lastSuccess()
            lastFailure()
            lastDuration()
            buildButton()
        }
    }
}

listViewForDistribution(baseModuleFolderName, "Iota")
listViewForDistribution(baseModuleFolderName, "Omega")