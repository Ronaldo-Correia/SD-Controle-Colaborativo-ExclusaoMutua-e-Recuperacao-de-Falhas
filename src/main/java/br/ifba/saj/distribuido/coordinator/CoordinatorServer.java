package br.ifba.saj.distribuido.coordinator;

import br.ifba.saj.distribuido.model.*;
import com.google.gson.Gson;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

//Foi reajustada com a adição de registro de sockets dos nós no JOIN, 
//broadcast assíncrono de STATE para todos os nós e envio de ROLLBACK a todos os nós. 
public class CoordinatorServer {
    private static final int PORT = 5000;
    private static int sharedCounter = 0;
    private static boolean busy = false;
    private static LamportClock clock = new LamportClock();
    private static PriorityBlockingQueue<Request> requestQueue = new PriorityBlockingQueue<>();

    // map de pid -> socket + writer (para broadcast)
    private static final ConcurrentMap<Integer, Socket> nodeSockets = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, PrintWriter> nodeWriters = new ConcurrentHashMap<>();

    // executor para replicação assíncrona
    private static final ExecutorService repExecutor = Executors.newCachedThreadPool();

    private static class Request implements Comparable<Request> {
        int lamportTime;
        int pid;
        Socket socket;

        Request(int lamportTime, int pid, Socket socket) {
            this.lamportTime = lamportTime;
            this.pid = pid;
            this.socket = socket;
        }

        @Override
        public int compareTo(Request other) {
            if (this.lamportTime == other.lamportTime)
                return Integer.compare(this.pid, other.pid);
            return Integer.compare(this.lamportTime, other.lamportTime);
        }
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("[COORD] Listening on " + PORT);

        while (true) {
            Socket client = serverSocket.accept();
            new Thread(() -> handleClient(client)).start();
        }
    }

    private static void handleClient(Socket client) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            Gson gson = new Gson();
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = gson.fromJson(line, Message.class);

                // atualiza relógio do coordenador com o clock recebido
                clock.update(msg.getClock());

                switch (msg.getType()) {
                    case JOIN -> {
                        System.out.println("[COORD] JOIN pid=" + msg.getPid());
                        // registra socket/writer para broadcasts futuros
                        nodeSockets.put(msg.getPid(), client);
                        nodeWriters.put(msg.getPid(), new PrintWriter(client.getOutputStream(), true));
                    }
                    case REQUEST -> {
                        requestQueue.add(new Request(msg.getClock(), msg.getPid(), client));
                        tryGrantNext();
                    }
                    case DO_OP -> {
                        // coordenador aplica a operação canônica no estado global
                        sharedCounter++;
                        Message state = new Message();
                        state.setType(MessageType.STATE);
                        state.setPid(0);
                        state.setClock(clock.increment());
                        state.setPayload("{\"counter\":" + sharedCounter + "}");

                        System.out.println("[COORD] DO_OP pid=" + msg.getPid() + " -> counter=" + sharedCounter);

                        // broadcast assíncrono (consistência eventual)
                        broadcastStateAsync(sharedCounter, state.getClock());
                    }
                    case RELEASE -> {
                        busy = false;
                        tryGrantNext();
                    }
                    default -> {

                    }
                }
            }
        } catch (Exception e) {
            // Se um node desconectar, remova do map (tenta identificar pid removendo
            // entradas com mesmo socket)
            handleClientDisconnect(client);
            e.printStackTrace();
        }
    }

    private static void tryGrantNext() {
        if (busy || requestQueue.isEmpty())
            return;
        Request req = requestQueue.poll();
        busy = true;

        Gson gson = new Gson();
        Message grant = new Message();
        grant.setType(MessageType.GRANT);
        grant.setPid(0);
        grant.setClock(clock.increment());
        grant.setPayload("{}");

        try {
            PrintWriter out = new PrintWriter(req.socket.getOutputStream(), true);
            out.println(gson.toJson(grant));
            System.out.println("[COORD] GRANT -> pid=" + req.pid);
        } catch (IOException e) {
            e.printStackTrace();
            // se falhar, libera e tenta próximo
            busy = false;
            tryGrantNext();
        }
    }

    private static void broadcastStateAsync(int novoValor, int lamportTs) {
        Gson gson = new Gson();
        Message state = new Message();
        state.setType(MessageType.STATE);
        state.setPid(0);
        state.setClock(lamportTs);
        state.setPayload("{\"counter\":" + novoValor + "}");

        for (Map.Entry<Integer, PrintWriter> e : nodeWriters.entrySet()) {
            final int targetPid = e.getKey();
            final PrintWriter writer = e.getValue();
            repExecutor.submit(() -> {
                try {
                    // simula atraso de rede aleatório (0-2000ms) para demonstrar eventual
                    // consistency
                    Thread.sleep(ThreadLocalRandom.current().nextInt(0, 2000));
                } catch (InterruptedException ignored) {
                }
                try {
                    writer.println(gson.toJson(state));

                    System.out.println("[COORD] STATE enviado -> pid=" + targetPid + " counter=" + novoValor + " (ts="
                            + lamportTs + ")");
                } catch (Exception ex) {
                    System.err.println("[COORD] Erro ao replicar para pid=" + targetPid + ": " + ex.getMessage());
                }
            });
        }
    }

    // método para pedir rollback globalmente (comunicando todos os nós)
    public static void requestGlobalRollback(String reason) {
        System.out.println("[COORD] Solicitando rollback global: " + reason);
        Gson gson = new Gson();
        Message rollback = new Message();
        rollback.setType(MessageType.ROLLBACK);
        rollback.setPid(0);
        rollback.setClock(clock.increment());
        rollback.setPayload("{\"reason\":\"" + reason + "\"}");

        for (Map.Entry<Integer, PrintWriter> e : nodeWriters.entrySet()) {
            final int targetPid = e.getKey();
            final PrintWriter writer = e.getValue();
            repExecutor.submit(() -> {
                try {
                    writer.println(gson.toJson(rollback));
                    System.out.println("[COORD] ROLLBACK enviado -> pid=" + targetPid);
                } catch (Exception ex) {
                    System.err
                            .println("[COORD] Erro ao mandar ROLLBACK para pid=" + targetPid + ": " + ex.getMessage());
                }
            });
        }
    }

    private static void handleClientDisconnect(Socket client) {
        // remove sockets/writers que usam esse socket
        nodeSockets.entrySet().removeIf(entry -> entry.getValue().equals(client));
        nodeWriters.entrySet().removeIf(entry -> entry.getValue().checkError());
    }
}
