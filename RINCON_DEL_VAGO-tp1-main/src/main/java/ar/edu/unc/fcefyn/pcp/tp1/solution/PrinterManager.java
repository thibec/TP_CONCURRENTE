package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.PrinterSnapshot;

import java.util.ArrayList;
import java.util.List;

public class PrinterManager {
    private final List<Printer> printers = new ArrayList<>();
    private int lastAssignedIndex = 0;

    public PrinterManager(int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                printers.add(new Printer("P-" + r + "-" + c));
            }
        }
    }

    public synchronized Printer acquirePrinter(int orderId) throws InterruptedException {
        while (true) {
            int size = printers.size();
            for (int i = 0; i < size; i++) {
                int index = (lastAssignedIndex + i) % size;
                Printer printer = printers.get(index);
                if (printer.tryReserve(orderId)) {
                    lastAssignedIndex = (index + 1) % size;
                    return printer;
                }
            }
            wait();
        }
    }

    public synchronized void releasePrinter(Printer printer) {
        if (printer != null) {
            printer.release();
            notifyAll();
        }
    }

    public synchronized void markPrinterOutOfService(Printer printer) {
        if (printer != null) {
            printer.markOutOfService();
            notifyAll();
        }
    }

    public synchronized List<PrinterSnapshot> getSnapshots() {
        List<PrinterSnapshot> snapshots = new ArrayList<>();
        for (Printer p : printers) {
            snapshots.add(p.toSnapshot());
        }
        return snapshots;
    }
}