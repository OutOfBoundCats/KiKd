plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":kikd-core"))
    implementation(project(":kikd-azure"))
    implementation(project(":kikd-azure-pipelines"))
    implementation(project(":kikd-azure-resources"))
    implementation(project(":kikd-azure-integration"))
}

application {
    mainClass = "org.kikd.examples.SimpleInfrastructureExample"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

fun registerExampleRunTask(taskName: String, exampleClass: String) {
    tasks.register<JavaExec>(taskName) {
        group = "application"
        description = "Runs $exampleClass"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = exampleClass
        workingDir = rootProject.projectDir
    }
}

registerExampleRunTask("runSimpleInfrastructureExample", "org.kikd.examples.SimpleInfrastructureExample")
registerExampleRunTask("runNestedNetworkExample", "org.kikd.examples.NestedNetworkExample")
registerExampleRunTask("runStageVariablesExample", "org.kikd.examples.StageVariablesExample")
registerExampleRunTask("runVariableGroupExample", "org.kikd.examples.VariableGroupExample")
registerExampleRunTask("runStageOutputVariablesExample", "org.kikd.examples.StageOutputVariablesExample")
