package com.example.danale.new_genfence.move_trend_detector;

import android.content.Context;
import android.location.Location;
import android.widget.Toast;

/**
 * Date:2017/10/24 <p>
 * Author:chenzehao@danale.com <p>
 * Description:运动趋势检测
 */

public class MoveTrendDetector {
    public static final int GO_HOME = 3; // 回家
    public static final int LEAVE_HOME = 4; // 离家
    public static final int UNKNOWN = -1; // 未知（数据不够，无法检测出结果）

    private static final int SIZE = 3;
    private Location[] locations = new Location[SIZE];
    private int locationIndex = 0;

    private Location destination;

    private Context mContext;

    public MoveTrendDetector(Context context) {
        mContext = context;
    }

    public MoveTrendDetector(Location destination) {
        this.destination = destination;
    }

    public void setDestination(Location destination) {
        this.destination = destination;
    }

    public void setDestination(double longitude, double latitude) {
        if (destination == null) {
            destination = new Location("");
        }
        destination.setLongitude(longitude);
        destination.setLatitude(latitude);
    }

    public Location getDestination() {
        return destination;
    }

    public void reset() {
        locationIndex = 0;
        across20MetersTime = 0L;
        across50MetersTime = 0L;
    }

    /**
     * 收集实时定位得到的location
     */
    public boolean collectLocation(Location location) {
        if (location.getAccuracy() > 0 && location.getAccuracy() <= GeofenceUtil.GPS_ACCURACY_IN_METERS) {
            if (locationIndex == 0) {
                locations[locationIndex++ % SIZE] = location;
                return true;
            } else {
                Location last = locations[(locationIndex - 1) % SIZE];
                if (GeofenceUtil.distance(location, last) <= 100) {
                    locations[locationIndex++ % SIZE] = location;
                    return true;
                }
            }
        }
        return false;
    }

    private long across50MetersTime = 0L; // 跨越50m的时刻
    private long across20MetersTime = 0L; // 跨越20m的时刻

    /**
     * 获得移动趋势的检测结果
     * @return 判断得出的移动趋势
     * @see #GO_HOME
     * @see #LEAVE_HOME
     * @see #UNKNOWN
     *
     * 逻辑是：首次进入20米范围内记录时间t1，首次进入50米到100米范围内记录时间t2,
     * 如果t1小于t2，判定为LEAVE_HOME；如果t2小于t1判定为GO_HOME。
     */
    public int getMoveTrendResult() {
        if (locationIndex < SIZE) { // 取的点太少，无法判断
            return UNKNOWN;
        } else {
            float averageDistance = getAverageDistance();
            if (averageDistance <= 20) {
                if (across20MetersTime == 0L) {
                    across20MetersTime = System.currentTimeMillis();
                }
            } else if (averageDistance >= 50 && averageDistance <= 100) {
                if (across50MetersTime == 0L) {
                    across50MetersTime = System.currentTimeMillis();
                }
            } else {
                return UNKNOWN;
            }

            if (across20MetersTime != 0L && across50MetersTime != 0L) {
                if (across20MetersTime < across50MetersTime) {
                    return LEAVE_HOME;
                } else if (across20MetersTime > across50MetersTime) {
                    return GO_HOME;
                } else {
                    return UNKNOWN;
                }
            } else {
                return UNKNOWN;
            }
        }
    }

    /**
     * 计算locations数组中所有有效location距离目标的平均距离（单位：m）
     * @return 平均距离
     */
    private float getAverageDistance() {
        float distance = 0.0f;
        if (locationIndex > 0 && locationIndex <= SIZE) {
            for (int i = 0; i < locationIndex; i++) {
                distance += GeofenceUtil.distance(locations[i], destination);
            }
            distance /= locationIndex;
        } else if (locationIndex > SIZE) {
            for (int i = 0; i < SIZE; i++) {
                distance += GeofenceUtil.distance(locations[i], destination);
            }
            distance /= SIZE;
        }

        System.out.println("averDistance:"+distance);
        return distance;
    }

    /**
     * 计算locations数组中所有有效location距离目标的最短距离（单位：m）
     * @return 最短距离
     */
    private float getMinDistance() {
        float minDistance = 0.0f;
        if (locationIndex > 0 && locationIndex <= SIZE) {
            minDistance = GeofenceUtil.distance(locations[0], destination);
            for (int i = 1; i < locationIndex; i++) {
                float distance = GeofenceUtil.distance(locations[i], destination);
                if (distance < minDistance) {
                    minDistance = distance;
                }
            }
        } else if (locationIndex > SIZE) {
            minDistance = GeofenceUtil.distance(locations[0], destination);
            for (int i = 1; i < SIZE; i++) {
                float distance = GeofenceUtil.distance(locations[i], destination);
                if (distance < minDistance) {
                    minDistance = distance;
                }
            }
        }
        return minDistance;
    }
}
