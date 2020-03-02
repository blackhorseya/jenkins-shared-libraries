def gitAddTag(String credentialId, String env, String version) {
    if (credentialId == null) {
        error("missing credentialId from parameters")
    }

    if (env == null) {
        error("missing env from parameters")
    }

    if (version == null) {
        error("missing version from parameters")
    }

    def postfix = ""
    switch(env) {
        case "dev":
            postfix = "alpha"
            break
        case "stg":
            postfix = "beta"
            break
    }

    sshagent(["${credentialId}"]) {
        sh label: "git tag new v${VERSION}-alpha", script: """
        git tag --delete v${VERSION}-alpha | exit 0 && git push --delete origin v${VERSION}-alpha | exit 0
        git tag v${VERSION}-alpha && git push --tags
        """
    }
}