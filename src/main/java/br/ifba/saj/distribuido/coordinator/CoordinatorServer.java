package br.ifba.saj.distribuido.coordinator;

import br.ifba.saj.distribuido.model.*;
import com.google.gson.Gson;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

public class CoordinatorServer {
    private static final int PORT = 5000;
    private static int sharedCounter = 0;
    private static boolean busy = false;
    private static LamportClock clock = new LamportClock();
    private static PriorityBlockingQueue<Request> requestQueue = new PriorityBlockingQueue<>();

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
                clock.update(msg.getClock());

                switch (msg.getType()) {
                    case JOIN -> System.out.println("[COORD] JOIN pid=" + msg.getPid());
                    case REQUEST -> {
                        requestQueue.add(new Request(msg.getClock(), msg.getPid(), client));
                        tryGrantNext();
                    }
                    case DO_OP -> {
                        sharedCounter++;
                        Message state = new Message();
                        state.setType(MessageType.STATE);
                        state.setPid(0);
                        state.setClock(clock.increment());
                        state.setPayload("{\"counter\":" + sharedCounter + "}");
                        out.println(gson.toJson(state));
                        System.out.println("[COORD] DO_OP pid=" + msg.getPid() + " -> counter=" + sharedCounter);
                    }
                    case RELEASE -> {
                        busy = false;
                        tryGrantNext();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void tryGrantNext() {
        if (busy || requestQueue.isEmpty()) return;
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
        }
    }
}
