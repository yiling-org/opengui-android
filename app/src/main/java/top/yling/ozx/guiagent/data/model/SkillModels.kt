package top.yling.ozx.guiagent.model

/**
 * Skill document model matching backend SkillDocument
 */
data class SkillDocument(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val content: String? = null,
    val location: String? = null,
    val source: String? = null,  // SYSTEM, USER, SHARED
    val category: String? = null,
    val tags: List<String>? = null,
    val userId: String? = null,
    val priority: Int? = null,
    val enabled: Boolean? = true
) {
    /**
     * Check if this is a user-defined skill
     */
    fun isUserSkill(): Boolean = source == "USER"

    /**
     * Check if this is a system preset skill
     */
    fun isSystemSkill(): Boolean = source == "SYSTEM"

    /**
     * Get display title (displayName or name)
     */
    fun getTitle(): String = displayName ?: name

    /**
     * Get source display text
     */
    fun getSourceText(): String = when (source) {
        "SYSTEM" -> "System"
        "USER" -> "My Skill"
        "SHARED" -> "Shared"
        else -> "Unknown"
    }

    /**
     * Get category display text
     */
    fun getCategoryText(): String = when (category) {
        "writing" -> "Writing"
        "coding" -> "Coding"
        "daily" -> "Daily"
        "custom" -> "Custom"
        else -> category ?: "General"
    }
}

/**
 * Skill request model for creating/updating skills
 */
data class SkillRequest(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val content: String,
    val category: String? = null,
    val tags: List<String>? = null
)

/**
 * Skill category options
 */
object SkillCategory {
    const val WRITING = "writing"
    const val CODING = "coding"
    const val DAILY = "daily"
    const val CUSTOM = "custom"

    val ALL = listOf(
        WRITING to "Writing",
        CODING to "Coding",
        DAILY to "Daily",
        CUSTOM to "Custom"
    )
}

/**
 * Skill generate request model
 * 用于请求 AI 生成技能
 */
data class SkillGenerateRequest(
    val prompt: String
)

/**
 * Skill generate response model
 * AI 生成的技能定义
 */
data class SkillGenerateResponse(
    val name: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val content: String? = null,
    val category: String? = null,
    val tags: List<String>? = null
)
