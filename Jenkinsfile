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
                    sh """
                        docker build -t ${DOCKER_IMAGE_NAME}:${IMAGE_TAG} --build-arg JAR_FILE=target/*.jar .
                        echo "✅ Docker image built: ${DOCKER_IMAGE_NAME}:${IMAGE_TAG}"
                    """
                }
            }
        }

        stage('📤 Push Docker Image') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
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

        stage('🚀 Deploy to Kubernetes') {
            environment {
                SPRING_DATASOURCE_URL = credentials('db-url')
                SPRING_DATASOURCE_USERNAME = credentials('db-username')
                SPRING_DATASOURCE_PASSWORD = credentials('db-password')
                JWT_SECRET = credentials('jwt-secret')
            }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                    sh """
                        kubectl apply -f k8s/namespace.yaml
                        kubectl apply -f k8s/secrets.yaml
                        kubectl apply -f k8s/service.yaml
                        kubectl apply -f k8s/deployment.yaml

                        kubectl set image deployment/${APP_NAME}-deployment \\
                        ${APP_NAME}=docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG} \\
                        -n ${KUBE_NAMESPACE} --record

                        kubectl rollout status deployment/${APP_NAME}-deployment -n ${KUBE_NAMESPACE} --timeout=600s

                        kubectl get pods -n ${KUBE_NAMESPACE} -l app=${APP_NAME}
                        kubectl get svc -n ${KUBE_NAMESPACE} -l app=${APP_NAME}
                    """
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
Image: docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG}
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
                sh "docker rmi ${DOCKER_IMAGE_NAME}:${IMAGE_TAG} || true"
                sh "docker system prune -f || true"
                archiveArtifacts artifacts: 'target/surefire-reports/*.xml', allowEmptyArchive: true
            }
        }
    }
}
