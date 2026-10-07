package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;
import ar.edu.unc.fcefyn.pcp.tp1.api.OutcomeDecider;
import ar.edu.unc.fcefyn.pcp.tp1.api.SimulationConfig;

public class PipelineStages {

    public static final Order POISON_PILL = new Order(-1);

    /**
     * Método auxiliar para simular la demora del trabajo sin activar la advertencia de busy-waiting.
     */
    private static void simulateDelay(long delayMillis) throws InterruptedException {
        if (delayMillis > 0) {
            Thread.sleep(delayMillis);
        }
    }

    // ETAPA 1: ASIGNACIÓN
    public static class AssignmentStage implements Runnable {
        private final CustomBoundedQueue<Order> inQueue;
        private final CustomBoundedQueue<Order> outQueue;
        private final PrinterManager printerManager;
        private final SimulationConfig config;
        private final CSVLogger logger;

        public AssignmentStage(CustomBoundedQueue<Order> in, CustomBoundedQueue<Order> out,
                               PrinterManager pm, SimulationConfig config, CSVLogger logger) {
            this.inQueue = in;
            this.outQueue = out;
            this.printerManager = pm;
            this.config = config;
            this.logger = logger;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Order order = inQueue.take();
                    if (order == POISON_PILL) {
                        break;
                    }

                    simulateDelay(config.assignmentDelayMillis());

                    Printer printer = printerManager.acquirePrinter(order.getId());
                    OrderState oldState = order.getState();

                    order.setAssignedPrinter(printer);
                    order.setState(OrderState.WAITING_VALIDATION);
                    order.incrementAssignmentCount();

                    logger.logEvent(order.getId(), "ASSIGNMENT", "ORDER_STATE_CHANGED",
                            oldState, OrderState.WAITING_VALIDATION, printer.getId());

                    outQueue.put(order);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ETAPA 2: VALIDACIÓN
    public static class ValidationStage implements Runnable {
        private final CustomBoundedQueue<Order> inQueue;
        private final CustomBoundedQueue<Order> outQueue;
        private final PrinterManager printerManager;
        private final SimulationConfig config;
        private final CSVLogger logger;
        private final OrderTracker tracker;

        public ValidationStage(CustomBoundedQueue<Order> in, CustomBoundedQueue<Order> out,
                               PrinterManager pm, SimulationConfig config, CSVLogger logger, OrderTracker tracker) {
            this.inQueue = in;
            this.outQueue = out;
            this.printerManager = pm;
            this.config = config;
            this.logger = logger;
            this.tracker = tracker;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Order order = inQueue.take();
                    if (order == POISON_PILL) {
                        break;
                    }

                    simulateDelay(config.validationDelayMillis());

                    order.incrementValidationCount();
                    boolean isValid = OutcomeDecider.isModelValid(order.getId(), config);
                    OrderState oldState = order.getState();
                    String printerId = order.getHistoricalPrinterId();

                    if (isValid) {
                        order.setState(OrderState.READY_TO_PRINT);
                        logger.logEvent(order.getId(), "VALIDATION", "ORDER_STATE_CHANGED",
                                oldState, OrderState.READY_TO_PRINT, printerId);
                        outQueue.put(order);
                    } else {
                        order.setState(OrderState.REJECTED);
                        printerManager.releasePrinter(order.getAssignedPrinter());
                        order.setAssignedPrinter(null);

                        logger.logEvent(order.getId(), "VALIDATION", "ORDER_STATE_CHANGED",
                                oldState, OrderState.REJECTED, printerId);

                        tracker.markOrderFinished(OrderState.REJECTED);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ETAPA 3: IMPRESIÓN
    public static class PrintingStage implements Runnable {
        private final CustomBoundedQueue<Order> inQueue;
        private final CustomBoundedQueue<Order> outQueue;
        private final PrinterManager printerManager;
        private final SimulationConfig config;
        private final CSVLogger logger;
        private final OrderTracker tracker;

        public PrintingStage(CustomBoundedQueue<Order> in, CustomBoundedQueue<Order> out,
                             PrinterManager pm, SimulationConfig config, CSVLogger logger, OrderTracker tracker) {
            this.inQueue = in;
            this.outQueue = out;
            this.printerManager = pm;
            this.config = config;
            this.logger = logger;
            this.tracker = tracker;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Order order = inQueue.take();
                    if (order == POISON_PILL) {
                        break;
                    }

                    simulateDelay(config.printingDelayMillis());

                    order.incrementPrintingCount();
                    boolean isSuccess = OutcomeDecider.isPrintSuccessful(order.getId(), config);
                    OrderState oldState = order.getState();
                    Printer printer = order.getAssignedPrinter();
                    String printerId = order.getHistoricalPrinterId();

                    if (isSuccess) {
                        order.setState(OrderState.PRINTED);
                        printerManager.releasePrinter(printer);
                        order.setAssignedPrinter(null);

                        logger.logEvent(order.getId(), "PRINTING", "ORDER_STATE_CHANGED",
                                oldState, OrderState.PRINTED, printerId);

                        outQueue.put(order);
                    } else {
                        order.setState(OrderState.PRINT_FAILED);
                        printerManager.markPrinterOutOfService(printer);
                        order.setAssignedPrinter(null);

                        logger.logEvent(order.getId(), "PRINTING", "ORDER_STATE_CHANGED",
                                oldState, OrderState.PRINT_FAILED, printerId);

                        tracker.markOrderFinished(OrderState.PRINT_FAILED);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ETAPA 4: CONTROL DE CALIDAD
    public static class QualityControlStage implements Runnable {
        private final CustomBoundedQueue<Order> inQueue;
        private final SimulationConfig config;
        private final CSVLogger logger;
        private final OrderTracker tracker;

        public QualityControlStage(CustomBoundedQueue<Order> in, SimulationConfig config,
                                   CSVLogger logger, OrderTracker tracker) {
            this.inQueue = in;
            this.config = config;
            this.logger = logger;
            this.tracker = tracker;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Order order = inQueue.take();
                    if (order == POISON_PILL) {
                        break;
                    }

                    simulateDelay(config.qualityControlDelayMillis());

                    order.incrementQualityControlCount();
                    boolean isApproved = OutcomeDecider.isQualityApproved(order.getId(), config);
                    OrderState oldState = order.getState();
                    String printerId = order.getHistoricalPrinterId();

                    OrderState finalState = isApproved ? OrderState.APPROVED : OrderState.DEFECTIVE;
                    order.setState(finalState);

                    logger.logEvent(order.getId(), "QUALITY_CONTROL", "ORDER_STATE_CHANGED",
                            oldState, finalState, printerId);

                    tracker.markOrderFinished(finalState);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}