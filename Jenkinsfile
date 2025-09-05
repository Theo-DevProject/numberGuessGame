pipeline {
    agent any

    environment {

        //Maven Configured in Jenkins Global Tool Configuration
        MAVEN_HOME = tool name: 'Maven3', type: 'maven'

        // Nexus repo URL
        NEXUS_REPO = "http://<NEXUS_SERVER_IP>:8081/repository/maven-releases/"

        // SonarQube server (configured in Jenkins > Manage Jenkins > Configure System)
        SONARQUBE_ENV = "MySonarQube_1"

        // Deployment target
        TOMCAT_HOST = "<TOMCAT_SERVER_IP>"
        TOMCAT_USER = "ec2-user"
        TOMCAT_PATH = "/opt/tomcat/webapps/"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Checking out source code..."
                git branch: 'feature/Arafat', url: 'https://github.com/Theo-DevProject/numberGuessGame.git'
            }
        }

        stage('Build') {
            steps {
                echo "Building project with Maven on Jenkins/Maven server..."
                sh "${MAVEN_HOME}/bin/mvn clean package -DskipTests"
            }
        }

        stage('Unit Tests') {
            steps {
                echo "Running JUnit tests..."
                sh "${MAVEN_HOME}/bin/mvn test"
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                echo "Analyzing code with SonarQube..."
                withSonarQubeEnv("${SONARQUBE_ENV}") {
                    sh """
                        ${MAVEN_HOME}/bin/mvn sonar:sonar \
                          -Dsonar.projectKey=my-java-app \
                          -Dsonar.host.url=http://3.125.123.230:9000 \
                          -Dsonar.login=<SONARQUBE_TOKEN>
                    """
                }
            }
        }

        stage("Quality Gate") {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Publish to Nexus') {
            steps {
                echo "Deploying artifact to Nexus repository..."
                sh """
                    ${MAVEN_HOME}/bin/mvn deploy -DskipTests \
                      -Dnexus.url=${NEXUS_REPO}
                """
            }
        }

        stage('Deploy to Tomcat') {
            steps {
                echo "Deploying WAR to Tomcat server..."
                sshagent(['ec2-ssh-key']) { // Jenkins credential ID
                    sh """
                        scp target/*.war ${TOMCAT_USER}@${TOMCAT_HOST}:${TOMCAT_PATH}
                        ssh ${TOMCAT_USER}@${TOMCAT_HOST} "sudo systemctl restart tomcat"
                    """
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline was a success: Build → Test → SonarQube → Nexus → Tomcat"
        }
        failure {
            echo "Pipeline was not a success. Check logs in Jenkins."
        }
    }
}
