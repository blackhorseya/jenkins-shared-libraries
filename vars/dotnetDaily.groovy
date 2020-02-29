def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    pipeline {
        environment {
            // application settings
            FULL_VERSION = "${config.Version}.${BUILD_ID}"
            IMAGE_NAME = "${DOCKERHUB_USR}/${config.AppName}"

            // docker credentials
            DOCKERHUB = credentials('docker-hub-credential')

            // sonarqube settings
            SONARQUBE_HOST_URL = "https://sonar.blackhorseya.com"
            SONARQUBE_TOKEN = credentials('sonarqube-token')

            // kubernetes settings
            KUBE_NS = "dev"
            KUBE_CONFIG_FILE = credentials('kube-config')
        }
        agent {
            kubernetes {
                yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: dotnet-builder
    image: blackhorseya/dotnet-builder:3.1-alpine
    command: ['cat']
    tty: true
  - name: docker
    image: docker:latest
    command: ['cat']
    tty: true
    volumeMounts:
    - name: dockersock
      mountPath: /var/run/docker.sock
  - name: helm
    image: alpine/helm:3.0.1
    command: ['cat']
    tty: true
  volumes:
  - name: dockersock
    hostPath:
      path: /var/run/docker.sock
"""
            }
        }
        stages {
            stage('Prepare') {
                steps {
                    echo """
Perform ${env.JOB_NAME} for
Repo: ${env.GIT_URL}
Branch: ${env.GIT_BRANCH}
Application: ${config.AppName}:${FULL_VERSION}
"""
                    sh '''
                    printenv | sort
                    '''
                    
                    container('dotnet-builder') {
                        sh 'dotnet --info'
                    }
                    
                    container('docker') {
                        sh 'docker info'
                        sh 'docker version'
                    }

                    container('helm') {
                        sh 'helm version'
                        sh 'mkdir -p /root/.kube/ && cp $KUBE_CONFIG_FILE /root/.kube/config'
                    }
                }
            }

            stage('Build') {
                steps {
                    container('dotnet-builder') {
                        sh """
                        echo '### sonarscanner begin ###'
                        dotnet sonarscanner begin /k:\"${config.AppName}\" \
                        /v:${FULL_VERSION} \
                        /d:sonar.host.url=${SONARQUBE_HOST_URL} \
                        /d:sonar.login=${SONARQUBE_TOKEN} \
                        /d:sonar.exclusions=**/*.js,**/*.ts,**/*.css,bin/**/*,obj/**/*,wwwroot/**/*,ClientApp/**/* \
                        /d:sonar.cs.opencover.reportsPaths=${PWD}/coverage/coverage.opencover.xml \
                        /d:sonar.coverage.exclusions=**/Entities/**/*,test/**/* \
                        /d:sonar.cs.vstest.reportsPaths=${PWD}/TestResults/report.trx
                        """
                        sh 'dotnet build -c Release -o ./publish'
                    }
                }
            }

            stage('Test') {
                steps {
                    container('dotnet-builder') {
                        sh '''
                        echo '### dotnet test with code coverage and test report ###'
                        dotnet test /p:CollectCoverage=true \
                        /p:CoverletOutputFormat=opencover \
                        /p:CoverletOutput=$(pwd)/coverage/ \
                        --logger trx \
                        -r ./TestResults/report.trx \
                        -o ./publish \
                        --no-build --no-restore
                        '''
                    }
                }
            }

            stage('Static Code Analysis') {
                steps {
                    container('dotnet-builder') {
                        sh '''
                        echo '### sonarscanner end ###'
                        dotnet sonarscanner end /d:sonar.login=${SONARQUBE_TOKEN}
                        '''
                    }
                }
            }

            stage('Build and push docker image') {
                steps {
                    container('docker') {
                        sh 'docker build -t ${IMAGE_NAME}:latest -f Dockerfile --network host .'
                        sh 'docker login --username ${DOCKERHUB_USR} --password ${DOCKERHUB_PSW}'
                        sh 'docker push ${IMAGE_NAME}:latest'
                        sh 'docker tag ${IMAGE_NAME}:latest ${IMAGE_NAME}:${FULL_VERSION}'
                        sh 'docker push ${IMAGE_NAME}:${FULL_VERSION}'
                        sh 'docker images ${IMAGE_NAME}'
                    }
                }
            }
        }
    }
}