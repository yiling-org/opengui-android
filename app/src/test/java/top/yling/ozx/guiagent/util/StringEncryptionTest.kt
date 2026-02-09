package top.yling.ozx.guiagent.util

import org.junit.Test
import org.junit.Assert.*

/**
 * 测试 StringEncryption 加密解密功能
 * 
 * 注意：不要在测试中包含真实的 API 密钥或敏感数据！
 * 所有敏感配置应通过 local.properties 配置，不应提交到代码库。
 */
class StringEncryptionTest {

    @Test
    fun testEncryptDecrypt() {
        val original = "test_string_123"
        val encrypted = StringEncryption.encrypt(original)
        val decrypted = StringEncryption.decrypt(encrypted)

        println("Original: $original")
        println("Encrypted: $encrypted")
        println("Decrypted: $decrypted")

        assertEquals(original, decrypted)
    }

    @Test
    fun testEncryptDecryptWithChineseCharacters() {
        val original = "测试中文字符串"
        val encrypted = StringEncryption.encrypt(original)
        val decrypted = StringEncryption.decrypt(encrypted)

        assertEquals(original, decrypted)
    }

    @Test
    fun testEncryptDecryptWithSpecialCharacters() {
        val original = "test@#\$%^&*()_+-={}[]|:;<>?,./"
        val encrypted = StringEncryption.encrypt(original)
        val decrypted = StringEncryption.decrypt(encrypted)

        assertEquals(original, decrypted)
    }

    @Test
    fun testEncryptDecryptWithEmptyString() {
        val original = ""
        val encrypted = StringEncryption.encrypt(original)
        val decrypted = StringEncryption.decrypt(encrypted)

        assertEquals(original, decrypted)
    }

    @Test
    fun testEncryptDecryptWithLongString() {
        val original = "a".repeat(1000)
        val encrypted = StringEncryption.encrypt(original)
        val decrypted = StringEncryption.decrypt(encrypted)

        assertEquals(original, decrypted)
    }

    @Test
    fun testDecryptInvalidString() {
        // 解密无效字符串应返回空字符串，不应崩溃
        val result = StringEncryption.decrypt("invalid_base64_string")
        assertEquals("", result)
    }

    @Test
    fun testVerifyFunction() {
        val original = "verify_test"
        val encrypted = StringEncryption.encrypt(original)

        assertTrue(StringEncryption.verify(original, encrypted))
        assertFalse(StringEncryption.verify("wrong_value", encrypted))
    }

    @Test
    fun testXorObfuscation() {
        val original = "xor_test_string"
        val obfuscated = StringEncryption.XorObfuscation.obfuscate(original)
        val deobfuscated = StringEncryption.XorObfuscation.deobfuscate(obfuscated)

        assertEquals(original, deobfuscated)
    }
}
