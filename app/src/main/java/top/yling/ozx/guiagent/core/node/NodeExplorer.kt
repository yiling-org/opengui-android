package top.yling.ozx.guiagent.a11y

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import top.yling.ozx.guiagent.MyAccessibilityService
import top.yling.ozx.guiagent.a11y.data.*
import top.yling.ozx.guiagent.shizuku.ShizukuApi

/**
 * 节点探索器
 * 用于根据坐标查找节点并获取完整信息
 *
 * 参考设计文档: NODE_CLICK_FEATURE_DESIGN.md
 */
object NodeExplorer {

    /**
     * 查询模式开关
     * true: 使用 AccessibilityNodeInfo 直接查询（默认）
     * false: 使用镜像数据查询
     */
    var useDirectNodeQuery: Boolean = true

    /**
     * 根据屏幕坐标从镜像中找到对应的节点
     * 遍历镜像中的所有节点，返回包含该坐标且面积最小的可见节点
     * @param nodes 镜像节点列表
     * @param x X坐标（px）
     * @param y Y坐标（px）
     * @return 匹配的 NodeInfo，未找到返回 null
     */
    fun findNodeByPositionFromSnapshot(
        nodes: List<NodeInfo>,
        x: Float,
        y: Float
    ): NodeInfo? {
        val xInt = x.toInt()
        val yInt = y.toInt()
        var result: NodeInfo? = null
        var minArea = Long.MAX_VALUE

        for (node in nodes) {
            val attr = node.attr

            // 判断点是否在矩形内（包含边界）
            val containsPoint = xInt >= attr.left && xInt <= attr.right &&
                               yInt >= attr.top && yInt <= attr.bottom

            // 检查：包含坐标 + 可见 + 可点击
            if (containsPoint && attr.visibleToUser && attr.clickable) {
                val area = attr.width.toLong() * attr.height
                // 选择面积最小的可见可点击节点（更精确）
                if (area > 0 && area < minArea) {
                    result = node
                    minArea = area
                }
            }
        }

        return result
    }

    /**
     * 根据屏幕坐标找到对应的节点
     * 遍历节点树，返回包含该坐标且面积最小的可见可点击节点
     * 使用剪枝优化：父节点不包含坐标时跳过子节点遍历
     */
    fun findNodeByPosition(
        root: AccessibilityNodeInfo,
        x: Float,
        y: Float
    ): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        var minArea = Long.MAX_VALUE
        val rect = Rect()
        val xInt = x.toInt()
        val yInt = y.toInt()

        fun traverse(node: AccessibilityNodeInfo) {
            node.getBoundsInScreen(rect)

            // 剪枝：如果当前节点不包含目标坐标，跳过子节点遍历
            if (!rect.contains(xInt, yInt)) {
                return
            }

            // 检查：包含坐标 + 可见 + 可点击
            if (node.isVisibleToUser && node.isClickable) {
                val area = rect.width().toLong() * rect.height()
                // 选择面积最小的可见可点击节点
                if (area > 0 && area < minArea) {
                    result = node
                    minArea = area
                }
            }

            // 继续遍历子节点
            repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { i ->
                node.getChild(i)?.let {
                    traverse(it)
                    if (it != result && it != root) {
                        it.recycle()
                    }
                }
            }
        }

        traverse(root)

        // 打印找到的节点信息
        result?.let {
            val attr = AttrInfo.fromNode(it, 0, 0)
            Log.d("NodeExplorer", "findNodeByPosition($x, $y) -> $attr")
        }

