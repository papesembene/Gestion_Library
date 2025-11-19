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
            when {
                expression { env.SKIP_DEPLOY != 'true' }  // On skip si déjà fait
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    script {
                        def image = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG}"
                        def latest = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:latest"

                        // Vérif si l'image existe déjà (pour éviter les rebuilds inutiles)
                        def exists = sh(script: """
                            docker manifest inspect ${image} >/dev/null 2>&1 && echo 'yes' || echo 'no'
                        """, returnStdout: true).trim()

                        if (exists == 'yes') {
                            echo "✅ Image ${image} déjà sur Docker Hub → Skip build & push"
                        } else {
                            echo "🔨 Construction de l'image ${image}..."

                            // Login d'abord (sécurisé)
                            sh """
                                echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
                            """

                            // Build simple (sans buildx, sans platform – ça marche natif amd64)
                            sh """
                                docker build -t ${image} .
                            """

                            // Tag latest
                            sh """
                                docker tag ${image} ${latest}
                            """

                            // Push les deux
                            sh """
                                docker push ${image}
                                docker push ${latest}
                                docker logout
                            """

                            echo "🚀 Image construite et poussée avec succès !"
                        }
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