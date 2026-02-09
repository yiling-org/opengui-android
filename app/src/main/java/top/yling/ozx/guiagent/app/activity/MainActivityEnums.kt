package top.yling.ozx.guiagent.app.activity

/**
 * MainActivity 相关枚举定义
 * @author shanwb
 */

/**
 * 录音状态
 */
enum class RecordingState {
    IDLE,       // 空闲
    RECORDING,  // 录音中
    PROCESSING, // 处理中
    SUCCESS,    // 成功
    ERROR       // 错误
}

/**
 * 任务状态
 */
enum class TaskStatus {
    IDLE,       // 空闲状态，没有任务在执行
    RUNNING,    // 任务正在执行中
    COMPLETED,  // 任务已完成
    CANCELLED   // 任务已取消
}

/**
 * 输入模式
 */
enum class InputMode {
    VOICE,  // 语音输入模式
    TEXT    // 文字输入模式
}
