package com.example.danale.new_genfence.move_mode_detector;

/**
 * Date:2017/10/16 <p>
 * Author:chenzehao@danale.com <p>
 * Description:
 */

public abstract class AbstractStepDetector {
    protected StepListener listener;

    public void registerListener(StepListener listener) {
        this.listener = listener;
    }


    public abstract void updateAcceleration(long timeNs, float x, float y, float z);
}
