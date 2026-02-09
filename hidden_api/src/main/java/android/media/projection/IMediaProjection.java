package android.media.projection;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * IMediaProjection 隐藏接口
 * 媒体投影
 * @noinspection unused
 */
public interface IMediaProjection extends IInterface {
    abstract class Stub extends Binder implements IMediaProjection {
        public static IMediaProjection asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    void start(IMediaProjectionCallback callback);
    void stop();
}
