package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

import java.util.EnumMap;
import java.util.Map;

public class OrderTracker {
    private final int totalOrders;
    private int completedOrders = 0;
    private final Map<OrderState, Integer> countsByState = new EnumMap<>(OrderState.class);

    public OrderTracker(int totalOrders) {
        this.totalOrders = totalOrders;
        for (OrderState state : OrderState.values()) {
            countsByState.put(state, 0);
        }
    }

    public synchronized void markOrderFinished(OrderState finalState) {
        completedOrders++;
        countsByState.put(finalState, countsByState.get(finalState) + 1);
        notifyAll();
    }

    public synchronized void waitUntilAllFinished() throws InterruptedException {
        while (completedOrders < totalOrders) {
            wait();
        }
    }

    public synchronized int getCompletedOrders() {
        return completedOrders;
    }

    public synchronized Map<OrderState, Integer> getTotalsByState() {
        return new EnumMap<>(countsByState);
    }
}