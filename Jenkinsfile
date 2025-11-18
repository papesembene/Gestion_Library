pipeline {
    agent {
        docker {
            image 'maven:3.9.6-eclipse-temurin-17'
            args '-v /var/run/docker.sock:/var/run/docker.sock -v maven-cache:/root/.m2 -u root'
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
                script {
                    def imageExists = sh(script: "docker manifest inspect docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG} >/dev/null 2>&1 && echo yes || echo no", returnStdout: true).trim()
                    if (imageExists == "yes") {
                        echo "✅ Docker image already exists for this commit. Skipping build."
                    } else {
                        sh """
                            docker build -t ${DOCKER_IMAGE_NAME}:${IMAGE_TAG} --build-arg JAR_FILE=target/*.jar .
                        """
                    }
                }
            }
        }

        stage('Push Docker Image') {
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

        stage('Deploy to Kubernetes') {
            environment {
                SPRING_DATASOURCE_URL = credentials('db-url')
                SPRING_DATASOURCE_USERNAME = credentials('db-username')
                SPRING_DATASOURCE_PASSWORD = credentials('db-password')
                JWT_SECRET = credentials('jwt-secret')
            }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                    sh """
                        # Install kubectl if not present
                        if ! command -v kubectl &> /dev/null; then
                            echo "Installing kubectl..."
                            curl -LO "https://dl.k8s.io/release/\$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
                            chmod +x kubectl
                            mv kubectl /usr/local/bin/
                            echo "kubectl installed successfully"
                        else
                            echo "kubectl already installed"
                        fi

                        # Deploy to Kubernetes
                        kubectl apply -f k8s/
                        kubectl set image deployment/${APP_NAME}-deployment ${APP_NAME}=docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG} -n ${KUBE_NAMESPACE} --record
                        kubectl rollout status deployment/${APP_NAME}-deployment -n ${KUBE_NAMESPACE} --timeout=600s
                    """
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
            archiveArtifacts artifacts: 'target/surefire-reports/*.xml', allowEmptyArchive: true
        }
    }
}
