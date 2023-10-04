package main.common;

import java.util.concurrent.atomic.AtomicInteger;

public class LamportClock {
    private AtomicInteger time;

    public LamportClock() {
        this.time = new AtomicInteger(0);
    }

    // Tick the clock due to an internal event
    public void tick() {
        time.incrementAndGet();
    }

    // Simulates sending a message by returning the current time
    public int send() {
        return time.incrementAndGet();
    }

    // Simulates receiving a message
    public void receive(int receivedTimestamp) {
        int current;
        do {
            current = time.get();
            if (receivedTimestamp < current) {
                break;
            }
        } while (!time.compareAndSet(current, receivedTimestamp + 1));
    }

    public int getTime() {
        return time.get();
    }

    public void setClock(int newValue) {
        time.set(newValue);
    }

    @Override
    public String toString() {
        return "LamportClock [time=" + time + "]";
    }
}
