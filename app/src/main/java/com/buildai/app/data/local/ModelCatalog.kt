package com.buildai.app.data.local

/** Curated text GGUF files pinned to publisher revisions and their LFS SHA-256 digests. */
data class CatalogModel(
    val id: String,
    val name: String,
    val minRamGb: Int,
    val sizeBytes: Long,
    val repository: String,
    val revision: String,
    val fileName: String,
    val sha256: String,
    val guidance: String
) {
    val downloadUrl: String get() = "https://huggingface.co/$repository/resolve/$revision/$fileName?download=true"
    val sourceUrl: String get() = "https://huggingface.co/$repository/tree/$revision"
}

object ModelCatalog {
    val models = listOf(
        CatalogModel("qwen-06", "Qwen3 0.6B · Q4_0", 4, 428970080L,
            "ggml-org/Qwen3-0.6B-GGUF", "b5f37287796e5be0ea3dab2e7430873fb3f73e49",
            "Qwen3-0.6B-Q4_0.gguf", "da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4",
            "Basic streaming, arithmetic, history and cancellation checked on an x86 emulator. Native builder development trials failed; phone performance remains unverified."),
        CatalogModel("qwen-17", "Qwen3 1.7B · Q8_0", 6, 1834426016L,
            "Qwen/Qwen3-1.7B-GGUF", "90862c4b9d2787eaed51d12237eafdfe7c5f6077",
            "Qwen3-1.7B-Q8_0.gguf", "061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a",
            "Streaming exercised in an x86 emulator; native builder development trials failed. Galaxy A35 performance remains unverified."),
        CatalogModel("qwen-4", "Qwen3 4B · Q4_K_M", 8, 2497280256L,
            "Qwen/Qwen3-4B-GGUF", "bc640142c66e1fdd12af0bd68f40445458f3869b",
            "Qwen3-4B-Q4_K_M.gguf", "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",
            "Pinned GGUF text model. Inference and phone performance unverified."),
        CatalogModel("gemma-4-e2b", "Gemma 4 E2B · Google", 8, 3349516256L,
            "google/gemma-4-E2B-it-qat-q4_0-gguf", "675cff42a74c774d6cb76f76d8eacb49b48c9b93",
            "gemma-4-E2B_q4_0-it.gguf", "fa401b55b07ee70a54c6dae3903c783a6e65064312529ea57175cb5f8dec6634",
            "Text-only here; no multimodal projector. Inference and phone performance unverified."),
        CatalogModel("qwen-8", "Qwen3 8B · Q4_K_M", 12, 5027783488L,
            "Qwen/Qwen3-8B-GGUF", "7c41481f57cb95916b40956ab2f0b139b296d974",
            "Qwen3-8B-Q4_K_M.gguf", "d98cdcbd03e17ce47681435b5150e34c1417f50b5c0019dd560e4882c5745785",
            "Pinned GGUF text model. Inference and phone performance unverified.")
    )
    fun find(id: String): CatalogModel? = models.firstOrNull { it.id == id }
}
