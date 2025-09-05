pipeline {
    agent any

    environment {

        //Maven Configured in Jenkins Global Tool Configuration
        MAVEN_HOME = tool name: 'Maven3', type: 'maven'

        // Nexus repo URL
        NEXUS_REPO = "http://3.67.77.37:8081/nexus/repository/maven-releases/"

        // SonarQube server (configured in Jenkins > Manage Jenkins > Configure System)
        SONARQUBE_ENV = "MySonarQube_1"

        // Deployment target
        TOMCAT_HOST = "http://18.196.94.213:8080"
        TOMCAT_USER = "ec2-user"
        TOMCAT_PATH = "/opt/tomcat-7.0.94/webapps/"
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
                    
                          -Dsonar.projectKey=pipeline-javaweb \
                          -Dsonar.projectName='pipeline javaweb' \
                          -Dsonar.host.url=http://3.77.144.146:9000 \
                          -Dsonar.token=sqa_f65a1a1621ed88b728e60e3a3efb1c7179f11ea8
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
