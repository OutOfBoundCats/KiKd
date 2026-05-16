plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":kikd-core"))
    testImplementation(kotlin("test"))
}
