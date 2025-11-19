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
        SKIP_BUILD_PUSH   = "false"
    }

    stages {

        stage('Prepare') {
            agent any
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG    = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.FULL_IMAGE   = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${IMAGE_TAG}"
                    env.LATEST_IMAGE = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:latest"
                    echo "Tag de build : ${IMAGE_TAG}"
                }
            }
        }

        stage('Check Docker Image Exists') {
            agent {
                docker {
                    image 'docker:27.3.1-dind-alpine3.20'
                    args '--privileged'
                    reuseNode true
                }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    script {
                        def exists = sh(
                            script: '''
                                set +e
                                echo "$PASS" | docker login -u "$USER" --password-stdin >/dev/null
                                docker manifest inspect ${FULL_IMAGE} >/dev/null 2>&1
                                echo $?
                            ''',
                            returnStdout: true
                        ).trim()

                        env.SKIP_BUILD_PUSH = (exists == "0") ? "true" : "false"

                        echo (env.SKIP_BUILD_PUSH == "true" 
                            ? "Image déjà publiée → skip build & deploy" 
                            : "Nouvelle image → build & deploy")
                    }
                }
            }
        }

        stage('Build & Push Docker (Multi-stage)') {
            when { environment name: 'SKIP_BUILD_PUSH', value: 'false' }
            agent {
                docker {
                    image 'docker:27.3.1-dind-alpine3.20'
                    args '--privileged'
                    reuseNode true
                }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    sh '''
                        set -euo pipefail

                        echo "$PASS" | docker login -u "$USER" --password-stdin

                        DOCKER_BUILDKIT=1 docker build \
                            -t ${FULL_IMAGE} \
                            -t ${LATEST_IMAGE} \
                            --pull \
                            .

                        docker push ${FULL_IMAGE}
                        docker push ${LATEST_IMAGE}
                    '''
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when { environment name: 'SKIP_BUILD_PUSH', value: 'false' }
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

                        kubectl apply -f k8s/ --recursive --prune -l app=library-api

                        kubectl set image deployment/${DEPLOYMENT_NAME} \
                            library-api=${FULL_IMAGE} \
                            -n ${KUBE_NAMESPACE} \
                            --record

                        kubectl rollout status deployment/${DEPLOYMENT_NAME} \
                            -n ${KUBE_NAMESPACE} \
                            --timeout=300s

                        echo "🚀 Déploiement terminé avec succès !"
                    '''
                }
            }
        }
    }

    post {
        success { echo '🔥 Pipeline terminé avec succès !' }

        failure {
            script {
                if (env.SKIP_BUILD_PUSH == 'false') {
                    withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                        sh '''
                            kubectl rollout undo deployment/${DEPLOYMENT_NAME} \
                                -n ${KUBE_NAMESPACE} || true
                        '''
                    }
                }
            }
        }

        always {
            node('built-in') {
                sh 'docker image prune -f || true'
                cleanWs()
            }
        }
    }
}
