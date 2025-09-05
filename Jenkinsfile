pipeline {
  agent any

  options { timestamps() }
  tools   { jdk 'Java'; maven 'Maven' }

  parameters {
    // === Nexus (EIP) ===
    string(name: 'NEXUS_HOST',            defaultValue: '52.21.103.235', description: 'Nexus host/IP')
    string(name: 'NEXUS_PORT',            defaultValue: '8081',          description: 'Nexus port')
    string(name: 'NEXUS_RELEASES_PATH',   defaultValue: 'repository/maven-releases/',   description: 'Releases path')
    string(name: 'NEXUS_SNAPSHOTS_PATH',  defaultValue: 'repository/maven-snapshots/',  description: 'Snapshots path')

    // === SonarQube (EIP) ===
    string(name: 'SONAR_HOST_URL',        defaultValue: 'http://52.72.165.200:9000',     description: 'SonarQube URL')

    // === Tomcat (optional) ===
    booleanParam(name: 'DEPLOY_TO_TOMCAT', defaultValue: true,                            description: 'Deploy after Nexus')
    string(name: 'TOMCAT_HOST',            defaultValue: 'YOUR_TOMCAT_IP',                description: 'Tomcat host/IP')
    string(name: 'TOMCAT_USER',            defaultValue: 'ubuntu',                        description: 'SSH user on Tomcat box')
    string(name: 'TOMCAT_WEBAPPS',         defaultValue: '/opt/tomcat/webapps',           description: 'Tomcat webapps dir')
    string(name: 'TOMCAT_BIN',             defaultValue: '/opt/tomcat/bin',               description: 'Tomcat bin dir')
    string(name: 'APP_NAME',               defaultValue: 'NumberGuessGame',               description: 'WAR base name (no .war)')
    string(name: 'HEALTH_PATH',            defaultValue: '/NumberGuessGame/guess',        description: 'Health check path')
  }

  environment {
    // Nexus
    NEXUS_BASE         = "http://${params.NEXUS_HOST}:${params.NEXUS_PORT}"
    NEXUS_RELEASES_ID  = 'nexus-releases'
    NEXUS_SNAPSHOTS_ID = 'nexus-snapshots'

    // SonarQube token stored in Jenkins Credentials (Secret text) with id: sonar_token
    SONAR_AUTH_TOKEN = credentials('sonar_token')
  }

  stages {
    stage('Checkout SCM') {
      steps { checkout scm }
    }

    stage('Tool Install') {
      steps {
        sh '''
          set -e
          echo "Java version:"
          java -version || true
          echo "Maven version:"
          mvn -v
        '''
      }
    }

    stage('Show branch') {
      steps {
        sh '''
          echo "== Git remotes ==" && git remote -v || true
          echo "== Last commit ==" && git log -1 --oneline || true
          echo "== Branch name ==" && git rev-parse --abbrev-ref HEAD || true
        '''
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
    <server>
      <id>${env.NEXUS_RELEASES_ID}</id>
      <username>${NEXUS_USER}</username>
      <password>${NEXUS_PASS}</password>
    </server>
    <server>
      <id>${env.NEXUS_SNAPSHOTS_ID}</id>
      <username>${NEXUS_USER}</username>
      <password>${NEXUS_PASS}</password>
    </server>
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

    stage('Static Analysis (SonarQube)') {
      steps {
        withSonarQubeEnv('sonarqube') {
          sh """
            mvn -B -s jenkins-settings.xml -DskipTests sonar:sonar \
              -Dsonar.projectKey=com.studentapp:NumberGuessGame \
              -Dsonar.projectName=NumberGuessGame \
              -Dsonar.host.url=${SONAR_HOST_URL} \
              -Dsonar.login=${SONAR_AUTH_TOKEN}
          """
        }
      }
    }

    stage('Quality Gate') {
      steps {
        timeout(time: 10, unit: 'MINUTES') {
          waitForQualityGate abortPipeline: true
        }
      }
    }

    stage('Deploy to Nexus') {
      steps {
        script {
          def version    = sh(script: "mvn -q -DforceStdout help:evaluate -Dexpression=project.version", returnStdout: true).trim()
          def isSnapshot = version.endsWith('-SNAPSHOT')
          def repoId     = isSnapshot ? env.NEXUS_SNAPSHOTS_ID : env.NEXUS_RELEASES_ID
          def repoUrl    = isSnapshot ? "${env.NEXUS_BASE}/${params.NEXUS_SNAPSHOTS_PATH}"
                                      : "${env.NEXUS_BASE}/${params.NEXUS_RELEASES_PATH}"
          echo "Deploying version ${version} (snapshot: ${isSnapshot}) to ${repoId} → ${repoUrl}"

          sh """
            mvn -B -s jenkins-settings.xml -DskipTests deploy \
              -DaltDeploymentRepository=${repoId}::default::${repoUrl}
          """
        }
      }
    }

    stage('Deploy to Tomcat (SSH)') {
      when { expression { return params.DEPLOY_TO_TOMCAT } }
      steps {
        sshagent(credentials: ['tomcat_ssh']) {
          sh '''
            set -e
            WAR=$(ls target/*.war | head -n1)
            [ -f "$WAR" ] || { echo "WAR not found in target/"; exit 1; }

            echo "Copying $WAR to ${TOMCAT_HOST} ..."
            scp -o StrictHostKeyChecking=no "$WAR" ${TOMCAT_USER}@${TOMCAT_HOST}:/home/${TOMCAT_USER}/${APP_NAME}.war

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

            echo "Waiting for app to come up..."
            sleep 10
            echo "Health check:"
            curl -fsS "http://${TOMCAT_HOST}:8080${HEALTH_PATH}" | head -c 400 || true
          '''
        }
      }
    }
  }

  post {
    success { echo 'Pipeline finished successfully' }
    failure { echo 'Pipeline failed — check logs' }
  }
}
