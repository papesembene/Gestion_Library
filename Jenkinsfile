pipeline {
    agent none

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 40, unit: 'MINUTES')
        timestamps()
    }

    environment {
        DOCKER_USER         = "papesembene"
        DOCKER_IMAGE_NAME   = "library-api"
        KUBE_NAMESPACE      = "library"
        DEPLOYMENT_NAME     = "library-api-deployment"
        SKIP_BUILD_PUSH     = "false"
    }

    stages {
        // === NOUVELLE VERSION QUI MARCHE À TOUS LES COUPS ===
        stage('Prepare') {
            agent any
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG    = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.FULL_IMAGE   = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:${env.IMAGE_TAG}"
                    env.LATEST_IMAGE = "docker.io/${DOCKER_USER}/${DOCKER_IMAGE_NAME}:latest"
                    echo "Tag de cette build : ${env.IMAGE_TAG}"
                }
            }
        }
        // === le reste du pipeline reste exactement le même que la dernière version qui passait le parsing ===
        stage('Build Maven') {
            agent {
                docker {
                    image 'maven:3.9.9-eclipse-temurin-17-alpine'
                    args '-v maven-repo:/root/.m2 --user root'
                    reuseNode true
                }
            }
            steps { sh 'mvn -B clean verify' }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
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
                        def exists = sh(script: '''
                            echo "$PASS" | docker login -u "$USER" --password-stdin >/dev/null
                            docker manifest inspect ${FULL_IMAGE} >/dev/null 2>&1 && echo true || echo false
                        ''', returnStdout: true).trim()
                        env.SKIP_BUILD_PUSH = (exists == 'true') ? 'true' : 'false'
                        echo env.SKIP_BUILD_PUSH == 'true' ? 
                            "Image déjà sur Docker Hub → skip build & deploy" : 
                            "Nouvelle image à builder"
                    }
                }
            }
        }

        stage('Build & Push Docker') {
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
                        cp target/*.jar app.jar
                        echo "$PASS" | docker login -u "$USER" --password-stdin
                        DOCKER_BUILDKIT=1 docker build --build-arg JAR_FILE=app.jar -t ${FULL_IMAGE} -t ${LATEST_IMAGE} --pull --no-cache .
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
                        kubectl apply -f k8s/ --recursive --prune -l app=library-api || true
                        kubectl set image deployment/${DEPLOYMENT_NAME} library-api=${FULL_IMAGE} -n ${KUBE_NAMESPACE} --record
                        kubectl rollout status deployment/${DEPLOYMENT_NAME} -n ${KUBE_NAMESPACE} --timeout=300s
                        echo "DÉPLOIEMENT RÉUSSI !"
                    '''
                }
            }
        }
    }

    post {
        success { echo 'Tout est OK !' }
        failure {
            script {
                if (env.SKIP_BUILD_PUSH == 'false') {
                    withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                        sh 'kubectl rollout undo deployment/${DEPLOYMENT_NAME} -n ${KUBE_NAMESPACE} || true'
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