        return result
    }

    /**
     * 候选节点数据类，用于排序
     */
    private data class CandidateNode(
        val node: AccessibilityNodeInfo,
        val area: Long,
        val hasIdentifier: Boolean,
        val isClickable: Boolean
    )

    /**
     * 根据屏幕坐标找到最优的多个节点（最多3个）
     * 有效节点条件：可见 + (可点击 或 有 text/desc/id/vid)
     * 优先级：面积小的优先，有标识属性的优先
     */
    fun findNodesByPosition(
        root: AccessibilityNodeInfo,
        x: Float,
        y: Float,
        maxResults: Int = 3
    ): List<AccessibilityNodeInfo> {
        val candidates = mutableListOf<CandidateNode>()
        val rect = Rect()
        val xInt = x.toInt()
        val yInt = y.toInt()
        val resultNodes = mutableListOf<AccessibilityNodeInfo>()

        fun traverse(node: AccessibilityNodeInfo) {
            node.getBoundsInScreen(rect)

            // 剪枝：如果当前节点不包含目标坐标，跳过子节点遍历
            if (!rect.contains(xInt, yInt)) {
                return
            }

            // 检查：包含坐标 + 可见 + (可点击 或 有标识属性)
            if (node.isVisibleToUser) {
                val hasIdentifier = hasIdentifiableAttr(node)
                val isClickable = node.isClickable

                if (isClickable || hasIdentifier) {
                    val area = rect.width().toLong() * rect.height()
                    if (area > 0) {
                        candidates.add(CandidateNode(node, area, hasIdentifier, isClickable))
                    }
                }
            }

            // 继续遍历子节点
            repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { i ->
                node.getChild(i)?.let {
                    traverse(it)
                }
            }
        }

        traverse(root)

        // 排序：优先有标识属性且可点击的，其次按面积从小到大
        val sortedCandidates = candidates.sortedWith(
            compareBy<CandidateNode> { !(it.hasIdentifier && it.isClickable) }  // 有标识且可点击的优先
                .thenBy { !it.isClickable }  // 可点击的优先
                .thenBy { !it.hasIdentifier }  // 有标识的优先
                .thenBy { it.area }  // 面积小的优先
        )

        // 取前 maxResults 个
        val topResults = sortedCandidates.take(maxResults)

        // 回收不需要的节点
        candidates.filter { candidate ->
            !topResults.contains(candidate) && candidate.node != root
        }.forEach { candidate ->
            candidate.node.recycle()
        }

        resultNodes.addAll(topResults.map { it.node })

        // 打印找到的节点信息
        Log.d("NodeExplorer", "findNodesByPosition($x, $y) -> found ${resultNodes.size} nodes")
        resultNodes.forEachIndexed { index, node ->
            val attr = AttrInfo.fromNode(node, 0, 0)
            Log.d("NodeExplorer", "  [$index] $attr")
        }

        return resultNodes
    }

    /**
     * 获取节点的完整信息
     * 包括：当前节点、父节点路径、子节点、选择器建议
     */
    fun getFullNodeInfo(
        node: AccessibilityNodeInfo,
        maxChildren: Int = 2,
        maxAncestors: Int = 2  // 最多向上追溯的祖先层数
    ): ClickedNodeInfo {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // 1. 获取父节点路径
        val ancestorPath = mutableListOf<NodeInfo>()
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        val nodeDepthMap = mutableMapOf<AccessibilityNodeInfo, Int>()

        // 先收集所有祖先节点
        val allAncestors = mutableListOf<AccessibilityNodeInfo>()
        while (current != null) {
            allAncestors.add(0, current)
            current = current.parent
        }

        // 只保留最近 maxAncestors 层的祖先节点（不包括当前节点自身）
        // allAncestors 最后一个是当前节点，前面的是祖先
        val ancestorCount = allAncestors.size - 1  // 排除当前节点
        val startIndex = (ancestorCount - maxAncestors).coerceAtLeast(0)
        val ancestors = if (ancestorCount > 0) {
            allAncestors.subList(startIndex, allAncestors.size)
        } else {
            allAncestors  // 只有当前节点
        }

        // 构建祖先节点信息（从最近的祖先到当前）
        ancestors.forEachIndexed { index, ancestor ->
            val parentIndex = if (index > 0) {
                val parent = ancestors[index - 1]
                findChildIndex(parent, ancestor)
            } else 0

            ancestorPath.add(
                NodeInfo(
                    id = index,
                    pid = if (index > 0) index - 1 else -1,
                    idQf = null,
                    textQf = null,
                    attr = AttrInfo.fromNode(ancestor, parentIndex, index)
                )
            )
            nodeDepthMap[ancestor] = index
        }

        // 2. 获取当前节点信息
        val currentDepth = ancestors.size - 1
        val currentIndex = if (ancestors.size > 1) {
            findChildIndex(ancestors[ancestors.size - 2], node)
        } else 0
        
        val nodeInfo = NodeInfo(
            id = currentDepth,
            pid = if (currentDepth > 0) currentDepth - 1 else -1,
            idQf = null,
            textQf = null,
            attr = AttrInfo.fromNode(node, currentIndex, currentDepth)
        )

        // 3. 获取子节点
        val children = mutableListOf<NodeInfo>()
        repeat(node.childCount.coerceAtMost(maxChildren)) { i ->
            node.getChild(i)?.let { child ->
                children.add(
                    NodeInfo(
                        id = currentDepth + 1 + i,
                        pid = currentDepth,
                        idQf = null,
                        textQf = null,
                        attr = AttrInfo.fromNode(child, i, currentDepth + 1)
                    )
                )
            }
        }

        // 4. 生成选择器建议
        val selectorSuggestion = generateSelectorSuggestion(node, ancestorPath)

        return ClickedNodeInfo(
            node = nodeInfo,
            ancestorPath = ancestorPath,
            children = children,
            selectorSuggestion = selectorSuggestion,
            position = NodePosition(
                x = rect.left,
                y = rect.top,
                centerX = rect.centerX(),
                centerY = rect.centerY()
            )
        )
    }

    /**
     * 查找子节点在父节点中的索引
     */
    private fun findChildIndex(parent: AccessibilityNodeInfo, child: AccessibilityNodeInfo): Int {
        repeat(parent.childCount) { i ->
            if (parent.getChild(i) == child) {
                return i
            }
        }
        return 0
    }

    /**
     * 生成选择器建议（合法的 GKD 选择器表达式）
     * 格式：@Parent > @Child[attr=value] 或 @ClassName[attr=value]
     */
    private fun generateSelectorSuggestion(
        node: AccessibilityNodeInfo,
        ancestorPath: List<NodeInfo>
    ): String {
        // 构建当前节点选择器
        val nodeSelector = buildNodeSelector(node)

        // 如果有父节点，构建父子关系选择器
        if (ancestorPath.size >= 2) {
            val parent = ancestorPath[ancestorPath.size - 2]
            val parentClassName = parent.attr.name?.substringAfterLast('.') ?: ""

            if (parentClassName.isNotEmpty()) {
                // 构建父节点选择器
                val parentSelector = buildParentSelector(parent, parentClassName)
                // 使用 > 表示直接子节点关系
                return "$parentSelector > $nodeSelector"
            }
        }

        return nodeSelector
    }

    /**
     * 构建当前节点的选择器
     */
    private fun buildNodeSelector(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString()?.substringAfterLast('.') ?: "*"
        val attrs = mutableListOf<String>()

        // 优先使用 vid
        val vid = node.getVid()?.toString()
        if (!vid.isNullOrEmpty()) {
            attrs.add("vid='$vid'")
        }

        // 次选 text（短文本，需转义特殊字符）
        if (attrs.isEmpty() && !node.text.isNullOrEmpty() && node.text.length <= 20) {
            val escapedText = node.text.toString().replace("'", "\\'")
            attrs.add("text='$escapedText'")
        }

        // 再选 desc（contentDescription）
        if (attrs.isEmpty() && !node.contentDescription.isNullOrEmpty() && node.contentDescription.length <= 20) {
            val escapedDesc = node.contentDescription.toString().replace("'", "\\'")
            attrs.add("desc='$escapedDesc'")
        }

        // 如果 clickable=true 可以增加区分度
        if (node.isClickable) {
            attrs.add("clickable=true")
        }

        return if (attrs.isNotEmpty()) {
            "@$className[${attrs.joinToString("][")}]"
        } else {
            "@$className"
        }
    }

    /**
     * 构建父节点的选择器
     */
    private fun buildParentSelector(parent: NodeInfo, className: String): String {
        val attrs = mutableListOf<String>()

        // 使用 vid
        if (!parent.attr.vid.isNullOrEmpty()) {
            attrs.add("vid='${parent.attr.vid}'")
        }

        return if (attrs.isNotEmpty()) {
            "@$className[${attrs.joinToString("][")}]"
        } else {
            "@$className"
        }
    }

    /**
     * 基于 AccessibilityNodeInfo 构建选择器（往上遍历直到找到有标识属性的父节点）
     */
    private fun buildSelectorFromNode(node: AccessibilityNodeInfo): String {
        val nodeSelector = buildNodeSelector(node)

        // 收集父节点的选择器，直到找到有 vid/id/text/desc 的节点或达到最大深度
        val parentSelectors = mutableListOf<String>()
        var currentParent = node.parent
        var depth = 0
        val maxDepth = 2

        while (currentParent != null && depth < maxDepth) {
            val parentClassName = currentParent.className?.toString()?.substringAfterLast('.') ?: ""
            if (parentClassName.isNotEmpty()) {
                parentSelectors.add(buildParentSelectorFromNode(currentParent))
            }

            // 检查当前父节点是否有标识属性，有则停止遍历
            if (hasIdentifiableAttr(currentParent)) {
                break
            }

            currentParent = currentParent.parent
            depth++
        }

        // 反转列表，使最顶层的父节点在前
        return if (parentSelectors.isNotEmpty()) {
            parentSelectors.reversed().joinToString(" > ") + " > $nodeSelector"
        } else {
            nodeSelector
        }
    }

    /**
     * 检查节点是否有可标识的属性（vid、viewIdResourceName、text、desc）
     */
    private fun hasIdentifiableAttr(node: AccessibilityNodeInfo): Boolean {
        // 检查 vid
        val vid = node.getVid()?.toString()
        if (!vid.isNullOrEmpty()) return true

        // 检查 viewIdResourceName
        if (!node.viewIdResourceName.isNullOrEmpty()) return true

        // 检查 text
        if (!node.text.isNullOrEmpty() && node.text.length <= 20) return true

        // 检查 desc
        if (!node.contentDescription.isNullOrEmpty() && node.contentDescription.length <= 20) return true

        return false
    }

    /**
     * 基于 AccessibilityNodeInfo 构建父节点选择器
     */
    private fun buildParentSelectorFromNode(parent: AccessibilityNodeInfo): String {
        val className = parent.className?.toString()?.substringAfterLast('.') ?: "*"
        val attrs = mutableListOf<String>()

        // 优先使用 vid
        val vid = parent.getVid()?.toString()
        if (!vid.isNullOrEmpty()) {
            attrs.add("vid='$vid'")
        }

        // 次选 text
        if (attrs.isEmpty() && !parent.text.isNullOrEmpty() && parent.text.length <= 20) {
            val escapedText = parent.text.toString().replace("'", "\\'")
            attrs.add("text='$escapedText'")
        }

        // 再选 desc
        if (attrs.isEmpty() && !parent.contentDescription.isNullOrEmpty() && parent.contentDescription.length <= 20) {
            val escapedDesc = parent.contentDescription.toString().replace("'", "\\'")
            attrs.add("desc='$escapedDesc'")
        }

        return if (attrs.isNotEmpty()) {
            "@$className[${attrs.joinToString("][")}]"
        } else {
            "@$className"
        }
    }

    /**
     * 根据坐标查询节点并返回完整信息
     * 根据 useDirectNodeQuery 开关选择查询方式
     */
    fun queryNodeAtPosition(x: Float, y: Float): NodeQueryResult {
        return if (useDirectNodeQuery) {
            queryNodeAtPositionDirect(x, y)
        } else {
            queryNodeAtPositionFromSnapshot(x, y)
        }
    }

    /**
     * 使用 AccessibilityNodeInfo 直接查询节点（返回最多3个最优解）
     */
    private fun queryNodeAtPositionDirect(x: Float, y: Float): NodeQueryResult {
        val startTime = System.currentTimeMillis()

        val root = MyAccessibilityService.instance?.rootInActiveWindow
            ?: return NodeQueryResult(
                success = false,
                error = "无法获取当前窗口",
                queryTimeMs = System.currentTimeMillis() - startTime
            )

        // 查找最优的3个节点
        val matchedNodes = findNodesByPosition(root, x, y, maxResults = 3)
        if (matchedNodes.isEmpty()) {
            return NodeQueryResult(
                success = false,
                error = "未找到对应位置的有效节点",
                queryTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // 为每个节点构建 ClickedNodeInfo
        val nodeInfos = matchedNodes.map { matchedNode ->
            // 生成选择器建议（支持父节点）
            val selectorSuggestion = buildSelectorFromNode(matchedNode)

            // 构建位置信息
            val rect = Rect()
            matchedNode.getBoundsInScreen(rect)
            val position = NodePosition(
                x = rect.left,
                y = rect.top,
                centerX = rect.centerX(),
                centerY = rect.centerY()
            )

            // 构建 NodeInfo
            val nodeInfo = NodeInfo(
                id = 0,
                pid = -1,
                idQf = null,
                textQf = null,
                attr = AttrInfo.fromNode(matchedNode, 0, 0)
            )

            ClickedNodeInfo(
                node = nodeInfo,
                ancestorPath = emptyList(),
                children = emptyList(),
                selectorSuggestion = selectorSuggestion,
                position = position
            )
        }

        return NodeQueryResult(
            success = true,
            nodeInfo = nodeInfos.firstOrNull(),  // 兼容旧接口
            nodeInfos = nodeInfos,
            queryTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * 使用镜像数据查询节点
     */
    private fun queryNodeAtPositionFromSnapshot(x: Float, y: Float): NodeQueryResult {
        val startTime = System.currentTimeMillis()

        val root = MyAccessibilityService.instance?.rootInActiveWindow
            ?: return NodeQueryResult(
                success = false,
                error = "无法获取当前窗口",
                queryTimeMs = System.currentTimeMillis() - startTime
            )

        // 1. 先获取镜像数据
        val nodes = nodeToNodeInfoList(root)
        if (nodes.isEmpty()) {
            return NodeQueryResult(
                success = false,
                error = "镜像数据为空",
                queryTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // 2. 从镜像中查找匹配的节点
        val matchedNode = findNodeByPositionFromSnapshot(nodes, x, y)
            ?: return NodeQueryResult(
                success = false,
                error = "未找到对应位置的节点",
                queryTimeMs = System.currentTimeMillis() - startTime
            )

        // 3. 生成选择器建议
        val selectorSuggestion = buildNodeSelectorFromAttr(matchedNode.attr)

        // 4. 构建位置信息
        val attr = matchedNode.attr
        val position = NodePosition(
            x = attr.left,
            y = attr.top,
            centerX = attr.left + attr.width / 2,
            centerY = attr.top + attr.height / 2
        )

        val clickedNodeInfo = ClickedNodeInfo(
            node = matchedNode,
            ancestorPath = emptyList(),
            children = emptyList(),
            selectorSuggestion = selectorSuggestion,
            position = position
        )

        return NodeQueryResult(
            success = true,
            nodeInfo = clickedNodeInfo,
            queryTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * 基于 AttrInfo 构建节点选择器
     */
    private fun buildNodeSelectorFromAttr(attr: AttrInfo): String {
        val className = attr.name?.substringAfterLast('.') ?: "*"
        val attrs = mutableListOf<String>()

        // 优先使用 vid
        if (!attr.vid.isNullOrEmpty()) {
            attrs.add("vid='${attr.vid}'")
        }

        // 次选 text
        if (attrs.isEmpty() && !attr.text.isNullOrEmpty() && attr.text.length <= 20) {
            val escapedText = attr.text.replace("'", "\\'")
            attrs.add("text='$escapedText'")
        }

        // 再选 desc
        if (attrs.isEmpty() && !attr.desc.isNullOrEmpty() && attr.desc.length <= 20) {
            val escapedDesc = attr.desc.replace("'", "\\'")
            attrs.add("desc='$escapedDesc'")
        }

        // 如果 clickable=true 可以增加区分度
        if (attr.clickable) {
            attrs.add("clickable=true")
        }

        return if (attrs.isNotEmpty()) {
            "@$className[${attrs.joinToString("][")}]"
        } else {
            "@$className"
        }
    }

    /**
     * 获取当前窗口的所有节点快照
     */
    fun getSnapshot(): SnapshotNodeInfo? {
        val root = MyAccessibilityService.instance?.rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString() ?: "unknown"

        // 优先通过 Shizuku 获取正确的 activityId
        val activityName = getTopActivityId(packageName) ?: root.className?.toString()

        val nodes = nodeToNodeInfoList(root)

        return SnapshotNodeInfo(
            packageName = packageName,
            activityName = activityName,
            nodes = nodes
        )
    }

    /**
     * 获取完整快照信息（类似 GKD 格式）
     * 包括应用信息、设备信息、屏幕信息和所有节点
     */
    fun getFullSnapshot(context: Context): FullSnapshotInfo? {
        val root = MyAccessibilityService.instance?.rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString() ?: "unknown"
        
        // 优先通过 Shizuku 获取正确的 activityId
        // root.className 只是根节点的类名（如 FrameLayout），不是真正的 Activity
        val activityName = getTopActivityId(packageName) ?: root.className?.toString()

        // 获取屏幕尺寸
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // 获取应用信息
        val appInfo = getAppInfo(context, packageName)

        // 获取设备信息
        val deviceInfo = DeviceInfo(
            device = Build.DEVICE,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE
        )

        // 获取所有节点
        val nodes = nodeToNodeInfoList(root)

        return FullSnapshotInfo(
            appId = packageName,
            activityId = activityName,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            isLandscape = isLandscape,
            appInfo = appInfo,
            device = deviceInfo,
            nodes = nodes
        )
    }

    /**
     * 获取当前顶部 Activity 的类名
     * 通过 Shizuku 获取正确的 Activity 信息
     * @param expectedAppId 期望的应用包名，用于验证
     * @return Activity 类名，如果获取失败或包名不匹配返回 null
     */
    private fun getTopActivityId(expectedAppId: String): String? {
        return try {
            val shizukuContext = ShizukuApi.getContextOrNull() ?: return null
            val topCpn = shizukuContext.topCpn() ?: return null
            
            // 验证包名是否匹配
            if (topCpn.packageName == expectedAppId) {
                topCpn.className
            } else {
                Log.d("NodeExplorer", "topCpn package mismatch: ${topCpn.packageName} vs $expectedAppId")
                null
            }
        } catch (e: Exception) {
            Log.w("NodeExplorer", "获取 topActivityId 失败", e)
            null
        }
    }

    /**
     * 获取应用信息
     */
    private fun getAppInfo(context: Context, packageName: String): AppInfo? {
        return try {
            val pm = context.packageManager
            val appInfoFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_UNINSTALLED_PACKAGES
            }
            val packageInfo = pm.getPackageInfo(packageName, appInfoFlags)
            val applicationInfo = packageInfo.applicationInfo

            AppInfo(
                id = packageName,
                name = applicationInfo?.let { pm.getApplicationLabel(it).toString() },
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                },
                versionName = packageInfo.versionName,
                isSystem = applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w("NodeExplorer", "获取应用信息失败: $packageName", e)
            null
        }
    }

    /**
     * 使用选择器查询节点
     */
    fun queryBySelector(selectorString: String): List<ClickedNodeInfo> {
        val root = MyAccessibilityService.instance?.rootInActiveWindow ?: return emptyList()
        
        val selector = try {
            li.songe.selector.Selector.parse(selectorString)
        } catch (e: Exception) {
            return emptyList()
        }
        
        return a11yContext.querySelectorAll(root, selector)
            .take(10)  // 限制返回数量
            .map { node -> getFullNodeInfo(node) }
            .toList()
    }

    /**
     * 查找第一个匹配选择器的节点
     */
    fun queryFirstBySelector(selectorString: String): ClickedNodeInfo? {
        val root = MyAccessibilityService.instance?.rootInActiveWindow ?: return null
        
        val selector = try {
            li.songe.selector.Selector.parse(selectorString)
        } catch (e: Exception) {
            return null
        }
        
        val node = a11yContext.querySelector(root, selector) ?: return null
        return getFullNodeInfo(node)
    }
}

