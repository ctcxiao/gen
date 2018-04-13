package com.example.danale.new_genfence.move_mode_detector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.maps2d.AMapUtils;
import com.amap.api.maps2d.model.LatLng;
import com.example.danale.new_genfence.DetectActivity;
import com.example.danale.new_genfence.move_trend_detector.GeofenceUtil;


/**
 * Date:2017/10/24 <p>
 * Author:chenzehao@danale.com <p>
 * Description:运动方式检测
 */

public class MoveModeDetector implements SensorEventListener {
    public static final int STILL = 0; // 静止
    public static final int STEP = 1; // 步行
    public static final int DRIVE = 2; // 开车
    public static final int UNKNOWN = -1; // 未知（数据不够，无法检测出结果）

    public static final int RUN = 3;//跑步
    private static final int SIZE = 10;// TODO: 2018/3/26 数量到底取多少好
    private Location[] locations = new Location[SIZE];
    private int locationIndex = 0;

    private StepDetectHelper stepDetectHelper;
    private int useWhichSensor = -1;

    private SensorManager sensorManager;
    private Context mContext;

    private boolean isDrive = false;
    private boolean isStep = false;

    private float x;
    private float y;
    private float z;

    private long firstComeInDrive;
    private long firstComeInStep;

    private int offset_drive;
    private int offset_step;

    private Location destination;

