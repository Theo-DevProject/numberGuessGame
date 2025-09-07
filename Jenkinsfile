pipeline {
  agent any
  options { timestamps() }
  tools { jdk 'Java'; maven 'Maven' }

  // Auto-build on GitHub push (webhook must point to /github-webhook/)
  triggers { githubPush() }

  parameters {
    // Build branch (optional one-off override)
    string(name: 'BRANCH_OVERRIDE', defaultValue: '', description: 'Manually override branch (normally leave blank)')

    // Nexus
    string(name: 'NEXUS_HOST',           defaultValue: '52.21.103.235', description: 'Nexus host/IP')
    string(name: 'NEXUS_PORT',           defaultValue: '8081',          description: 'Nexus port')
    string(name: 'NEXUS_RELEASES_PATH',  defaultValue: 'repository/maven-releases/',  description: 'Releases path')
    string(name: 'NEXUS_SNAPSHOTS_PATH', defaultValue: 'repository/maven-snapshots/', description: 'Snapshots path')

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

    // Email
    string(name: 'EMAIL_TO', defaultValue: 'tijiebor@gmail.com', description: 'Build notifications recipients (comma separated)')
  }

  environment {
    TARGET_BRANCH      = 'main'  // build/deploy only from main
    NEXUS_BASE         = "http://${params.NEXUS_HOST}:${params.NEXUS_PORT}"
    NEXUS_RELEASES_ID  = 'nexus-releases'
    NEXUS_SNAPSHOTS_ID = 'nexus-snapshots'
    SONAR_AUTH_TOKEN   = credentials('sonar_token') // Secret Text in Jenkins
  }

  stages {
    // Pin checkout to main unless overridden
    stage('Checkout SCM') {
      steps {
        script {
          def branchToBuild = (params.BRANCH_OVERRIDE?.trim()) ? params.BRANCH_OVERRIDE.trim() : env.TARGET_BRANCH
          git branch: branchToBuild,
              url: 'https://github.com/Theo-DevProject/numberGuessGame.git'
          env.CURRENT_BRANCH = branchToBuild
          echo "Checked out branch: ${env.CURRENT_BRANCH}"
        }
      }
    }

    stage('Tool Install') { steps { sh 'java -version && mvn -v' } }

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

    stage('Build & Test') {
      steps {
        sh 'mvn -B -s jenkins-settings.xml clean verify'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
          archiveArtifacts artifacts: 'target/*.war, target/site/jacoco/*.xml', allowEmptyArchive: true, fingerprint: true
        }
      }
    }

    stage('Static Analysis (SonarQube)') {
      steps {
        withSonarQubeEnv('sonarqube') {
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
      when { expression { env.CURRENT_BRANCH == env.TARGET_BRANCH } }
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
      when { expression { env.CURRENT_BRANCH == env.TARGET_BRANCH } }
      steps {
        sh 'curl -fsSI "${NEXUS_BASE}/service/rest/v1/status" > /dev/null && echo "✅ Nexus is reachable"'
      }
    }

    // --- NEW: prove SSH key works before deploy
    stage('Preflight SSH to Tomcat') {
      when { expression { env.CURRENT_BRANCH == env.TARGET_BRANCH && params.DEPLOY_TO_TOMCAT } }
      steps {
        sshagent(credentials: ['tomcat_ssh']) {
          sh 'ssh -o StrictHostKeyChecking=no ${TOMCAT_USER}@${TOMCAT_HOST} "echo SSH_OK && whoami && hostname"'
        }
      }
    }

    stage('Deploy WAR to Tomcat (from Nexus)') {
      when {
        allOf {
          expression { env.CURRENT_BRANCH == env.TARGET_BRANCH }
          expression { params.DEPLOY_TO_TOMCAT }
        }
      }
      steps {
        script {
          // Resolve GAV from the POM of this project
          def version    = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.version",     returnStdout: true).trim()
          def artifactId = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.artifactId", returnStdout: true).trim()
          def groupId    = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.groupId",    returnStdout: true).trim()
          def isSnapshot = version.endsWith('-SNAPSHOT')

          // Pick the right Nexus repo URL (strip any trailing slash in the path)
          def releasesPath  = params.NEXUS_RELEASES_PATH.replaceAll('/+$','')
          def snapshotsPath = params.NEXUS_SNAPSHOTS_PATH.replaceAll('/+$','')
          def repoUrl       = isSnapshot ? "${env.NEXUS_BASE}/${snapshotsPath}" : "${env.NEXUS_BASE}/${releasesPath}"

          echo "Resolving ${groupId}:${artifactId}:${version}:war from ${repoUrl} ..."
          // Use maven-dependency-plugin to copy the WAR locally from Nexus
          sh """
            mvn -B -q org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy \
              -DremoteRepositories=nexus::::${repoUrl} \
              -Dartifact=${groupId}:${artifactId}:${version}:war \
              -DoutputDirectory=. \
              -Dtransitive=false
          """
          def warName = "${artifactId}-${version}.war"
          echo "Downloaded WAR: ${warName}"

          // SCP then deploy with sshagent, using a here-doc for the remote script
          sshagent(credentials: ['tomcat_ssh']) {
            sh """
              set -e
              echo "Copying ${warName} to ${params.TOMCAT_HOST} ..."
              scp -o StrictHostKeyChecking=no "${warName}" ${params.TOMCAT_USER}@${params.TOMCAT_HOST}:/home/${params.TOMCAT_USER}/${params.APP_NAME}.war

              echo "Restarting Tomcat and deploying WAR ..."
              ssh -o StrictHostKeyChecking=no ${params.TOMCAT_USER}@${params.TOMCAT_HOST} 'bash -s' <<EOF
set -euo pipefail
T_WEBAPPS="${params.TOMCAT_WEBAPPS}"
T_BIN="${params.TOMCAT_BIN}"
APP="${params.APP_NAME}"

if [ -z "\$T_WEBAPPS" ] || [ -z "\$T_BIN" ] || [ -z "\$APP" ]; then
  echo 'One or more required vars empty (T_WEBAPPS/T_BIN/APP)'
  exit 1
fi
case "\$T_WEBAPPS" in
  /|/root|'') echo 'Refusing to operate on unsafe webapps path'; exit 1 ;;
esac

sudo rm -rf "\$T_WEBAPPS/\$APP" "\$T_WEBAPPS/\$APP.war" || true
sudo mv /home/${params.TOMCAT_USER}/${params.APP_NAME}.war "\$T_WEBAPPS/"

if [ -x "\$T_BIN/shutdown.sh" ]; then
  sudo "\$T_BIN/shutdown.sh" || true
  sleep 3
  sudo "\$T_BIN/startup.sh"
else
  sudo systemctl restart tomcat || sudo systemctl restart tomcat9 || true
fi
EOF
            """
          }
        }
      }
    }

    stage('Health Check') {
      when {
        allOf {
          expression { env.CURRENT_BRANCH == env.TARGET_BRANCH }
          expression { params.DEPLOY_TO_TOMCAT }
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
    always  { echo 'Pipeline finished.' }

    success {
      emailext(
        subject: "[SUCCESS] ${env.JOB_NAME} #${env.BUILD_NUMBER}",
        to: params.EMAIL_TO,
        body: """<p>✅ Build succeeded.</p>
                 <p>Job: ${env.JOB_NAME} #${env.BUILD_NUMBER}</p>
                 <p>Branch: ${env.CURRENT_BRANCH}</p>
                 <p>URL: ${env.BUILD_URL}</p>""",
        mimeType: 'text/html'
      )
    }

    failure {
      emailext(
        subject: "[FAILED] ${env.JOB_NAME} #${env.BUILD_NUMBER}",
        to: params.EMAIL_TO,
        body: """<p>❌ Build failed.</p>
                 <p>Job: ${env.JOB_NAME} #${env.BUILD_NUMBER}</p>
                 <p>Branch: ${env.CURRENT_BRANCH}</p>
                 <p>URL: ${env.BUILD_URL}</p>""",
        mimeType: 'text/html'
      )
    }
  }
}
