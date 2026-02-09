package top.yling.ozx.guiagent.util

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 字符串加密工具类
 * 用于保护敏感字符串（如API密钥、URL等）避免被直接反编译获取
 *
 * 使用方法：
 * 1. 使用 encryptForCode() 方法加密字符串，获取加密后的Base64字符串
 * 2. 在代码中存储加密后的字符串
 * 3. 运行时使用 decrypt() 方法解密
 *
 * 注意：此方法增加了逆向工程的难度，但不能提供绝对的安全性
 * 对于高安全要求的场景，建议使用服务端下发或硬件安全模块
 */
object StringEncryption {

    // 加密密钥 (16 bytes for AES-128)
    // 注意：实际使用时应该使用更复杂的密钥派生方案
    private val KEY_BYTES = byteArrayOf(
        0x47, 0x75, 0x69, 0x41, 0x67, 0x65, 0x6E, 0x74,
        0x53, 0x65, 0x63, 0x72, 0x65, 0x74, 0x4B, 0x65
    )

    // 初始化向量 (16 bytes)
    private val IV_BYTES = byteArrayOf(
        0x4F, 0x7A, 0x58, 0x41, 0x49, 0x53, 0x65, 0x63,
        0x75, 0x72, 0x69, 0x74, 0x79, 0x49, 0x56, 0x21
    )

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    private val secretKey: SecretKeySpec by lazy {
        SecretKeySpec(KEY_BYTES, ALGORITHM)
    }

    private val ivSpec: IvParameterSpec by lazy {
        IvParameterSpec(IV_BYTES)
    }

    /**
     * 解密字符串
     * @param encrypted Base64编码的加密字符串
     * @return 解密后的原始字符串
     */
    fun decrypt(encrypted: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decoded = Base64.getDecoder().decode(encrypted)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            // 解密失败时返回空字符串，避免崩溃
            ""
        }
    }

    /**
     * 加密字符串（用于开发时生成加密字符串）
     * 此方法主要用于开发阶段生成加密后的字符串
     * @param plainText 原始字符串
     * @return Base64编码的加密字符串
     */
    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(encrypted)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 生成用于代码中使用的加密字符串
     * 打印格式化的输出，方便复制到代码中
     */
    fun encryptForCode(plainText: String, variableName: String = "encrypted"): String {
        val encrypted = encrypt(plainText)
        return """
            // Original: $plainText
            private const val $variableName = "$encrypted"
            // Usage: StringEncryption.decrypt($variableName)
        """.trimIndent()
    }

    /**
     * 验证加密/解密是否正确
     * @param plainText 原始字符串
     * @param encrypted 加密后的Base64字符串
     * @return true 如果解密后的字符串与原始字符串匹配
     */
    fun verify(plainText: String, encrypted: String): Boolean {
        val decrypted = decrypt(encrypted)
        return decrypted == plainText
    }

    /**
     * 验证并打印加密/解密结果（用于调试）
     * @param plainText 原始字符串
     * @param encrypted 加密后的Base64字符串
     * @param label 标签（用于日志输出）
     * @return true 如果验证通过
     */
    fun verifyAndPrint(plainText: String, encrypted: String, label: String = "验证"): Boolean {
        val decrypted = decrypt(encrypted)
        val isValid = decrypted == plainText
        println("[$label]")
        println("  原始值: $plainText")
        println("  加密值: $encrypted")
        println("  解密值: $decrypted")
        println("  验证结果: ${if (isValid) "✓ 通过" else "✗ 失败"}")
        return isValid
    }

    /**
     * 简单的XOR混淆（用于不太敏感的字符串）
     * 比AES更轻量，适用于大量字符串
     */
    object XorObfuscation {
        private const val XOR_KEY = 0x5A.toByte()

        fun obfuscate(input: String): String {
            val bytes = input.toByteArray(Charsets.UTF_8)
            val result = ByteArray(bytes.size)
            for (i in bytes.indices) {
                result[i] = (bytes[i].toInt() xor XOR_KEY.toInt()).toByte()
            }
            return Base64.getEncoder().encodeToString(result)
        }

        fun deobfuscate(input: String): String {
            val decoded = Base64.getDecoder().decode(input)
            val result = ByteArray(decoded.size)
            for (i in decoded.indices) {
                result[i] = (decoded[i].toInt() xor XOR_KEY.toInt()).toByte()
            }
            return String(result, Charsets.UTF_8)
        }
    }
}

