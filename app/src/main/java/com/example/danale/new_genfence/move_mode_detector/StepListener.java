package com.example.danale.new_genfence.move_mode_detector;

public interface StepListener {

    /**
     * @param id 使用何种算法
     * Called when a step has been detected.  Given the time in nanoseconds at
     * which the step was detected.
     */
    void step(int id, long timeNs);

}