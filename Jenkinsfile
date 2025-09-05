pipeline {
  agent any
  options { timestamps() }
  tools { jdk 'Java'; maven 'Maven' }

  parameters {
    // ===== Nexus =====
    string(name: 'NEXUS_HOST',            defaultValue: '52.21.103.235', description: 'Nexus host/IP (Elastic IP)')
    string(name: 'NEXUS_PORT',            defaultValue: '8081',          description: 'Nexus port')
    string(name: 'NEXUS_RELEASES_PATH',   defaultValue: 'repository/maven-releases/',  description: 'Releases path')
    string(name: 'NEXUS_SNAPSHOTS_PATH',  defaultValue: 'repository/maven-snapshots/', description: 'Snapshots path')
    booleanParam(name: 'DEPLOY_TO_NEXUS', defaultValue: true, description: 'Deploy artifacts to Nexus')

    // ===== Tomcat (webServer) =====
    booleanParam(name: 'DEPLOY_TO_TOMCAT', defaultValue: true, description: 'Deploy WAR to Tomcat after Nexus')
    string(name: 'TOMCAT_HOST',   defaultValue: '54.236.97.199',                           description: 'Tomcat host/IP (Elastic IP)')
    string(name: 'TOMCAT_USER',   defaultValue: 'ubuntu',                                   description: 'SSH user on Tomcat')
    string(name: 'TOMCAT_WEBAPPS',defaultValue: '/opt/apache-tomcat-10.1.44/webapps',       description: 'Tomcat webapps dir')
    string(name: 'TOMCAT_BIN',    defaultValue: '/opt/apache-tomcat-10.1.44/bin',           description: 'Tomcat bin dir')
    string(name: 'APP_NAME',      defaultValue: 'NumberGuessGame',                          description: 'WAR base name (no .war)')
    string(name: 'HEALTH_PATH',   defaultValue: '/NumberGuessGame/guess',                   description: 'HTTP path to check after deploy')
  }

  environment {
    // Nexus
    NEXUS_BASE = "http://${params.NEXUS_HOST}:${params.NEXUS_PORT}"
    NEXUS_RELEASES_ID  = 'nexus-releases'
    NEXUS_SNAPSHOTS_ID = 'nexus-snapshots'
  }

  stages {
    stage('Checkout SCM') {
      steps { checkout scm }
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

    stage('Build & Test') {
      steps {
        sh 'mvn -B -s jenkins-settings.xml clean verify'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
          archiveArtifacts artifacts: 'target/*.war', fingerprint: true, onlyIfSuccessful: false
        }
      }
    }

    stage('Deploy to Nexus') {
      when { expression { return params.DEPLOY_TO_NEXUS } }
      steps {
        script {
          // robust: no extra plugin required
          def version = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.version",
                           returnStdout: true).trim()
          def isSnapshot = version.endsWith('-SNAPSHOT')
          def repoId  = isSnapshot ? env.NEXUS_SNAPSHOTS_ID : env.NEXUS_RELEASES_ID
          def repoUrl = isSnapshot ? "${env.NEXUS_BASE}/${params.NEXUS_SNAPSHOTS_PATH}"
                                   : "${env.NEXUS_BASE}/${params.NEXUS_RELEASES_PATH}"
          echo "Deploying version ${version} -> ${repoId} @ ${repoUrl}"

          sh """
            mvn -B -s jenkins-settings.xml -DskipTests deploy \
              -DaltDeploymentRepository=${repoId}::default::${repoUrl}
          """
        }
      }
    }

stage('Deploy to Tomcat (SSH)') {
  when { allOf { branch 'features/theoDev'; expression { return params.DEPLOY_TO_TOMCAT } } }
  steps {
    sshagent(credentials: ['tomcat_ssh']) {
      sh '''
        #!/bin/bash
        set -euo pipefail

        WAR=$(ls target/*.war | head -n1)
        [ -f "$WAR" ] || { echo "WAR not found"; exit 1; }

        echo "Copying $WAR to ${TOMCAT_HOST} ..."
        scp -o StrictHostKeyChecking=no "$WAR" ${TOMCAT_USER}@${TOMCAT_HOST}:/home/${TOMCAT_USER}/${APP_NAME}.war

        echo "Restarting Tomcat and deploying WAR ..."
        ssh -o StrictHostKeyChecking=no ${TOMCAT_USER}@${TOMCAT_HOST} bash -lc "
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
        "

        echo "Waiting for deploy..."
        sleep 8
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
