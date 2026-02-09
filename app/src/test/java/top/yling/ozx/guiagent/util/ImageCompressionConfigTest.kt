package top.yling.ozx.guiagent.util

import android.app.Application
import android.util.DisplayMetrics
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ImageCompressionConfig 压缩参数测试
 *
 * 测试图片压缩配置的常量值、范围约束和动态默认值逻辑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImageCompressionConfigTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // 每次测试前重置为默认值
        ImageCompressionConfig.resetToDefaults(context)
    }

    // ========== 常量值测试 ==========

    @Test
    fun `默认JPEG质量应为85`() {
        assertEquals("DEFAULT_JPEG_QUALITY 应为 85", 85, ImageCompressionConfig.DEFAULT_JPEG_QUALITY)
    }

    @Test
    fun `高分辨率默认缩放比例应为0_4`() {
        assertEquals("DEFAULT_SCALE_FACTOR_HIGH_RES 应为 0.4",
            0.4f, ImageCompressionConfig.DEFAULT_SCALE_FACTOR_HIGH_RES, 0.001f)
    }

    @Test
    fun `低分辨率默认缩放比例应为0_7`() {
        assertEquals("DEFAULT_SCALE_FACTOR_LOW_RES 应为 0.7",
            0.7f, ImageCompressionConfig.DEFAULT_SCALE_FACTOR_LOW_RES, 0.001f)
    }

    @Test
    fun `JPEG质量范围应为70到100`() {
        assertEquals("MIN_JPEG_QUALITY 应为 70", 70, ImageCompressionConfig.MIN_JPEG_QUALITY)
        assertEquals("MAX_JPEG_QUALITY 应为 100", 100, ImageCompressionConfig.MAX_JPEG_QUALITY)
    }

    @Test
    fun `缩放比例范围应为0_3到1_0`() {
        assertEquals("MIN_SCALE_FACTOR 应为 0.3",
            0.3f, ImageCompressionConfig.MIN_SCALE_FACTOR, 0.001f)
        assertEquals("MAX_SCALE_FACTOR 应为 1.0",
            1.0f, ImageCompressionConfig.MAX_SCALE_FACTOR, 0.001f)
    }

    @Test
    fun `兼容性DEFAULT_SCALE_FACTOR应等于高分辨率默认值`() {
        assertEquals("DEFAULT_SCALE_FACTOR 应等于 DEFAULT_SCALE_FACTOR_HIGH_RES",
            ImageCompressionConfig.DEFAULT_SCALE_FACTOR_HIGH_RES,
            ImageCompressionConfig.DEFAULT_SCALE_FACTOR, 0.001f)
    }

    // ========== JPEG 质量 get/set 测试 ==========

    @Test
    fun `获取默认JPEG质量应为85`() {
        val quality = ImageCompressionConfig.getJpegQuality(context)
        assertEquals("默认 JPEG 质量应为 85", 85, quality)
    }

    @Test
    fun `设置JPEG质量应生效`() {
        ImageCompressionConfig.setJpegQuality(context, 90)
        val quality = ImageCompressionConfig.getJpegQuality(context)
        assertEquals("设置后 JPEG 质量应为 90", 90, quality)
    }

    @Test
    fun `JPEG质量低于最小值应被钳制`() {
        ImageCompressionConfig.setJpegQuality(context, 50) // 低于 MIN_JPEG_QUALITY=70
        val quality = ImageCompressionConfig.getJpegQuality(context)
        assertEquals("低于最小值应被钳制为 70", 70, quality)
    }

    @Test
    fun `JPEG质量高于最大值应被钳制`() {
        ImageCompressionConfig.setJpegQuality(context, 150) // 高于 MAX_JPEG_QUALITY=100
        val quality = ImageCompressionConfig.getJpegQuality(context)
        assertEquals("高于最大值应被钳制为 100", 100, quality)
    }

    @Test
    fun `JPEG质量在边界值应正常设置`() {
        ImageCompressionConfig.setJpegQuality(context, 70)
        assertEquals("边界值 70 应正常", 70, ImageCompressionConfig.getJpegQuality(context))

        ImageCompressionConfig.setJpegQuality(context, 100)
        assertEquals("边界值 100 应正常", 100, ImageCompressionConfig.getJpegQuality(context))
    }

    // ========== 缩放比例 get/set 测试 ==========

    @Test
    fun `设置缩放比例应生效`() {
        ImageCompressionConfig.setScaleFactor(context, 0.6f)
        val scale = ImageCompressionConfig.getScaleFactor(context)
        assertEquals("设置后缩放比例应为 0.6", 0.6f, scale, 0.001f)
    }

    @Test
    fun `缩放比例低于最小值应被钳制`() {
        ImageCompressionConfig.setScaleFactor(context, 0.1f) // 低于 MIN_SCALE_FACTOR=0.3
        val scale = ImageCompressionConfig.getScaleFactor(context)
        assertEquals("低于最小值应被钳制为 0.3", 0.3f, scale, 0.001f)
    }

    @Test
    fun `缩放比例高于最大值应被钳制`() {
        ImageCompressionConfig.setScaleFactor(context, 1.5f) // 高于 MAX_SCALE_FACTOR=1.0
        val scale = ImageCompressionConfig.getScaleFactor(context)
        assertEquals("高于最大值应被钳制为 1.0", 1.0f, scale, 0.001f)
    }

    @Test
    fun `缩放比例在边界值应正常设置`() {
        ImageCompressionConfig.setScaleFactor(context, 0.3f)
        assertEquals("边界值 0.3 应正常", 0.3f, ImageCompressionConfig.getScaleFactor(context), 0.001f)

        ImageCompressionConfig.setScaleFactor(context, 1.0f)
        assertEquals("边界值 1.0 应正常", 1.0f, ImageCompressionConfig.getScaleFactor(context), 0.001f)
    }

    // ========== 重置测试 ==========

    @Test
    fun `重置后JPEG质量应恢复默认值`() {
        ImageCompressionConfig.setJpegQuality(context, 95)
        ImageCompressionConfig.resetToDefaults(context)

        val quality = ImageCompressionConfig.getJpegQuality(context)
        assertEquals("重置后 JPEG 质量应为 85", 85, quality)
    }

    @Test
    fun `重置后缩放比例应使用屏幕分辨率动态默认值`() {
        ImageCompressionConfig.setScaleFactor(context, 0.9f)
        ImageCompressionConfig.resetToDefaults(context)

        // 重置后应使用 getDefaultScaleFactorForScreen 的动态值
        val expectedDefault = ImageCompressionConfig.getDefaultScaleFactorForScreen(context)
        val scale = ImageCompressionConfig.getScaleFactor(context)
        assertEquals("重置后缩放比例应为屏幕动态默认值", expectedDefault, scale, 0.001f)
    }

    // ========== 用户设置标记测试 ==========

    @Test
    fun `未手动设置过缩放比例时应使用动态默认值`() {
        // 不调用 setScaleFactor，直接获取
        val scale = ImageCompressionConfig.getScaleFactor(context)
        val expectedDefault = ImageCompressionConfig.getDefaultScaleFactorForScreen(context)
        assertEquals("未设置过应使用动态默认值", expectedDefault, scale, 0.001f)
    }

    @Test
    fun `手动设置过缩放比例后应使用保存的值`() {
        ImageCompressionConfig.setScaleFactor(context, 0.55f)
        val scale = ImageCompressionConfig.getScaleFactor(context)
        assertEquals("手动设置后应使用保存的值", 0.55f, scale, 0.001f)
    }

    @Test
    fun `重置后缩放比例应恢复为未手动设置状态`() {
        ImageCompressionConfig.setScaleFactor(context, 0.55f)
        ImageCompressionConfig.resetToDefaults(context)

        // 重置后应恢复到"未手动设置"状态，使用动态默认值
        val scale = ImageCompressionConfig.getScaleFactor(context)
        val expectedDefault = ImageCompressionConfig.getDefaultScaleFactorForScreen(context)
        assertEquals("重置后应恢复动态默认值", expectedDefault, scale, 0.001f)
    }
}

