package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import java.util.List;

/**
 * IActivityManager 接口
 * 参考 GKD: android.app.IActivityManager
 * @noinspection unused
 */
public interface IActivityManager extends IInterface {
    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    /**
     * 获取运行中的任务 (Android P+)
     */
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    /**
     * 获取运行中的任务 (Android P 之前)
     */
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, int flags);
}
