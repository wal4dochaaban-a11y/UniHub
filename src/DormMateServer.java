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
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class DormMateServer {
    private static final int PORT = 8080;
    private static final Path PUBLIC_DIR = Path.of("public");
    private static final Path DB_PATH = Path.of(System.getenv().getOrDefault("UNIHUB_DB", "unihub.db"));

    private final UniHubDatabase database = new UniHubDatabase(DB_PATH);

    public static void main(String[] args) throws IOException {
        new DormMateServer().start();
    }

    private void start() throws IOException {
        database.initialize();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/listings", this::handleListings);
        server.createContext("/api/roommates", this::handleRoommates);
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("UniHub is live at http://localhost:" + PORT);
    }

    private void handleListings(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendEmpty(exchange, 204);
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 200, "{\"listings\":" + database.getListingsJson() + "}");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                Map<String, String> form = readForm(exchange);
                String title = required(form, "title");
                String campus = required(form, "campus");
                int price = positiveInt(required(form, "price"), "price");
                int beds = positiveInt(required(form, "beds"), "beds");
                String description = required(form, "description");

                String listingJson = database.createListing(
                    title,
                    campus,
                    price,
                    beds,
                    optional(form, "distance", "Walkable"),
                    optional(form, "badge", "New"),
                    optional(form, "amenities", "Wi-Fi, Study lounge, Laundry"),
                    description
                );

                sendJson(exchange, 201, "{\"status\":\"ok\",\"listing\":" + listingJson + "}");
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
            sendJson(exchange, 200, "{\"roommates\":" + database.getRoommatesJson() + "}");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                Map<String, String> form = readForm(exchange);
                String name = required(form, "name");
                int budget = positiveInt(required(form, "budget"), "budget");
                LocalDate moveIn = parseDate(required(form, "moveIn"), "moveIn");
                String lifestyle = required(form, "lifestyle");
                String bio = required(form, "bio");

                String postJson = database.createRoommatePost(
                    name,
                    optional(form, "school", "Local Campus"),
                    budget,
                    moveIn,
                    lifestyle,
                    optional(form, "match", "Quiet evenings"),
                    bio
                );

                sendJson(exchange, 201, "{\"status\":\"ok\",\"post\":" + postJson + "}");
            } catch (IllegalArgumentException exception) {
                sendJson(exchange, 400, "{\"error\":\"" + escape(exception.getMessage()) + "\"}");
            }
            return;
        }

        sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
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

    private String optional(Map<String, String> form, String key, String fallback) {
        String value = form.getOrDefault(key, "").trim();
        return value.isEmpty() ? fallback : value;
    }

    private int positiveInt(String value, String fieldName) {
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }
        return parsed;
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + " must use YYYY-MM-DD format");
        }
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

    private static final class UniHubDatabase {
        private final Path dbPath;
        private final String sqliteCommand;

        UniHubDatabase(Path dbPath) {
            this.dbPath = dbPath;
            this.sqliteCommand = System.getenv().getOrDefault("SQLITE_EXE", "sqlite3");
        }

        void initialize() throws IOException {
            runSql("""
                PRAGMA foreign_keys = ON;

                CREATE TABLE IF NOT EXISTS dorm_listings (
                    id INTEGER PRIMARY KEY,
                    title TEXT NOT NULL,
                    campus TEXT NOT NULL,
                    price INTEGER NOT NULL CHECK (price > 0),
                    beds INTEGER NOT NULL CHECK (beds > 0),
                    distance TEXT NOT NULL,
                    badge TEXT NOT NULL,
                    amenities TEXT NOT NULL,
                    description TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE IF NOT EXISTS roommate_posts (
                    id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL,
                    school TEXT NOT NULL,
                    budget INTEGER NOT NULL CHECK (budget > 0),
                    move_in_date TEXT NOT NULL,
                    lifestyle TEXT NOT NULL,
                    match_text TEXT NOT NULL,
                    bio TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """);
            seedData();
        }

        String getListingsJson() throws IOException {
            return runSql("""
                SELECT COALESCE(json_group_array(json_object(
                    'id', id,
                    'title', title,
                    'campus', campus,
                    'price', price,
                    'beds', beds,
                    'distance', distance,
                    'badge', badge,
                    'amenities', amenities,
                    'description', description
                )), '[]')
                FROM (
                    SELECT * FROM dorm_listings
                    ORDER BY price ASC, id ASC
                );
                """);
        }

        String getRoommatesJson() throws IOException {
            return runSql("""
                SELECT COALESCE(json_group_array(json_object(
                    'id', id,
                    'name', name,
                    'school', school,
                    'budget', budget,
                    'moveInDate', move_in_date,
                    'lifestyle', lifestyle,
                    'match', match_text,
                    'bio', bio
                )), '[]')
                FROM (
                    SELECT * FROM roommate_posts
                    ORDER BY move_in_date ASC, id ASC
                );
                """);
        }

        String createListing(
            String title,
            String campus,
            int price,
            int beds,
            String distance,
            String badge,
            String amenities,
            String description
        ) throws IOException {
            return runSql("""
                INSERT INTO dorm_listings (title, campus, price, beds, distance, badge, amenities, description)
                VALUES (%s, %s, %d, %d, %s, %s, %s, %s);

                SELECT json_object(
                    'id', id,
                    'title', title,
                    'campus', campus,
                    'price', price,
                    'beds', beds,
                    'distance', distance,
                    'badge', badge,
                    'amenities', amenities,
                    'description', description
                )
                FROM dorm_listings
                WHERE id = last_insert_rowid();
                """.formatted(
                    sqlText(title),
                    sqlText(campus),
                    price,
                    beds,
                    sqlText(distance),
                    sqlText(badge),
                    sqlText(amenities),
                    sqlText(description)
                ));
        }

        String createRoommatePost(
            String name,
            String school,
            int budget,
            LocalDate moveInDate,
            String lifestyle,
            String match,
            String bio
        ) throws IOException {
            return runSql("""
                INSERT INTO roommate_posts (name, school, budget, move_in_date, lifestyle, match_text, bio)
                VALUES (%s, %s, %d, %s, %s, %s, %s);

                SELECT json_object(
                    'id', id,
                    'name', name,
                    'school', school,
                    'budget', budget,
                    'moveInDate', move_in_date,
                    'lifestyle', lifestyle,
                    'match', match_text,
                    'bio', bio
                )
                FROM roommate_posts
                WHERE id = last_insert_rowid();
                """.formatted(
                    sqlText(name),
                    sqlText(school),
                    budget,
                    sqlText(moveInDate.toString()),
                    sqlText(lifestyle),
                    sqlText(match),
                    sqlText(bio)
                ));
        }

        private void seedData() throws IOException {
            runSql("""
                INSERT OR IGNORE INTO dorm_listings
                    (id, title, campus, price, beds, distance, badge, amenities, description)
                VALUES
                    (901, 'Sunlit Studio Loft', 'Near AUB', 420, 1, '6 min walk', 'Popular', 'Wi-Fi, Balcony, Desk nook', 'A bright studio for students who want privacy, storage, and a quick campus walk.'),
                    (902, 'Shared Penthouse Floor', 'Hamra District', 280, 2, '10 min bus', 'Budget pick', 'Laundry, Rooftop, Utilities included', 'Split a stylish upper-floor unit with generous common space and a social vibe.'),
                    (903, 'Focus House Suite', 'Medical Campus', 360, 1, '3 min walk', 'Quiet zone', 'Study lounge, Generator, Security', 'Designed for exam seasons with quiet hours, backup power, and easy late-night access.');

                INSERT OR IGNORE INTO roommate_posts
                    (id, name, school, budget, move_in_date, lifestyle, match_text, bio)
                VALUES
                    (1801, 'Maya R.', 'AUB', 300, date('now', '+21 days'), 'Early riser, tidy, low-noise weekdays', 'Looking for another student who likes clean shared kitchens and calm nights.', 'Architecture student who cooks twice a week and wants a roommate who communicates clearly.'),
                    (1802, 'Karim H.', 'LAU', 250, date('now', '+35 days'), 'Gym routine, social but respectful', 'Best with someone who is okay with friends visiting occasionally and shared grocery runs.', 'Computer science major hunting for a 2-bedroom setup near transit.'),
                    (1803, 'Nour A.', 'USJ', 340, date('now', '+14 days'), 'Quiet, focused, loves routines', 'Hoping to match with another postgraduate student who values personal space.', 'Master''s student who needs reliable internet, calm evenings, and a serious study environment.');
                """);
        }

        private String runSql(String sql) throws IOException {
            Process process = new ProcessBuilder(sqliteCommand, "-batch", dbPath.toString())
                .redirectErrorStream(true)
                .start();

            try (OutputStream input = process.getOutputStream()) {
                input.write(sql.getBytes(StandardCharsets.UTF_8));
            }

            String output;
            try (InputStream processOutput = process.getInputStream()) {
                output = new String(processOutput.readAllBytes(), StandardCharsets.UTF_8).trim();
            }

            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("SQLite command failed: " + output);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("SQLite command was interrupted", exception);
            }

            return output.isBlank() ? "[]" : output;
        }

        private String sqlText(String value) {
            return "'" + value.replace("'", "''").replace("\r", "").replace("\n", " ") + "'";
        }
    }
}
