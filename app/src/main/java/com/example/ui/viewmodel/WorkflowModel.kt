package com.example.ui.viewmodel

import org.json.JSONArray
import org.json.JSONObject

enum class WorkflowSource {
    SERVER, LOCAL
}

enum class WorkflowFieldType {
    TEXT, INT_SLIDER, FLOAT_SLIDER, DROPDOWN, TOGGLE, IMAGE_PICKER, SEED
}

enum class SectionType {
    LOADER, PROMPT, SAMPLER, NOISE, LATENT, PROCESSING, OUTPUT, FACE_SWAP, IMAGE_INPUT, UNKNOWN
}

data class NodeConnection(
    val sourceNodeId: String,
    val outputIndex: Int
)

data class WorkflowNode(
    val id: String,
    val classType: String,
    val inputs: Map<String, Any>,     // raw input values
    val connections: Map<String, NodeConnection>  // field → [nodeId, outputIndex]
)

data class NodeGraph(
    val nodes: Map<String, WorkflowNode>,
    val outputNodeId: String    // SaveImage or PreviewImage node
)

data class WorkflowField(
    val fieldName: String,
    val friendlyLabel: String,
    val fieldType: WorkflowFieldType,
    val currentValue: Any,
    val options: List<String> = emptyList(),    // for dropdowns
    val min: Float = 0f,
    val max: Float = 0f,
    val step: Float = 0f
)

data class WorkflowSection(
    val nodeId: String,
    val classType: String,
    val friendlyName: String,
    val editableFields: List<WorkflowField>,
    val connectionBadges: List<String>,   // display only
    val isCollapsedByDefault: Boolean,
    val sectionType: SectionType
)

data class LoadedWorkflow(
    val name: String,
    val source: WorkflowSource,  // SERVER or LOCAL
    val originalJson: String,
    val nodeGraph: NodeGraph,
    val orderedSections: List<WorkflowSection>
)
