package top.yling.ozx.guiagent.a11y.data

import kotlinx.serialization.Serializable

/**
 * 点击节点的完整信息
 * 包含：当前节点、父节点路径、子节点、选择器建议
 * 
 * 参考设计文档: NODE_CLICK_FEATURE_DESIGN.md
 */
@Serializable
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
data class ClickedNodeInfo(
    /**
     * 当前点击的节点信息
     */
    val node: NodeInfo,
    
    /**
     * 从根节点到当前节点的路径（包含当前节点）
     */
    val ancestorPath: List<NodeInfo>,
    
    /**
     * 当前节点的前几个子节点
     */
    val children: List<NodeInfo>,
    
    /**
     * 生成的选择器建议
     */
    val selectorSuggestion: String?,
    
    /**
     * 节点位置信息
     */
    val position: NodePosition,
)

/**
 * 节点位置信息
 */
@Serializable
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
data class NodePosition(
    val x: Int,
    val y: Int,
    val centerX: Int,
    val centerY: Int,
)

/**
 * 节点查询结果
 */
@Serializable
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
data class NodeQueryResult(
    /**
     * 是否成功
     */
    val success: Boolean,

    /**
     * 错误信息
     */
    val error: String? = null,

    /**
     * 查询到的节点信息（兼容旧接口，取第一个）
     */
    val nodeInfo: ClickedNodeInfo? = null,

    /**
     * 查询到的多个节点信息（最多3个最优解）
     */
    val nodeInfos: List<ClickedNodeInfo> = emptyList(),

    /**
     * 查询耗时（毫秒）
     */
    val queryTimeMs: Long = 0,
)

/**
 * 批量节点信息（用于快照）
 */
@Serializable
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
data class SnapshotNodeInfo(
    /**
     * 当前应用包名
     */
    val packageName: String,

    /**
     * 当前 Activity 名称
     */
    val activityName: String?,

    /**
     * 所有节点列表
     */
    val nodes: List<NodeInfo>,

    /**
     * 快照时间戳
     */
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * 完整快照信息（类似 GKD 格式）
 */
@Serializable
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
data class FullSnapshotInfo(
    /**
     * 快照ID（时间戳）
     */
    val id: Long = System.currentTimeMillis(),

    /**
     * 当前应用包名
     */
    val appId: String,

    /**
     * 当前 Activity 名称
     */
    val activityId: String?,

    /**
     * 屏幕高度
     */
    val screenHeight: Int,

    /**
     * 屏幕宽度
     */
    val screenWidth: Int,

    /**
     * 是否横屏
     */
    val isLandscape: Boolean,

    /**
     * 当前应用信息
     */
    val appInfo: AppInfo?,

    /**
     * 设备信息
     */
    val device: DeviceInfo,

    /**
     * 所有节点列表
     */
    val nodes: List<NodeInfo>,
)

/**
 * 应用信息
 */
@Serializable
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
data class AppInfo(
    val id: String,
    val name: String?,
    val versionCode: Long,
    val versionName: String?,
    val isSystem: Boolean,
)

/**
 * 设备信息
 */
@Serializable
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
data class DeviceInfo(
    val device: String,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val sdkInt: Int,
    val release: String,
)

