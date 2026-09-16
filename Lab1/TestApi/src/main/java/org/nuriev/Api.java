package org.nuriev;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class Api {
    // Здесь держим аллоцированные массивы, чтобы GC их не собрал.
    private static final List<byte[]> HOLD = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        // /health — просто "ok"
        server.createContext("/health", ex -> respond(ex, 200, "ok\n"));

        // /eat?mb=N — выделяем N МБ и держим их в static-списке
        server.createContext("/eat", ex -> {
            int mb = 100; // дефолт
            String q = ex.getRequestURI().getQuery();
            if (q != null && q.startsWith("mb=")) {
                try { mb = Integer.parseInt(q.substring(3)); } catch (NumberFormatException ignored) {}
            }
            for (int i = 0; i < mb; i++) {
                HOLD.add(new byte[1024 * 1024]); // 1 МБ
            }
            respond(ex, 200, "allocated " + mb + " MB, total held: " + HOLD.size() + " MB\n");
        });

        // /burn — бесконечный CPU-loop, никогда не отвечает (это ок)
        server.createContext("/burn", ex -> {
            long x = 0;
            while (true) {
                x = x * 31 + 17;
                if (x == Long.MIN_VALUE) System.out.print(""); // защита от JIT-оптимизации
            }
        });

        // cached thread pool, чтобы /burn не блокировал остальные запросы
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("api listening on :" + port);
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes();
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }
}
