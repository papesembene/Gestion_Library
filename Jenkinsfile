pipeline {
    agent {
        docker {
            image 'maven:3.9.6-eclipse-temurin-17'
            args '-v /var/run/docker.sock:/var/run/docker.sock -v maven-cache:/root/.m2 -u root'
            reuseNode true
        }
    }

    environment {
        DOCKER_REGISTRY = 'index.docker.io'
        DOCKER_REPO = 'papesembene/library-api'
        IMAGE_TAG = "${env.GIT_COMMIT.take(8)}"
        DOCKER_IMAGE = "${DOCKER_REPO}:${IMAGE_TAG}"
        KUBE_NAMESPACE = 'library'
        APP_NAME = 'library-api'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('📦 Build & Package') {
            steps {
                sh 'mvn clean package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('🐳 Build Docker Image') {
            steps {
                script {
                    def dockerImage = docker.build("${DOCKER_IMAGE}", "--build-arg JAR_FILE=target/*.jar .")
                    echo "✅ Docker image built: ${DOCKER_IMAGE}"
                }
            }
        }

        stage('📤 Push to Registry') {
            steps {
                script {
                    docker.withRegistry("https://index.docker.io", 'dockerhub') {
                        def image = docker.image("${DOCKER_IMAGE}")
                        image.push()
                        image.push('latest')
                    }
                    echo "✅ Image pushed: ${DOCKER_IMAGE}"
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
                        // Apply Kubernetes manifests
                        sh """
                            kubectl apply -f k8s/namespace.yaml
                            kubectl apply -f k8s/secrets.yaml
                            kubectl apply -f k8s/service.yaml
                            kubectl apply -f k8s/deployment.yaml
                        """

                        // Update deployment with new image
                        sh """
                            kubectl set image deployment/${APP_NAME}-deployment \\
                            ${APP_NAME}=${DOCKER_REGISTRY}/${DOCKER_IMAGE} \\
                            -n ${KUBE_NAMESPACE} --record
                        """

                        // Wait for rollout to complete
                        sh """
                            kubectl rollout status deployment/${APP_NAME}-deployment \\
                            -n ${KUBE_NAMESPACE} --timeout=600s
                        """

                        // Verify deployment
                        sh """
                            kubectl get pods -n ${KUBE_NAMESPACE} -l app=${APP_NAME}
                            kubectl get svc -n ${KUBE_NAMESPACE} -l app=${APP_NAME}
                        """
                    }
                }
            }
        }

    }

    post {
        success {
            script {
                def duration = currentBuild.durationString.replace(' and counting', '')
                def message = """
🎉 **DÉPLOIEMENT RÉUSSI**

✅ Pipeline exécuté avec succès
⏱️ Durée: ${duration}
🏷️ Image: ${DOCKER_IMAGE}
🌐 Namespace: ${KUBE_NAMESPACE}
📊 Build: #${currentBuild.number}

🔗 Application accessible sur le cluster Kubernetes
                """.stripIndent()

                echo message
            }
        }

        failure {
            script {
                def message = """
❌ **ÉCHEC DU DÉPLOIEMENT**

🔴 Pipeline échoué
🏷️ Build: #${currentBuild.number}
📋 Logs: ${currentBuild.absoluteUrl}

🔍 Vérifiez les logs pour diagnostiquer le problème
                """.stripIndent()

                echo message

                // Rollback automatique en cas d'échec
                withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                    sh """
                        kubectl rollout undo deployment/${APP_NAME}-deployment \\
                        -n ${KUBE_NAMESPACE} || true
                    """
                }
            }
        }

        always {
            script {
                // Cleanup Docker images
                sh "docker rmi ${DOCKER_IMAGE} || true"
                sh "docker system prune -f || true"

                // Archive logs
                archiveArtifacts artifacts: 'target/surefire-reports/*.xml', allowEmptyArchive: true
            }
        }
    }
}
