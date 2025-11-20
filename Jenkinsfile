pipeline {
    // Agent global pour tous les stages (utilise un agent Jenkins avec Docker installé)
    agent any

    // Variables d'environnement essentielles pour le déploiement
    environment {
        // Nom d'utilisateur Docker Hub (remplacez par le vôtre)
        DOCKER_USER = 'papesembene'
        // Nom de l'image Docker
        DOCKER_IMAGE_NAME = 'library-api'
        // Namespace Kubernetes
        KUBE_NAMESPACE = 'library'
        // Nom du déploiement Kubernetes
        DEPLOYMENT_NAME = 'library-api-deployment'
    }

    stages {
        // Étape 1: Récupération du code source depuis Git
        stage('Checkout Code') {
            steps {
                // Clone le repository Git (branche main par défaut)
                checkout scm
                // Définit le tag de l'image basé sur le commit Git
                script {
                    env.IMAGE_TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.FULL_IMAGE = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${env.IMAGE_TAG}"
                }
            }
        }

        // Étape 2: Construction de l'application Java avec Maven
        stage('Build Application') {
            agent {
                docker {
                    image 'maven:3.9.9-eclipse-temurin-17-alpine'
                    args '-v maven-repo:/root/.m2'
                    reuseNode true
                }
            }
            steps {
                // Utilise Maven pour compiler et packager l'application
                // -B : mode batch (non interactif)
                // -DskipTests : ignore les tests pour accélérer le build
                sh 'mvn -B clean package -DskipTests'
            }
        }

        // Étape 3: Construction de l'image Docker
        stage('Build Docker Image') {
            steps {
                script {
                    // Construit l'image Docker à partir du Dockerfile
                    // Utilise BuildKit pour une construction plus rapide
                    sh "DOCKER_BUILDKIT=1 docker build -t ${FULL_IMAGE} ."
                }
            }
        }

        // Étape 4: Push de l'image vers Docker Hub
        stage('Push Docker Image') {
            steps {
                script {
                    // Authentification Docker Hub avec les credentials Jenkins
                    withCredentials([usernamePassword(
                        credentialsId: 'dockerhub',
                        usernameVariable: 'USER',
                        passwordVariable: 'PASS'
                    )]) {
                        // Login et push de l'image
                        sh """
                            echo "\$PASS" | docker login -u "\$USER" --password-stdin
                            docker push ${FULL_IMAGE}
                        """
                    }
                }
            }
        }

        // Étape 5: Déploiement sur Kubernetes
        stage('Deploy to Kubernetes') {
            steps {
                // Utilise le fichier kubeconfig pour accéder au cluster K8s
                withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                    sh """
                        # Applique les manifests Kubernetes (namespace, service, deployment, etc.)
                        kubectl apply -f k8s/ --recursive

                        # Met à jour l'image dans le déploiement
                        kubectl set image deployment/${DEPLOYMENT_NAME} \\
                            ${DOCKER_IMAGE_NAME}=${FULL_IMAGE} \\
                            -n ${KUBE_NAMESPACE} \\
                            --record

                        # Attend que le rollout soit terminé (timeout 5 minutes)
                        kubectl rollout status deployment/${DEPLOYMENT_NAME} \\
                            -n ${KUBE_NAMESPACE} \\
                            --timeout=300s

                        echo "✅ Déploiement réussi sur Kubernetes"
                    """
                }
            }
        }
    }

    // Actions post-build (toujours exécutées)
    post {
        always {
            // Nettoie les images Docker non utilisées pour économiser de l'espace
            sh 'docker image prune -f || true'
            // Nettoie le workspace Jenkins
            cleanWs()
        }
    }
}
