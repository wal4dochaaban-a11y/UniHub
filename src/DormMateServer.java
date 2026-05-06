import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DormMateServer {
    private static final int PORT = 8080;
    private static final Path PUBLIC_DIR = Path.of("public");

    private final CopyOnWriteArrayList<DormListing> listings = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<RoommatePost> roommatePosts = new CopyOnWriteArrayList<>();
    private final AtomicInteger listingIds = new AtomicInteger(1000);
    private final AtomicInteger roommateIds = new AtomicInteger(2000);

    public static void main(String[] args) throws IOException {
        new DormMateServer().start();
    }

    private void start() throws IOException {
        seedData();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/listings", this::handleListings);
        server.createContext("/api/roommates", this::handleRoommates);
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("DormMate is live at http://localhost:" + PORT);
    }

    private void handleListings(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendEmpty(exchange, 204);
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            List<DormListing> sorted = listings.stream()
                .sorted(Comparator.comparingInt(DormListing::price))
                .toList();
            sendJson(exchange, 200, toListingsJson(sorted));
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                Map<String, String> form = readForm(exchange);
                String title = required(form, "title");
                String campus = required(form, "campus");
                String price = required(form, "price");
                String beds = required(form, "beds");
                String description = required(form, "description");

                DormListing listing = new DormListing(
                    listingIds.incrementAndGet(),
                    title,
                    campus,
                    Integer.parseInt(price),
                    Integer.parseInt(beds),
                    form.getOrDefault("distance", "Walkable"),
                    form.getOrDefault("badge", "New"),
                    form.getOrDefault("amenities", "Wi-Fi, Study lounge, Laundry"),
                    description
                );

                listings.add(0, listing);
                sendJson(exchange, 201, "{\"status\":\"ok\",\"listing\":" + listing.toJson() + "}");
            } catch (IllegalArgumentException exception) {
                sendJson(exchange, 400, "{\"error\":\"" + escape(exception.getMessage()) + "\"}");
            }
            return;
        }

        sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
    }

    private void handleRoommates(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendEmpty(exchange, 204);
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            List<RoommatePost> sorted = roommatePosts.stream()
                .sorted(Comparator.comparing(RoommatePost::moveInDate))
                .toList();
            sendJson(exchange, 200, toRoommatesJson(sorted));
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                Map<String, String> form = readForm(exchange);
                String name = required(form, "name");
                String budget = required(form, "budget");
                String moveIn = required(form, "moveIn");
                String lifestyle = required(form, "lifestyle");
                String bio = required(form, "bio");

                RoommatePost post = new RoommatePost(
                    roommateIds.incrementAndGet(),
                    name,
                    form.getOrDefault("school", "Local Campus"),
                    Integer.parseInt(budget),
                    LocalDate.parse(moveIn),
                    lifestyle,
                    form.getOrDefault("match", "Quiet evenings"),
                    bio
                );

                roommatePosts.add(0, post);
                sendJson(exchange, 201, "{\"status\":\"ok\",\"post\":" + post.toJson() + "}");
            } catch (IllegalArgumentException exception) {
                sendJson(exchange, 400, "{\"error\":\"" + escape(exception.getMessage()) + "\"}");
            }
            return;
        }

        sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
    }

    private void seedData() {
        listings.addAll(List.of(
            new DormListing(901, "Sunlit Studio Loft", "Near AUB", 420, 1, "6 min walk", "Popular", "Wi-Fi, Balcony, Desk nook", "A bright studio for students who want privacy, storage, and a quick campus walk."),
            new DormListing(902, "Shared Penthouse Floor", "Hamra District", 280, 2, "10 min bus", "Budget pick", "Laundry, Rooftop, Utilities included", "Split a stylish upper-floor unit with generous common space and a social vibe."),
            new DormListing(903, "Focus House Suite", "Medical Campus", 360, 1, "3 min walk", "Quiet zone", "Study lounge, Generator, Security", "Designed for exam seasons with quiet hours, backup power, and easy late-night access.")
        ));

        roommatePosts.addAll(List.of(
            new RoommatePost(1801, "Maya R.", "AUB", 300, LocalDate.now().plusDays(21), "Early riser, tidy, low-noise weekdays", "Looking for another student who likes clean shared kitchens and calm nights.", "Architecture student who cooks twice a week and wants a roommate who communicates clearly."),
            new RoommatePost(1802, "Karim H.", "LAU", 250, LocalDate.now().plusDays(35), "Gym routine, social but respectful", "Best with someone who is okay with friends visiting occasionally and shared grocery runs.", "Computer science major hunting for a 2-bedroom setup near transit."),
            new RoommatePost(1803, "Nour A.", "USJ", 340, LocalDate.now().plusDays(14), "Quiet, focused, loves routines", "Hoping to match with another postgraduate student who values personal space.", "Master's student who needs reliable internet, calm evenings, and a serious study environment.")
        ));
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = new HashMap<>();

            if (body.isBlank()) {
                return form;
            }

            for (String part : body.split("&")) {
                String[] pieces = part.split("=", 2);
                String key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8);
                String value = pieces.length > 1
                    ? URLDecoder.decode(pieces[1], StandardCharsets.UTF_8)
                    : "";
                form.put(key, value.trim());
            }

            return form;
        }
    }

    private String required(Map<String, String> form, String key) {
        String value = form.getOrDefault(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    private void sendText(HttpExchange exchange, int status, String text, String contentType) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    private void sendBytes(HttpExchange exchange, int status, byte[] payload, String contentType) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    private void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private void addCors(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private String toListingsJson(List<DormListing> items) {
        List<String> json = new ArrayList<>();
        for (DormListing item : items) {
            json.add(item.toJson());
        }
        return "{\"listings\":[" + String.join(",", json) + "]}";
    }

    private String toRoommatesJson(List<RoommatePost> items) {
        List<String> json = new ArrayList<>();
        for (RoommatePost item : items) {
            json.add(item.toJson());
        }
        return "{\"roommates\":[" + String.join(",", json) + "]}";
    }

    private String jsonString(String value) {
        return "\"" + escape(value) + "\"";
    }

    private String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "");
    }

    private final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
                return;
            }

            String requestPath = exchange.getRequestURI().getPath();
            String relative = "/".equals(requestPath) ? "index.html" : requestPath.substring(1);
            Path target = PUBLIC_DIR.resolve(relative).normalize();

            if (!target.startsWith(PUBLIC_DIR) || !Files.exists(target) || Files.isDirectory(target)) {
                sendText(exchange, 404, "Not found", "text/plain; charset=utf-8");
                return;
            }

            String contentType = switch (target.getFileName().toString()) {
                case "index.html" -> "text/html; charset=utf-8";
                case "styles.css" -> "text/css; charset=utf-8";
                case "app.js" -> "application/javascript; charset=utf-8";
                default -> "application/octet-stream";
            };

            sendBytes(exchange, 200, Files.readAllBytes(target), contentType);
        }
    }

    private record DormListing(
        int id,
        String title,
        String campus,
        int price,
        int beds,
        String distance,
        String badge,
        String amenities,
        String description
    ) {
        String toJson() {
            return "{"
                + "\"id\":" + id + ","
                + "\"title\":\"" + escape(title) + "\","
                + "\"campus\":\"" + escape(campus) + "\","
                + "\"price\":" + price + ","
                + "\"beds\":" + beds + ","
                + "\"distance\":\"" + escape(distance) + "\","
                + "\"badge\":\"" + escape(badge) + "\","
                + "\"amenities\":\"" + escape(amenities) + "\","
                + "\"description\":\"" + escape(description) + "\""
                + "}";
        }

        private static String escape(String value) {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        }
    }

    private record RoommatePost(
        int id,
        String name,
        String school,
        int budget,
        LocalDate moveInDate,
        String lifestyle,
        String match,
        String bio
    ) {
        String toJson() {
            return "{"
                + "\"id\":" + id + ","
                + "\"name\":\"" + escape(name) + "\","
                + "\"school\":\"" + escape(school) + "\","
                + "\"budget\":" + budget + ","
                + "\"moveInDate\":\"" + moveInDate + "\","
                + "\"lifestyle\":\"" + escape(lifestyle) + "\","
                + "\"match\":\"" + escape(match) + "\","
                + "\"bio\":\"" + escape(bio) + "\""
                + "}";
        }

        private static String escape(String value) {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        }
    }
}
