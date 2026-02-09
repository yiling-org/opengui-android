package android.media.projection;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * IMediaProjectionCallback 隐藏接口
 * 媒体投影回调
 * @noinspection unused
 */
public interface IMediaProjectionCallback extends IInterface {
    abstract class Stub extends Binder implements IMediaProjectionCallback {
        public static IMediaProjectionCallback asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    void onStop();
}
