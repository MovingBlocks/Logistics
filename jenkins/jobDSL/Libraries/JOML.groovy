pipelineJob('Libraries/JOML') {
    description("Builds our fork of JOML purely for early convenience releases while waiting on upstream. Note: Only builds main branch and doesn't trigger automatically")

    definition {
        cps {
            script("""
pipeline {
    agent {
        label "light-java"
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', credentialsId: 'github-app-terasology-jenkins-io', url: 'https://github.com/MovingBlocks/JOML.git'
                sh 'chmod +x mvnw'
            }
        }

        stage('BuildAndPublish') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'artifactory-gooey', usernameVariable: 'artifactoryUser', passwordVariable: 'artifactoryPass')]) {
                    withEnv(["SONATYPE_USER=\$artifactoryUser", "SONATYPE_PASS=\$artifactoryPass"]) {
                        sh 'printenv'

                        // Do some text wrangling to redirect publishing to our Artifactory without modifying the repo
                        //sh 'cat .travis/settings.xml'
                        sh "sed -i 's,<id>oss.sonatype.org</id>,<id>artifactory.terasology.org</id><url>artifactory.terasology.org</url>,' .travis/settings.xml"
                        //sh 'cat .travis/settings.xml'

                        //sh 'cat pom.xml'
                        sh "sed -i 's,https://oss.sonatype.org/content/repositories/snapshots,http://artifactory.terasology.org/artifactory/libs-snapshot-local,' pom.xml"
                        sh "sed -i 's,https://oss.sonatype.org/service/local/staging/deploy/maven2,http://artifactory.terasology.org/artifactory/libs-snapshot-local,' pom.xml"
                        sh "sed -i 's,oss.sonatype.org,artifactory.terasology.org,' pom.xml"
                        //sh 'cat pom.xml'

                        sh './mvnw deploy --settings .travis/settings.xml -Dmaven.test.skip=true'
                    }
                }
            }
        }
    }
}
            """)
            sandbox()
        }
    }
}
