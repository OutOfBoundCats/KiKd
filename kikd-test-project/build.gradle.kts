plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":kikd-core"))
    implementation(project(":kikd-azure"))
    implementation(project(":kikd-azure-pipelines"))
    implementation(project(":kikd-azure-resources"))
    testImplementation(kotlin("test"))
}
