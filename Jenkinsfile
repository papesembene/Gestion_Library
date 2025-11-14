pipeline {
	agent any

	environment {
		DOCKER_IMAGE = "papesembene/library-api:latest"
		SPRING_DATASOURCE_URL = credentials('SUPABASE_URL')
		SPRING_DATASOURCE_USERNAME = credentials('SUPABASE_USERNAME')
		SPRING_DATASOURCE_PASSWORD = credentials('SUPABASE_PASSWORD')
		JWT_SECRET = credentials('JWT_SECRET')
	}

	stages {

		stage('Checkout') {
			steps {
				git branch: 'main', url: 'https://github.com/papesembene/Gestion_Library.git'
			}
		}

		stage('Build Maven') {
			agent {
				docker {
					image 'maven:3.9.5-openjdk-17'
				}
			}
			steps {
				sh 'mvn clean package -DskipTests'
			}
		}

		stage('Build Docker Image') {
			steps {
				sh 'docker build -t $DOCKER_IMAGE .'
			}
		}

		stage('Push Docker Image') {
			steps {
				withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
					sh 'echo $PASS | docker login -u $USER --password-stdin'
					sh 'docker push $DOCKER_IMAGE'
				}
			}
		}

		stage('Deploy Production') {
			steps {
				sh '''
                ssh -o StrictHostKeyChecking=no user@server "
                    docker pull $DOCKER_IMAGE &&
                    docker stop library-api || true &&
                    docker rm library-api || true &&
                    docker run -d \
                        -e SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL \
                        -e SPRING_DATASOURCE_USERNAME=$SPRING_DATASOURCE_USERNAME \
                        -e SPRING_DATASOURCE_PASSWORD=$SPRING_DATASOURCE_PASSWORD \
                        -e JWT_SECRET=$JWT_SECRET \
                        -p 8080:8080 \
                        --name library-api \
                        $DOCKER_IMAGE
                "
                '''
			}
		}
	}
}
