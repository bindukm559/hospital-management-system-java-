import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

public class HospitalServer {
    private static final String DATA_DIR = "data/";
    private static final String DOCTORS_FILE = DATA_DIR + "doctors.csv";
    private static final String APPOINTMENTS_FILE = DATA_DIR + "appointments.csv";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "1234";
    
    public static void main(String[] args) throws IOException {
        initializeDataFiles();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));
        
        // CORS and routing
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/doctors", new DoctorsHandler());
        server.createContext("/api/appointments", new AppointmentsHandler());
        server.createContext("/api/timeslots", new TimeSlotsHandler());
        server.createContext("/api/admin/login", new AdminLoginHandler());
        server.createContext("/api/debug/csv", new DebugCSVHandler());
        server.createContext("/api/admin/reset", new ResetDataHandler());
        
        server.start();
        System.out.println("========================================");
        System.out.println("Hospital Management System started!");
        System.out.println("Open http://localhost:8080 in your browser");
        System.out.println("Admin Login: Username = admin, Password = 1234");
        System.out.println("========================================");
    }
    
    private static void initializeDataFiles() throws IOException {
        Files.createDirectories(Paths.get(DATA_DIR));
        
        // Always ensure files have proper headers
        if (!Files.exists(Paths.get(DOCTORS_FILE)) || Files.size(Paths.get(DOCTORS_FILE)) == 0) {
            List<String> doctors = Arrays.asList(
                "id,name,specialty",
                "1,Dr. Sharma,Cardiology",
                "2,Dr. Patel,Neurology",
                "3,Dr. Kumar,Pediatrics"
            );
            Files.write(Paths.get(DOCTORS_FILE), doctors);
            System.out.println("Initialized doctors.csv with sample data");
        }
        
        if (!Files.exists(Paths.get(APPOINTMENTS_FILE)) || Files.size(Paths.get(APPOINTMENTS_FILE)) == 0) {
            Files.write(Paths.get(APPOINTMENTS_FILE), 
                Collections.singletonList("id,patientName,doctorId,date,timeSlot"));
            System.out.println("Initialized appointments.csv with header");
        }
        
        // Verify files are readable
        System.out.println("=== Data Files Status ===");
        System.out.println("Doctors file: " + Paths.get(DOCTORS_FILE).toAbsolutePath());
        System.out.println("Doctors lines: " + Files.readAllLines(Paths.get(DOCTORS_FILE)).size());
        System.out.println("Appointments file: " + Paths.get(APPOINTMENTS_FILE).toAbsolutePath());
        System.out.println("Appointments lines: " + Files.readAllLines(Paths.get(APPOINTMENTS_FILE)).size());
    }
    
    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            String file = "../frontend" + path;
            File f = new File(file);
            
            if (f.exists() && f.isFile()) {
                byte[] bytes = Files.readAllBytes(f.toPath());
                String contentType = "text/html";
                if (path.endsWith(".css")) contentType = "text/css";
                if (path.endsWith(".js")) contentType = "application/javascript";
                
                ex.getResponseHeaders().set("Content-Type", contentType);
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
            } else {
                String response = "404 Not Found";
                ex.sendResponseHeaders(404, response.length());
                ex.getResponseBody().write(response.getBytes());
            }
            ex.getResponseBody().close();
        }
    }
    
    static class AdminLoginHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            
            if (ex.getRequestMethod().equals("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            
            if (ex.getRequestMethod().equals("POST")) {
                String body = readBody(ex);
                Map<String, String> data = parseJson(body);
                
                String username = data.get("username");
                String password = data.get("password");
                
                if (ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password)) {
                    sendResponse(ex, 200, "{\"success\":true,\"message\":\"Login successful\"}");
                } else {
                    sendResponse(ex, 401, "{\"success\":false,\"message\":\"Invalid credentials\"}");
                }
            }
        }
    }
    
    static class DebugCSVHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            
            if (ex.getRequestMethod().equals("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            
            try {
                List<String> lines = Files.readAllLines(Paths.get(APPOINTMENTS_FILE));
                StringBuilder debug = new StringBuilder("{\"rawLines\":[");
                
                for (int i = 0; i < lines.size(); i++) {
                    if (i > 0) debug.append(",");
                    debug.append("\"").append(escapeJsonString(lines.get(i))).append("\"");
                }
                debug.append("]}");
                
                sendResponse(ex, 200, debug.toString());
            } catch (Exception e) {
                sendResponse(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }
    
    static class ResetDataHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            
            if (ex.getRequestMethod().equals("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            
            if (ex.getRequestMethod().equals("POST")) {
                try {
                    // Reset appointments file
                    Files.write(Paths.get(APPOINTMENTS_FILE), 
                        Collections.singletonList("id,patientName,doctorId,date,timeSlot"));
                    
                    // Reset doctors file
                    List<String> doctors = Arrays.asList(
                        "id,name,specialty",
                        "1,Dr. Sharma,Cardiology",
                        "2,Dr. Patel,Neurology",
                        "3,Dr. Kumar,Pediatrics"
                    );
                    Files.write(Paths.get(DOCTORS_FILE), doctors);
                    
                    System.out.println("Data files reset successfully");
                    sendResponse(ex, 200, "{\"success\":true,\"message\":\"Data files reset successfully\"}");
                } catch (Exception e) {
                    System.err.println("Error resetting data: " + e.getMessage());
                    sendResponse(ex, 500, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
                }
            }
        }
    }
    
    static class DoctorsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            
            if (ex.getRequestMethod().equals("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            
            String method = ex.getRequestMethod();
            
            if (method.equals("GET")) {
                List<String> lines = Files.readAllLines(Paths.get(DOCTORS_FILE));
                String json = csvToJson(lines);
                sendResponse(ex, 200, json);
            } 
            else if (method.equals("POST")) {
                String body = readBody(ex);
                Map<String, String> data = parseJson(body);
                
                int nextId = getNextId(DOCTORS_FILE);
                String name = data.get("name").trim();
                String specialty = data.get("specialty").trim();
                String newLine = nextId + "," + name + "," + specialty;
                
                System.out.println("Adding doctor: " + newLine);
                
                List<String> lines = new ArrayList<>(Files.readAllLines(Paths.get(DOCTORS_FILE)));
                lines.add(newLine);
                Files.write(Paths.get(DOCTORS_FILE), lines);
                
                sendResponse(ex, 201, "{\"success\":true,\"id\":" + nextId + "}");
            }
            else if (method.equals("DELETE")) {
                String query = ex.getRequestURI().getQuery();
                String id = query.split("=")[1];
                
                List<String> lines = Files.readAllLines(Paths.get(DOCTORS_FILE));
                List<String> filtered = lines.stream()
                    .filter(line -> !line.startsWith(id + ","))
                    .collect(Collectors.toList());
                
                Files.write(Paths.get(DOCTORS_FILE), filtered);
                sendResponse(ex, 200, "{\"success\":true}");
            }
        }
    }
    
    static class AppointmentsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            
            if (ex.getRequestMethod().equals("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            
            String method = ex.getRequestMethod();
            
            if (method.equals("GET")) {
                try {
                    List<String> lines = Files.readAllLines(Paths.get(APPOINTMENTS_FILE));
                    System.out.println("=== Reading appointments.csv ===");
                    System.out.println("File path: " + Paths.get(APPOINTMENTS_FILE).toAbsolutePath());
                    System.out.println("Number of lines: " + lines.size());
                    for (int i = 0; i < lines.size(); i++) {
                        System.out.println("Line " + i + ": " + lines.get(i));
                    }
                    
                    String json = csvToJson(lines);
                    System.out.println("Generated JSON: " + json);
                    sendResponse(ex, 200, json);
                } catch (Exception e) {
                    System.err.println("Error reading appointments: " + e.getMessage());
                    e.printStackTrace();
                    sendResponse(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            }
            else if (method.equals("POST")) {
                try {
                    String body = readBody(ex);
                    System.out.println("Received booking request: " + body);
                    
                    Map<String, String> data = parseJson(body);
                    
                    // Validate data
                    if (data.get("patientName") == null || data.get("patientName").trim().isEmpty()) {
                        sendResponse(ex, 400, "{\"success\":false,\"error\":\"Patient name is required\"}");
                        return;
                    }
                    if (data.get("doctorId") == null || data.get("doctorId").trim().isEmpty()) {
                        sendResponse(ex, 400, "{\"success\":false,\"error\":\"Doctor selection is required\"}");
                        return;
                    }
                    if (data.get("date") == null || data.get("date").trim().isEmpty()) {
                        sendResponse(ex, 400, "{\"success\":false,\"error\":\"Date is required\"}");
                        return;
                    }
                    if (data.get("timeSlot") == null || data.get("timeSlot").trim().isEmpty()) {
                        sendResponse(ex, 400, "{\"success\":false,\"error\":\"Time slot is required\"}");
                        return;
                    }
                    
                    // Check for conflicts
                    if (isSlotBooked(data.get("doctorId"), data.get("date"), data.get("timeSlot"))) {
                        sendResponse(ex, 409, "{\"success\":false,\"error\":\"Time slot already booked\"}");
                        return;
                    }
                    
                    int nextId = getNextId(APPOINTMENTS_FILE);
                    
                    // Create CSV line with proper escaping
                    String patientName = data.get("patientName").trim();
                    String doctorId = data.get("doctorId").trim();
                    String date = data.get("date").trim();
                    String timeSlot = data.get("timeSlot").trim();
                    
                    String newLine = nextId + "," + patientName + "," + doctorId + "," + date + "," + timeSlot;
                    
                    System.out.println("Writing to CSV: " + newLine);
                    
                    List<String> lines = new ArrayList<>(Files.readAllLines(Paths.get(APPOINTMENTS_FILE)));
                    lines.add(newLine);
                    Files.write(Paths.get(APPOINTMENTS_FILE), lines);
                    
                    System.out.println("Appointment booked successfully with ID: " + nextId);
                    sendResponse(ex, 201, "{\"success\":true,\"id\":" + nextId + "}");
                    
                } catch (Exception e) {
                    System.err.println("Error booking appointment: " + e.getMessage());
                    e.printStackTrace();
                    sendResponse(ex, 500, "{\"success\":false,\"error\":\"Server error: " + e.getMessage() + "\"}");
                }
            }
            else if (method.equals("DELETE")) {
                try {
                    String query = ex.getRequestURI().getQuery();
                    String id = query.split("=")[1];
                    
                    System.out.println("Deleting appointment with ID: " + id);
                    
                    List<String> lines = Files.readAllLines(Paths.get(APPOINTMENTS_FILE));
                    List<String> filtered = lines.stream()
                        .filter(line -> !line.startsWith(id + ","))
                        .collect(Collectors.toList());
                    
                    if (lines.size() == filtered.size()) {
                        sendResponse(ex, 404, "{\"success\":false,\"error\":\"Appointment not found\"}");
                        return;
                    }
                    
                    Files.write(Paths.get(APPOINTMENTS_FILE), filtered);
                    System.out.println("Appointment deleted successfully");
                    sendResponse(ex, 200, "{\"success\":true}");
                    
                } catch (Exception e) {
                    System.err.println("Error deleting appointment: " + e.getMessage());
                    e.printStackTrace();
                    sendResponse(ex, 500, "{\"success\":false,\"error\":\"Server error: " + e.getMessage() + "\"}");
                }
            }
        }
    }
    
    static class TimeSlotsHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            
            if (ex.getRequestMethod().equals("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            
            try {
                String query = ex.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                
                String doctorId = params.get("doctorId");
                String date = params.get("date");
                
                if (doctorId == null || date == null) {
                    sendResponse(ex, 400, "{\"error\":\"doctorId and date are required\"}");
                    return;
                }
                
                List<String> allSlots = generateTimeSlots();
                List<String> bookedSlots = getBookedSlots(doctorId, date);
                
                List<String> available = allSlots.stream()
                    .filter(slot -> !bookedSlots.contains(slot))
                    .collect(Collectors.toList());
                
                String json = "[" + available.stream()
                    .map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(",")) + "]";
                
                sendResponse(ex, 200, json);
            } catch (Exception e) {
                System.err.println("Error getting time slots: " + e.getMessage());
                sendResponse(ex, 500, "{\"error\":\"Server error\"}");
            }
        }
    }
    
    private static List<String> generateTimeSlots() {
        List<String> slots = new ArrayList<>();
        for (int h = 9; h <= 16; h++) {
            slots.add(String.format("%02d:00", h));
            if (h < 16) slots.add(String.format("%02d:30", h));
        }
        return slots;
    }
    
    private static List<String> getBookedSlots(String doctorId, String date) throws IOException {
        return Files.readAllLines(Paths.get(APPOINTMENTS_FILE)).stream()
            .skip(1)
            .filter(line -> {
                if (line == null || line.trim().isEmpty()) return false;
                String[] parts = line.split(",", -1);
                return parts.length >= 5 && parts[2].equals(doctorId) && parts[3].equals(date);
            })
            .map(line -> line.split(",", -1)[4])
            .collect(Collectors.toList());
    }
    
    private static boolean isSlotBooked(String doctorId, String date, String slot) throws IOException {
        return getBookedSlots(doctorId, date).contains(slot);
    }
    
    private static String csvToJson(List<String> lines) {
        if (lines.size() <= 1) return "[]";
        
        String[] headers = lines.get(0).split(",");
        StringBuilder json = new StringBuilder("[");
        
        boolean firstEntry = true;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue; // Skip empty lines
            
            String[] values = line.split(",", -1); // -1 to include trailing empty strings
            
            if (!firstEntry) json.append(",");
            firstEntry = false;
            
            json.append("{");
            for (int j = 0; j < headers.length; j++) {
                if (j > 0) json.append(",");
                String value = j < values.length ? values[j] : "";
                json.append("\"").append(headers[j]).append("\":\"").append(escapeJsonString(value)).append("\"");
            }
            json.append("}");
        }
        json.append("]");
        return json.toString();
    }
    
    private static String escapeJsonString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim().replaceAll("[{}]", "");
        
        // Handle empty JSON
        if (json.isEmpty()) return map;
        
        // Split by comma but not inside quotes
        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : json.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) {
                pairs.add(current.toString());
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }
        pairs.add(current.toString());
        
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("\"", "");
                String value = kv[1].trim().replaceAll("\"", "");
                map.put(key, value);
            }
        }
        return map;
    }
    
    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                map.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
        }
        return map;
    }
    
    private static int getNextId(String file) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(file));
        if (lines.size() <= 1) return 1;
        
        return lines.stream()
            .skip(1)
            .mapToInt(line -> {
                try {
                    return Integer.parseInt(line.split(",")[0]);
                } catch (Exception e) {
                    return 0;
                }
            })
            .max()
            .orElse(0) + 1;
    }
    
    private static String escapeComma(String str) {
        if (str == null) return "";
        // Don't replace commas, just escape quotes if needed
        return str.replace("\"", "\"\"");
    }
    
    private static String readBody(HttpExchange ex) throws IOException {
        return new BufferedReader(new InputStreamReader(ex.getRequestBody()))
            .lines().collect(Collectors.joining());
    }
    
    private static void sendResponse(HttpExchange ex, int code, String response) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes("UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }
    
    private static void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }
}