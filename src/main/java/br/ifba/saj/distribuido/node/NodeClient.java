package br.ifba.saj.distribuido.node;

import br.ifba.saj.distribuido.model.*;
import br.ifba.saj.distribuido.model.LamportClock;
import br.ifba.saj.distribuido.model.Message;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.util.Random;

public class NodeClient {
    private int pid;
    private LamportClock clock;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Gson gson;

    public NodeClient(int pid, String host, int port) throws IOException {
        this.pid = pid;
        this.clock = new LamportClock();
        this.socket = new Socket(host, port);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.gson = new Gson();

        join();
        listen();
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

    private void doCriticalOperation() {
        Message op = new Message();
        op.setType(MessageType.DO_OP);
        op.setPid(pid);
        op.setClock(clock.increment());
        op.setPayload("{\"delta\":1}");

        send(op);
        System.out.println("[NODE " + pid + "] DO_OP enviado (clock=" + clock.getTime() + ")");
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
                    clock.update(msg.getClock());

                    switch (msg.getType()) {
                        case GRANT -> {
                            System.out.println("[NODE " + pid + "] GRANT recebido. Entrando na RC… (clock=" + clock.getTime() + ")");
                            doCriticalOperation();
                        }
                        case STATE -> {
                            System.out.println("[NODE " + pid + "] STATE recebido: " + msg.getPayload());
                            releaseCS();
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("[NODE " + pid + "] erro na escuta: " + e.getMessage());
            }
        }).start();
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
        NodeClient client = new NodeClient(pid, "127.0.0.1", 5000);
        client.start();
    }
}
