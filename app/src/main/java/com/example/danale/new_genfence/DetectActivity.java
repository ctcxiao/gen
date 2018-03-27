package com.example.danale.new_genfence;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.example.danale.new_genfence.move_mode_detector.MoveModeDetector;
import com.example.danale.new_genfence.move_trend_detector.GeofenceUtil;
import com.example.danale.new_genfence.move_trend_detector.MoveTrendDetector;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by danale on 2018/3/21.
 */
// TODO: 2018/3/28 省电优化，车载蓝牙判断，以及在家打开定位功能优化省电
public class DetectActivity extends Activity implements View.OnClickListener {
    private static final int REQUEST_CODE_ACCESS_FINE_LOCATION = 200; // 请求定位权限
    private static final int LOCATION_FOR_GARAGER = 1; // 为获得车库门经纬度而定位
    private static final int LOCATION_FOR_DETECT = 2; // 为检测出行而定位
    private static final int WHAT_RESULT = 10;
    private int locationType = 0;
    private EditText etLongitude, etLatitude;
    private Button btnGetLatlng, btnStart, btnStop, btnSetGPSAccuracy;
    private TextView tvOutput;


    private MoveModeDetector moveModeDetector;
    private MoveTrendDetector moveTrendDetector;
    public AMapLocationClient mLocationClient = null;
    private String deviceId;

    private String info;

    private static final int LARGE_CIRCLE = 1000;
    private static final int MIDDLE_CIRCLE = 200;
    private static final int SMALL_CIRCLE = 20;

    private static final int DRIVE_DETECT_CIRCLE = 100;

    private boolean openCmdHasSent = false;
    private boolean closeCmdHasSent = false;

    private int lastMoveMode;
    private long lastTime;

    private boolean come_in_large_circle = true;
    private boolean come_in_middle_circle = true;
    private boolean come_in_small_circle = true;

    private boolean coming_home = false;
    private long curr_time;


    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            if (msg.what == WHAT_RESULT) {
                int moveModeResult = 0;

                if (!coming_home) {
                    moveModeResult = moveModeDetector.getMoveModeResult();
                } else {
                    // TODO: 2018/3/27 change to DRIVE
                    moveModeResult = MoveModeDetector.STEP;
                }

                Toast.makeText(DetectActivity.this, moveModeDetector.getAverageDistance()+"", Toast.LENGTH_SHORT).show();
                //进入不同范围，使用不同的定位方式
                if (moveModeDetector.getAverageDistance() > LARGE_CIRCLE){
                    if (moveModeResult == MoveModeDetector.DRIVE ) {
                        coming_home = false;
                        if (come_in_large_circle) {
                            changeLocationMode(1000 * 60 * 5, AMapLocationClientOption.AMapLocationMode.Battery_Saving);
                            come_in_large_circle = false;
                            come_in_middle_circle = true;
                            come_in_small_circle = true;
                        }
                    }
                } else if(moveModeResult == MoveModeDetector.DRIVE && moveModeDetector.getAverageDistance() > MIDDLE_CIRCLE){
                    coming_home = false;
                    if (come_in_middle_circle) {
                        changeLocationMode(1000 * 30, AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                        come_in_middle_circle = false;
                        come_in_small_circle = true;
                        come_in_large_circle = true;
                    }
                } else if (moveModeResult == MoveModeDetector.DRIVE){
                    if (come_in_small_circle) {
                        changeLocationMode(1000, AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                        come_in_small_circle = false;
                        come_in_large_circle = true;
                        come_in_middle_circle =true;
                    }
                }
                //如果是驾驶模式且离家在100米内，则认为之后会一直在驾车
                // TODO: 2018/3/27 change to DRIVE
                if (moveModeResult == MoveModeDetector.STEP && moveModeDetector.getAverageDistance() < DRIVE_DETECT_CIRCLE){
                    //todo 此处可以进行定位停止操作等
                    coming_home = true;
                    //到家一分钟后降低定位精度和定位间隔，省电同时能防止从家门路过导致车库门打开的情况
                    if (curr_time == 0L){
                        curr_time = System.currentTimeMillis();
                    }
                    if (System.currentTimeMillis() - curr_time > 1000*60*2) {
                        curr_time=0L;
                    } else {
                        return;
                    }
                }
                curr_time = 0L;

                /**
                 * 如果上一个模式是开车的话，且当前模式不是开车，开始计时30s
                 * 如果30s内当前模式转为开车，则计时清零，不更新当前状态
                 * 如果30s内当前模式依旧不是开车，则计时清零，并更新当前状态
                 * */
                if (lastMoveMode == MoveModeDetector.DRIVE && moveModeResult != MoveModeDetector.DRIVE){
                    if (lastTime == 0L) {
                        lastTime = System.currentTimeMillis();
                    }
                    if (System.currentTimeMillis() - lastTime < 30000L){
                        return;
                    } else {
                        lastTime = 0L;
                    }
                }
                lastTime = 0L;



                StringBuilder sb = new StringBuilder(GeofenceUtil.formatTime(System.currentTimeMillis()));
                sb.append("\n检测到出行方式：");
                if (moveModeResult == MoveModeDetector.STILL) {
                    lastMoveMode = MoveModeDetector.STILL;
                    sb.append("静止");
                } else if (moveModeResult == MoveModeDetector.STEP) {
                    lastMoveMode = MoveModeDetector.STEP;
                    sb.append("步行");
                } else if (moveModeResult == MoveModeDetector.DRIVE) {
                    lastMoveMode = MoveModeDetector.DRIVE;
                    sb.append("开车");
                }else if(moveModeResult == MoveModeDetector.RUN){
                    lastMoveMode = MoveModeDetector.RUN;
                    sb.append("跑步");
                } else if (moveModeResult == MoveModeDetector.UNKNOWN) {
                    lastMoveMode = MoveModeDetector.UNKNOWN;
                    sb.append("正在检测");
                }

                sb.append("\n执行动作：");
                // TODO: 2018/3/27 change to DRIVE
                if (moveModeResult == MoveModeDetector.STEP) {
                    if (moveModeDetector.getAverageDistance() < SMALL_CIRCLE) {
                        sb.append("发送开车库门的命令！");
                        tvOutput.setText(sb.toString());
                        if (!openCmdHasSent) {
                            //new ActionPresenter(deviceId).controlGarageDoor(0);
                            openCmdHasSent = true;
                            closeCmdHasSent = false;
                            moveTrendDetector.reset();
                            coming_home = false;
                            changeLocationMode(1000 * 60 * 10, AMapLocationClientOption.AMapLocationMode.Battery_Saving);
                        }
                        return;
                    }
                }
                sb.append("不发命令");
                info = sb.toString();
                tvOutput.setText(info);
            }
        }
    };

