pipeline {
    agent {
       docker {
        image 'maven:3.9.6-eclipse-temurin-17-alpine'
        args '-v /var/run/docker.sock:/var/run/docker.sock -v maven-repo:/root/.m2 -u root'
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
                            env.SKIP_DEPLOY = 'true'
                        } else {
                            echo "🔨 Construction de l'image ${image}..."

                            // Copier le JAR pour le build
                            sh "cp target/*.jar app.jar"

                            // Login d'abord (sécurisé)
                            sh """
                                echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
                            """

                            // Build simple (sans buildx, sans platform – ça marche natif amd64)
                            sh """
                                docker build --build-arg JAR_FILE=app.jar -t ${image} .
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
                expression { env.SKIP_DEPLOY != 'true' }   // ne se lance que si on a poussé une nouvelle image
            }
            steps {
                // Le kubeconfig est stocké en sécurité dans Jenkins Credentials (fichier)
                withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                    sh '''
                        set -e  # arrêt immédiat si une commande échoue

                        echo "Connexion au cluster Kubernetes..."
                        kubectl version --client && kubectl cluster-info

                        echo "Application des manifests (namespace, secrets, service, deployment)..."
                        kubectl apply -f k8s/ --recursive --prune -l app=library-api

                        echo "Mise à jour de l'image dans le Deployment..."
                        kubectl set image deployment/library-api-deployment \
                            library-api=${FULL_IMAGE} \
                            -n ${KUBE_NAMESPACE} \
                            --record

                        echo "Attente du rollout (max 5 minutes)..."
                        kubectl rollout status deployment/library-api-deployment \
                            -n ${KUBE_NAMESPACE} \
                            --timeout=5m

                        echo ""
                        echo "DÉPLOIEMENT RÉUSSI !"
                        echo "Ton API est accessible ici :"
                        kubectl get svc -n ${KUBE_NAMESPACE} library-api-service -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
                        echo ""
                        echo "ou via le port-forward temporaire :"
                        echo "kubectl port-forward svc/library-api-service 8080:80 -n ${KUBE_NAMESPACE}"
                    '''
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