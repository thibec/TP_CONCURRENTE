package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterState;

public class Printer {
    private final String id;
    private PrinterState state;
    private int usageCount;
    private Integer assignedOrderId;

    public Printer(String id) {
        this.id = id;
        this.state = PrinterState.AVAILABLE;
        this.usageCount = 0;
        this.assignedOrderId = null;
    }

    public String getId() {
        return id;
    }

    public synchronized PrinterState getState() {
        return state;
    }

    public synchronized int getUsageCount() {
        return usageCount;
    }

    public synchronized Integer getAssignedOrderId() {
        return assignedOrderId;
    }

    public synchronized boolean tryReserve(int orderId) {
        if (this.state == PrinterState.AVAILABLE) {
            this.state = PrinterState.RESERVED;
            this.usageCount++;
            this.assignedOrderId = orderId;
            return true;
        }
        return false;
    }

    public synchronized void release() {
        if (this.state == PrinterState.RESERVED) {
            this.state = PrinterState.AVAILABLE;
            this.assignedOrderId = null;
        }
    }

    public synchronized void markOutOfService() {
        this.state = PrinterState.OUT_OF_SERVICE;
        this.assignedOrderId = null;
    }

    public synchronized PrinterSnapshot toSnapshot() {
        return new PrinterSnapshot(id, state, usageCount, assignedOrderId);
    }
}