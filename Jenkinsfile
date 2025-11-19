pipeline {
    agent none

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 40, unit: 'MINUTES')
        timestamps()
    }

    environment {
        DOCKER_USER       = "papesembene"
        DOCKER_IMAGE_NAME = "library-api"
        KUBE_NAMESPACE    = "library"
        DEPLOYMENT_NAME   = "library-api-deployment"
        SKIP_BUILD_PUSH   = "false"
        // On calcule le tag plus tard, dans un stage avec agent → plus jamais d'erreur "sh without node"
    }

    stages {
        // =========================================================
        // 0. Checkout + Calcul du tag (obligatoire avec agent none)
        // =========================================================
        stage('Prepare') {
            agent { docker { image 'alpine/git:latest' } }
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG   = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.FULL_IMAGE  = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${env.IMAGE_TAG}"
                    env.LATEST_IMAGE = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:latest"
                    echo "Tag de cette build : ${env.IMAGE_TAG}"
                }
            }
        }

        // =========================================================
        // 1. Build & Tests Maven
        // =========================================================
        stage('Build Maven') {
            agent {
                docker {
                    image 'maven:3.9.9-eclipse-temurin-17-alpine'
                    args '-v maven-repo:/root/.m2 --user root'
                    reuseNode true
                }
            }
            steps {
                sh 'mvn -B clean verify'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        // =========================================================
        // 2. Vérifier si l'image existe déjà sur Docker Hub
        // =========================================================
        stage('Check Docker Image Exists') {
            agent { docker { image 'docker:27.3.1-dind-alpine3.20' args '--privileged' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    script {
                        def exists = sh(
                            script: """
                                echo "$PASS" | docker login -u "$USER" --password-stdin > /dev/null
                                docker manifest inspect ${env.FULL_IMAGE} > /dev/null 2>&1 && echo true || echo false
                            """,
                            returnStdout: true
                        ).trim()

                        env.SKIP_BUILD_PUSH = exists == 'true' ? 'true' : 'false'

                        if (env.SKIP_BUILD_PUSH == 'true') {
                            echo "Image ${env.FULL_IMAGE} déjà sur Docker Hub → skip build & déploiement"
                        } else {
                            echo "Nouvelle image à construire : ${env.FULL_IMAGE}"
                        }
                    }
                }
            }
        }

        // =========================================================
        // 3. Build & Push Docker (seulement si nécessaire)
        // =========================================================
        stage('Build & Push Docker') {
            when { environment name: 'SKIP_BUILD_PUSH', value: 'false' }
            agent { docker { image 'docker:27.3.1-dind-alpine3.20' args '--privileged' } }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    sh '''
                        set -euo pipefail

                        # Résolution du problème .dockerignore + target/
                        # On copie le JAR à la racine → garanti présent dans le contexte
                        cp target/*.jar app.jar

                        echo "$PASS" | docker login -u "$USER" --password-stdin

                        DOCKER_BUILDKIT=1 docker build \
                            --build-arg JAR_FILE=app.jar \
                            -t ${FULL_IMAGE} \
                            -t ${LATEST_IMAGE} \
                            --pull --no-cache .

                        docker push ${FULL_IMAGE}
                        docker push ${LATEST_IMAGE}

                        echo "Images poussées avec succès !"
                    '''
                }
            }
        }

        // =========================================================
        // 4. Déploiement Kubernetes
        // =========================================================
        stage('Deploy to Kubernetes') {
            when { environment name: 'SKIP_BUILD_PUSH', value: 'false' }
            agent {
                kubernetes {
                    yaml '''
                    apiVersion: v1
                    kind: Pod
                    spec:
                      containers:
                      - name: kubectl
                        image: bitnami/kubectl:1.31
                        command: ["sleep", "3600"]
                    '''
                }
            }
            steps {
                container('kubectl') {
                    withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                        sh '''
                            set -euo pipefail

                            kubectl apply -f k8s/ --recursive --prune -l app=library-api || true

                            kubectl set image deployment/${DEPLOYMENT_NAME} \
                                library-api=${FULL_IMAGE} \
                                -n ${KUBE_NAMESPACE} --record

                            kubectl rollout status deployment/${DEPLOYMENT_NAME} \
                                -n ${KUBE_NAMESPACE} --timeout=300s

                            echo "DÉPLOIEMENT RÉUSSI !"
                            echo "URL : http://$(kubectl get svc library-api-service -n ${KUBE_NAMESPACE} \
                                -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo 'pas-d-ip-externe')"
                        '''
                    }
                }
            }
        }
    }

    post {
        success { echo 'Tout est OK !' }
        failure {
            script {
                if (env.SKIP_BUILD_PUSH == 'false') {
                    echo 'Rollback du déploiement...'
                    withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                        sh 'kubectl rollout undo deployment/${DEPLOYMENT_NAME} -n ${KUBE_NAMESPACE} || true'
                    }
                }
            }
        }
        always {
            // Nettoyage sûr et ciblé (plus de prune -af destructeur)
            node('master || built-in') {
                sh 'docker image prune -f || true'
                cleanWs(cleanWhenAborted: true, cleanWhenFailure: true, cleanWhenSuccess: true)
            }
        }
    }
}