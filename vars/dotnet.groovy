def printInfo() {
    sh label: "print dotnet info", script: "dotnet --info"
}

def build(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def nugetSource = ""
    def useCache = ""

    if (!config.nuget?.trim()) {
        nugetSource = "--source ${config.nuget}"
    }

    if (!config.useCache) {
        useCache = "--no-cache"
    }

    sh label: "dotnet core build", script: """
    dotnet build -v normal --nologo ${nugetSource} ${useCache} -c Release -o ./publish
    """
}
