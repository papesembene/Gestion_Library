pipeline {
    agent {
        docker {
            image 'maven:3.9.6-eclipse-temurin-17'
            args '-v /var/run/docker.sock:/var/run/docker.sock -v maven-cache:/root/.m2 -v /home/mr-sem-s/.minikube:/home/mr-sem-s/.minikube -u root'
            reuseNode true
        }
    }

    environment {
        DOCKER_USER         = "papesembene"
        DOCKER_IMAGE_NAME   = "library-api"
        IMAGE_TAG           = "${env.GIT_COMMIT.take(8)}"
        FULL_IMAGE          = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG}"
        KUBE_NAMESPACE      = 'library'
        APP_NAME            = 'library-api'
    }

    stages {

        // =========================================================
        // 1. Build & Package Maven
        // =========================================================
        stage('Build Maven') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        // =========================================================
        // 2. Build Docker Image (avec cache intelligent + skip si déjà existante)
        // =========================================================
        stage('Build & Push Docker Image') {
            steps {
                script {
                    // Vérifie si l'image existe déjà sur Docker Hub
                    def imageExists = sh(
                        script: "docker manifest inspect ${FULL_IMAGE} > /dev/null 2>&1 && echo 'yes' || echo 'no'",
                        returnStdout: true
                    ).trim()

                    if (imageExists == 'yes') {
                        echo "Docker image ${FULL_IMAGE} déjà présente sur Docker Hub → on skip le build & push"
                        env.SKIP_DEPLOY = 'true'
                    } else {
                        echo "Construction de l'image Docker ${FULL_IMAGE}"

                        // Build avec BuildKit (plus rapide + cache)
                        sh """
                            docker buildx create --use --name mybuilder || true
                            docker buildx build --push \
                                --tag ${FULL_IMAGE} \
                                --tag docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:latest \
                                --platform linux/amd64 \
                                .
                        """
                        echo "Image construite et poussée avec succès"
                    }
                }
            }
        }

        // =========================================================
        // 3. Déploiement Kubernetes (seulement si on a poussé une nouvelle image)
        // =========================================================
        stage('Deploy to Kubernetes') {
            when {
                expression { env.SKIP_DEPLOY != 'true' }
            }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                    script {
                        // Installation de kubectl si besoin
                        sh '''
                            if ! command -v kubectl >/dev/null 2>&1; then
                                echo "Installation de kubectl..."
                                curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
                                chmod +x kubectl
                                mv kubectl /usr/local/bin/
                            fi
                        '''

                        sh '''
                            kubectl apply -f k8s/namespace.yaml
                            kubectl apply -f k8s/secrets.yaml --validate=false
                            kubectl apply -f k8s/service.yaml
                            kubectl apply -f k8s/deployment.yaml

                            kubectl set image deployment/library-api-deployment \
                                library-api=${FULL_IMAGE} \
                                -n ${KUBE_NAMESPACE}

                            kubectl rollout status deployment/library-api-deployment \
                                -n ${KUBE_NAMESPACE} --timeout=600s
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
            // Nettoyage léger
            sh "docker image prune -f || true"
            archiveArtifacts artifacts: 'target/surefire-reports/*.xml', allowEmptyArchive: true
        }
    }
}