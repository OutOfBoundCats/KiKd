package org.kikd.core

data class KikdProjectPlan(
    val project: KikdProject,
    val graph: KikdDependencyGraph,
)

data class KikdDependencyGraph(
    val nodes: List<KikdGraphNode>,
    val dependencyEdges: List<KikdDependencyEdge>,
) {
    fun node(id: String): KikdGraphNode? = nodesById[id]

    fun childrenOf(parentId: String): List<KikdGraphNode> =
        nodes.filter { it.parentId == parentId }

    fun dependenciesOf(nodeId: String): List<KikdDependencyEdge> =
        dependencyEdges.filter { it.dependentId == nodeId }

    private val nodesById: Map<String, KikdGraphNode> = nodes.associateBy { it.id }
}

data class KikdGraphNode(
    val id: String,
    val kind: KikdGraphNodeKind,
    val label: String,
    val parentId: String? = null,
    val payload: Any? = null,
)

enum class KikdGraphNodeKind {
    PROJECT,
    CLOUD,
    COMPONENT,
    PIPELINE,
    STAGE,
    JOB,
    STEP,
    ARTIFACT,
    INFRASTRUCTURE,
    STACK,
    RESOURCE,
}

data class KikdDependencyEdge(
    val dependentId: String,
    val dependencyId: String,
    val kind: KikdDependencyKind,
)

enum class KikdDependencyKind {
    STAGE_DEPENDS_ON,
    JOB_DEPENDS_ON,
    STEP_DEPENDS_ON,
    ARTIFACT_PRODUCED_BY,
    ARTIFACT_CONSUMED_BY,
    STACK_DEPENDS_ON,
    RESOURCE_DEPENDS_ON,
}

interface KikdGraphContributor {
    fun contributeGraph(builder: KikdGraphBuilder)
}

interface PlanAwareGeneratorBackend : GeneratorBackend {
    fun generate(plan: KikdProjectPlan): List<GeneratedFile>

    override fun generate(project: KikdProject): List<GeneratedFile> =
        generate(KikdProjectPlanner.plan(project))
}

object KikdProjectPlanner {
    const val ROOT_ID: String = "project"

    fun plan(project: KikdProject): KikdProjectPlan {
        val builder = KikdGraphBuilder()
        builder.node(
            id = ROOT_ID,
            kind = KikdGraphNodeKind.PROJECT,
            label = "Project",
        )
        project.clouds.filterIsInstance<KikdGraphContributor>().forEach {
            it.contributeGraph(builder)
        }
        return KikdProjectPlan(project, builder.build())
    }
}

class KikdGraphBuilder {
    private val nodes = linkedMapOf<String, KikdGraphNode>()
    private val dependencyEdges = mutableListOf<KikdDependencyEdge>()

    fun node(
        id: String,
        kind: KikdGraphNodeKind,
        label: String,
        parentId: String? = null,
        payload: Any? = null,
    ): KikdGraphNode {
        require(id.isNotBlank()) { "Graph node id cannot be blank." }
        require(nodes[id] == null) { "Graph node '$id' has already been defined." }
        return KikdGraphNode(
            id = id,
            kind = kind,
            label = label,
            parentId = parentId,
            payload = payload,
        ).also { nodes[id] = it }
    }

    fun dependsOn(
        dependentId: String,
        dependencyId: String,
        kind: KikdDependencyKind,
    ) {
        dependencyEdges += KikdDependencyEdge(
            dependentId = dependentId,
            dependencyId = dependencyId,
            kind = kind,
        )
    }

    fun build(): KikdDependencyGraph {
        val graph = KikdDependencyGraph(nodes.values.toList(), dependencyEdges.toList())
        validateParents(graph)
        validateDependencies(graph)
        validateArtifacts(graph)
        validateAcyclic(graph)
        return graph
    }

    private fun validateParents(graph: KikdDependencyGraph) {
        graph.nodes.forEach { node ->
            val parentId = node.parentId ?: return@forEach
            require(graph.node(parentId) != null) {
                "Graph node '${node.id}' references missing parent '$parentId'."
            }
        }
    }

    private fun validateDependencies(graph: KikdDependencyGraph) {
        graph.dependencyEdges.forEach { edge ->
            require(graph.node(edge.dependentId) != null) {
                "Graph dependency references missing dependent '${edge.dependentId}'."
            }
            require(graph.node(edge.dependencyId) != null) {
                "Graph dependency references missing dependency '${edge.dependencyId}'."
            }
        }
    }

    private fun validateArtifacts(graph: KikdDependencyGraph) {
        val producedArtifacts = graph.dependencyEdges
            .filter { it.kind == KikdDependencyKind.ARTIFACT_PRODUCED_BY }
            .map { it.dependentId }
            .toSet()
        val consumedArtifacts = graph.dependencyEdges
            .filter { it.kind == KikdDependencyKind.ARTIFACT_CONSUMED_BY }
            .map { it.dependencyId }
            .toSet()

        val missing = consumedArtifacts - producedArtifacts
        require(missing.isEmpty()) {
            "Artifact dependencies are missing producers: ${missing.sorted().joinToString()}."
        }
    }

    private fun validateAcyclic(graph: KikdDependencyGraph) {
        val dependenciesByNode = graph.dependencyEdges.groupBy { it.dependentId }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(nodeId: String, path: List<String>) {
            if (nodeId in visited) return
            require(nodeId !in visiting) {
                "Dependency cycle detected: ${(path + nodeId).joinToString(" -> ")}."
            }
            visiting += nodeId
            dependenciesByNode[nodeId].orEmpty().forEach { edge ->
                visit(edge.dependencyId, path + nodeId)
            }
            visiting -= nodeId
            visited += nodeId
        }

        graph.nodes.forEach { visit(it.id, emptyList()) }
    }
}

fun graphIdSegment(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "unnamed" }
