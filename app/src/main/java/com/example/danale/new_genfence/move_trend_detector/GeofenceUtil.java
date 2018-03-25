package com.example.danale.new_genfence.move_trend_detector;

import android.location.Location;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Date:2017/10/24 <p>
 * Author:chenzehao@danale.com <p>
 * Description:地理围栏相关工具方法
 */

public class GeofenceUtil {
    /**
     * 计算两个坐标之间的距离（单位：米）
     * @param location1 坐标1
     * @param location2 坐标2
     * @return 距离
     */
    public static float distance(Location location1, Location location2) {
        float[] results = new float[1];
        Location.distanceBetween(location1.getLatitude(), location1.getLongitude(),
                location2.getLatitude(), location2.getLongitude(), results);
        return results[0];
    }

    private static SimpleDateFormat simpleDateFormat;

    public static String formatTime(long time) {
        if (simpleDateFormat == null) {
            simpleDateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        }
        return simpleDateFormat.format(new Date(time));
    }

    public static float GPS_ACCURACY_IN_METERS = 36;
}
