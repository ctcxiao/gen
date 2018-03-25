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
    private static final int SIZE = 3;
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

    public MoveModeDetector(Context context) {
        stepDetectHelper = new StepDetectHelper(context);
        mContext = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
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
       // if (location.getAccuracy() > 0 && location.getAccuracy() <= GeofenceUtil.GPS_ACCURACY_IN_METERS) {
                locations[locationIndex++ % SIZE] = location;
                return true;
      //  }
       // return false;
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
            Toast.makeText(mContext, "1", Toast.LENGTH_SHORT).show();
            return UNKNOWN;
        } else {
            float averageSpeed = getAverageSpeed();
            Toast.makeText(mContext, averageSpeed+":", Toast.LENGTH_SHORT).show();
            if (averageSpeed <= 0.8) {
                return STILL;
            } else if (averageSpeed >= 4) {
                if (isDrive && getAverageDistance() >= 10.0f) {
                    return DRIVE;
                } else if (isStep) {
                    return RUN;
                } else {
                    Toast.makeText(mContext, "2", Toast.LENGTH_SHORT).show();
                    return UNKNOWN;
                }
            } else {
                if (useWhichSensor == -1) {
                    Toast.makeText(mContext, "3", Toast.LENGTH_SHORT).show();
                    return UNKNOWN;
                } else {
                    int stepResult = getStepDetectResult();
                    if (stepResult == StepDetectHelper.IS_STEP) {
                        return STEP;
                    } else if (stepResult == StepDetectHelper.NOT_STEP && getAverageDistance() >= 10.0f) {
                        return DRIVE;
                    } else {
                        Toast.makeText(mContext, averageSpeed+":4", Toast.LENGTH_SHORT).show();
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
    private float getAverageSpeed() {
        float speed = 0.0f;
       // float distance = getAverageDistance();

        if (locationIndex > 0 && locationIndex <= SIZE) {
            for (int i = 0; i < locationIndex; i++) {
                speed += locations[i].getSpeed();
            }
//            long time = locations[locationIndex-1].getTime() - locations[0].getTime();
//            //Toast.makeText(mContext, locationIndex+":"+locations[locationIndex-1].getTime()+":"+locations[0].getTime()+":"+distance, Toast.LENGTH_SHORT).show();
//            speed = distance/time*1000;
            speed /= locationIndex;
        } else if (locationIndex > SIZE) {
            for (int i = 0; i < SIZE; i++) {
                speed += locations[i].getSpeed();
            }
            speed /= SIZE;
            DetectActivity.writeToFile(locationIndex+":"+locations[SIZE-2].getTime()+":"+locations[0].getTime()+":"+speed);
//            long time = locations[SIZE-2].getTime() - locations[0].getTime();
           // Toast.makeText(mContext, locationIndex+":"+locations[SIZE-2].getTime()+":"+locations[0].getTime()+":"+speed, Toast.LENGTH_SHORT).show();
//            speed = distance/time*1000;
        }

        return speed;
    }
    /**
     * 计算locations数组中所有有效location距离目标的平均距离（单位：m）
     * @return 平均距离
     */
    private float getAverageDistance() {
        float distance = 0.0f;
        if (locationIndex > 0 && locationIndex < SIZE) {
            distance+= AMapUtils.calculateLineDistance(new LatLng(locations[0].getLatitude(), locations[0].getLongitude()),
                    new LatLng(locations[locationIndex-1].getLatitude(), locations[locationIndex-1].getLongitude()));
        } else if (locationIndex >= SIZE) {
            distance+= AMapUtils.calculateLineDistance(new LatLng(locations[0].getLatitude(), locations[0].getLongitude()),
                    new LatLng(locations[SIZE-2].getLatitude(), locations[SIZE-2].getLongitude()));
        }
       // Toast.makeText(mContext,"averDistance:"+distance, Toast.LENGTH_SHORT).show();
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

                float x_change = Math.abs(x - sensorEvent.values[0]);
                float y_change = Math.abs(y - sensorEvent.values[1]);
                float z_change = Math.abs(z - sensorEvent.values[2]);

                if (x_change >= 1.0 || y_change >= 1.0 || z_change >= 1.0) {

                    if (isDrive) {//上个模式是驾驶的话，进行驾车偏差计算，过滤掉驾车计时的噪音
                        offset_drive++; //驾车偏差数，小于10次则驾车计时不清零,大于10次则开始计时0.5秒进入跑步模式
                        if (offset_drive >= 10) {
                           // Toast.makeText(mContext, x_change + ":" + y_change + ":" + z_change + "isstep", Toast.LENGTH_SHORT).show();
                            offset_drive = 0;
                            firstComeInDrive = 0;
                            if (firstComeInStep == 0) {
                                firstComeInStep = System.currentTimeMillis();
                            }
                            if (System.currentTimeMillis() - firstComeInStep > 500L) {
                                isStep = true;
                                isDrive = false;
                            }
                        }
                    } else {
                      //  Toast.makeText(mContext, x_change + ":" + y_change + ":" + z_change + "isstep1", Toast.LENGTH_SHORT).show();
                        firstComeInDrive = 0;
                        offset_drive=0;
                        if (firstComeInStep == 0) {
                            firstComeInStep = System.currentTimeMillis();
                        }
                        if (System.currentTimeMillis() - firstComeInStep > 500L) {
                            isStep = true;
                            isDrive = false;
                        }
                    }
                } else {

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
                        offset_step = 0;
                        if (firstComeInDrive == 0) {
                            firstComeInDrive = System.currentTimeMillis();
                        }
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
