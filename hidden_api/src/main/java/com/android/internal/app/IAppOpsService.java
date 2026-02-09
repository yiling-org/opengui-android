package com.android.internal.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * IAppOpsService 接口
 * 参考 GKD: com.android.internal.app.IAppOpsService
 * @noinspection unused
 */
public interface IAppOpsService extends IInterface {
    abstract class Stub extends Binder implements IAppOpsService {
        public static IAppOpsService asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    void setMode(int code, int uid, String packageName, int mode);
}

