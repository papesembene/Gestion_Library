pipeline {
    agent none

    options {
        buildDiscarder(logRotator(numToKeepStr: '40'))
        timeout(time: 45, unit: 'MINUTES')
        timestamps()
    }

    environment {
        DOCKER_USER       = "papesembene"
        DOCKER_IMAGE_NAME = "library-api"
        KUBE_NAMESPACE    = "library"
        DEPLOYMENT_NAME   = "library-api-deployment"
        IMAGE_TAG         = ""
        FULL_IMAGE        = ""
        LATEST_IMAGE      = ""
    }

    stages {

        stage('Prepare') {
            agent any
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG    = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.FULL_IMAGE   = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${env.IMAGE_TAG}"
                    env.LATEST_IMAGE = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:latest"
                    echo "Tag de build : ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Build & Push Docker (Multi-stage)') {
            agent {
                docker {
                    image 'docker:dind'
                    alwaysPull false
                    args '--privileged -u root'
                    reuseNode true
                }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub', 
                    usernameVariable: 'USER', 
                    passwordVariable: 'PASS'
                )]) {
                    sh '''
                        set -euo pipefail

                        # Login Docker Hub (token permanent)
                        echo "$PASS" | docker login -u "$USER" --password-stdin

                        # Build multi-stage Dockerfile (production-grade)
                        DOCKER_BUILDKIT=1 docker build \
                            -t ${FULL_IMAGE} \
                            -t ${LATEST_IMAGE} \
                            --progress=plain \
                            .

                        # Push images
                        docker push ${FULL_IMAGE}
                        docker push ${LATEST_IMAGE}
                    '''
                }
            }
        }

        stage('Deploy to Kubernetes') {
            agent {
                docker {
                    image 'bitnami/kubectl:1.31'
                    reuseNode true
                }
            }
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                    sh '''
                        set -euo pipefail

                        kubectl apply -f k8s/ --recursive
                        kubectl set image deployment/${DEPLOYMENT_NAME} \
                            ${DOCKER_IMAGE_NAME}=${FULL_IMAGE} \
                            -n ${KUBE_NAMESPACE} \
                            --record
                        kubectl rollout status deployment/${DEPLOYMENT_NAME} \
                            -n ${KUBE_NAMESPACE} \
                            --timeout=300s

                        echo "✅ Déploiement terminé"
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f || true'
            cleanWs()
        }
    }
}
