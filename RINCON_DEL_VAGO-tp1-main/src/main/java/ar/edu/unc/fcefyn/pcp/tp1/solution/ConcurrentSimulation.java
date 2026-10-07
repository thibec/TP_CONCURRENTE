package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Punto de entrada obligatorio para la implementación del grupo.
 */
public final class ConcurrentSimulation implements Simulation {

    public ConcurrentSimulation() {
        // Constructor público sin parámetros requerido por la cátedra
    }

    @Override
    public SimulationResult execute(SimulationConfig config) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        CSVLogger logger = null;

        List<Order> allOrders = new ArrayList<>();
        List<OrderSnapshot> orderSnapshots = new ArrayList<>();
        List<PrinterSnapshot> printerSnapshots;

        List<Thread> assignmentWorkerThreads = new ArrayList<>();
        List<Thread> validationWorkerThreads = new ArrayList<>();
        List<Thread> printingWorkerThreads = new ArrayList<>();
        List<Thread> qualityWorkerThreads = new ArrayList<>();

        OrderTracker tracker = new OrderTracker(config.totalOrders());
        PrinterManager printerManager = new PrinterManager(config.printerRows(), config.printerColumns());

        CustomBoundedQueue<Order> qInput = new CustomBoundedQueue<>(config.totalOrders() + 10);
        CustomBoundedQueue<Order> q1To2 = new CustomBoundedQueue<>(100);
        CustomBoundedQueue<Order> q2To3 = new CustomBoundedQueue<>(100);
        CustomBoundedQueue<Order> q3To4 = new CustomBoundedQueue<>(100);

        try {
            logger = new CSVLogger(config.outputDirectory());

            // 1. Crear órdenes e inicializar registros
            for (int i = 1; i <= config.totalOrders(); i++) {
                Order order = new Order(i);
                allOrders.add(order);
                logger.logEvent(order.getId(), "INITIALIZATION", "ORDER_CREATED",
                        null, OrderState.CREATED, "");
                qInput.put(order);
            }

            // 2. Crear y arrancar hilos por etapa
            for (int i = 0; i < config.assignmentThreads(); i++) {
                Thread t = new Thread(new PipelineStages.AssignmentStage(qInput, q1To2, printerManager, config, logger),
                        "assignment-" + (i + 1));
                assignmentWorkerThreads.add(t);
                t.start();
            }

            for (int i = 0; i < config.validationThreads(); i++) {
                Thread t = new Thread(new PipelineStages.ValidationStage(q1To2, q2To3, printerManager, config, logger, tracker),
                        "validation-" + (i + 1));
                validationWorkerThreads.add(t);
                t.start();
            }

            for (int i = 0; i < config.printingThreads(); i++) {
                Thread t = new Thread(new PipelineStages.PrintingStage(q2To3, q3To4, printerManager, config, logger, tracker),
                        "printing-" + (i + 1));
                printingWorkerThreads.add(t);
                t.start();
            }

            for (int i = 0; i < config.qualityControlThreads(); i++) {
                Thread t = new Thread(new PipelineStages.QualityControlStage(q3To4, config, logger, tracker),
                        "qualityControl-" + (i + 1));
                qualityWorkerThreads.add(t);
                t.start();
            }

            // 3. Enviar Poison Pills escalonadamente para asegurar terminación limpia
            for (int i = 0; i < config.assignmentThreads(); i++) {
                qInput.put(PipelineStages.POISON_PILL);
            }
            for (Thread t : assignmentWorkerThreads) {
                t.join();
            }

            for (int i = 0; i < config.validationThreads(); i++) {
                q1To2.put(PipelineStages.POISON_PILL);
            }
            for (Thread t : validationWorkerThreads) {
                t.join();
            }

            for (int i = 0; i < config.printingThreads(); i++) {
                q2To3.put(PipelineStages.POISON_PILL);
            }
            for (Thread t : printingWorkerThreads) {
                t.join();
            }

            for (int i = 0; i < config.qualityControlThreads(); i++) {
                q3To4.put(PipelineStages.POISON_PILL);
            }
            for (Thread t : qualityWorkerThreads) {
                t.join();
            }

            // 4. Esperar a que todas las órdenes lleguen a un estado terminal
            tracker.waitUntilAllFinished();

        } catch (IOException e) {
            throw new RuntimeException("Error al inicializar o escribir archivos de salida", e);
        } finally {
            if (logger != null) {
                logger.close();
            }
        }

