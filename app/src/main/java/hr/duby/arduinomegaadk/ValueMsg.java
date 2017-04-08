package hr.duby.arduinomegaadk;

/**
 * Created by Duby on 8.4.2017..
 */

public class ValueMsg {
    private char flag;
    private int reading;

    public ValueMsg(char flag, int reading) {
        this.flag = flag;
        this.reading = reading;
    }

    public int getReading() {
        return reading;
    }

    public char getFlag() {
        return flag;
    }
}