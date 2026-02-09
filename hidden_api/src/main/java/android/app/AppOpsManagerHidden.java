package android.app;

import android.os.Build;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;

/**
 * AppOpsManager 隐藏 API
 * 参考 GKD: android.app.AppOpsManagerHidden
 * @noinspection unused
 */
@RefineAs(AppOpsManager.class)
public class AppOpsManagerHidden {
    public static int OP_POST_NOTIFICATION;
    @RequiresApi(Build.VERSION_CODES.P)
    public static String OPSTR_POST_NOTIFICATION;

    public static int OP_SYSTEM_ALERT_WINDOW;
    public static String OPSTR_SYSTEM_ALERT_WINDOW;

    @RequiresApi(Build.VERSION_CODES.Q)
    public static int OP_ACCESS_ACCESSIBILITY;

    @RequiresApi(Build.VERSION_CODES.Q)
    public static String OPSTR_ACCESS_ACCESSIBILITY;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static int OP_CREATE_ACCESSIBILITY_OVERLAY;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static String OPSTR_CREATE_ACCESSIBILITY_OVERLAY;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static int OP_ACCESS_RESTRICTED_SETTINGS;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static String OPSTR_ACCESS_RESTRICTED_SETTINGS;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static int OP_FOREGROUND_SERVICE_SPECIAL_USE;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static String OPSTR_FOREGROUND_SERVICE_SPECIAL_USE;

    // 电话权限相关
    public static int OP_CALL_PHONE;
    public static String OPSTR_CALL_PHONE;

    public static int OP_READ_PHONE_STATE;
    public static String OPSTR_READ_PHONE_STATE;

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static boolean opRestrictsRead(int op) {
        throw new RuntimeException("Stub");
    }

    /**
     * @return X_Y_Z
     */
    public static String opToName(int op) {
        throw new RuntimeException("Stub");
    }

    /**
     * @return android:x_y_z
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static String opToPublicName(int op) {
        throw new RuntimeException("Stub");
    }
}

