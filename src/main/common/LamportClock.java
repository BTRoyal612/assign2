package main.common;

import java.util.concurrent.atomic.AtomicInteger;

public class LamportClock {
    private AtomicInteger time;

    /**
     * Constructs a new LamportClock initialized to zero.
     */
    public LamportClock() {
        this.time = new AtomicInteger(0);
    }

    /**
     * Ticks the clock forward by one unit due to an internal event.
     */
    public void tick() {
        time.incrementAndGet();
    }

    /**
     * Simulates sending a message by ticking the clock and returning the current time.
     * @return The current value of the clock after ticking.
     */
    public int send() {
        return time.incrementAndGet();
    }

    /**
     * Adjusts the clock based on the timestamp of a received message.
     * The clock is set to the maximum of its current value and the received timestamp,
     * and then it's incremented by one.
     * @param receivedTimestamp The timestamp of the received message.
     */
    public void receive(int receivedTimestamp) {
        int current;
        do {
            current = time.get();
            if (receivedTimestamp < current) {
                break;
            }
        } while (!time.compareAndSet(current, receivedTimestamp + 1));
    }

    /**
     * Retrieves the current value of the clock.
     * @return The current time of the clock.
     */
    public int getTime() {
        return time.get();
    }

    /**
     * Sets the clock to a specific value.
     * Caution: Use this method judiciously as it can disrupt the causal ordering.
     * @param newValue The new value to set the clock to.
     */
    public void setClock(int newValue) {
        time.set(newValue);
    }

    /**
     * Provides a string representation of the LamportClock.
     * @return A string representation of the clock's current time.
     */
    @Override
    public String toString() {
        return "LamportClock [time=" + time + "]";
    }
}