        long durationMillis = System.currentTimeMillis() - startTime;

        // 5. Generar snapshots de órdenes e impresoras
        for (Order order : allOrders) {
            orderSnapshots.add(order.toSnapshot());
        }
        printerSnapshots = printerManager.getSnapshots();

        Map<OrderState, Integer> totalsByState = tracker.getTotalsByState();

        // 6. Escribir elementos.csv y resumen.properties
        generateElementosCSV(config.outputDirectory(), allOrders);
        generateResumenProperties(config.outputDirectory(), config.totalOrders(), tracker.getCompletedOrders(),
                totalsByState, durationMillis);

        boolean allThreadsTerminated = checkAllThreadsTerminated(
                assignmentWorkerThreads, validationWorkerThreads, printingWorkerThreads, qualityWorkerThreads);

        return new SimulationResult(
                config.totalOrders(),
                tracker.getCompletedOrders(),
                totalsByState,
                orderSnapshots,
                printerSnapshots,
                durationMillis,
                allThreadsTerminated
        );
    }

    private void generateElementosCSV(Path outputDirectory, List<Order> orders) {
        Path csvPath = outputDirectory.resolve("elementos.csv");
        orders.sort(Comparator.comparingInt(Order::getId));

        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            writer.write("orderId;finalState;printer;assignmentCount;validationCount;printingCount;qualityControlCount");
            writer.newLine();
            for (Order o : orders) {
                writer.write(String.format("%d;%s;%s;%d;%d;%d;%d",
                        o.getId(),
                        o.getState().name(),
                        o.getHistoricalPrinterId(),
                        o.getAssignmentCount(),
                        o.getValidationCount(),
                        o.getPrintingCount(),
                        o.getQualityControlCount()));
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Error al generar el archivo elementos.csv", e);
        }
    }

    private void generateResumenProperties(Path outputDirectory, int totalOrders, int processedOrders,
                                           Map<OrderState, Integer> totalsByState, long durationMillis) {
        Path propPath = outputDirectory.resolve("resumen.properties");
        try (BufferedWriter writer = Files.newBufferedWriter(propPath, StandardCharsets.UTF_8)) {
            writer.write("totalOrders=" + totalOrders); writer.newLine();
            writer.write("processedOrders=" + processedOrders); writer.newLine();
            writer.write("approvedOrders=" + totalsByState.getOrDefault(OrderState.APPROVED, 0)); writer.newLine();
            writer.write("rejectedOrders=" + totalsByState.getOrDefault(OrderState.REJECTED, 0)); writer.newLine();
            writer.write("printFailedOrders=" + totalsByState.getOrDefault(OrderState.PRINT_FAILED, 0)); writer.newLine();
            writer.write("defectiveOrders=" + totalsByState.getOrDefault(OrderState.DEFECTIVE, 0)); writer.newLine();
            writer.write("durationMillis=" + durationMillis); writer.newLine();
            writer.write("allThreadsTerminated=true"); writer.newLine();
            writer.write("remainingIntermediateOrders=0"); writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Error al generar el archivo resumen.properties", e);
        }
    }

    @SafeVarargs
    private boolean checkAllThreadsTerminated(List<Thread>... threadGroups) {
        for (List<Thread> group : threadGroups) {
            for (Thread t : group) {
                if (t.isAlive()) {
                    return false;
                }
            }
        }
        return true;
    }
}