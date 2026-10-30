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
    val QWEN_3B = LlmModelItem(
        id = "qwen_2_5_3b",
        name = "Qwen2.5 3B Instruct",
        parameterCount = "3.09B",
        fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
        minExpectedBytes = 1_800_000_000L,
        downloadSizeDisplay = "~1.9 GB",
        ramRequiredDisplay = "~2.6 GB",
        tag = "Recomendado (6 GB)",
        isRecommendedFor6Gb = true,
        description = "Excelente razonamiento, vocabulario en español y análisis profundo sin riesgo de saturar 6 GB de RAM.",
        chatTemplateType = ChatTemplateType.CHATML
    )

    val QWEN_1_5B = LlmModelItem(
        id = "qwen_2_5_1_5b",
        name = "Qwen2.5 1.5B Instruct",
        parameterCount = "1.54B",
        fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        minExpectedBytes = 900_000_000L,
        downloadSizeDisplay = "~1.0 GB",
        ramRequiredDisplay = "~1.5 GB",
        tag = "Ultraligero",
        isRecommendedFor6Gb = false,
        description = "Mínimo consumo de RAM y batería. Máxima velocidad de respuesta.",
        chatTemplateType = ChatTemplateType.CHATML
    )

    val LLAMA_3B = LlmModelItem(
        id = "llama_3_2_3b",
        name = "Llama-3.2 3B Instruct",
        parameterCount = "3.21B",
        fileName = "llama-3.2-3b-instruct-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        minExpectedBytes = 1_850_000_000L,
        downloadSizeDisplay = "~2.0 GB",
        ramRequiredDisplay = "~2.5 GB",
        tag = "Instrucciones",
        isRecommendedFor6Gb = false,
        description = "Gran precisión en seguimiento de instrucciones estructuradas y esquemas JSON.",
        chatTemplateType = ChatTemplateType.LLAMA3
    )

    val DEEPSEEK_R1_7B = LlmModelItem(
        id = "deepseek_r1_distill_qwen_7b",
        name = "DeepSeek-R1 Distill Qwen 7B",
        parameterCount = "7.61B",
        fileName = "DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf",
        minExpectedBytes = 4_400_000_000L,
        downloadSizeDisplay = "~4.7 GB",
        ramRequiredDisplay = "~5.2 GB",
        tag = "Razonamiento R1",
        isRecommendedFor6Gb = false,
        description = "Modelo de razonamiento profundo con cadena de pensamiento (<think>). Máximo análisis lógico y matemático.",
        chatTemplateType = ChatTemplateType.CHATML
    )

    val ALL_MODELS = listOf(QWEN_3B, QWEN_1_5B, LLAMA_3B, DEEPSEEK_R1_7B)

    fun getById(id: String): LlmModelItem {
        return ALL_MODELS.find { it.id == id } ?: QWEN_3B
    }
}
