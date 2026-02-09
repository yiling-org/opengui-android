package top.yling.ozx.guiagent.data.model

import com.google.gson.annotations.SerializedName

/**
 * 任务相关数据模型
 * @author shanwb
 */

/**
 * 任务详情 DTO（包含步骤信息）
 */
data class TaskWithStepsDTO(
    @SerializedName("taskId")
    val taskId: String?,

    @SerializedName("clientId")
    val clientId: String?,

    @SerializedName("androidId")
    val androidId: String?,

    @SerializedName("userId")
    val userId: String?,

    @SerializedName("agentId")
    val agentId: String?,

    @SerializedName("description")
    val description: String?,

    @SerializedName("status")
    val status: String?,

    @SerializedName("currentStep")
    val currentStep: Int?,

    @SerializedName("totalSteps")
    val totalSteps: Int?,

    @SerializedName("progress")
    val progress: Int?,

    @SerializedName("lastMessage")
    val lastMessage: String?,

    @SerializedName("lastAction")
    val lastAction: String?,

    @SerializedName("result")
    val result: String?,

    @SerializedName("errorMessage")
    val errorMessage: String?,

    @SerializedName("cancelReason")
    val cancelReason: String?,

    @SerializedName("createdAt")
    val createdAt: Long?,

    @SerializedName("updatedAt")
    val updatedAt: Long?,

    @SerializedName("endedAt")
    val endedAt: Long?,

    @SerializedName("durationMs")
    val durationMs: Long?,

    @SerializedName("steps")
    val steps: List<StepDTO>?
) {
    /**
     * 步骤 DTO
     */
    data class StepDTO(
        @SerializedName("stepIndex")
        val stepIndex: Int?,

        @SerializedName("stepName")
        val stepName: String?,

        @SerializedName("status")
        val status: String?,

        @SerializedName("startTime")
        val startTime: Long?,

        @SerializedName("endTime")
        val endTime: Long?,

        @SerializedName("errorMsg")
        val errorMsg: String?
    )
}

/**
 * 任务详情 API 响应包装类
 * 对应服务端的 Result<TaskWithStepsDTO> 结构
 */
data class TaskApiResultResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String?,

    @SerializedName("data")
    val data: TaskWithStepsDTO?
) {
    val isSuccess: Boolean
        get() = code == 0
}
