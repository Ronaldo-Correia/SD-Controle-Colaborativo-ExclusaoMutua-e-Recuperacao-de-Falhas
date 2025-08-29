package br.ifba.saj.distribuido.node;

import br.ifba.saj.distribuido.model.*;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.Random;
import java.util.concurrent.*;

/**
 * NodeClient adaptado para:
 * - checkpoint periódico
 * - pré-imagem (snapshot) antes do DO_OP
 * - aplicar STATE apenas se lamportTs > last
 * - simular atraso e crash via args:
 * --delay : ativa atraso (sleep) durante doCriticalOperation
 * --crash : crasha durante a próxima operação crítica
 */
public class NodeClient {
    public final int pid;
    private final LamportClock clock;
    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader in;
    private final Gson gson;

    // estado local e checkpoint
    private final NodeState state;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // flags de simulação
    private volatile boolean simulateDelay = false;
    private volatile boolean simulateCrashOnNextOp = false;

    public NodeClient(int pid, String host, int port, boolean simulateDelay, boolean simulateCrashOnNextOp)
            throws IOException {
        this.pid = pid;
        this.clock = new LamportClock();
        this.socket = new Socket(host, port);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.gson = new Gson();

        this.state = new NodeState(pid);
        this.simulateDelay = simulateDelay;
        this.simulateCrashOnNextOp = simulateCrashOnNextOp;

        startBackgroundTasks();
        join();
        listen();
    }

    private void startBackgroundTasks() {
        // checkpoint a cada 10s
        scheduler.scheduleAtFixedRate(() -> {
            state.saveCheckpoint();
            System.out.println("[NODE " + pid + "] Checkpoint salvo.");
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void join() {
        Message join = new Message();
        join.setType(MessageType.JOIN);
        join.setPid(pid);
        join.setClock(clock.increment());
        join.setPayload("{}");

        send(join);
        System.out.println("[NODE " + pid + "] JOIN enviado (clock=" + clock.getTime() + ")");
    }

    private void send(Message msg) {
        out.println(gson.toJson(msg));
    }

    private void requestCS() {
        Message req = new Message();
        req.setType(MessageType.REQUEST);
        req.setPid(pid);
        req.setClock(clock.increment());
        req.setPayload("{}");

        send(req);
        System.out.println("[NODE " + pid + "] REQUEST enviado (clock=" + clock.getTime() + ")");
    }

    // novo: operação crítica com pré-imagem, delay e crash simulado
    private void doCriticalOperation() {
        // cria pré-imagem
        NodeState.StateSnapshot pre = state.createSnapshot();

        // simulação de atraso no processamento local
        if (simulateDelay) {
            try {
                System.out
                        .println("[NODE " + pid + "] Simulação de delay ativa - dormindo 5s antes de aplicar operação");
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
        }

        // simula crash: encerra processo antes de confirmar ao coordenador
        if (simulateCrashOnNextOp) {
            System.out.println("[NODE " + pid + "] Simulando crash durante operação! (pré-imagem salva)");
            // opcional: grava um arquivo pra diagnóstico
            try {
                Files.write(Paths.get("node-" + pid + "-precrash.json"),
                        ("preCounter:" + pre.counter + ",preLamport:" + pre.lamportTs).getBytes());
            } catch (IOException ignored) {
            }
            System.exit(1); // simula queda abrupta
        }

        // aplica operação local: incrementa contador
        int novo = state.getCounter() + 1;
        long lamportTs = clock.increment();
        state.setCounterAndTs(novo, lamportTs);
        System.out.println("[NODE " + pid + "] DO_OP aplicado localmente -> " + novo + " (ts=" + lamportTs + ")");

        // salva checkpoint logo após operação (opcional)
        state.saveCheckpoint();

        // envia DO_OP ao coordenador (coordenador aplica globalmente)
        Message op = new Message();
        op.setType(MessageType.DO_OP);
        op.setPid(pid);
        op.setClock((int) lamportTs);
        op.setPayload("{\"delta\":1}");
        send(op);
        System.out.println("[NODE " + pid + "] DO_OP enviado ao COORD (clock=" + clock.getTime() + ")");
    }

    private void releaseCS() {
        Message rel = new Message();
        rel.setType(MessageType.RELEASE);
        rel.setPid(pid);
        rel.setClock(clock.increment());
        rel.setPayload("{}");

        send(rel);
        System.out.println("[NODE " + pid + "] RELEASE enviado (clock=" + clock.getTime() + ")");
    }

    private void listen() {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    Message msg = gson.fromJson(line, Message.class);
                    clock.update(msg.getClock()); // atualiza relógio local com o recebido

                    switch (msg.getType()) {
                        case GRANT -> {
                            System.out.println("[NODE " + pid + "] GRANT recebido. Entrando na RC… (clock="
                                    + clock.getTime() + ")");
                            doCriticalOperation();
                        }
                        case STATE -> {
                            // parse payload simples: {"counter":N}
                            String payload = msg.getPayload();
                            int valor = parseCounterFromPayload(payload);
                            int last = state.getLastLamportTs();
                            // só aplica se o clock recebido for maior que last (Lamport monotonic)
                            if (msg.getClock() <= last) {
                                System.out.println("[NODE " + pid + "] Ignorando STATE antigo (ts=" + msg.getClock()
                                        + " <= last=" + last + ")");
                            } else {
                                state.setCounterAndTs(valor, msg.getClock());
                                System.out.println("[NODE " + pid + "] STATE recebido e aplicado: counter=" + valor
                                        + " (ts=" + msg.getClock() + ")");
                                state.saveCheckpoint();
                            }
                            // após receber STATE (seja update ou confirm), libera CS
                            releaseCS();
                        }
                        case ROLLBACK -> {
                            System.out.println(
                                    "[NODE " + pid + "] ROLLBACK recebido do COORD. Restaurando checkpoint...");
                            state.loadCheckpoint();
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("[NODE " + pid + "] erro na escuta: " + e.getMessage());
            }
        }).start();
    }

    private int parseCounterFromPayload(String payload) {
        if (payload == null)
            return state.getCounter();
        try {
            // payload esperado: {"counter":N}
            String trimmed = payload.replaceAll("[{}\\s\"]", "");
            for (String kv : trimmed.split(",")) {
                String[] parts = kv.split(":");
                if (parts[0].equals("counter"))
                    return Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {
        }
        return state.getCounter();
    }

    public void start() {
        Random rand = new Random();
        while (true) {
            try {
                Thread.sleep(2000 + rand.nextInt(2000));
                requestCS();
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int pid = Integer.parseInt(args[0]); // ex: 1, 2, 3
        boolean delay = false;
        boolean crash = false;
        for (String a : args) {
            if ("--delay".equals(a))
                delay = true;
            if ("--crash".equals(a))
                crash = true;
        }
        NodeClient client = new NodeClient(pid, "127.0.0.1", 5000, delay, crash);
        client.start();
    }
}
