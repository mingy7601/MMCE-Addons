//pluginManagement {
//    repositories {
//        gradlePluginPortal()
//        mavenCentral()
//
//        maven {
//            name = "GTNH Maven"
//            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
//            mavenContent {
//                includeGroup("com.gtnewhorizons")
//                includeGroupByRegex("com\\.gtnewhorizons\\..+")
//            }
//        }
//    }
//}

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()

        maven("https://nexus.gtnewhorizons.com/repository/public/")
    }
}