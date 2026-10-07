package ar.edu.unc.fcefyn.pcp.tp1.solution;

import ar.edu.unc.fcefyn.pcp.tp1.api.OrderState;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CSVLogger implements AutoCloseable {
    private final BufferedWriter writer;
    private final long startTimeMs;
    private long sequence = 0;

    public CSVLogger(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Path csvPath = outputDirectory.resolve("eventos.csv");
        this.writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8);
        this.startTimeMs = System.currentTimeMillis();

        this.writer.write("sequence;elapsedMs;thread;orderId;stage;event;fromState;toState;printer");
        this.writer.newLine();
        this.writer.flush();
    }

    public synchronized void logEvent(int orderId, String stage, String event,
                                      OrderState fromState, OrderState toState, String printerId) {
        sequence++;
        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        String threadName = Thread.currentThread().getName();
        String fromStr = (fromState != null) ? fromState.name() : "";
        String toStr = (toState != null) ? toState.name() : "";
        String printerStr = (printerId != null) ? printerId : "";

        try {
            writer.write(String.format("%d;%d;%s;%d;%s;%s;%s;%s;%s",
                    sequence, elapsedMs, threadName, orderId, stage, event, fromStr, toStr, printerStr));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Error de escritura en el archivo de eventos CSV", e);
        }
    }

    @Override
    public synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                throw new IllegalStateException("Error al cerrar el archivo de eventos CSV", e);
            }
        }
    }
}