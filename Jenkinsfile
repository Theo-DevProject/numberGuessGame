pipeline {
  agent any
  options { timestamps(); ansiColor('xterm') }
  tools { jdk 'JDK17'; maven 'Maven3' }

  parameters {
    // Nexus
    string(name: 'NEXUS_HOST', defaultValue: '18.232.94.39', description: 'Nexus host/IP')
    string(name: 'NEXUS_PORT', defaultValue: '8081', description: 'Nexus port')
    string(name: 'NEXUS_RELEASES_PATH', defaultValue: 'repository/maven-releases/', description: 'Releases path')
    string(name: 'NEXUS_SNAPSHOTS_PATH', defaultValue: 'repository/maven-snapshots/', description: 'Snapshots path')
    // Tomcat (optional)
    booleanParam(name: 'DEPLOY_TO_TOMCAT', defaultValue: true, description: 'Deploy after Nexus')
    string(name: 'TOMCAT_HOST', defaultValue: 'YOUR_TOMCAT_IP', description: 'Tomcat host/IP')
    string(name: 'TOMCAT_USER', defaultValue: 'ubuntu', description: 'SSH user on Tomcat box')
    string(name: 'TOMCAT_WEBAPPS', defaultValue: '/opt/tomcat/webapps', description: 'Tomcat webapps dir')
    string(name: 'TOMCAT_BIN', defaultValue: '/opt/tomcat/bin', description: 'Tomcat bin dir')
    string(name: 'APP_NAME', defaultValue: 'NumberGuessGame', description: 'WAR base name (no .war)')
    string(name: 'HEALTH_PATH', defaultValue: '/NumberGuessGame/guess', description: 'Health check path')
  }

  environment {
    NEXUS_BASE = "http://${params.NEXUS_HOST}:${params.NEXUS_PORT}"
    NEXUS_RELEASES_ID  = 'nexus-releases'
    NEXUS_SNAPSHOTS_ID = 'nexus-snapshots'
  }

  stages {
    stage('Checkout') { steps { checkout scm } }

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
      steps { sh 'mvn -B -s jenkins-settings.xml clean verify' }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
          archiveArtifacts artifacts: 'target/*.war', fingerprint: true, onlyIfSuccessful: false
        }
      }
    }

    stage('Deploy to Nexus') {
      // Only deploy from the testing branch for now
      when { branch 'features/theoDev' }
      steps {
        script {
          def version = sh(script: "mvn -q -Dexec.executable=echo -Dexec.args='\\${project.version}' --non-recursive exec:exec", returnStdout: true).trim()
          def isSnapshot = version.endsWith('-SNAPSHOT')
          def repoId  = isSnapshot ? env.NEXUS_SNAPSHOTS_ID : env.NEXUS_RELEASES_ID
          def repoUrl = isSnapshot ? "${env.NEXUS_BASE}/${params.NEXUS_SNAPSHOTS_PATH}" : "${env.NEXUS_BASE}/${params.NEXUS_RELEASES_PATH}"
          echo "Version: ${version} (snapshot? ${isSnapshot}) → ${repoId}"

          sh """
            mvn -B -s jenkins-settings.xml -DskipTests deploy \
              -DaltDeploymentRepository=${repoId}::default::${repoUrl}
          """
        }
      }
    }

    stage('Deploy to Tomcat (SSH)') {
      // Only from testing branch + controlled by checkbox
      when { allOf { branch 'features/theoDev'; expression { return params.DEPLOY_TO_TOMCAT } } }
      steps {
        sshagent(credentials: ['tomcat_ssh']) {
          sh '''
            set -e
            WAR=$(ls target/*.war | head -n1)
            [ -f "$WAR" ] || { echo "WAR not found"; exit 1; }

            scp -o StrictHostKeyChecking=no "$WAR" ${TOMCAT_USER}@${TOMCAT_HOST}:/home/${TOMCAT_USER}/${APP_NAME}.war

            ssh -o StrictHostKeyChecking=no ${TOMCAT_USER}@${TOMCAT_HOST} bash -lc '
              sudo rm -rf ${TOMCAT_WEBAPPS}/${APP_NAME} ${TOMCAT_WEBAPPS}/${APP_NAME}.war
              sudo mv /home/${TOMCAT_USER}/${APP_NAME}.war ${TOMCAT_WEBAPPS}/
              if [ -x ${TOMCAT_BIN}/shutdown.sh ]; then
                sudo ${TOMCAT_BIN}/shutdown.sh || true
                sleep 3
                sudo ${TOMCAT_BIN}/startup.sh
              else
                sudo systemctl restart tomcat || sudo systemctl restart tomcat9 || true
              fi
            '

            echo "Waiting for deploy..."; sleep 8
            curl -fsS "http://${TOMCAT_HOST}:8080${HEALTH_PATH}" | head -c 400 || true
          '''
        }
      }
    }
  }

  post {
    success { echo '✅ Pipeline finished successfully' }
    failure { echo '❌ Pipeline failed — check logs' }
  }
}
