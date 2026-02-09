package top.yling.ozx.guiagent

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yling.ozx.guiagent.databinding.ActivitySkillListBinding
import top.yling.ozx.guiagent.databinding.ItemSkillBinding
import top.yling.ozx.guiagent.model.SkillDocument
import top.yling.ozx.guiagent.network.RetrofitClient
import top.yling.ozx.guiagent.util.TokenManager

/**
 * Skill List Activity
 * Displays user's own skills only (no system skills)
 */
class SkillListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySkillListBinding
    private lateinit var skillAdapter: SkillAdapter
    private var allSkills: List<SkillDocument> = emptyList()

    companion object {
        const val REQUEST_EDIT_SKILL = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySkillListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        setupWindowInsets()
        setupUI()
        loadSkills()
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
        // Back button
        binding.backButton.setOnClickListener { finish() }

        // Add button
        binding.addButton.setOnClickListener {
            val intent = Intent(this, SkillEditActivity::class.java)
            startActivityForResult(intent, REQUEST_EDIT_SKILL)
        }

        // Retry button
        binding.retryButton.setOnClickListener { loadSkills() }

        // Setup RecyclerView
        skillAdapter = SkillAdapter(
            onEditClick = { skill -> editSkill(skill) },
            onDeleteClick = { skill -> confirmDeleteSkill(skill) },
            onToggleStatus = { skill, enabled -> toggleSkillStatus(skill, enabled) }
        )
        binding.skillRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SkillListActivity)
            adapter = skillAdapter
        }
    }

    private fun loadSkills() {
        showLoading()

        val token = TokenManager.getToken(this)
        if (token == null) {
            showError("请先登录")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = RetrofitClient.getApiService(this@SkillListActivity)
                // Only load user's own skills, not system skills
                val response = apiService.getMySkills("Bearer $token")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.isSuccess == true) {
                            allSkills = apiResponse.data ?: emptyList()
                            displaySkills()
                        } else {
                            showError(apiResponse?.message ?: "加载技能失败")
                        }
                    } else {
                        showError("服务器错误: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("网络错误: ${e.message}")
                }
            }
        }
    }

    private fun displaySkills() {
        if (allSkills.isEmpty()) {
            showEmpty()
        } else {
            showSkills(allSkills)
        }
    }

    private fun editSkill(skill: SkillDocument) {
        val intent = Intent(this, SkillEditActivity::class.java).apply {
            putExtra(SkillEditActivity.EXTRA_SKILL_NAME, skill.name)
            putExtra(SkillEditActivity.EXTRA_IS_EDIT, true)
        }
        startActivityForResult(intent, REQUEST_EDIT_SKILL)
    }

    private fun confirmDeleteSkill(skill: SkillDocument) {
        AlertDialog.Builder(this)
            .setTitle("Delete Skill")
            .setMessage("Are you sure you want to delete \"${skill.getTitle()}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteSkill(skill) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSkill(skill: SkillDocument) {
        val token = TokenManager.getToken(this) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = RetrofitClient.getApiService(this@SkillListActivity)
                val response = apiService.deleteSkill("Bearer $token", skill.name)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body()?.isSuccess == true) {
                        Toast.makeText(this@SkillListActivity, "Skill deleted", Toast.LENGTH_SHORT).show()
                        loadSkills()
                    } else {
                        Toast.makeText(this@SkillListActivity, "Delete failed: ${response.body()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SkillListActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleSkillStatus(skill: SkillDocument, enabled: Boolean) {
        val token = TokenManager.getToken(this) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = RetrofitClient.getApiService(this@SkillListActivity)
                val response = apiService.toggleSkillStatus("Bearer $token", skill.name, enabled)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body()?.isSuccess == true) {
                        val statusText = if (enabled) "enabled" else "disabled"
                        Toast.makeText(this@SkillListActivity, "Skill $statusText", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@SkillListActivity, "Status update failed", Toast.LENGTH_SHORT).show()
                        // Reload to reset switch state
                        loadSkills()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SkillListActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                    loadSkills()
                }
            }
        }
    }

    private fun showLoading() {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.skillRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.errorStateLayout.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.loadingIndicator.visibility = View.GONE
        binding.skillRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.errorStateLayout.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.loadingIndicator.visibility = View.GONE
        binding.skillRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.errorStateLayout.visibility = View.VISIBLE
        binding.errorText.text = message
    }

    private fun showSkills(skills: List<SkillDocument>) {
        binding.loadingIndicator.visibility = View.GONE
        binding.skillRecyclerView.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
        binding.errorStateLayout.visibility = View.GONE
        skillAdapter.submitList(skills)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT_SKILL && resultCode == RESULT_OK) {
            loadSkills()
        }
    }
}

/**
 * Skill Adapter for RecyclerView
 * Only displays user's own skills
 */
class SkillAdapter(
    private val onEditClick: (SkillDocument) -> Unit,
    private val onDeleteClick: (SkillDocument) -> Unit,
    private val onToggleStatus: (SkillDocument, Boolean) -> Unit
) : RecyclerView.Adapter<SkillAdapter.SkillViewHolder>() {

    private val skills = mutableListOf<SkillDocument>()

    fun submitList(newSkills: List<SkillDocument>) {
        skills.clear()
        skills.addAll(newSkills)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkillViewHolder {
        val binding = ItemSkillBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SkillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SkillViewHolder, position: Int) {
        holder.bind(skills[position])
    }

    override fun getItemCount(): Int = skills.size

    inner class SkillViewHolder(
        private val binding: ItemSkillBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(skill: SkillDocument) {
            // Name
            binding.skillName.text = skill.getTitle()

            // Hide source tag (all are user skills now)
            binding.sourceTag.visibility = View.GONE

            // Category tag (displayed inline)
            binding.categoryTag.text = skill.getCategoryText()
            binding.categoryTag.visibility = View.VISIBLE

            // Description
            if (skill.description.isNullOrEmpty()) {
                binding.skillDescription.visibility = View.GONE
            } else {
                binding.skillDescription.visibility = View.VISIBLE
                binding.skillDescription.text = skill.description
            }

            // Enable switch
            binding.enableSwitch.setOnCheckedChangeListener(null)
            binding.enableSwitch.isChecked = skill.enabled ?: true
            binding.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggleStatus(skill, isChecked)
            }

            // All are user skills - show edit/delete, hide fork
            binding.editButton.visibility = View.VISIBLE
            binding.deleteButton.visibility = View.VISIBLE
            binding.forkButton.visibility = View.GONE

            // Button click handlers
            binding.editButton.setOnClickListener { onEditClick(skill) }
            binding.deleteButton.setOnClickListener { onDeleteClick(skill) }
        }
    }
}
