package android.hardware.display;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * IVirtualDisplayCallback 隐藏接口
 * 虚拟屏幕回调
 * @noinspection unused
 */
public interface IVirtualDisplayCallback extends IInterface {
    abstract class Stub extends Binder implements IVirtualDisplayCallback {
        public static IVirtualDisplayCallback asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    void onPaused();
    void onResumed();
    void onStopped();
}
