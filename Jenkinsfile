pipeline {
    agent {
        docker {
            image 'maven:3.9.6-eclipse-temurin-17'
            args '-v /var/run/docker.sock:/var/run/docker.sock -v maven-cache:/root/.m2 -u root'
            reuseNode true
        }
    }

    environment {
        DOCKER_USER = "papesembene"
        DOCKER_IMAGE_NAME = "library-api"
        IMAGE_TAG = "${env.GIT_COMMIT.take(8)}"
        FULL_IMAGE_TAG = "${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG}"
        KUBE_NAMESPACE = 'library'
        APP_NAME = 'library-api'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {

        stage('📦 Build JAR') {
            steps {
                sh 'mvn clean package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('🐳 Build Docker Image') {
            steps {
                script {
                    echo "Building Docker image: ${FULL_IMAGE_TAG}"
                    sh "docker build -t ${FULL_IMAGE_TAG} --build-arg JAR_FILE=target/*.jar ."
                }
            }
        }

        stage('📤 Push to DockerHub') {
            steps {
                script {
                    docker.withRegistry("https://index.docker.io", 'dockerhub') {
                        sh """
                            docker push ${FULL_IMAGE_TAG}
                            docker tag ${FULL_IMAGE_TAG} ${DOCKER_USER}/${DOCKER_IMAGE_NAME}:latest
                            docker push ${DOCKER_USER}/${DOCKER_IMAGE_NAME}:latest
                        """
                        echo "🚀 Pushed successfully: ${FULL_IMAGE_TAG}"
                    }
                }
            }
        }

        stage('🚀 Deploy to Kubernetes') {
            environment {
                SPRING_DATASOURCE_URL = credentials('db-url')
                SPRING_DATASOURCE_USERNAME = credentials('db-username')
                SPRING_DATASOURCE_PASSWORD = credentials('db-password')
                JWT_SECRET = credentials('jwt-secret')
            }
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {

                        sh """
                            kubectl apply -f k8s/namespace.yaml
                            kubectl apply -f k8s/secrets.yaml
                            kubectl apply -f k8s/service.yaml
                            kubectl apply -f k8s/deployment.yaml
                        """

                        // Deployment update
                        sh """
                            kubectl set image deployment/${APP_NAME}-deployment \\
                            ${APP_NAME}=docker.io/${FULL_IMAGE_TAG} \\
                            -n ${KUBE_NAMESPACE} --record
                        """

                        sh """
                            kubectl rollout status deployment/${APP_NAME}-deployment -n ${KUBE_NAMESPACE} --timeout=600s
                        """

                        sh """
                            kubectl get pods -n ${KUBE_NAMESPACE}
                            kubectl get svc -n ${KUBE_NAMESPACE}
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                echo """
            🎉 DEPLOYMENT SUCCESSFUL
            -----------------------------------
            Image: ${FULL_IMAGE_TAG}
            Namespace: ${KUBE_NAMESPACE}
            Build #: ${currentBuild.number}
            -----------------------------------
                            """
            }
        }

        failure {
            script {
                echo "❌ Deployment failed, rolling back..."
                withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                    sh "kubectl rollout undo deployment/${APP_NAME}-deployment -n ${KUBE_NAMESPACE} || true"
                }
            }
        }

        always {
            script {
                sh "docker rmi ${FULL_IMAGE_TAG} || true"
                sh "docker system prune -f || true"
                archiveArtifacts artifacts: 'target/surefire-reports/*.xml', allowEmptyArchive: true
            }
        }
    }
}