    public MoveModeDetector(Context context) {
        stepDetectHelper = new StepDetectHelper(context);
        mContext = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void clearLocation()
    {
        locations = new Location[SIZE];
    }
    public void setDestination(double longitude, double latitude) {
        if (destination == null) {
            destination = new Location("");
        }
        destination.setLongitude(longitude);
        destination.setLatitude(latitude);
    }
    /**
     * 注册步行检测相关传感器
     */
    public void startDetect() {
        checkSensor();
        registerSensorListener();
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
    }

    /**
     * 注销步行检测相关传感器
     */
    public void stopDetect() {
        unregisterSensorListener();
        locationIndex = 0;
        sensorManager.unregisterListener(this);
    }

    /**
     * 收集实时定位得到的location
     */
    public boolean collectLocation(Location location) {
        if(location ==  null){
            return false;
        }
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
        return false;
    }

    /**
     * 获得运动方式的检测结果
     *
     * @return 运动方式
     * @see #STILL
     * @see #STEP
     * @see #DRIVE
     * @see #UNKNOWN
     * <p>
     * 逻辑是：通过GPS定位获得的点计算平均速度v，如果v<=0.8m/s，则断定为静止；如果v>=4m/s，且处于驾驶模式，且平均行驶距离>=10.0m,，则断定为开车
     *                                                                                      且处于跑步模式，则判断为跑步；否则，结合步行检测的结果来判断。
     */
    public int getMoveModeResult() {
        if (locationIndex < SIZE) { // 取的点太少，无法判断
            return UNKNOWN;
        } else {
            float averageSpeed = getAverageSpeed();
            if (averageSpeed <= 0.8) {
                return STILL;
            } else if (averageSpeed >= 10) {
                if (isDrive) {
                    return DRIVE;
                } else if (isStep) {
                    return RUN;
                } else {
                    return UNKNOWN;
                }
            } else {
                if (useWhichSensor == -1) {
                    return UNKNOWN;
                } else {
                    int stepResult = getStepDetectResult();
                    if (stepResult == StepDetectHelper.IS_STEP) {
                        return STEP;
                    } else if (stepResult == StepDetectHelper.NOT_STEP) {
                        return DRIVE;
                    } else {
                        return UNKNOWN;
                    }
                }
            }
        }
    }

    /**
     * 计算locations数组中所有有效location的平均速度（单位：m/s）
     *
     * @return 平均速度
     */
    public float getAverageSpeed() {
        float speed = 0.0f;

        if (locationIndex > 0 && locationIndex <= SIZE) {
            for (int i = 0; i < locationIndex; i++) {
                speed += locations[i].getSpeed();
            }

            speed /= locationIndex;
        } else if (locationIndex > SIZE) {
            for (int i = 0; i < SIZE; i++) {
                speed += locations[i].getSpeed();
            }
            speed /= SIZE;

        }

        return speed;
    }
    /**
     * 计算locations数组中所有有效location距离目标的平均距离（单位：m）
     * @return 平均距离
     */
    public float getAverageDistance() {
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
     * 获取步行检测的结果
     *
     * @return 步行检测的结果
     * @see StepDetectHelper#IS_STEP
     * @see StepDetectHelper#NOT_STEP
     * @see StepDetectHelper#UNKNOWN
     */
    private int getStepDetectResult() {
        if (useWhichSensor == Sensor.TYPE_STEP_DETECTOR) {
            return stepDetectHelper.getStepDetectorSensorDetectResult();
        } else if (useWhichSensor == Sensor.TYPE_STEP_COUNTER) {
            return stepDetectHelper.getStepCounterSensorDetectResult();
        } else if (useWhichSensor == Sensor.TYPE_ACCELEROMETER) {
            return stepDetectHelper.getAccelerometerSensorDetectResult();
        } else {
            throw new IllegalStateException("useWhichSensor:" + useWhichSensor);
        }
    }

    private void checkSensor() {
        if (stepDetectHelper.isStepCounterSensorAvailable()) {
            useWhichSensor = Sensor.TYPE_STEP_COUNTER;
        } else if (stepDetectHelper.isStepDetectorSensorAvailable()) {
            useWhichSensor = Sensor.TYPE_STEP_DETECTOR;
        } else if (stepDetectHelper.isAccelerometerSensorAvailable()) {
            useWhichSensor = Sensor.TYPE_ACCELEROMETER;
        }
    }

    private void registerSensorListener() {
        if (useWhichSensor == Sensor.TYPE_STEP_COUNTER) {
            stepDetectHelper.registerStepCounterListener();
        } else if (useWhichSensor == Sensor.TYPE_STEP_DETECTOR) {
            stepDetectHelper.registerStepDetectorListener();
        } else if (useWhichSensor == Sensor.TYPE_ACCELEROMETER) {
            stepDetectHelper.registerAccelerometerListener();
        }
    }

    private void unregisterSensorListener() {
        if (useWhichSensor == Sensor.TYPE_STEP_COUNTER) {
            stepDetectHelper.unregisterStepCounterListener();
        } else if (useWhichSensor == Sensor.TYPE_STEP_DETECTOR) {
            stepDetectHelper.unregisterStepDetectorListener();
        } else if (useWhichSensor == Sensor.TYPE_ACCELEROMETER) {
            stepDetectHelper.unregisterAccelerometerListener();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        if (getAverageSpeed() >= 5) {
            if (x == 0.0 && y == 0.0 && z == 0.0) {
                // TODO: 2018/3/22
            } else {
                //计算x,y,z轴上加速度变化量
                float x_change = Math.abs(x - sensorEvent.values[0]);
                float y_change = Math.abs(y - sensorEvent.values[1]);
                float z_change = Math.abs(z - sensorEvent.values[2]);
                //如果加速度变化量超过1.0，则进入跑步判断
                if (x_change >= 1.0 || y_change >= 1.0 || z_change >= 1.0) {
                    //todo 如果DetectActivity中设置了驾车转其他状态的判断，则这个判断不是必须
//                    if (isDrive) {//上个模式是驾驶的话，进行驾车偏差计算，过滤掉驾车计时的噪音
//                        offset_drive++; //驾车偏差数(减小偶然抖动对计时的影响，如果不设置则会导致在进入驾驶判断的3s内任意一次大的抖动都会导致计时重置而进入跑步判断)，小于10次则驾车计时不清零,大于10次则开始计时0.5秒进入跑步模式
//                        if (offset_drive >= 10) {
//
//                            offset_drive = 0;
//                            firstComeInDrive = 0;
//                            if (firstComeInStep == 0) {
//                                firstComeInStep = System.currentTimeMillis();
//                            }
//                            if (System.currentTimeMillis() - firstComeInStep > 500L) {
//                                isStep = true;
//                                isDrive = false;
//                            }
//                        }
//                    } else {

                        firstComeInDrive = 0;
                        offset_step=0;
                        //开始计时
                        if (firstComeInStep == 0) {
                            firstComeInStep = System.currentTimeMillis();
                        }
                        //如果状态维持0.5s以上，则判断为跑步
                        if (System.currentTimeMillis() - firstComeInStep > 500L) {
                            isStep = true;
                            isDrive = false;
                        }
                  //  }
                } else {//否则进入驾车模式判断

                    if (isStep) {//上个模式是跑步的话，则进行步行偏差计算，过滤掉步行计时噪音
                        offset_step++;//步行偏差数，小于10次则步行计时不清零，大于十次则开始计时3秒进入驾车模式
                        if (offset_step >= 10) {
                            offset_step=0;
                            System.out.println(x_change + ":" + y_change + ":" + z_change + "isdrive");
                            firstComeInStep = 0;
                            offset_step = 0;
                            if (firstComeInDrive == 0) {
                                firstComeInDrive = System.currentTimeMillis();
                            }
                            if (System.currentTimeMillis() - firstComeInDrive > 3000L) {
                                isDrive = true;
                                isStep = false;
                            }
                        }
                    } else {
                        firstComeInStep = 0;
                        offset_drive = 0;
                        //开始计时
                        if (firstComeInDrive == 0) {
                            firstComeInDrive = System.currentTimeMillis();
                        }
                        //如果状态维持3s以上，则判断为驾车
                        if (System.currentTimeMillis() - firstComeInDrive > 3000L) {
                            isDrive = true;
                            isStep = false;
                        }
                    }
                }
            }
            x = sensorEvent.values[0];
            y = sensorEvent.values[1];
            z = sensorEvent.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
