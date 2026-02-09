package android.hardware.display;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.view.Surface;

/**
 * IDisplayManager 隐藏接口
 * 用于创建和管理 VirtualDisplay
 * @noinspection unused
 */
public interface IDisplayManager extends IInterface {
    abstract class Stub extends Binder implements IDisplayManager {
        public static IDisplayManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    /**
     * 创建虚拟屏幕 (Android 13+ 使用 VirtualDisplayConfig)
     * @param virtualDisplayConfig VirtualDisplayConfig 对象
     * @param callback 回调接口
     * @param projectionToken MediaProjection token
     * @param packageName 包名
     * @return displayId
     */
    int createVirtualDisplay(VirtualDisplayConfig virtualDisplayConfig,
                             IVirtualDisplayCallback callback,
                             android.media.projection.IMediaProjection projectionToken,
                             String packageName);

    /**
     * 释放虚拟屏幕
     * @param appToken 应用 token
     */
    void releaseVirtualDisplay(IBinder appToken);
}
