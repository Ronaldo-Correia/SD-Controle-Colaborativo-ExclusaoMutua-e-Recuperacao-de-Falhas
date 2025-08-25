package br.ifba.saj.distribuido.model;

public class LamportClock {
    private int time = 0;

    public synchronized int increment() {
        return ++time;
    }

    public synchronized int update(int remote) {
        time = Math.max(time, remote) + 1;
        return time;
    }

    public synchronized int getTime() {
        return time;
    }
}
