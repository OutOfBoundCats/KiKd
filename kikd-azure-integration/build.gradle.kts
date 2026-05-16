plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":kikd-azure-pipelines"))
    api(project(":kikd-azure-resources"))
    testImplementation(kotlin("test"))
}
