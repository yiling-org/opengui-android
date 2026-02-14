package top.yling.ozx.guiagent.model

/**
 * 模型类型
 * 对应后端 /api/config/model-types 接口返回的数据结构
 *
 * @author shanwb
 */
data class ModelType(
    val name: String,
    val displayName: String,
    val description: String,
    val requiresModelKey: Boolean = false
)
