def helmInfo() {
    sh label: "print helm info", script: "helm version"
}

def helmListWithEnv(String env = "all") {
    def ns = "--all-namespace"
    if (env != "all") {
        ns = "--namespace=${env}"
    }

    sh label: "list releases with ${env}", script: """
    helm list ${ns}
    """
}

def helmUpgrade(Map configs) {
    if (configs.imageName == null) {
        error("missing imageName from parameters")
    }

    if (configs.version == null) {
        error("missing version from parameters")
    }

    if (configs.env == null) {
        error("missing env from parameters")
    }

    if (configs.appName == null) {
        error("missing appName from parameters")
    }

    def values = "deploy/config/${configs.env}/values.yaml"
    if (configs.valuesPath != null) {
        values = configs.valuesPath
    }

    def releasePrefix = ""
    if (configs.env != "prod") {
        releasePrefix = "${configs.env}-"
    }

    def chart = "deploy/helm"
    if (configs.chartPath != null) {
        chart = confgis.chartPath
    }

    sh label: "deploy to ${configs.env} with ${configs.imageName}:${configs.version}", script: """
    helm --namespace=${configs.env} upgrade --install ${releasePrefix}${appName} ${chart} \
    -f ${values} \
    --set image.tag=${configs.version}
    """
}
