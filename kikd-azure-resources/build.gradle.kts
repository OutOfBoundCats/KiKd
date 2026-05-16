plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":kikd-azure"))
    testImplementation(kotlin("test"))
}
