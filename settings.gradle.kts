pluginManagement {

//    repositories {
//        maven {
//            url = uri("https://mirror-maven.runflare.com/android/maven2/")
//        }
//        maven {
//            url = uri("https://mirror-maven.runflare.com/maven2/")
//        }
//        maven {
//            url = uri("https://mirror-maven.runflare.com/gradle-plugins/")
//        }
//    }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

//    repositories {
//        maven {
//            url = uri("https://mirror-maven.runflare.com/android/maven2/")
//        }
//        maven {
//            url = uri("https://mirror-maven.runflare.com/maven2/")
//        }
//        maven {
//            url = uri("https://mirror-maven.runflare.com/gradle-plugins/")
//        }
//    }

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
    }

}

rootProject.name = "TehVPN-Kotlin"

include(":app")
include(":v2ray")
