pluginManagement {
    repositories {
        google()
        // Google-hosted mirror of Maven Central. repo.maven.apache.org
        // rate-limited (HTTP 429) the shared GitHub runner IPs in CI run
        // 45002710921 before any project code compiled; the mirror serves
        // the same artifacts without that limit. mavenCentral() stays as
        // the fallback. Refs #8.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
    }
}
rootProject.name = "GodsEyeViewAndroidV2"
include(":app", ":core-globe", ":core-ui", ":layer-api")
