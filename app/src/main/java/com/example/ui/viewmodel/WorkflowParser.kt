package com.example.ui.viewmodel

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object WorkflowParser {
    private const val TAG = "WorkflowParser"

    fun parseWorkflowJson(
        jsonStr: String,
        name: String,
        source: WorkflowSource,
        objectInfo: JSONObject?
    ): LoadedWorkflow {
        val root = JSONObject(jsonStr)
        val nodes = mutableMapOf<String, WorkflowNode>()
        var outputNodeId = ""

        // Trace positive/negative text prompts
        val positivePromptIds = mutableSetOf<String>()
        val negativePromptIds = mutableSetOf<String>()

        val keys = root.keys()
        while (keys.hasNext()) {
            val nodeId = keys.next()
            val nodeObj = root.optJSONObject(nodeId) ?: continue
            val classType = nodeObj.optString("class_type")

            val inputsObj = nodeObj.optJSONObject("inputs") ?: continue
            if (classType == "KSampler" || classType == "KSamplerAdvanced" || classType == "SamplerCustomAdvanced") {
                inputsObj.optJSONArray("positive")?.optString(0)?.let { posId ->
                    positivePromptIds.addAll(traceToSourceTextEncodeNode(root, posId))
                }
                inputsObj.optJSONArray("negative")?.optString(0)?.let { negId ->
                    negativePromptIds.addAll(traceToSourceTextEncodeNode(root, negId))
                }
            }
        }

        // Parse nodes
        val keys2 = root.keys()
        while (keys2.hasNext()) {
            val nodeId = keys2.next()
            val nodeObj = root.optJSONObject(nodeId) ?: continue
            val classType = nodeObj.optString("class_type")
            val inputsObj = nodeObj.optJSONObject("inputs") ?: continue

            if (classType == "SaveImage" || classType == "PreviewImage") {
                outputNodeId = nodeId
            }

            val inputs = mutableMapOf<String, Any>()
            val connections = mutableMapOf<String, NodeConnection>()

            val inputKeys = inputsObj.keys()
            while (inputKeys.hasNext()) {
                val inputKey = inputKeys.next()
                val value = inputsObj.get(inputKey)

                if (value is JSONArray && value.length() == 2) {
                    val first = value.opt(0)
                    val second = value.optInt(1, -1)
                    if ((first is String || first is Number) && second != -1) {
                        connections[inputKey] = NodeConnection(first.toString(), second)
                        continue
                    }
                }
                inputs[inputKey] = value
            }

            nodes[nodeId] = WorkflowNode(
                id = nodeId,
                classType = classType,
                inputs = inputs,
                connections = connections
            )
        }

        // Build Sections
        val sections = mutableListOf<WorkflowSection>()
        for ((nodeId, node) in nodes) {
            val sectionType = getSectionType(node.classType)

            // Connection badges
            val connectionBadges = mutableListOf<String>()
            for ((field, conn) in node.connections) {
                val sourceNode = nodes[conn.sourceNodeId]
                val sourceName = sourceNode?.let { getFriendlyNodeName(it.classType, conn.sourceNodeId, false, false) } ?: "Node ${conn.sourceNodeId}"
                connectionBadges.add("← $field connected from $sourceName")
            }

            // Editable fields
            val editableFields = mutableListOf<WorkflowField>()
            val nodeSchema = objectInfo?.optJSONObject(node.classType)
            val reqFieldsSchema = nodeSchema?.optJSONObject("input")?.optJSONObject("required")
            val optFieldsSchema = nodeSchema?.optJSONObject("input")?.optJSONObject("optional")

            for ((fieldName, value) in node.inputs) {
                // Determine field structure
                var fieldType = WorkflowFieldType.TEXT
                var friendlyLabel = fieldName.replace("_", " ").replaceFirstChar { it.uppercase() }
                var options = emptyList<String>()
                var min = 0f
                var max = 100f
                var step = 1f

                // Find schema in required or optional
                val fieldSchema = reqFieldsSchema?.optJSONArray(fieldName) ?: optFieldsSchema?.optJSONArray(fieldName)

                if (fieldName == "seed" || fieldName.endsWith("_seed")) {
                    fieldType = WorkflowFieldType.SEED
                    min = 0f
                    max = Long.MAX_VALUE.toFloat()
                } else if (node.classType == "LoadImage" && fieldName == "image") {
                    fieldType = WorkflowFieldType.IMAGE_PICKER
                } else if (fieldSchema != null) {
                    val typeDesc = fieldSchema.opt(0)
                    val configObj = fieldSchema.optJSONObject(1)

                    if (typeDesc is JSONArray) {
                        fieldType = WorkflowFieldType.DROPDOWN
                        options = List(typeDesc.length()) { typeDesc.getString(it) }
                    } else {
                        val typeStr = typeDesc.toString()
                        when (typeStr) {
                            "INT" -> {
                                fieldType = WorkflowFieldType.INT_SLIDER
                                min = configObj?.optDouble("min", 1.0)?.toFloat() ?: 1f
                                max = configObj?.optDouble("max", 500.0)?.toFloat() ?: 500f
                                step = configObj?.optDouble("step", 1.0)?.toFloat() ?: 1f
                            }
                            "FLOAT" -> {
                                fieldType = WorkflowFieldType.FLOAT_SLIDER
                                min = configObj?.optDouble("min", 0.0)?.toFloat() ?: 0f
                                max = configObj?.optDouble("max", 1.0)?.toFloat() ?: 1f
                                step = configObj?.optDouble("step", 0.05)?.toFloat() ?: 0.05f
                            }
                            "BOOLEAN", "BOOL" -> {
                                fieldType = WorkflowFieldType.TOGGLE
                            }
                            "STRING" -> {
                                fieldType = WorkflowFieldType.TEXT
                            }
                            else -> {
                                // Try checking combo options in configObj (e.g. models dropdowns sometimes returned differently)
                                if (configObj != null && configObj.has("choices")) {
                                    fieldType = WorkflowFieldType.DROPDOWN
                                    val choiceArr = configObj.optJSONArray("choices")
                                    if (choiceArr != null) {
                                        options = List(choiceArr.length()) { choiceArr.getString(it) }
                                    }
                                } else {
                                    fieldType = WorkflowFieldType.TEXT
                                }
                            }
                        }
                    }
                } else {
                    // Fallback heuristics based on value type
                    when (value) {
                        is Boolean -> fieldType = WorkflowFieldType.TOGGLE
                        is Int -> {
                            fieldType = WorkflowFieldType.INT_SLIDER
                            min = 1f
                            max = if (fieldName.contains("step")) 100f else 2048f
                            step = 1f
                        }
                        is Long -> {
                            fieldType = WorkflowFieldType.INT_SLIDER
                            min = 0f
                            max = 1000f
                            step = 1f
                        }
                        is Double -> {
                            fieldType = WorkflowFieldType.FLOAT_SLIDER
                            min = 0f
                            max = if (fieldName.contains("denoise") || fieldName.contains("strength")) 1.0f else 20.0f
                            step = 0.05f
                        }
                        is Float -> {
                            fieldType = WorkflowFieldType.FLOAT_SLIDER
                            min = 0f
                            max = if (fieldName.contains("denoise") || fieldName.contains("strength")) 1.0f else 20.0f
                            step = 0.05f
                        }
                        else -> fieldType = WorkflowFieldType.TEXT
                    }
                }

                // Friendly Labels
                if (fieldName == "text" && node.classType == "CLIPTextEncode") {
                    friendlyLabel = "Prompt"
                }

                editableFields.add(
                    WorkflowField(
                        fieldName = fieldName,
                        friendlyLabel = friendlyLabel,
                        fieldType = fieldType,
                        currentValue = value,
                        options = options,
                        min = min,
                        max = max,
                        step = step
                    )
                )
            }

            val isPositive = positivePromptIds.contains(nodeId)
            val isNegative = negativePromptIds.contains(nodeId)

            val isCollapsed = when (sectionType) {
                SectionType.LOADER, SectionType.UNKNOWN -> true
                else -> false
            }

            sections.add(
                WorkflowSection(
                    nodeId = nodeId,
                    classType = node.classType,
                    friendlyName = getFriendlyNodeName(node.classType, nodeId, isPositive, isNegative),
                    editableFields = editableFields,
                    connectionBadges = connectionBadges,
                    isCollapsedByDefault = isCollapsed,
                    sectionType = sectionType
                )
            )
        }

        // Sort sections topologically / logically
        val orderedSections = sections.sortedBy { getSectionTypeOrder(it.sectionType) }

        return LoadedWorkflow(
            name = name,
            source = source,
            originalJson = jsonStr,
            nodeGraph = NodeGraph(nodes, outputNodeId),
            orderedSections = orderedSections
        )
    }

    private fun traceToSourceTextEncodeNode(root: JSONObject, startNodeId: String): Set<String> {
        val results = mutableSetOf<String>()
        fun dfs(nodeId: String, visited: MutableSet<String>) {
            if (visited.contains(nodeId)) return
            visited.add(nodeId)
            val node = root.optJSONObject(nodeId) ?: return
            val classType = node.optString("class_type")
            if (classType == "CLIPTextEncode") {
                results.add(nodeId)
                return
            }
            // Trace upstream connections
            val inputs = node.optJSONObject("inputs") ?: return
            val keys = inputs.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = inputs.optJSONArray(key)
                if (value != null && value.length() == 2) {
                    val connectedNodeId = value.optString(0)
                    if (connectedNodeId.isNotEmpty()) {
                        dfs(connectedNodeId, visited)
                    }
                }
            }
        }
        dfs(startNodeId, mutableSetOf())
        return results
    }

    private fun getSectionType(classType: String): SectionType {
        return when (classType) {
            "UNETLoader", "UnetLoaderGGUF", "CheckpointLoaderSimple", "DualCLIPLoader", "CLIPLoader", "VAELoader",
            "LoraLoader", "LoraLoaderModelOnly", "ControlNetLoader", "ReActorLoadFaceModel" -> SectionType.LOADER

            "CLIPTextEncode" -> SectionType.PROMPT

            "KSampler", "KSamplerAdvanced", "SamplerCustomAdvanced", "BasicScheduler", "BasicGuider", "KSamplerSelect" -> SectionType.SAMPLER

            "RandomNoise" -> SectionType.NOISE

            "EmptyLatentImage", "EmptySD3LatentImage", "LatentUpscale" -> SectionType.LATENT

            "ControlNetApply", "CLIPSetLastLayer", "ImageUpscaleWithModel", "UpscaleModelLoader" -> SectionType.PROCESSING

            "ReActorFaceSwap" -> SectionType.FACE_SWAP

            "LoadImage" -> SectionType.IMAGE_INPUT

            "SaveImage", "PreviewImage" -> SectionType.OUTPUT

            else -> SectionType.UNKNOWN
        }
    }

    private fun getSectionTypeOrder(type: SectionType): Int {
        return when (type) {
            SectionType.LOADER -> 1
            SectionType.PROMPT -> 2
            SectionType.LATENT -> 3
            SectionType.NOISE -> 4
            SectionType.SAMPLER -> 5
            SectionType.PROCESSING -> 6
            SectionType.FACE_SWAP -> 7
            SectionType.IMAGE_INPUT -> 8
            SectionType.OUTPUT -> 9
            SectionType.UNKNOWN -> 10
        }
    }

    private fun getFriendlyNodeName(
        classType: String,
        nodeId: String,
        isPositivePrompt: Boolean,
        isNegativePrompt: Boolean
    ): String {
        if (classType == "CLIPTextEncode") {
            if (isPositivePrompt) return "Positive Text Prompt"
            if (isNegativePrompt) return "Negative Text Prompt"
            return "Text Prompt"
        }
        return when (classType) {
            "UNETLoader" -> "Flux Model Loader"
            "UnetLoaderGGUF" -> "Flux GGUF Loader"
            "CheckpointLoaderSimple" -> "Checkpoint Model"
            "DualCLIPLoader" -> "CLIP Loader (Dual)"
            "CLIPLoader" -> "CLIP Loader"
            "VAELoader" -> "VAE Loader"
            "EmptyLatentImage" -> "Canvas Size"
            "EmptySD3LatentImage" -> "Canvas Size (Flux)"
            "KSampler" -> "Sampler Settings"
            "KSamplerAdvanced" -> "Advanced Sampler"
            "SamplerCustomAdvanced" -> "Custom Sampler"
            "BasicScheduler" -> "Scheduler"
            "BasicGuider" -> "Guider"
            "FluxGuidance" -> "Flux Guidance Scale"
            "RandomNoise" -> "Noise / Seed"
            "KSamplerSelect" -> "Sampler Type"
            "LatentUpscale" -> "Latent Upscaler"
            "VAEDecode" -> "VAE Decoder"
            "SaveImage" -> "Output Settings"
            "PreviewImage" -> "Preview Output"
            "LoadImage" -> "Input Image"
            "LoraLoader" -> "LoRA"
            "LoraLoaderModelOnly" -> "LoRA (Model Only)"
            "ControlNetLoader" -> "ControlNet Model"
            "ControlNetApply" -> "ControlNet Apply"
            "ReActorFaceSwap" -> "Face Swap (ReActor)"
            "ReActorLoadFaceModel" -> "Face Swap Model"
            "UpscaleModelLoader" -> "Upscale Model"
            "ImageUpscaleWithModel" -> "Image Upscaler"
            "CLIPSetLastLayer" -> "CLIP Skip"
            else -> classType
        }
    }

    fun rebuildWorkflowJson(
        originalJson: String,
        sections: List<WorkflowSection>
    ): String {
        val root = JSONObject(originalJson)
        for (section in sections) {
            val nodeObj = root.optJSONObject(section.nodeId) ?: continue
            val inputsObj = nodeObj.optJSONObject("inputs") ?: continue

            for (field in section.editableFields) {
                val valToStore = when (field.fieldType) {
                    WorkflowFieldType.INT_SLIDER -> {
                        val d = field.currentValue.toString().toDoubleOrNull()
                        d?.toInt() ?: field.currentValue
                    }
                    WorkflowFieldType.FLOAT_SLIDER -> {
                        field.currentValue.toString().toDoubleOrNull() ?: field.currentValue
                    }
                    WorkflowFieldType.TOGGLE -> {
                        if (field.currentValue is Boolean) field.currentValue
                        else field.currentValue.toString().lowercase().toBoolean()
                    }
                    WorkflowFieldType.SEED -> {
                        field.currentValue.toString().toLongOrNull() ?: field.currentValue
                    }
                    else -> field.currentValue
                }
                inputsObj.put(field.fieldName, valToStore)
            }
        }
        return root.toString()
    }
}
