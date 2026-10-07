package ar.edu.unc.fcefyn.pcp.tp1.solution;

import java.util.LinkedList;
import java.util.Queue;

public class CustomBoundedQueue<T> {
    private final Queue<T> queue = new LinkedList<>();
    private final int capacity;

    public CustomBoundedQueue(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void put(T item) throws InterruptedException {
        while (queue.size() == capacity) {
            wait();
        }
        queue.add(item);
        notifyAll();
    }

    public synchronized T take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        T item = queue.poll();
        notifyAll();
        return item;
    }
}