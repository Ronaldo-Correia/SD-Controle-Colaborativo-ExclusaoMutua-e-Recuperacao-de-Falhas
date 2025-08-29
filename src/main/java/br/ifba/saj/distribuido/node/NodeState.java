package br.ifba.saj.distribuido.node;

import java.io.*;
import java.nio.file.*;

public class NodeState {
    public final int nodeId;
    private int counter = 0;
    private int lastLamportTs = 0;
    private final Path checkpointPath;

    public NodeState(int nodeId) {
        this.nodeId = nodeId;
        this.checkpointPath = Paths.get("node-" + nodeId + "-checkpoint.json");
        loadCheckpoint();
    }

    public synchronized int getCounter() {
        return counter;
    }

    public synchronized int getLastLamportTs() {
        return lastLamportTs;
    }

    public synchronized void setCounterAndTs(int value, long lamportTs) {
        this.counter = value;
        this.lastLamportTs = (int) Math.max(this.lastLamportTs, lamportTs);
    }

    // PRE-IMAGE
    public synchronized StateSnapshot createSnapshot() {
        return new StateSnapshot(counter, lastLamportTs);
    }

    public synchronized void restoreSnapshot(StateSnapshot snap) {
        this.counter = snap.counter;
        this.lastLamportTs = snap.lamportTs;
    }

    // checkpoint em arquivo
    public synchronized void saveCheckpoint() {
        try (BufferedWriter w = Files.newBufferedWriter(checkpointPath)) {
            String json = String.format("{\"counter\":%d,\"lamport\":%d}", counter, lastLamportTs);
            w.write(json);
            w.flush();
        } catch (IOException e) {
            System.err.println("[NODE " + nodeId + "] Erro ao salvar checkpoint: " + e.getMessage());
        }
    }

    public synchronized void loadCheckpoint() {
        if (!Files.exists(checkpointPath)) {
            System.out.println("[NODE " + nodeId + "] Nenhum checkpoint encontrado.");
            return;
        }
        try {
            String content = new String(Files.readAllBytes(checkpointPath));
            content = content.replaceAll("[{}\\s\"]", "");
            for (String part : content.split(",")) {
                String[] kv = part.split(":");
                if (kv[0].equals("counter"))
                    counter = Integer.parseInt(kv[1]);
                if (kv[0].equals("lamport"))
                    lastLamportTs = Integer.parseInt(kv[1]);
            }
            System.out.println(
                    "[NODE " + nodeId + "] Checkpoint carregado: counter=" + counter + " lamport=" + lastLamportTs);
        } catch (IOException e) {
            System.err.println("[NODE " + nodeId + "] Falha ao ler checkpoint: " + e.getMessage());
        }
    }

    public static class StateSnapshot {
        public final int counter;
        public final int lamportTs;

        public StateSnapshot(int counter, int lamportTs) {
            this.counter = counter;
            this.lamportTs = lamportTs;
        }
    }
}
