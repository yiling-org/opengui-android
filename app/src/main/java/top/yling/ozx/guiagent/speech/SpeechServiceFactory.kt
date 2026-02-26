package top.yling.ozx.guiagent.speech

import android.content.Context
import top.yling.ozx.guiagent.speech.impl.android.AndroidTTSEngine
import top.yling.ozx.guiagent.speech.impl.google.GoogleSpeechRecognizer
import top.yling.ozx.guiagent.speech.impl.iflytek.IFlytekSpeechRecognizer
import top.yling.ozx.guiagent.speech.impl.iflytek.IFlytekWakeUpEngine

/**
 * 语音服务工厂
 * 根据配置创建对应的语音服务实现
 * 
 * 使用示例：
 * ```kotlin
 * // 创建语音识别器
 * val recognizer = SpeechServiceFactory.createSpeechRecognizer(context, ASRProvider.IFLYTEK)
 * 
 * // 创建 TTS 引擎
 * val tts = SpeechServiceFactory.createTTSEngine(context, TTSProvider.ANDROID)
 * ```
 * 
 * @author shanwb
 */
object SpeechServiceFactory {

    /**
     * 语音识别提供商
     */
    enum class ASRProvider(val displayName: String) {
        /** 讯飞语音识别 */
        IFLYTEK("讯飞语音"),
        /** Google Speech-to-Text（预留） */
        GOOGLE("Google Speech"),
        /** OpenAI Whisper（预留） */
        WHISPER("Whisper"),
        /** Azure Speech（预留） */
        AZURE("Azure Speech")
    }

    /**
     * 语音唤醒提供商
     */
    enum class WakeUpProvider(val displayName: String) {
        /** 讯飞离线唤醒 */
        IFLYTEK("讯飞唤醒"),
        /** Picovoice Porcupine（预留） */
        PORCUPINE("Porcupine"),
        /** Snowboy（预留） */
        SNOWBOY("Snowboy")
    }

    /**
     * TTS 提供商
     */
    enum class TTSProvider(val displayName: String) {
        /** Android 系统 TTS */
        ANDROID("系统 TTS"),
        /** 讯飞语音合成（预留） */
        IFLYTEK("讯飞语音"),
        /** Google TTS（预留） */
        GOOGLE("Google TTS")
    }

    /**
     * 创建语音识别器
     * 
     * @param context Android Context
     * @param provider 语音识别提供商
     * @return 语音识别器实例
     * @throws UnsupportedOperationException 如果提供商尚未实现
     */
    fun createSpeechRecognizer(
        context: Context,
        provider: ASRProvider = ASRProvider.IFLYTEK
    ): SpeechRecognizer {
        return when (provider) {
            ASRProvider.IFLYTEK -> IFlytekSpeechRecognizer(context)
            ASRProvider.GOOGLE -> GoogleSpeechRecognizer(context)
            ASRProvider.WHISPER -> throw UnsupportedOperationException(
                "Whisper 尚未实现，欢迎贡献代码！"
            )
            ASRProvider.AZURE -> throw UnsupportedOperationException(
                "Azure Speech 尚未实现，欢迎贡献代码！"
            )
        }
    }

    /**
     * 创建语音唤醒引擎
     * 
     * @param context Android Context
     * @param provider 唤醒引擎提供商
     * @return 唤醒引擎实例
     * @throws UnsupportedOperationException 如果提供商尚未实现
     */
    fun createWakeUpEngine(
        context: Context,
        provider: WakeUpProvider = WakeUpProvider.IFLYTEK
    ): WakeUpEngine {
        return when (provider) {
            WakeUpProvider.IFLYTEK -> IFlytekWakeUpEngine(context)
            WakeUpProvider.PORCUPINE -> throw UnsupportedOperationException(
                "Porcupine 尚未实现，欢迎贡献代码！"
            )
            WakeUpProvider.SNOWBOY -> throw UnsupportedOperationException(
                "Snowboy 尚未实现，欢迎贡献代码！"
            )
        }
    }

    /**
     * 创建 TTS 引擎
     * 
     * @param context Android Context
     * @param provider TTS 提供商
     * @return TTS 引擎实例
     * @throws UnsupportedOperationException 如果提供商尚未实现
     */
    fun createTTSEngine(
        context: Context,
        provider: TTSProvider = TTSProvider.ANDROID
    ): TextToSpeechEngine {
        return when (provider) {
            TTSProvider.ANDROID -> AndroidTTSEngine(context)
            TTSProvider.IFLYTEK -> throw UnsupportedOperationException(
                "讯飞 TTS 尚未实现，欢迎贡献代码！"
            )
            TTSProvider.GOOGLE -> throw UnsupportedOperationException(
                "Google TTS 尚未实现，欢迎贡献代码！"
            )
        }
    }

    /**
     * 获取所有可用的语音识别提供商
     * @return 已实现的提供商列表
     */
    fun getAvailableASRProviders(): List<ASRProvider> {
        return listOf(ASRProvider.IFLYTEK, ASRProvider.GOOGLE)
    }

    /**
     * 获取所有可用的唤醒引擎提供商
     * @return 已实现的提供商列表
     */
    fun getAvailableWakeUpProviders(): List<WakeUpProvider> {
        return listOf(WakeUpProvider.IFLYTEK)
    }

    /**
     * 获取所有可用的 TTS 提供商
     * @return 已实现的提供商列表
     */
    fun getAvailableTTSProviders(): List<TTSProvider> {
        return listOf(TTSProvider.ANDROID)
    }
}

