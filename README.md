# numberGuessGame for Team 7
Number Guess Game – DevOps CI/CD Project

📌 Project Overview

This project demonstrates a complete DevOps CI/CD pipeline for a Java web application – Number Guess Game.
The pipeline is built using Jenkins, Maven, SonarQube, Nexus, and Tomcat on AWS EC2 instances.

Key Features:
	•	Automated build and packaging of a Java WAR application
	•	Static code analysis with SonarQube
	•	Artifact versioning and storage in Nexus repository
	•	Continuous deployment to a Tomcat staging server
	•	GitHub Webhook integration for CI/CD automation


⚙️ Tech Stack
	•	Java 17 – application runtime
	•	Maven 3.9.11 – build & dependency management
	•	SonarQube – code quality & static analysis
	•	Nexus 2.x – artifact repository
	•	Tomcat 10.x – application server
	•	Jenkins – CI/CD automation

🚀 Pipeline Stages

The Jenkinsfile defines the following pipeline:
	1.	Checkout SCM – clone source code from GitHub
	2.	Build – compile & package WAR with Maven
	3.	Code Analysis – run SonarQube scan
	4.	Publish Artifact – deploy WAR to Nexus (maven-releases / maven-snapshots)
	5.	Deploy to Tomcat – copy WAR to Tomcat webapps directory via SSH

⸻
git clone https://github.com/<your-org>/NumberGuessGame.git
cd NumberGuessGame


2. Configure Jenkins
	•	Install required plugins: Git, Pipeline, Maven Integration, SonarQube, Nexus Artifact Uploader, SSH Agent
	•	Configure Tools:
	•	JDK 17
	•	Maven 3.9.11
	•	Add credentials:
	•	GitHub access token
	•	Nexus username/password
	•	SSH key for Tomcat server

3. Setup Webhook
	•	Go to GitHub repo → Settings → Webhooks
	•	Add Jenkins webhook:

4. SonarQube Project
	•	Create project number-guess-game
	•	Generate a project token and add it in Jenkins credentials

5. Nexus Repository
	•	Ensure maven-releases and maven-snapshots repos are created
	•	Update settings.xml in Jenkins with <servers> block for Nexus credentials

6. Tomcat Serv
	•	Install Tomcat on a separate EC2 instance
	•	Ensure Jenkins user can SSH into Tomcat server with deployment key

⸻

▶️ Run the Pipeline
	•	Push changes to dev or feature branch → pipeline runs automatically
	•	Merged code into main branch → triggers full build, quality scan, artifact upload, and deployment
