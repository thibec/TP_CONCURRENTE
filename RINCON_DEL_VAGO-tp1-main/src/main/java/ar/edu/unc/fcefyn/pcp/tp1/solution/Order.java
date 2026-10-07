package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderSnapshot;
import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

public class Order {
    private final int id;
    private OrderState state;
    private Printer assignedPrinter;
    private String historicalPrinterId;

    private int assignmentCount = 0;
    private int validationCount = 0;
    private int printingCount = 0;
    private int qualityControlCount = 0;

    public Order(int id) {
        this.id = id;
        this.state = OrderState.CREATED;
        this.historicalPrinterId = "";
    }

    public int getId() {
        return id;
    }

    public synchronized OrderState getState() {
        return state;
    }

    public synchronized void setState(OrderState state) {
        this.state = state;
    }

    public synchronized Printer getAssignedPrinter() {
        return assignedPrinter;
    }

    public synchronized void setAssignedPrinter(Printer printer) {
        this.assignedPrinter = printer;
        if (printer != null) {
            this.historicalPrinterId = printer.getId();
        }
    }

    public synchronized String getHistoricalPrinterId() {
        return historicalPrinterId;
    }

    public synchronized void incrementAssignmentCount() { assignmentCount++; }
    public synchronized void incrementValidationCount() { validationCount++; }
    public synchronized void incrementPrintingCount() { printingCount++; }
    public synchronized void incrementQualityControlCount() { qualityControlCount++; }

    public synchronized int getAssignmentCount() { return assignmentCount; }
    public synchronized int getValidationCount() { return validationCount; }
    public synchronized int getPrintingCount() { return printingCount; }
    public synchronized int getQualityControlCount() { return qualityControlCount; }

    public synchronized OrderSnapshot toSnapshot() {
        String currentPrinterId = (assignedPrinter != null) ? assignedPrinter.getId() : null;
        return new OrderSnapshot(
                id,
                state,
                currentPrinterId,
                assignmentCount,
                validationCount,
                printingCount,
                qualityControlCount
        );
    }
}