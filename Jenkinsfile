pipeline {
  agent any
  options { timestamps() }
  tools { jdk 'Java'; maven 'Maven' }

  // Auto-build when GitHub sends a push webhook
  triggers { githubPush() }

  parameters {
    // Optional manual override (normally leave blank)
    string(name: 'BRANCH_OVERRIDE', defaultValue: '', description: 'Override branch name if Jenkins can’t detect it')

    // Nexus
    string(name: 'NEXUS_HOST',           defaultValue: '52.21.103.235', description: 'Nexus host/IP')
    string(name: 'NEXUS_PORT',           defaultValue: '8081',          description: 'Nexus port')
    string(name: 'NEXUS_RELEASES_PATH',  defaultValue: 'repository/maven-releases/',   description: 'Releases path')
    string(name: 'NEXUS_SNAPSHOTS_PATH', defaultValue: 'repository/maven-snapshots/',  description: 'Snapshots path')

    // SonarQube
    string(name: 'SONAR_HOST_URL', defaultValue: 'http://52.72.165.200:9000', description: 'SonarQube URL')

    // Tomcat
    booleanParam(name: 'DEPLOY_TO_TOMCAT', defaultValue: true, description: 'Deploy after Nexus')
    string(name: 'TOMCAT_HOST',    defaultValue: '54.236.97.199',                 description: 'Tomcat host/IP')
    string(name: 'TOMCAT_USER',    defaultValue: 'ubuntu',                        description: 'SSH user on Tomcat box')
    string(name: 'TOMCAT_WEBAPPS', defaultValue: '/opt/apache-tomcat-10.1.44/webapps', description: 'Tomcat webapps dir')
    string(name: 'TOMCAT_BIN',     defaultValue: '/opt/apache-tomcat-10.1.44/bin',     description: 'Tomcat bin dir')
    string(name: 'APP_NAME',       defaultValue: 'NumberGuessGame',               description: 'WAR base name (no .war)')
    string(name: 'HEALTH_PATH',    defaultValue: '/NumberGuessGame/guess',        description: 'Health check path')

    // Email recipients (comma-separated)
    string(name: 'NOTIFY_TO', defaultValue: 'you@example.com', description: 'Emails for notifications')
  }

  environment {
    // Nexus
    NEXUS_BASE         = "http://${params.NEXUS_HOST}:${params.NEXUS_PORT}"
    NEXUS_RELEASES_ID  = 'nexus-releases'
    NEXUS_SNAPSHOTS_ID = 'nexus-snapshots'

    // SonarQube token stored as a Secret Text credential in Jenkins
    SONAR_AUTH_TOKEN   = credentials('sonar_token')
  }

  stages {
    stage('Checkout SCM') {
      steps { checkout scm }
    }

    stage('Tool Install') {
      steps { sh 'java -version && mvn -v' }
    }

    stage('Detect branch') {
      steps {
        script {
          def override = (params.BRANCH_OVERRIDE ?: '').trim()
          // Try several ways to get the branch name even on detached HEAD
          def shortRef  = sh(script: "git symbolic-ref -q --short HEAD || true", returnStdout: true).trim()
          def fromNote  = sh(script: "git name-rev --name-only HEAD | sed 's#^remotes/origin/##' || true", returnStdout: true).trim()
          def fromList  = sh(script: "git branch -r --contains HEAD | sed -n 's#^[ *]*origin/##p' | head -n1 || true", returnStdout: true).trim()
          env.CURRENT_BRANCH = override ?: (shortRef ?: (fromNote ?: (fromList ?: 'unknown')))
          echo "Resolved branch: ${env.CURRENT_BRANCH}"
        }
      }
    }

    stage('Generate Maven settings.xml') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'nexus_creds', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
          writeFile file: 'jenkins-settings.xml', text: """
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server><id>${env.NEXUS_RELEASES_ID}</id><username>${NEXUS_USER}</username><password>${NEXUS_PASS}</password></server>
    <server><id>${env.NEXUS_SNAPSHOTS_ID}</id><username>${NEXUS_USER}</username><password>${NEXUS_PASS}</password></server>
  </servers>
</settings>
""".trim()
        }
      }
    }

    stage('Build, Test, Coverage') {
      steps {
        sh 'mvn -B -s jenkins-settings.xml clean verify'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
          // Requires "JaCoCo" plugin in Jenkins for the graph
          publishJacoco(
            execPattern: '**/target/jacoco.exec',
            classPattern: '**/target/classes',
            sourcePattern: '**/src/main/java',
            inclusionPattern: '',
            exclusionPattern: ''
          )
          archiveArtifacts artifacts: 'target/*.war', fingerprint: true, onlyIfSuccessful: false
        }
      }
    }

    stage('Static Analysis (SonarQube)') {
      steps {
        withSonarQubeEnv('sonarqube') {
          // Use sonar.token (not deprecated sonar.login)
          sh """
            mvn -B -s jenkins-settings.xml -DskipTests sonar:sonar \
              -Dsonar.projectKey=com.studentapp:NumberGuessGame \
              -Dsonar.projectName=NumberGuessGame \
              -Dsonar.host.url=${SONAR_HOST_URL} \
              -Dsonar.token=${SONAR_AUTH_TOKEN}
          """
        }
      }
    }

    stage('Quality Gate') {
      steps { timeout(time: 10, unit: 'MINUTES') { waitForQualityGate abortPipeline: true } }
    }

    stage('Deploy to Nexus') {
      when { expression { return env.CURRENT_BRANCH in ['features/theoDev', 'main', 'master'] } }
      steps {
        script {
          def version    = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.version", returnStdout: true).trim()
          def isSnapshot = version.endsWith('-SNAPSHOT')
          def repoId     = isSnapshot ? env.NEXUS_SNAPSHOTS_ID : env.NEXUS_RELEASES_ID
          def repoUrl    = isSnapshot ? "${env.NEXUS_BASE}/${params.NEXUS_SNAPSHOTS_PATH}"
                                      : "${env.NEXUS_BASE}/${params.NEXUS_RELEASES_PATH}"
          echo "Version: ${version} (snapshot? ${isSnapshot}) → ${repoId} @ ${repoUrl}"

          sh """
            mvn -B -s jenkins-settings.xml -DskipTests deploy \
              -DaltDeploymentRepository=${repoId}::default::${repoUrl}
          """
        }
      }
    }

    stage('Nexus Sanity Check') {
      when { expression { return env.CURRENT_BRANCH in ['features/theoDev', 'main', 'master'] } }
      steps {
        sh 'curl -fsSI "${NEXUS_BASE}/service/rest/v1/status" > /dev/null && echo "✅ Nexus is reachable"'
      }
    }

    stage('Deploy WAR to Tomcat') {
      when {
        allOf {
          expression { return env.CURRENT_BRANCH in ['features/theoDev', 'main', 'master'] }
          expression { return params.DEPLOY_TO_TOMCAT }
        }
      }
      steps {
        sshagent(credentials: ['tomcat_ssh']) {
          sh """
            set -e
            WAR=\$(ls target/*.war | head -n1)
            [ -f "\${WAR}" ] || { echo 'WAR not found'; exit 1; }

            echo "Copying \${WAR} to ${TOMCAT_HOST} ..."
            scp -o StrictHostKeyChecking=no "\${WAR}" ${TOMCAT_USER}@${TOMCAT_HOST}:/home/${TOMCAT_USER}/${APP_NAME}.war

            echo "Restarting Tomcat and deploying WAR ..."
            ssh -o StrictHostKeyChecking=no ${TOMCAT_USER}@${TOMCAT_HOST} bash -lc '
              set -e
              sudo rm -rf ${TOMCAT_WEBAPPS}/${APP_NAME} ${TOMCAT_WEBAPPS}/${APP_NAME}.war || true
              sudo mv /home/${TOMCAT_USER}/${APP_NAME}.war ${TOMCAT_WEBAPPS}/
              if [ -x ${TOMCAT_BIN}/shutdown.sh ]; then
                sudo ${TOMCAT_BIN}/shutdown.sh || true
                sleep 3
                sudo ${TOMCAT_BIN}/startup.sh
              else
                sudo systemctl restart tomcat || sudo systemctl restart tomcat9 || true
              fi
            '
          """
        }
      }
    }

    stage('Health Check') {
      when {
        allOf {
          expression { return env.CURRENT_BRANCH in ['features/theoDev', 'main', 'master'] }
          expression { return params.DEPLOY_TO_TOMCAT }
        }
      }
      steps {
        sh """
          set -e
          echo 'Checking health at http://${TOMCAT_HOST}:8080${HEALTH_PATH}'
          for i in \$(seq 1 12); do
            if curl -fsS "http://${TOMCAT_HOST}:8080${HEALTH_PATH}" | head -c 200; then
              echo '✅ Health check passed'; exit 0
            fi
            echo "⏳ App not ready yet... retry \$i/12"; sleep 5
          done
          echo '❌ Health check failed after 60s'; exit 1
        """
      }
    }
  }

  post {
    success {
      script {
        emailext(
          subject: "✅ SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
          to: params.NOTIFY_TO,
          mimeType: 'text/html',
          body: """
            <h2>Build Succeeded</h2>
            <p><b>Job:</b> ${env.JOB_NAME} #${env.BUILD_NUMBER}</p>
            <p><b>Branch:</b> ${env.CURRENT_BRANCH}</p>
            <p><a href="${env.BUILD_URL}">Open build</a></p>
            <hr/>
            <p>Test results and coverage graphs are available in the build page.</p>
          """
        )
      }
    }
    failure {
      script {
        emailext(
          subject: "❌ FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
          to: params.NOTIFY_TO,
          mimeType: 'text/html',
          attachmentsPattern: '**/target/surefire-reports/*.xml',
          body: """
            <h2>Build Failed</h2>
            <p><b>Job:</b> ${env.JOB_NAME} #${env.BUILD_NUMBER}</p>
            <p><b>Branch:</b> ${env.CURRENT_BRANCH}</p>
            <p><a href="${env.BUILD_URL}console">Console log</a></p>
            <hr/>
            <p>JUnit XML attached (if available).</p>
          """
        )
      }
    }
    always { echo 'Pipeline finished.' }
  }
}
