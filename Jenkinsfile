pipeline {
    agent {
        docker {
            image 'maven:3.9.6-eclipse-temurin-17'
            args '-v /var/run/docker.sock:/var/run/docker.sock -v maven-cache:/root/.m2 -v /home/mr-sem-s/.minikube:/home/mr-sem-s/.minikube -u root'
        }
    }

    environment {
        DOCKER_USER = "papesembene"
        DOCKER_IMAGE_NAME = "library-api"
        IMAGE_TAG = "${env.GIT_COMMIT.take(8)}"
        KUBE_NAMESPACE = 'library'
        APP_NAME = 'library-api'
    }

    stages {

        stage('Build & Package') {
            steps {
                sh 'mvn clean package -DskipTests'
                stash includes: 'target/*.jar', name: 'jar-built'
            }
        }

        stage('Check & Build Docker Image') {
            steps {
                unstash 'jar-built'
                sh "cp target/*.jar app.jar"
                script {
                    def imageExists = sh(script: "docker manifest inspect docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG} >/dev/null 2>&1 && echo yes || echo no", returnStdout: true).trim()
                    if (imageExists == "yes") {
                        echo "✅ Docker image already exists for this commit. Skipping build and push."
                        env.SKIP_PUSH = 'true'
                    } else {
                        sh """
                            echo "Building Docker image..."
                            docker build -t ${DOCKER_IMAGE_NAME}:${IMAGE_TAG} --build-arg JAR_FILE=app.jar .
                            echo "Docker image built successfully."
                        """
                    }
                }
            }
        }

        stage('Push Docker Image') {
            when {
                expression { env.SKIP_PUSH != 'true' }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    script {
                        retry(3) {
                            sh """
                                echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                                docker tag ${DOCKER_IMAGE_NAME}:${IMAGE_TAG} docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG}
                                docker push docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG}
                                docker tag docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG} docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:latest
                                docker push docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:latest
                                docker logout
                            """
                        }
                    }
                }
            }
        }

        stage('🚀 Deploy to Kubernetes') {
            when {
                expression { env.SKIP_PUSH != 'true' }
            }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                    script {
                        // Installer kubectl si absent
                        sh '''
                        if ! command -v kubectl &> /dev/null; then
                            echo "Installing kubectl..."
                            curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
                            chmod +x kubectl
                            mv kubectl /usr/local/bin/
                            echo "kubectl installed successfully"
                        fi
                        '''

                        // Déployer namespace et secrets
                        sh '''
                        kubectl apply -f k8s/namespace.yaml
                        kubectl apply -f k8s/secrets.yaml --validate=false
                        '''

                        // Déployer service et déploiement
                        sh '''
                        kubectl apply -f k8s/service.yaml
                        kubectl apply -f k8s/deployment.yaml
                        kubectl set image deployment/library-api-deployment \
                            library-api=docker.io/papesembene/library-api:${IMAGE_TAG} \
                            -n library
                        kubectl rollout status deployment/library-api-deployment \
                            -n library --timeout=600s
                        '''
                    }
                }
            }
        }

    }

    post {
        failure {
            withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                sh "kubectl rollout undo deployment/${APP_NAME}-deployment -n ${KUBE_NAMESPACE} || true"
            }
        }
        always {
            sh "docker rmi ${DOCKER_IMAGE_NAME}:${IMAGE_TAG} || true"
            sh "rm -f app.jar || true"
            archiveArtifacts artifacts: 'target/surefire-reports/*.xml', allowEmptyArchive: true
        }
    }
}