    public static void writeToFile(String s) {
        String store_path = Environment.getExternalStorageDirectory().getPath() + "/new_genfence.txt";
        System.out.println(store_path);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(store_path, true))) {
            writer.write(s + "\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void startActivity(Context context, String deviceId) {
        Intent intent = new Intent(context, DetectActivity.class);
        intent.putExtra("deviceId", deviceId);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);
        mLocationClient = new AMapLocationClient(getApplicationContext());
        initData();
        initView();
    }

    private void initData() {
        deviceId = getIntent().getStringExtra("deviceId");

        moveModeDetector = new MoveModeDetector(this);
        moveTrendDetector = new MoveTrendDetector(this);
    }

    private void initView() {
        etLongitude = (EditText) findViewById(R.id.et_longitude);
        etLatitude = (EditText) findViewById(R.id.et_latitude);
        btnGetLatlng = (Button) findViewById(R.id.btn_get_latlng);
        btnStart = (Button) findViewById(R.id.btn_start);
        btnStop = (Button) findViewById(R.id.btn_stop);
        tvOutput = (TextView) findViewById(R.id.tv_output);
        btnSetGPSAccuracy = (Button) findViewById(R.id.btn_set_accuracy);

        btnGetLatlng.setOnClickListener(this);
        btnStart.setOnClickListener(this);
        btnStop.setOnClickListener(this);
        btnSetGPSAccuracy.setOnClickListener(this);

        String[] latlng = restoreLatlng();
        etLongitude.setText(latlng[0]);
        etLatitude.setText(latlng[1]);
    }

    @Override
    public void onClick(View v) {
        startWriteTimer();
        if (v.getId() == btnGetLatlng.getId()) {
            locationType = LOCATION_FOR_GARAGER;
            startLocation();
        } else if (v.getId() == btnStart.getId()) {
            if (TextUtils.isEmpty(etLongitude.getText().toString()) || TextUtils.isEmpty(etLatitude.getText().toString())) {
                Toast.makeText(this, "请先输入车库门的经纬度或等待定位结果", Toast.LENGTH_SHORT).show();
                return;
            }
            locationType = LOCATION_FOR_DETECT;
            startDetect();
            startLocation();
            startTimer();
            btnStart.setTextColor(Color.GRAY);
            btnStart.setEnabled(false);
            btnStop.setTextColor(Color.BLACK);
            btnStop.setEnabled(true);
        } else if (v.getId() == btnStop.getId()) {
            stopLocation();
            stopDetect();
            stopTimer();
            btnStart.setTextColor(Color.BLACK);
            btnStart.setEnabled(true);
            btnStop.setTextColor(Color.GRAY);
            btnStop.setEnabled(false);
        } else if (v.getId() == btnSetGPSAccuracy.getId()) {
            showDialog();
        }
    }

    void showDialog() {
        final EditText et = new EditText(this);
        new AlertDialog.Builder(this).setTitle("设置GPS定位精度")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String input = et.getText().toString();
                        if (isNumber(input)) {
                            GeofenceUtil.GPS_ACCURACY_IN_METERS = Float.parseFloat(input);
                            Toast.makeText(DetectActivity.this, "GPS精度设置为" + input + " m/s", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(DetectActivity.this, "请输入数字！", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    public static boolean isNumber(String str) {
        String reg = "^[0-9]+(.[0-9]+)?$";
        return str.matches(reg);
    }

    /**
     * 开始定位
     */
    void startLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_ACCESS_FINE_LOCATION);
        } else {
            startLocation0();
        }
    }

    void startLocation0() {
        AMapLocationClientOption option = new AMapLocationClientOption();

        option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        option.setInterval(1000);
        option.setSensorEnable(true);
        if (null != mLocationClient) {

            //设置场景模式后最好调用一次stop，再调用start以保证场景模式生效
            mLocationClient.stopLocation();

            mLocationClient.setLocationListener(getLocationCallback());
            mLocationClient.setLocationOption(option);
            mLocationClient.startLocation();
        }

    }

    void changeLocationMode(int interval, AMapLocationClientOption.AMapLocationMode mode){
        AMapLocationClientOption option = new AMapLocationClientOption();

        option.setLocationMode(mode);
        option.setInterval(interval);
        option.setSensorEnable(true);
        if (null != mLocationClient) {

            //设置场景模式后最好调用一次stop，再调用start以保证场景模式生效
            mLocationClient.stopLocation();
            mLocationClient.setLocationListener(getLocationCallback());
            mLocationClient.setLocationOption(option);
            mLocationClient.startLocation();
        }
    }

    /**
     * 停止定位
     */
    void stopLocation() {
        mLocationClient.stopLocation();
    }

    void startDetect() {
        moveModeDetector.startDetect();
        moveTrendDetector.setDestination(Double.parseDouble(etLongitude.getText().toString()), Double.parseDouble(etLatitude.getText().toString()));
        moveModeDetector.setDestination(Double.parseDouble(etLongitude.getText().toString()), Double.parseDouble(etLatitude.getText().toString()));
    }

    void stopDetect() {
        moveModeDetector.stopDetect();
        moveTrendDetector.reset();
    }

    private AMapLocationListener locationCallback;

    private AMapLocationListener getLocationCallback() {
        if (locationCallback == null) {
            locationCallback = new LocationCallbackImpl();
        }

        return locationCallback;
    }

    private class LocationCallbackImpl implements AMapLocationListener {

        @Override
        public void onLocationChanged(AMapLocation aMapLocation) {

            if (locationType == LOCATION_FOR_GARAGER) {
                if (aMapLocation.getAccuracy() > 0 && aMapLocation.getAccuracy() <= GeofenceUtil.GPS_ACCURACY_IN_METERS) {
                    String longitude = String.valueOf(aMapLocation.getLongitude());
                    String latitude = String.valueOf(aMapLocation.getLatitude());
                    etLongitude.setText(longitude);
                    etLatitude.setText(latitude);
                    saveLatlng(longitude, latitude);
                    stopLocation();
                }
            } else if (locationType == LOCATION_FOR_DETECT) {
                System.out.println(aMapLocation.getSpeed() + "");
                moveModeDetector.collectLocation(aMapLocation);
                moveTrendDetector.collectLocation(aMapLocation);
            }
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocation();
        stopDetect();
        stopTimer();
        mLocationClient.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_ACCESS_FINE_LOCATION) {
            if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[0]) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "已授权", Toast.LENGTH_SHORT).show();
                startLocation0();
            } else {
                Toast.makeText(this, "用户拒绝授予定位权限，此功能不可用！", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private Timer timer;
    private Timer writeTimer;

    private void startWriteTimer() {
        if (writeTimer == null) {
            writeTimer = new Timer();
            writeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    writeToFile(info);
                }
            }, 30000L, 30000L);
        }
    }

    private void startTimer() {
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Message msg = Message.obtain();
                    msg.what = WHAT_RESULT;
                    handler.sendMessage(msg);
                }
            }, 3000L, 3000L);
        }
    }

    private void stopTimer() {
        if (timer != null && writeTimer != null) {
            timer.cancel();
            timer = null;
            writeTimer.cancel();
            writeTimer = null;
        }
    }

    private void saveLatlng(String longitude, String latitude) {
        SharedPreferences prefs = getSharedPreferences("garager_latlng", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("longitude", longitude);
        editor.putString("latitude", latitude);
        editor.apply();
    }

    private String[] restoreLatlng() {
        SharedPreferences prefs = getSharedPreferences("garager_latlng", MODE_PRIVATE);
        String longitude = prefs.getString("longitude", "");
        String latitude = prefs.getString("latitude", "");
        return new String[]{longitude, latitude};
    }

}
