package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/**
 * ParceledListSlice 类
 * 参考 GKD: android.content.pm.ParceledListSlice
 * @noinspection unused
 */
public class ParceledListSlice<T extends Parcelable> implements Parcelable {
    public List<T> getList() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public int describeContents() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        throw new RuntimeException("Stub!");
    }
}

