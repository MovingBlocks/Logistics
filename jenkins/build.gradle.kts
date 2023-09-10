// This doesn't build anything yet, but at least helps IntelliJ recognize the groovy DSL
// adapted from https://github.com/jenkinsci/job-dsl-plugin/wiki/IDE-Support

import de.undercouch.gradle.tasks.download.Download
import java.net.URI

plugins {
    groovy  // job-dsl scripts are Groovy
    `java-library`  // for API and implementation separation.
    id("de.undercouch.download").version("4.1.2")  // to download dsl definition
}

repositories {
    mavenCentral()

    maven {
        name = "Jenkins Plugins"
        url = URI("https://repo.jenkins-ci.org/public/")
    }
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:2.4.12") {
        because("Groovy version of current Jenkins installation")
    }

    implementation("org.jenkins-ci.plugins:job-dsl-core:1.77")

    // TODO: make some kind of test suite
    testImplementation(platform("org.junit:junit-bom:5.7.2")) {
        // junit-bom will set version numbers for the other org.junit dependencies.
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
}

sourceSets {
    main {
        groovy.srcDir("jobDSL")
    }
}

tasks.register<Download>("pipelineGdsl") {
    description = "Fetch the GDSL definition for Pipelines."
    // Caveat: If this is just for pipelines (Jenkinsfile), I'm not sure it is useful
    // for this repo with JobDSL scripts.

    src("https://jenkins.terasology.io/pipeline-syntax/gdsl")
    dest(file("jobDSL/pipeline-syntax.gdsl"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
