package com.example.data.local.llm

enum class ChatTemplateType {
    CHATML,
    LLAMA3
}

data class LlmModelItem(
    val id: String,
    val name: String,
    val parameterCount: String,
    val fileName: String,
    val downloadUrl: String,
    val minExpectedBytes: Long,
    val downloadSizeDisplay: String,
    val ramRequiredDisplay: String,
    val tag: String,
    val isRecommendedFor6Gb: Boolean,
    val description: String,
    val chatTemplateType: ChatTemplateType = ChatTemplateType.CHATML
)

object LlmModelCatalog {
    val DEEPSEEK_R1_1_5B_Q4 = LlmModelItem(
        id = "deepseek_r1_1_5b",
        name = "DeepSeek-R1-Distill-Qwen-1.5B (Q4_K_M)",
        parameterCount = "1.54B",
        fileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        minExpectedBytes = 1_000_000_000L,
        downloadSizeDisplay = "~1.1 GB",
        ramRequiredDisplay = "~1.5 GB",
        tag = "2 GB RAM",
        isRecommendedFor6Gb = false,
        description = "Diseñado para teléfonos con 2 GB de RAM. Razonamiento profundo (<think>) ultra-eficiente.",
        chatTemplateType = ChatTemplateType.CHATML
    )

    val SMALLTHINKER_3B_Q4 = LlmModelItem(
        id = "smallthinker_3b",
        name = "SmallThinker-3B-Preview (Q4_K_M)",
        parameterCount = "3.00B",
        fileName = "SmallThinker-3B-Preview-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/SmallThinker-3B-Preview-GGUF/resolve/main/SmallThinker-3B-Preview-Q4_K_M.gguf",
        minExpectedBytes = 1_900_000_000L,
        downloadSizeDisplay = "~2.0 GB",
        ramRequiredDisplay = "~2.5 GB",
        tag = "3 GB RAM",
        isRecommendedFor6Gb = true,
        description = "Especializado en cadenas de pensamiento largo (CoT). Gran equilibrio de velocidad y precisión.",
        chatTemplateType = ChatTemplateType.CHATML
    )

    val DEEPSEEK_R1_7B_Q3 = LlmModelItem(
        id = "deepseek_r1_7b_q3",
        name = "DeepSeek-R1-Distill-Qwen-7B (Q3_K_M)",
        parameterCount = "7.61B",
        fileName = "DeepSeek-R1-Distill-Qwen-7B-Q3_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-7B-Q3_K_M.gguf",
        minExpectedBytes = 3_300_000_000L,
        downloadSizeDisplay = "~3.6 GB",
        ramRequiredDisplay = "~3.9 GB",
        tag = "4 GB RAM",
        isRecommendedFor6Gb = false,
        description = "Máxima capacidad analítica con razonamiento R1 optimizado en Q3_K_M para teléfonos con 4 GB de RAM.",
        chatTemplateType = ChatTemplateType.CHATML
    )

    val LEGACY_MODEL_FILENAMES = setOf(
        "qwen2.5-3b-instruct-q4_k_m.gguf",
        "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        "llama-3.2-3b-instruct-q4_k_m.gguf",
        "DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf"
    )

    val ALL_MODELS = listOf(DEEPSEEK_R1_1_5B_Q4, SMALLTHINKER_3B_Q4, DEEPSEEK_R1_7B_Q3)

    fun getById(id: String): LlmModelItem {
        return ALL_MODELS.find { it.id == id } ?: SMALLTHINKER_3B_Q4
    }
}
