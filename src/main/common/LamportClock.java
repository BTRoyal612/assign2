package main.common;

public class LamportClock {
    private int time;

    public LamportClock() {
        this.time = 0;
    }

    // Tick the clock due to an internal event
    public void tick() {
        time++;
    }

    // Simulates sending a message by returning the current time
    public int send() {
        tick();
        return time;
    }

    // Simulates receiving a message
    public void receive(int receivedTimestamp) {
        time = Math.max(time, receivedTimestamp) + 1;
    }

    public int getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "LamportClock [time=" + time + "]";
    }
}
