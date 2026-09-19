pluginManagement {
    repositories {
        // 国内镜像（优先，加速 Gradle 插件下载）
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        // 官方仓库（兜底，镜像缺失时回退）
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 国内镜像（优先，加速依赖下载）
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        // 官方仓库（兜底，镜像未同步新版本时回退）
        google()
        mavenCentral()
    }
}

rootProject.name = "SalaryTick"
include(":app")
