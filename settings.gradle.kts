import org.gradle.kotlin.dsl.mavenCentral

rootProject.name = "vMessageRecord"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.szymonoff.me/repository/fishy-dependencies/")
        maven { url = uri("https://jitpack.io") }
    }
}
