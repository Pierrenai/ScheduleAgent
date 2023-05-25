package jadelab2;

public class ScheduleAgent {

    private float average(float actualAverage, float myValue, int respondentNumber) {
        return ((actualAverage * respondentNumber) + myValue)/(respondentNumber+1);
    }

}
