def read() {
    def configFile = env.CI_CONFIG_FILE ?: 'ci.properties'

    return readProperties(
        text: libraryResource("config/${configFile}")
    )
}

def requireProperties(Map config, String... propertyNames) {

    propertyNames.each { propertyName ->

        if (!config[propertyName]?.trim()) {
            error "${propertyName} is not configured"
        }

    }
}

return this