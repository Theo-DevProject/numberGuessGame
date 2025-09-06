pipeline {
  agent any
  options { timestamps() }
  tools { jdk 'Java'; maven 'Maven' }

  // Auto-build on GitHub push (webhook must point to /github-webhook/)
  triggers { githubPush() }

  parameters {
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
  }

  environment {
    TARGET_BRANCH      = 'main'  // build/deploy only from main
    NEXUS_BASE         = "http://${params.NEXUS_HOST}:${params.NEXUS_PORT}"
    NEXUS_RELEASES_ID  = 'nexus-releases'
    NEXUS_SNAPSHOTS_ID = 'nexus-snapshots'
    SONAR_AUTH_TOKEN   = credentials('sonar_token') // Secret Text in Jenkins
  }

  stages {
    // 🔒 Pin checkout to main (or manual override)
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

    // --------- CHANGED: deploy WAR downloaded from Nexus ----------
    stage('Deploy WAR to Tomcat (from Nexus)') {
      when {
        allOf {
          expression { env.CURRENT_BRANCH == env.TARGET_BRANCH }
          expression { params.DEPLOY_TO_TOMCAT }
        }
      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'nexus_creds', usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {
          sshagent(credentials: ['tomcat_ssh']) {
            script {
              // Resolve Maven coordinates
              def version    = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.version", returnStdout: true).trim()
              def artifactId = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.artifactId", returnStdout: true).trim()
              def groupId    = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.groupId", returnStdout: true).trim()
              def gpath      = groupId.replace('.', '/')
              def isSnap     = version.endsWith('-SNAPSHOT')
              def baseRepo   = isSnap ? "${env.NEXUS_BASE}/${params.NEXUS_SNAPSHOTS_PATH}"
                                      : "${env.NEXUS_BASE}/${params.NEXUS_RELEASES_PATH}"
              def warName    = "${artifactId}-${version}.war"
              def warUrl     = "${baseRepo}${gpath}/${artifactId}/${version}/${warName}"

              echo "Downloading WAR from Nexus: ${warUrl}"
              sh """
                set -e
                curl -fSL -u "${NUSER}:${NPASS}" -o ${env.APP_NAME}.war "${warUrl}"
                echo "Copying WAR to ${params.TOMCAT_HOST} ..."
                scp -o StrictHostKeyChecking=no ${env.APP_NAME}.war ${params.TOMCAT_USER}@${params.TOMCAT_HOST}:/home/${params.TOMCAT_USER}/${env.APP_NAME}.war
              """

              // safer remote script (no 'bash -lc' quoting issues)
              sh """
                ssh -o StrictHostKeyChecking=no ${params.TOMCAT_USER}@${params.TOMCAT_HOST} <<'REMOTE'
                set -e
                sudo rm -rf ${params.TOMCAT_WEBAPPS}/${env.APP_NAME} ${params.TOMCAT_WEBAPPS}/${env.APP_NAME}.war || true
                sudo mv /home/${params.TOMCAT_USER}/${env.APP_NAME}.war ${params.TOMCAT_WEBAPPS}/

                if [ -x ${params.TOMCAT_BIN}/shutdown.sh ]; then
                  sudo ${params.TOMCAT_BIN}/shutdown.sh || true
                  sleep 3
                  sudo ${params.TOMCAT_BIN}/startup.sh
                else
                  if systemctl list-unit-files | grep -q '^tomcat\\.service'; then
                    sudo systemctl restart tomcat || true
                  elif systemctl list-unit-files | grep -q '^tomcat9\\.service'; then
                    sudo systemctl restart tomcat9 || true
                  fi
                fi
REMOTE
              """
            }
          }
        }
      }
    }
    // --------------------------------------------------------------

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
    always { echo 'Pipeline finished.' }
  }
}
