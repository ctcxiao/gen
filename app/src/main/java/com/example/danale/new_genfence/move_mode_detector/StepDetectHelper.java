package com.example.danale.new_genfence.move_mode_detector;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.Toast;

import java.net.URLEncoder;

/**
 * Date:2017/10/13 <p>
 * Author:chenzehao@danale.com <p>
 * Description:通过手机传感器检测是否在步行
 */

public class StepDetectHelper implements SensorEventListener, StepListener {
    private static final String TAG = "StepDetectHelper";
    public static final int IS_STEP = 1; // 在步行（步行特征明显）
    public static final int NOT_STEP = 0; // 非步行（无步行特征）
    public static final int UNKNOWN = -1; // 未知（采样不足，无法判断）

    private SensorManager sensorManager;
    private static final int SIZE = 5;
    private long[] stepDetectorTimeArray = new long[SIZE];
    private int stepDetectorIndex = 0;

    private StepInfo[] stepCounterInfoArray = new StepInfo[SIZE];
    private int stepCounterIndex = -1;

    private AbstractStepDetector stepDetector;
    private long[] accelerometerTimeArray = new long[SIZE];
    private int accelerometerIndex = 0;

    private static final long TEN_SECONDS_IN_MS = 10 * 1000L;

    private long firstGetIndex0TimeMs = 0L; // 获取detect结果时首次发现xxxIndex为0的时刻


    private Context mContext;


    public StepDetectHelper(Context context) {
        sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        mContext = context;
    }

    public boolean isStepDetectorSensorAvailable() {
//        return sensorManager.getSensorList(Sensor.TYPE_STEP_DETECTOR).size() > 0;
        return false;
    }

    public boolean isStepCounterSensorAvailable() {
//        return sensorManager.getSensorList(Sensor.TYPE_STEP_COUNTER).size() > 0;
        return false;
    }

    public boolean isAccelerometerSensorAvailable() {
        return sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).size() > 0;
    }

    public Sensor getStepDetectorSensor() {
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
    }

    public Sensor getStepCounterSensor() {
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    public Sensor getAccelerometerSensor() {
        return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void registerStepDetectorListener() {
        sensorManager.registerListener(this, getStepDetectorSensor(), SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void unregisterStepDetectorListener() {
        sensorManager.unregisterListener(this, getStepDetectorSensor());
        stepDetectorIndex = 0;
    }

    public void registerStepCounterListener() {
        sensorManager.registerListener(this, getStepCounterSensor(), SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void unregisterStepCounterListener() {
        sensorManager.unregisterListener(this, getStepCounterSensor());
        stepCounterIndex = -1;
    }

    public void registerAccelerometerListener() {
        sensorManager.registerListener(this, getAccelerometerSensor(), SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void unregisterAccelerometerListener() {
        sensorManager.unregisterListener(this, getAccelerometerSensor());
        accelerometerIndex = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long triggerTime = System.currentTimeMillis();

        //Toast.makeText(mContext, "sensor", Toast.LENGTH_SHORT).show();
        switch (event.sensor.getType()) {
            case Sensor.TYPE_STEP_DETECTOR:
//                Log.e(TAG, "step detector:" + triggerTime + "-----" + event.values[0]);
                stepDetectorTimeArray[stepDetectorIndex++ % SIZE] = triggerTime;
                break;
            case Sensor.TYPE_STEP_COUNTER:
//                Log.e(TAG, "step counter:" + triggerTime + "-----" + event.values[0]);
                if (stepCounterIndex == -1) {
                    stepCounterIndex++;
                    return;
                }
                stepCounterInfoArray[stepCounterIndex++ % SIZE] = new StepInfo(triggerTime, event.values[0]);
                break;
            case Sensor.TYPE_ACCELEROMETER:
                if (stepDetector == null) {
                    stepDetector = new StepDetector();
                    stepDetector.registerListener(this);
                }
                stepDetector.updateAcceleration(event.timestamp, event.values[0],event.values[1],event.values[2]);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /**
     * 获取基于StepDetectorSensor的步行检测结果
     * @return 步行/非步行/未知
     * @see #IS_STEP
     * @see #NOT_STEP
     * @see #UNKNOWN
     */
    public int getStepDetectorSensorDetectResult() {
        if (stepDetectorIndex == 0) {
            return NOT_STEP;
        } else if (stepDetectorIndex < SIZE) {
            return UNKNOWN;
        } else {
            long queryTime = System.currentTimeMillis();
            long newest = stepDetectorTimeArray[(stepDetectorIndex - 1) % SIZE];
            if (queryTime - newest <= TEN_SECONDS_IN_MS) { // 数组里的计步信息在时效范围内
                long oldest = stepDetectorTimeArray[stepDetectorIndex % SIZE];
                if (newest - oldest < SIZE * 1000L) {
                    return IS_STEP;
                } else {
                        return NOT_STEP;
                }
            } else {
                    stepDetectorIndex = 0;
                    return NOT_STEP;
            }
        }
    }

    /**
     * 获取基于StepCounterSensor的步行检测结果
     * @return 步行/非步行/未知
     * @see #IS_STEP
     * @see #NOT_STEP
     * @see #UNKNOWN
     */
    public int getStepCounterSensorDetectResult() {
        if (stepCounterIndex <= 0) {
            return NOT_STEP;
        } else if (stepCounterIndex < SIZE) {
            return UNKNOWN;
        } else {
            long queryTime = System.currentTimeMillis();
            StepInfo newest = stepCounterInfoArray[(stepCounterIndex - 1) % SIZE];
            if (queryTime - newest.time <= TEN_SECONDS_IN_MS) { // 数组里的计步信息在时效范围内
                StepInfo oldest = stepCounterInfoArray[stepCounterIndex % SIZE];
                if ((newest.steps - oldest.steps) / ((newest.time - oldest.time) / 1000f) >= 1) {
                    return IS_STEP;
                } else {
                    return NOT_STEP;
                }
            } else {
                stepCounterIndex = 0;
                return NOT_STEP;
            }
        }
    }

    /**
     * 获取基于AccelerometerSensor的步行检测结果
     * @return 步行/非步行/未知
     * @see #IS_STEP
     * @see #NOT_STEP
     * @see #UNKNOWN
     */
    public int getAccelerometerSensorDetectResult() {
        if (accelerometerIndex <= 0) {
            return NOT_STEP;
        } else if (accelerometerIndex < SIZE) {
            return UNKNOWN;
        } else {
            long queryTime = System.currentTimeMillis();
            long newest = accelerometerTimeArray[(accelerometerIndex - 1) % SIZE];
            if (queryTime - newest <= TEN_SECONDS_IN_MS) { // 数组里的计步信息在时效范围内
                long oldest = accelerometerTimeArray[accelerometerIndex % SIZE];
                if (newest - oldest < SIZE * 1000L) {
                    return IS_STEP;
                } else {
                    return NOT_STEP;
                }
            } else {
                accelerometerIndex = 0;
                return NOT_STEP;
            }
        }
    }

    @Override
    public void step(int id, long timeNs) {
//        Log.e(TAG, "accelerometer 检测到one step");
        accelerometerTimeArray[accelerometerIndex++ % SIZE] = System.currentTimeMillis();
    }

    private static class StepInfo {
        long time; // 产生一步对应的时间(毫秒)
        float steps; // 对应时间累积了多少步

        StepInfo(long t, float s) {
            time = t;
            steps = s;
        }
    }
}
