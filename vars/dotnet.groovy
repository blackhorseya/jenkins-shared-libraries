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

    if (config.nuget != null) {
        nugetSource = "--source ${config.nuget}"
    }

    if (!config.useCache) {
        useCache = "--no-cache"
    }

    sh label: "dotnet core build", script: """
    dotnet build -v normal --nologo ${nugetSource} ${useCache} -c Release -o ./publish
    """
}

def test(Map configs) {
    def coverage = ""
    def report = ""
    def output = ""

    if (configs.genCoverage) {
        echo "gen coverage"
        coverage = "/p:CollectCoverage=true /p:CoverletOutputFormat=opencover /p:CoverletOutput=${PWD}/coverage/"
    }

    if (configs.genReport) {
        report = "--logger trx -r ./TestResults/report.trx"
    }

    if (configs.output) {
        output = "-o ./publish --no-build --no-restore"
    }
    
    sh label: "dotnet core test", script: """
    dotnet test ${coverage} ${report} ${output}
    """
}
