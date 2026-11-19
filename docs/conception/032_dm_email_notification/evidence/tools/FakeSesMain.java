import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 수동 실험용 가짜 SES (프로세스 하나). 앱 프로세스 여러 개가 같은 서버를 부른다.
 * 관리: GET /_admin/mode?m=ACCEPT|ACCEPT_THEN_HANG&delayMs=N, GET /_admin/stats, GET /_admin/reset
 */
public class FakeSesMain {
    static volatile String mode = "ACCEPT";
    static volatile long delayMs = 0;
    static final AtomicInteger requests = new AtomicInteger();
    static final AtomicInteger accepted = new AtomicInteger();
    static final Map<String, AtomicInteger> perRecipient = new ConcurrentHashMap<>();
    static final Pattern TO = Pattern.compile("\"ToAddresses\":\\[\"([^\"]+)\"");

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/_admin", FakeSesMain::admin);
        server.createContext("/", FakeSesMain::ses);
        server.start();
        System.out.println("fake ses on " + port);
    }

    static void admin(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String q = ex.getRequestURI().getQuery() == null ? "" : ex.getRequestURI().getQuery();
        if (path.endsWith("/mode")) {
            for (String kv : q.split("&")) {
                String[] p = kv.split("=");
                if (p[0].equals("m")) mode = p[1];
                if (p[0].equals("delayMs")) delayMs = Long.parseLong(p[1]);
            }
        } else if (path.endsWith("/reset")) {
            requests.set(0);
            accepted.set(0);
            perRecipient.clear();
        }
        long dup = perRecipient.values().stream().filter(c -> c.get() > 1).count();
        String json = "{\"mode\":\"" + mode + "\",\"delayMs\":" + delayMs + ",\"requests\":" + requests.get()
                + ",\"accepted\":" + accepted.get() + ",\"recipients\":" + perRecipient.size()
                + ",\"recipientsWithMoreThanOneMail\":" + dup + "}";
        respond(ex, 200, json);
    }

    static void ses(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.incrementAndGet();
        try {
            Matcher m = TO.matcher(body);
            String to = m.find() ? m.group(1) : "?";
            if (mode.equals("ACCEPT_THEN_HANG")) {
                accepted.incrementAndGet();
                perRecipient.computeIfAbsent(to, k -> new AtomicInteger()).incrementAndGet();
                Thread.sleep(delayMs);
                return;
            }
            Thread.sleep(delayMs);
            int n = accepted.incrementAndGet();
            perRecipient.computeIfAbsent(to, k -> new AtomicInteger()).incrementAndGet();
            respond(ex, 200, "{\"MessageId\":\"exp-" + n + "\"}");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            ex.close();
        }
    }

    static void respond(HttpExchange ex, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
        }
    }
}
