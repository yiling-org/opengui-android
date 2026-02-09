package top.yling.ozx.guiagent

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import top.yling.ozx.guiagent.databinding.ActivityLifeAssistantBinding
import top.yling.ozx.guiagent.databinding.ItemLifeAssistantFoodBinding

/**
 * LifeAssistant Activity
 * 用于展示生活助手返回的 JSON 数据，以 UI 形式呈现
 */
class LifeAssistantActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "LifeAssistantActivity"
        const val EXTRA_JSON_CONTENT = "json_content"
        const val EXTRA_TASK_ID = "task_id"
        
        /**
         * 创建启动 Intent
         */
        fun createIntent(context: android.content.Context, jsonContent: String, taskId: String?): Intent {
            return Intent(context, LifeAssistantActivity::class.java).apply {
                putExtra(EXTRA_JSON_CONTENT, jsonContent)
                putExtra(EXTRA_TASK_ID, taskId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
        }
    }
    
    private lateinit var binding: ActivityLifeAssistantBinding
    private var taskId: String? = null
    private var currentAction: String = "" // 保存当前的 action 类型
    private lateinit var foodAdapter: FoodAdapter
    private val imageLoadScope = CoroutineScope(Dispatchers.IO)
    private val httpClient = OkHttpClient()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用边到边显示
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityLifeAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置窗口标志，确保可以在锁屏上显示
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        supportActionBar?.hide()
        
        // 获取传递的数据
        val jsonContent = intent.getStringExtra(EXTRA_JSON_CONTENT)
        taskId = intent.getStringExtra(EXTRA_TASK_ID)
        
        if (jsonContent.isNullOrEmpty()) {
            android.util.Log.e(TAG, "JSON 内容为空，关闭 Activity")
            finish()
            return
        }
        
        setupUI()
        parseAndDisplayData(jsonContent)
    }
    
    private fun setupUI() {
        // 关闭按钮 - 返回到主页
        binding.closeButton.setOnClickListener {
            // 创建 Intent 返回到 MainActivity
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
        
        // 设置 RecyclerView
        foodAdapter = FoodAdapter(
            onItemClick = { foodItem ->
                // 点击食物项的处理 - 根据 action 打开对应的 app
                android.util.Log.d(TAG, "点击了食物项: ${foodItem.name}，action: $currentAction")
                openAppByAction(currentAction)
            },
            imageLoadScope = imageLoadScope,
            httpClient = httpClient
        )
        
        binding.foodRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@LifeAssistantActivity)
            adapter = foodAdapter
        }
    }
    
    private fun parseAndDisplayData(jsonContent: String) {
        try {
            val jsonObject = JSONObject(jsonContent)
            
            // 解析基本信息
            val success = jsonObject.optBoolean("success", false)
            val action = jsonObject.optString("action", "")
            currentAction = action // 保存 action 类型
            val message = jsonObject.optString("message", "")
            
            // 显示消息
            if (message.isNotEmpty()) {
                binding.messageText.text = message
                binding.messageText.visibility = View.VISIBLE
            } else {
                binding.messageText.visibility = View.GONE
            }
            
            // 根据 action 类型显示不同的平台标识和图标
            val (platformName, platformIconRes) = when (action) {
                "book_train" -> Pair("12306", R.drawable.ic_train)
                "book_hotel" -> Pair("携程", R.drawable.ic_hotel)
                "buy_clothing" -> Pair("购物平台", R.drawable.ic_shopping_cart)
                "order_food" -> Pair("外卖平台", R.drawable.ic_food)
                else -> Pair("生活助手", R.drawable.ic_platform)
            }
            
            binding.platformText.text = platformName
            binding.platformIcon.setImageResource(platformIconRes)
            
            // 根据平台设置图标颜色（白色，因为背景是彩色卡片）
            binding.platformIcon.setColorFilter(getColor(R.color.white))
            
            // 解析数据数组
            val dataArray = jsonObject.optJSONArray("data")
            if (dataArray != null && dataArray.length() > 0) {
                val foodItems = mutableListOf<FoodItem>()
                
                for (i in 0 until dataArray.length()) {
                    val itemObj = dataArray.getJSONObject(i)
                    val foodItem = parseFoodItem(itemObj, action)
                    foodItems.add(foodItem)
                }
                
                if (foodItems.isNotEmpty()) {
                    // 显示食物列表
                    foodAdapter.submitList(foodItems)
                    binding.foodRecyclerView.visibility = View.VISIBLE
                    binding.emptyStateLayout.visibility = View.GONE
                } else {
                    showEmpty()
                }
            } else {
                showEmpty()
            }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "解析 JSON 失败: ${e.message}", e)
            Toast.makeText(this, "数据解析失败", Toast.LENGTH_SHORT).show()
            showEmpty()
        }
    }
    
    private fun parseFoodItem(itemObj: JSONObject, action: String): FoodItem {
        // 通用字段提取
        fun extractTags(): List<String> {
            val tags = mutableListOf<String>()
            if (itemObj.optBoolean("isHot", false)) tags.add("热销")
            if (itemObj.optBoolean("isNew", false)) tags.add("新品")
            if (itemObj.optBoolean("isRecommended", false)) tags.add("推荐")
            if (itemObj.optBoolean("hasDiscount", false)) tags.add("优惠")
            return tags
        }
        
        return when (action) {
            "book_train" -> {
                val rating = itemObj.optDouble("rating", 0.0)
                FoodItem(
                    id = itemObj.optInt("id", 0),
                    name = itemObj.optString("trainNumber", ""),
                    description = "${itemObj.optString("departure", "")} → ${itemObj.optString("destination", "")}",
                    price = itemObj.optDouble("price", 0.0),
                    imageUrl = itemObj.optString("imageUrl", ""),
                    distance = itemObj.optDouble("distance", 0.0),
                    extraInfo = "发车: ${formatTime(itemObj.optLong("departureTime", 0))} | 到达: ${formatTime(itemObj.optLong("arrivalTime", 0))}",
                    seatType = itemObj.optString("seatType", ""),
                    platform = itemObj.optString("platform", ""),
                    rating = rating,
                    originalPrice = itemObj.optDouble("originalPrice", 0.0),
                    tags = extractTags(),
                    deliveryTime = "${itemObj.optInt("duration", 0)}小时"
                )
            }
            "book_hotel" -> {
                val rating = itemObj.optDouble("rating", 0.0)
                FoodItem(
                    id = itemObj.optInt("id", 0),
                    name = itemObj.optString("hotelName", ""),
                    description = itemObj.optString("location", ""),
                    price = itemObj.optDouble("price", 0.0),
                    imageUrl = itemObj.optString("imageUrl", ""),
                    distance = 0.0,
                    extraInfo = "${itemObj.optString("roomType", "")} | 评分: ${rating.toInt()}分",
                    seatType = "",
                    platform = itemObj.optString("platform", ""),
                    rating = rating,
                    originalPrice = itemObj.optDouble("originalPrice", 0.0),
                    tags = extractTags()
                )
            }
            "buy_clothing" -> {
                val rating = itemObj.optDouble("rating", 0.0)
                FoodItem(
                    id = itemObj.optInt("id", 0),
                    name = itemObj.optString("name", ""),
                    description = "${itemObj.optString("brand", "")} | ${itemObj.optString("category", "")}",
                    price = itemObj.optDouble("price", 0.0),
                    imageUrl = itemObj.optString("imageUrl", ""),
                    distance = 0.0,
                    extraInfo = "${itemObj.optString("size", "")} | ${itemObj.optString("color", "")}",
                    seatType = "",
                    platform = itemObj.optString("store", ""),
                    rating = rating,
                    originalPrice = itemObj.optDouble("originalPrice", 0.0),
                    tags = extractTags(),
                    salesCount = itemObj.optInt("salesCount", 0)
                )
            }
            "order_food" -> {
                val rating = itemObj.optDouble("rating", 0.0)
                val deliveryAddress = itemObj.optString("deliveryAddress", "")
                val deliveryTime = itemObj.optString("deliveryTime", "")
                val extraInfo = buildString {
                    if (rating > 0) {
                        append("评分: ${String.format("%.1f", rating)}分")
                    }
                    if (deliveryAddress.isNotEmpty()) {
                        if (isNotEmpty()) append(" | ")
                        append("配送: $deliveryAddress")
                    }
                }
                FoodItem(
                    id = itemObj.optInt("id", 0),
                    name = itemObj.optString("foodName", ""),
                    description = itemObj.optString("category", ""),
                    price = itemObj.optDouble("price", 0.0),
                    imageUrl = itemObj.optString("imageUrl", ""),
                    distance = 0.0,
                    extraInfo = extraInfo,
                    seatType = itemObj.optString("restaurantName", ""), // 使用 seatType 字段存储餐厅名称
                    platform = itemObj.optString("platform", ""),
                    rating = rating,
                    originalPrice = itemObj.optDouble("originalPrice", 0.0),
                    tags = extractTags(),
                    deliveryTime = deliveryTime.ifEmpty { "30分钟" },
                    salesCount = itemObj.optInt("salesCount", 0)
                )
            }
            else -> {
                FoodItem(
                    id = itemObj.optInt("id", 0),
                    name = itemObj.optString("name", ""),
                    description = "",
                    price = itemObj.optDouble("price", 0.0),
                    imageUrl = itemObj.optString("imageUrl", ""),
                    distance = 0.0,
                    extraInfo = "",
                    seatType = "",
                    platform = "",
                    rating = itemObj.optDouble("rating", 0.0),
                    originalPrice = itemObj.optDouble("originalPrice", 0.0),
                    tags = extractTags()
                )
            }
        }
    }
    
    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
    
    private fun showEmpty() {
        binding.foodRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
    }
    
    /**
     * 打开美团app
     */
    /**
     * 根据 action 类型打开对应的 app
     */
    private fun openAppByAction(action: String) {
        val (packageNames, appNames, toastMessage) = when (action) {
            "book_train" -> {
                // 12306 app
                Triple(
                    listOf("com.MobileTicket", "com.MobileTicket.ctrip"),
                    listOf("12306", "铁路12306"),
                    "正在打开12306..."
                )
            }
            "book_hotel" -> {
                // 携程 app
                Triple(
                    listOf("ctrip.android.view", "ctrip.android.view.hotel"),
                    listOf("携程", "携程旅行"),
                    "正在打开携程..."
                )
            }
            "buy_clothing" -> {
                // 京东 app
                Triple(
                    listOf("com.jingdong.app.mall", "com.jd.app.reader"),
                    listOf("京东", "京东商城"),
                    "正在打开京东..."
                )
            }
            "order_food" -> {
                // 美团 app
                Triple(
                    listOf("com.sankuai.meituan.takeoutnew", "com.sankuai.meituan", "com.meituan.android.pt"),
                    listOf("美团", "美团外卖"),
                    "正在打开美团..."
                )
            }
            else -> {
                // 默认打开美团 app
                Triple(
                    listOf("com.sankuai.meituan.takeoutnew", "com.sankuai.meituan", "com.meituan.android.pt"),
                    listOf("美团", "美团外卖"),
                    "正在打开美团..."
                )
            }
        }
        
        var opened = false
        for (packageName in packageNames) {
            if (isAppInstalled(packageName)) {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        startActivity(intent)
                        opened = true
                        android.util.Log.i(TAG, "成功打开app: $packageName (action: $action)")
                        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
                        break
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "打开app失败: ${e.message}")
                    }
                }
            }
        }
        
        if (!opened) {
            // 尝试通过应用名称打开
            var found = false
            for (appName in appNames) {
                if (openAppByName(appName)) {
                    found = true
                    android.util.Log.i(TAG, "通过应用名称打开成功: $appName (action: $action)")
                    Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
                    break
                }
            }
            
            if (!found) {
                android.util.Log.w(TAG, "无法打开app，可能未安装 (action: $action)")
                Toast.makeText(this, "未找到对应应用", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * 通过应用名称打开应用
     */
    private fun openAppByName(appName: String): Boolean {
        return try {
            val pm = packageManager
            val apps = pm.getInstalledApplications(0)
            
            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.contains(appName, ignoreCase = true)) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "通过应用名称打开失败: ${e.message}", e)
            false
        }
    }
    
    
    /**
     * 食物项数据类
     */
    data class FoodItem(
        val id: Int,
        val name: String,
        val description: String,
        val price: Double,
        val imageUrl: String,
        val distance: Double,
        val extraInfo: String,
        val seatType: String,
        val platform: String,
        val rating: Double = 0.0,  // 评分
        val originalPrice: Double = 0.0,  // 原价（用于显示折扣）
        val tags: List<String> = emptyList(),  // 标签列表
        val deliveryTime: String = "",  // 配送时间
        val salesCount: Int = 0  // 销量
    )
    
    /**
     * 食物列表适配器
     */
    class FoodAdapter(
        private val onItemClick: (FoodItem) -> Unit,
        private val imageLoadScope: CoroutineScope,
        private val httpClient: OkHttpClient
    ) : RecyclerView.Adapter<FoodAdapter.FoodViewHolder>() {
        
        private val foodItems = mutableListOf<FoodItem>()
        
        fun submitList(newItems: List<FoodItem>) {
            foodItems.clear()
            foodItems.addAll(newItems)
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
            val binding = ItemLifeAssistantFoodBinding.inflate(
                android.view.LayoutInflater.from(parent.context),
                parent,
                false
            )
            return FoodViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
            if (position < foodItems.size) {
                holder.bind(foodItems[position], onItemClick)
            }
        }
        
        override fun getItemCount(): Int {
            return foodItems.size
        }
        
        inner class FoodViewHolder(
            private val binding: ItemLifeAssistantFoodBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(foodItem: FoodItem, onItemClick: (FoodItem) -> Unit) {
                // 食物名称
                binding.foodNameText.text = foodItem.name
                
                // 描述信息
                val descText = if (foodItem.seatType.isNotEmpty() && foodItem.description.isNotEmpty()) {
                    // 如果有餐厅名称（seatType）和类别（description），都显示
                    "${foodItem.seatType} | ${foodItem.description}"
                } else if (foodItem.description.isNotEmpty()) {
                    foodItem.description
                } else if (foodItem.seatType.isNotEmpty()) {
                    foodItem.seatType
                } else {
                    ""
                }
                
                if (descText.isNotEmpty()) {
                    binding.foodDescText.text = descText
                    binding.foodDescText.visibility = View.VISIBLE
                } else {
                    binding.foodDescText.visibility = View.GONE
                }
                
                // 评分徽章
                if (foodItem.rating > 0) {
                    binding.ratingBadge.visibility = View.VISIBLE
                    binding.ratingText.text = String.format("%.1f", foodItem.rating)
                } else {
                    binding.ratingBadge.visibility = View.GONE
                }
                
                // 标签
                val tags = foodItem.tags
                if (tags.isNotEmpty()) {
                    binding.tagsContainer.visibility = View.VISIBLE
                    binding.tag1Text.visibility = if (tags.size > 0) {
                        binding.tag1Text.text = tags[0]
                        View.VISIBLE
                    } else View.GONE
                    binding.tag2Text.visibility = if (tags.size > 1) {
                        binding.tag2Text.text = tags[1]
                        View.VISIBLE
                    } else View.GONE
                } else {
                    binding.tagsContainer.visibility = View.GONE
                }
                
                // 距离和额外信息
                val infoText = buildString {
                    if (foodItem.distance > 0) {
                        append("${String.format("%.2f", foodItem.distance)} km")
                    }
                    if (foodItem.deliveryTime.isNotEmpty()) {
                        if (isNotEmpty()) append(" | ")
                        append("预计 ${foodItem.deliveryTime} 送达")
                    }
                    if (foodItem.extraInfo.isNotEmpty()) {
                        if (isNotEmpty()) append(" | ")
                        append(foodItem.extraInfo)
                    }
                    if (foodItem.salesCount > 0) {
                        if (isNotEmpty()) append(" | ")
                        append("月售 ${foodItem.salesCount}")
                    }
                }
                
                if (infoText.isNotEmpty()) {
                    binding.foodInfoText.text = infoText
                    binding.foodInfoContainer.visibility = View.VISIBLE
                } else {
                    binding.foodInfoContainer.visibility = View.GONE
                }
                
                // 价格
                binding.foodPriceText.text = "¥${String.format("%.1f", foodItem.price)}"
                
                // 原价（如果有折扣）
                if (foodItem.originalPrice > 0 && foodItem.originalPrice > foodItem.price) {
                    binding.originalPriceText.visibility = View.VISIBLE
                    binding.originalPriceText.text = "¥${String.format("%.1f", foodItem.originalPrice)}"
                    binding.originalPriceText.paintFlags = 
                        binding.originalPriceText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    binding.originalPriceText.visibility = View.GONE
                }
                
                // 加载图片
                if (foodItem.imageUrl.isNotEmpty()) {
                    loadImageFromUrl(foodItem.imageUrl, binding.foodImage)
                } else {
                    binding.foodImage.setImageResource(android.R.drawable.ic_menu_gallery)
                }
                
                // 点击事件
                binding.root.setOnClickListener {
                    onItemClick(foodItem)
                }
            }
            
            /**
             * 从 URL 加载图片
             */
            private fun loadImageFromUrl(url: String, imageView: android.widget.ImageView) {
                // 先显示占位图
                imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                
                imageLoadScope.launch {
                    try {
                        val request = Request.Builder().url(url).build()
                        val response = httpClient.newCall(request).execute()
                        val body = response.body
                        if (body != null && response.isSuccessful) {
                            val bytes = body.bytes()
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            
                            withContext(Dispatchers.Main) {
                                imageView.setImageBitmap(bitmap)
                                // 确保图片有圆角裁剪
                                imageView.clipToOutline = true
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("LifeAssistantActivity", "加载图片失败: ${e.message}")
                        withContext(Dispatchers.Main) {
                            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    }
                }
            }
        }
    }
}

