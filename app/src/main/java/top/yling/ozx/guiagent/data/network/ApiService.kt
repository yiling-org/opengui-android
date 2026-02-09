package top.yling.ozx.guiagent.network

import top.yling.ozx.guiagent.model.ApiResponse
import top.yling.ozx.guiagent.model.AuthResponse
import top.yling.ozx.guiagent.model.LoginRequest
import top.yling.ozx.guiagent.model.ModelType
import top.yling.ozx.guiagent.model.RegisterRequest
import top.yling.ozx.guiagent.model.UpdateProfileRequest
import top.yling.ozx.guiagent.model.SkillDocument
import top.yling.ozx.guiagent.model.SkillGenerateRequest
import top.yling.ozx.guiagent.model.SkillGenerateResponse
import top.yling.ozx.guiagent.model.SkillRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API Service interface for Retrofit
 */
interface ApiService {

    /**
     * Login user
     */
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponse>>

    /**
     * Register new user
     */
    @POST("/api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthResponse>>

    /**
     * Get user profile
     */
    @GET("/api/auth/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ApiResponse<AuthResponse>>

    /**
     * Update user profile description
     */
    @PUT("/api/auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<ApiResponse<AuthResponse>>

    // ============ Skill APIs ============

    /**
     * Get all skills (merged: user + system)
     */
    @GET("/api/skills")
    suspend fun getSkills(
        @Header("Authorization") token: String
    ): Response<ApiResponse<List<SkillDocument>>>

    /**
     * Get user's own skills only
     */
    @GET("/api/skills/my")
    suspend fun getMySkills(
        @Header("Authorization") token: String
    ): Response<ApiResponse<List<SkillDocument>>>

    /**
     * Get system preset skills
     */
    @GET("/api/skills/system")
    suspend fun getSystemSkills(): Response<ApiResponse<List<SkillDocument>>>

    /**
     * Get a specific skill by name
     */
    @GET("/api/skills/{name}")
    suspend fun getSkill(
        @Header("Authorization") token: String,
        @Path("name") name: String
    ): Response<ApiResponse<SkillDocument>>

    /**
     * Create or update a user skill
     */
    @POST("/api/skills")
    suspend fun saveSkill(
        @Header("Authorization") token: String,
        @Body request: SkillRequest
    ): Response<ApiResponse<SkillDocument>>

    /**
     * Delete a user skill
     */
    @DELETE("/api/skills/{name}")
    suspend fun deleteSkill(
        @Header("Authorization") token: String,
        @Path("name") name: String
    ): Response<ApiResponse<Map<String, Boolean>>>

    /**
     * Fork a system skill to user space
     */
    @POST("/api/skills/fork/{systemSkillName}")
    suspend fun forkSkill(
        @Header("Authorization") token: String,
        @Path("systemSkillName") systemSkillName: String
    ): Response<ApiResponse<SkillDocument>>

    /**
     * Search skills by keyword
     */
    @GET("/api/skills/search")
    suspend fun searchSkills(
        @Header("Authorization") token: String,
        @Query("keyword") keyword: String
    ): Response<ApiResponse<List<SkillDocument>>>

    /**
     * Toggle skill status (enable/disable)
     */
    @PUT("/api/skills/{name}/status")
    suspend fun toggleSkillStatus(
        @Header("Authorization") token: String,
        @Path("name") name: String,
        @Query("enabled") enabled: Boolean
    ): Response<ApiResponse<Map<String, Boolean>>>

    /**
     * Generate skill using AI
     * 使用 AI 生成技能定义
     */
    @POST("/api/skills/generate")
    suspend fun generateSkill(
        @Header("Authorization") token: String,
        @Body request: SkillGenerateRequest
    ): Response<ApiResponse<SkillGenerateResponse>>

    // ============ Config APIs ============

    /**
     * Get supported model types
     * 获取支持的模型类型列表（无需认证）
     */
    @GET("/api/config/model-types")
    suspend fun getModelTypes(): Response<ApiResponse<List<ModelType>>>
}
