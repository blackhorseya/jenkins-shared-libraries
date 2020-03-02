def buildAndPushImage(Map configs) {
    if (configs.imageName == null) {
        error("missing imageName from parameters")
    }

    if (configs.registryUser == null) {
        error("missing registryUser from parameters")
    }

    if (configs.registryPassword == null) {
        error("missing registryPassword from parameters")
    }

    if (configs.version == null) {
        error("missing version from parameters")
    }

    sh label: "docker login to dockerhub", script: """
    docker login --username ${configs.registryUser} --password ${configs.registryPassword}
    """
    sh label: "docker build image ${configs.version}", script: """
    docker build -t ${configs.imageName}:latest -f Dockerfile --network host .
    docker tag ${configs.imageName}:latest ${configs.imageName}:${configs.version}
    """
    sh label: "docker push image", script: """
    docker push ${configs.imageName}:latest
    docker push ${configs.imageName}:${configs.version}
    """
    sh label: "print new 5 images", script: """
    docker images ${configs.imageName} | sort -r -V | header -n 5
    """
}