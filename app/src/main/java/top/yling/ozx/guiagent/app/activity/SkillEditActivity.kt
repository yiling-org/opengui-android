package top.yling.ozx.guiagent

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yling.ozx.guiagent.databinding.ActivitySkillEditBinding
import top.yling.ozx.guiagent.model.SkillCategory
import top.yling.ozx.guiagent.model.SkillGenerateRequest
import top.yling.ozx.guiagent.model.SkillGenerateResponse
import top.yling.ozx.guiagent.model.SkillRequest
import top.yling.ozx.guiagent.network.RetrofitClient
import top.yling.ozx.guiagent.util.TokenManager

/**
 * Skill Edit Activity - Optimized UI
 * Create or edit user skills with minimalist design
 */
class SkillEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySkillEditBinding
    private var isEditMode: Boolean = false
    private var originalSkillName: String? = null
    private var selectedCategory: String = SkillCategory.CUSTOM
    private var aiBottomSheet: BottomSheetDialog? = null

    companion object {
        const val EXTRA_SKILL_NAME = "skill_name"
        const val EXTRA_IS_EDIT = "is_edit"
        private const val MAX_DESCRIPTION_LENGTH = 200
        private const val MAX_CONTENT_LENGTH = 5000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySkillEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        setupWindowInsets()

        isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT, false)
        originalSkillName = intent.getStringExtra(EXTRA_SKILL_NAME)

        setupUI()
        setupCategorySelection()
        setupCharacterCounters()
        setupInputFocusAnimation()

        if (isEditMode && originalSkillName != null) {
            binding.titleText.text = "编辑技能"
            binding.nameInput.isEnabled = false
            loadSkillData(originalSkillName!!)
        } else {
            binding.titleText.text = "新建技能"
            updateCategorySelection(SkillCategory.CUSTOM)
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { confirmExit() }
        binding.saveButton.setOnClickListener { saveSkill() }
        binding.submitButton.setOnClickListener { saveSkill() }
        binding.previewButton.setOnClickListener { showPreview() }
        binding.templateButton.setOnClickListener { showTemplateDialog() }
        binding.aiGenerateButton.setOnClickListener { showAIGenerateSheet() }
    }

    private fun setupCategorySelection() {
        val categories = listOf(
            binding.categoryWriting to SkillCategory.WRITING,
            binding.categoryCoding to SkillCategory.CODING,
            binding.categoryDaily to SkillCategory.DAILY,
            binding.categoryCustom to SkillCategory.CUSTOM
        )

        categories.forEach { (view, category) ->
            view.setOnClickListener {
                updateCategorySelection(category)
            }
        }
    }

    private fun updateCategorySelection(category: String) {
        selectedCategory = category

        val activeColor = ContextCompat.getColor(this, R.color.skill_category_active)
        val inactiveColor = ContextCompat.getColor(this, R.color.skill_category_inactive)

        binding.categoryWriting.setTextColor(if (category == SkillCategory.WRITING) activeColor else inactiveColor)
        binding.categoryCoding.setTextColor(if (category == SkillCategory.CODING) activeColor else inactiveColor)
        binding.categoryDaily.setTextColor(if (category == SkillCategory.DAILY) activeColor else inactiveColor)
        binding.categoryCustom.setTextColor(if (category == SkillCategory.CUSTOM) activeColor else inactiveColor)
    }

    private fun setupCharacterCounters() {
        binding.descriptionInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val length = s?.length ?: 0
                binding.descriptionCounter.text = "$length/$MAX_DESCRIPTION_LENGTH"
                updateCounterColor(binding.descriptionCounter, length, MAX_DESCRIPTION_LENGTH)
            }
        })

        binding.contentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val length = s?.length ?: 0
                binding.contentCounter.text = "$length/$MAX_CONTENT_LENGTH"
                updateCounterColor(binding.contentCounter, length, MAX_CONTENT_LENGTH)
            }
        })
    }

    private fun updateCounterColor(counter: TextView, current: Int, max: Int) {
        val colorRes = if (current > max) R.color.skill_counter_warning else R.color.skill_counter_normal
        counter.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun setupInputFocusAnimation() {
        val inputs = listOf(
            binding.nameInput,
            binding.displayNameInput,
            binding.descriptionInput,
            binding.contentInput
        )

        val normalColor = ContextCompat.getColor(this, R.color.skill_input_line)
        val focusedColor = ContextCompat.getColor(this, R.color.skill_input_line_focused)

        inputs.forEach { input ->
            input.setOnFocusChangeListener { view, hasFocus ->
                animateInputUnderline(view as EditText, if (hasFocus) focusedColor else normalColor)
            }
        }
    }

    private fun animateInputUnderline(editText: EditText, targetColor: Int) {
        val background = editText.background
        if (background is LayerDrawable) {
            val shape = background.getDrawable(0) as? GradientDrawable
            if (shape != null) {
                val currentColor = ContextCompat.getColor(this, R.color.skill_input_line)
                ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor).apply {
                    duration = 200
                    addUpdateListener { animator ->
                        shape.setStroke(2, animator.animatedValue as Int)
                    }
                    start()
                }
            }
        }
    }

    private fun showAIGenerateSheet() {
        val sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_ai_generate, null)
        aiBottomSheet = BottomSheetDialog(this, R.style.SkillBottomSheetDialog).apply {
            setContentView(sheetView)
            window?.navigationBarColor = ContextCompat.getColor(this@SkillEditActivity, R.color.skill_sheet_background)
        }

        val promptInput = sheetView.findViewById<EditText>(R.id.aiPromptInput)
        val charCounter = sheetView.findViewById<TextView>(R.id.charCounter)
        val generateButton = sheetView.findViewById<MaterialButton>(R.id.generateButton)
        val loadingContainer = sheetView.findViewById<View>(R.id.loadingContainer)

        promptInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val length = s?.length ?: 0
                charCounter.text = "$length/500"
                val colorRes = if (length > 500) R.color.skill_counter_warning else R.color.skill_counter_normal
                charCounter.setTextColor(ContextCompat.getColor(this@SkillEditActivity, colorRes))
            }
        })

        generateButton.setOnClickListener {
            val prompt = promptInput.text.toString().trim()
            if (prompt.isEmpty()) {
                Toast.makeText(this, "请输入技能描述", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            generateButton.isEnabled = false
            loadingContainer.visibility = View.VISIBLE

            generateSkillWithAI(prompt) { success ->
                runOnUiThread {
                    generateButton.isEnabled = true
                    loadingContainer.visibility = View.GONE
                    if (success) {
                        aiBottomSheet?.dismiss()
                    }
                }
            }
        }

        aiBottomSheet?.show()
    }

    private fun showTemplateDialog() {
        val templates = arrayOf(
            "通用助手模板",
            "写作助手模板",
            "代码助手模板",
            "翻译助手模板"
        )

        val templateContents = arrayOf(
            "你是一个通用助手，帮助用户完成各种任务。\n\n请根据用户的输入提供帮助。",
            "你是一个专业的写作助手。\n\n请帮助用户:\n- 润色和优化文章\n- 提供写作建议\n- 检查语法和拼写",
            "你是一个编程助手。\n\n请帮助用户:\n- 编写和调试代码\n- 解释代码逻辑\n- 提供最佳实践建议",
            "你是一个翻译助手。\n\n请将用户输入的内容翻译成目标语言，保持原文的语气和风格。"
        )

        AlertDialog.Builder(this)
            .setTitle("选择模板")
            .setItems(templates) { _, which ->
                binding.contentInput.setText(templateContents[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadSkillData(skillName: String) {
        val token = TokenManager.getToken(this)
        if (token == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = RetrofitClient.getApiService(this@SkillEditActivity)
                val response = apiService.getSkill("Bearer $token", skillName)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.isSuccess == true && apiResponse.data != null) {
                            val skill = apiResponse.data
                            binding.nameInput.setText(skill.name)
                            binding.displayNameInput.setText(skill.displayName ?: "")
                            binding.descriptionInput.setText(skill.description ?: "")
                            binding.contentInput.setText(skill.content ?: "")

                            when (skill.category) {
                                SkillCategory.WRITING -> updateCategorySelection(SkillCategory.WRITING)
                                SkillCategory.CODING -> updateCategorySelection(SkillCategory.CODING)
                                SkillCategory.DAILY -> updateCategorySelection(SkillCategory.DAILY)
                                else -> updateCategorySelection(SkillCategory.CUSTOM)
                            }
                        } else {
                            Toast.makeText(this@SkillEditActivity, "技能不存在", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        Toast.makeText(this@SkillEditActivity, "加载失败", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SkillEditActivity, "网络错误", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun saveSkill() {
        val name = binding.nameInput.text.toString().trim()
        val displayName = binding.displayNameInput.text.toString().trim()
        val description = binding.descriptionInput.text.toString().trim()
        val content = binding.contentInput.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "请输入技能名称", Toast.LENGTH_SHORT).show()
            binding.nameInput.requestFocus()
            return
        }

        if (!name.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*$"))) {
            Toast.makeText(this, "名称只能包含英文、数字和下划线，且以字母开头", Toast.LENGTH_SHORT).show()
            binding.nameInput.requestFocus()
            return
        }

        if (content.isEmpty()) {
            Toast.makeText(this, "请输入技能提示词", Toast.LENGTH_SHORT).show()
            binding.contentInput.requestFocus()
            return
        }

        val token = TokenManager.getToken(this)
        if (token == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }

        setButtonsEnabled(false)

        val request = SkillRequest(
            name = name,
            displayName = displayName.ifEmpty { null },
            description = description.ifEmpty { null },
            content = content,
            category = selectedCategory,
            tags = null
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = RetrofitClient.getApiService(this@SkillEditActivity)
                val response = apiService.saveSkill("Bearer $token", request)

                withContext(Dispatchers.Main) {
                    setButtonsEnabled(true)

                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.isSuccess == true) {
                            val message = if (isEditMode) "技能已更新" else "技能已创建"
                            Toast.makeText(this@SkillEditActivity, message, Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        } else {
                            Toast.makeText(this@SkillEditActivity, "保存失败: ${apiResponse?.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@SkillEditActivity, "服务器错误", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setButtonsEnabled(true)
                    Toast.makeText(this@SkillEditActivity, "网络错误", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.saveButton.isEnabled = enabled
        binding.submitButton.isEnabled = enabled
    }

    private fun showPreview() {
        val content = binding.contentInput.text.toString()
        if (content.isEmpty()) {
            Toast.makeText(this, "暂无内容可预览", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("技能预览")
            .setMessage(content)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun generateSkillWithAI(prompt: String, callback: (Boolean) -> Unit) {
        val token = TokenManager.getToken(this)
        if (token == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            callback(false)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = RetrofitClient.getApiService(this@SkillEditActivity)
                val request = SkillGenerateRequest(prompt)
                val response = apiService.generateSkill("Bearer $token", request)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.isSuccess == true && apiResponse.data != null) {
                            fillFormWithGeneratedSkill(apiResponse.data)
                            Toast.makeText(this@SkillEditActivity, "生成成功！请检查内容", Toast.LENGTH_SHORT).show()
                            callback(true)
                        } else {
                            Toast.makeText(this@SkillEditActivity, "生成失败", Toast.LENGTH_SHORT).show()
                            callback(false)
                        }
                    } else {
                        Toast.makeText(this@SkillEditActivity, "生成失败", Toast.LENGTH_SHORT).show()
                        callback(false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SkillEditActivity, "网络错误", Toast.LENGTH_SHORT).show()
                    callback(false)
                }
            }
        }
    }

    private fun fillFormWithGeneratedSkill(skill: SkillGenerateResponse) {
        skill.name?.let { if (!isEditMode) binding.nameInput.setText(it) }
        skill.displayName?.let { binding.displayNameInput.setText(it) }
        skill.description?.let { binding.descriptionInput.setText(it) }
        skill.content?.let { binding.contentInput.setText(it) }

        when (skill.category) {
            SkillCategory.WRITING -> updateCategorySelection(SkillCategory.WRITING)
            SkillCategory.CODING -> updateCategorySelection(SkillCategory.CODING)
            SkillCategory.DAILY -> updateCategorySelection(SkillCategory.DAILY)
            else -> updateCategorySelection(SkillCategory.CUSTOM)
        }
    }

    private fun confirmExit() {
        val hasContent = binding.nameInput.text.toString().isNotEmpty() ||
                binding.displayNameInput.text.toString().isNotEmpty() ||
                binding.descriptionInput.text.toString().isNotEmpty() ||
                binding.contentInput.text.toString().isNotEmpty()

        if (hasContent) {
            AlertDialog.Builder(this)
                .setTitle("放弃修改？")
                .setMessage("您有未保存的更改，确定要退出吗？")
                .setPositiveButton("放弃") { _, _ -> finish() }
                .setNegativeButton("取消", null)
                .show()
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        confirmExit()
    }

    override fun onDestroy() {
        super.onDestroy()
        aiBottomSheet?.dismiss()
    }
}
