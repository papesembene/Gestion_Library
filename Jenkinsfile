pipeline {
  agent any

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
    stage('Setup Environment') {
      steps {
        sh '''
          apt-get update
          apt-get install -y default-jdk maven curl wget gnupg lsb-release
          # Install Docker
          curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
          echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
          apt-get update
          apt-get install -y docker-ce docker-ce-cli containerd.io
          # Install kubectl
          curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
          chmod +x kubectl
          mv kubectl /usr/local/bin/
          java -version
          mvn --version
          kubectl version --client
          docker --version
        '''
      }
    }

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
