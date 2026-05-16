package org.kikd.core

@DslMarker
annotation class KikdDsl

interface CloudDefinition {
    val name: String
}

class KikdProject internal constructor(
    val clouds: List<CloudDefinition>,
)

@KikdDsl
class KikdProjectBuilder {
    private val clouds = mutableListOf<CloudDefinition>()

    fun addCloud(cloud: CloudDefinition) {
        require(clouds.none { it.name == cloud.name }) {
            "Cloud '${cloud.name}' has already been defined."
        }
        clouds += cloud
    }

    fun build(): KikdProject = KikdProject(clouds.toList())
}

fun kikdProject(block: KikdProjectBuilder.() -> Unit): KikdProject =
    KikdProjectBuilder().apply(block).build()

inline fun <reified T : CloudDefinition> KikdProject.cloud(): T? =
    clouds.filterIsInstance<T>().singleOrNull()
