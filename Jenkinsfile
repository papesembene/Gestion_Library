pipeline {
  agent {
    docker {
      image 'dtzar/helm-kubectl:3.12.0'
      args '-u root'
    }
  }

  environment {
    DOCKER_IMAGE = "papesembene/library-api:${env.GIT_COMMIT}"
    KUBE_NAMESPACE = "library"
    SPRING_DATASOURCE_URL = credentials('db-url')
    SPRING_DATASOURCE_USERNAME = credentials('db-username')
    SPRING_DATASOURCE_PASSWORD = credentials('db-password')
    JWT_SECRET = credentials('jwt-secret')
    SERVER_PORT = "8080"
    DB_POOL_SIZE = "10"
    JWT_EXPIRATION = "3600000"
  }

  stages {
    stage('Checkout') {
      steps { git branch: 'main', url: 'https://github.com/papesembene/Gestion_Library.git' }
    }

    stage('Build Maven') {
      steps { sh './mvnw clean package' }
    }

    stage('Build Docker Image') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
          sh 'echo $PASS | docker login -u $USER --password-stdin'
          sh "docker build -t $DOCKER_IMAGE ."
          sh "docker push $DOCKER_IMAGE"
        }
      }
    }

    stage('Deploy Kubernetes') {
      steps {
        withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
          sh '''
            kubectl apply -f k8s/namespace.yaml
            kubectl apply -f k8s/secrets.yaml
            kubectl apply -f k8s/service.yaml
            kubectl apply -f k8s/deployment.yaml
            kubectl set image deployment/library-api-deployment library-api=${DOCKER_IMAGE} -n library --record
            kubectl rollout status deployment/library-api-deployment -n library
          '''
        }
      }
    }
  }

  post {
    failure {
      echo 'Pipeline failed'
    }
    success {
      echo 'Deployment successful'
    }
  }
}
