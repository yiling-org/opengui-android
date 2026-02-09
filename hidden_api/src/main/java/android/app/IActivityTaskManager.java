package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import java.util.List;

/**
 * IActivityTaskManager 接口 (Android 10+)
 * 参考 GKD: android.app.IActivityTaskManager
 * @noinspection unused
 */
public interface IActivityTaskManager extends IInterface {
    abstract class Stub extends Binder implements IActivityTaskManager {
        public static IActivityTaskManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    /**
     * 获取运行中的任务 (类型 1)
     */
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    /**
     * 获取运行中的任务 (类型 2)
     */
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra);

    /**
     * 获取运行中的任务 (类型 3)
     */
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra, int displayId);
}
