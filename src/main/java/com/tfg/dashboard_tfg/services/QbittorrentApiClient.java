package com.tfg.dashboard_tfg.services;

import com.tfg.dashboard_tfg.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Client for interacting with the qBittorrent Web API
 */
public class QbittorrentApiClient {

    private static final String API_PATH = "/api/v2";
    private static final String LOGIN_PATH = "/auth/login";
    private static final String LOGOUT_PATH = "/auth/logout";
    private static final String TORRENTS_INFO_PATH = "/torrents/info";
    private static final String TRANSFER_INFO_PATH = "/transfer/info";
    private static final String TORRENT_PROPERTIES_PATH = "/torrents/properties";
    private static final String TORRENT_FILES_PATH = "/torrents/files";
    private static final String TORRENT_TRACKERS_PATH = "/torrents/trackers";
    private static final String TORRENT_PAUSE_PATH = "/torrents/pause";
    private static final String TORRENT_RESUME_PATH = "/torrents/resume";
    private static final String TORRENT_DELETE_PATH = "/torrents/delete";
    private static final String TORRENT_ADD_PATH = "/torrents/add";
    private static final String TORRENT_SET_DOWNLOAD_LIMIT_PATH = "/torrents/setDownloadLimit";
    private static final String TORRENT_SET_UPLOAD_LIMIT_PATH = "/torrents/setUploadLimit";
    private static final String TORRENT_PEERS_PATH = "/torrents/peers";
    private static final String APP_VERSION_PATH = "/app/version";

    private String baseUrl;
    private CookieManager cookieManager;
    private boolean isLoggedIn = false;
    private final Random random = new Random();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public QbittorrentApiClient() {
        // Initialize cookie manager for session handling
        cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
    }

    /**
     * Login to qBittorrent Web API
     *
     * @param host     API host URL
     * @param username Username for login
     * @param password Password for login
     * @return true if login was successful
     */
    public boolean login(String host, String username, String password) {
        this.baseUrl = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;

        try {
            URL url = new URL(baseUrl + API_PATH + LOGIN_PATH);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            String params = String.format("username=%s&password=%s",
                    URLEncoder.encode(username, StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode(password, StandardCharsets.UTF_8.toString()));

            try (OutputStream os = connection.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                isLoggedIn = true;
                return true;
            }
        } catch (IOException e) {
            System.err.println("Login error: " + e.getMessage());
        }

        return false;
    }

    /**
     * Logout from qBittorrent Web API
     *
     * @return true if logout was successful
     */
    public boolean logout() {
        if (!isLoggedIn) {
            return false;
        }

        try {
            URL url = new URL(baseUrl + API_PATH + LOGOUT_PATH);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                isLoggedIn = false;
                return true;
            }
        } catch (IOException e) {
            System.err.println("Logout error: " + e.getMessage());
        }

        return false;
    }

    /**
     * Get list of torrents
     *
     * @return List of torrent data
     */
    public List<TorrentData> getTorrents() {
        if (!isLoggedIn) {
            return new ArrayList<>();
        }

        List<TorrentData> result = new ArrayList<>();

        try {
            URL url = new URL(baseUrl + API_PATH + TORRENTS_INFO_PATH);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String response = in.lines().collect(Collectors.joining());

                    // Simple JSON parsing - in a real app use a JSON library
                    String[] torrents = response.split("\\},\\{");
                    for (String torrent : torrents) {
                        torrent = torrent.replace("[{", "").replace("}]", "");

                        Map<String, String> torrentData = parseJson(torrent);

                        TorrentData data = new TorrentData();
                        data.setName(getJsonValue(torrentData, "name"));
                        data.setHash(getJsonValue(torrentData, "hash"));
                        data.setSize(formatSize(parseLong(getJsonValue(torrentData, "size"))));
                        data.setProgress(parseDouble(getJsonValue(torrentData, "progress")));

                        long dlspeed = parseLong(getJsonValue(torrentData, "dlspeed"));
                        long upspeed = parseLong(getJsonValue(torrentData, "upspeed"));
                        data.setDownloadSpeed(formatSpeed(dlspeed));
                        data.setUploadSpeed(formatSpeed(upspeed));

                        String state = getJsonValue(torrentData, "state");
                        if ("downloading".equalsIgnoreCase(state)) {
                            data.setStatus("Downloading");
                        } else if ("uploading".equalsIgnoreCase(state) || "stalledUP".equalsIgnoreCase(state)) {
                            data.setStatus("Seeding");
                        } else if ("pausedDL".equalsIgnoreCase(state) || "pausedUP".equalsIgnoreCase(state)) {
                            data.setStatus("Paused");
                        } else if ("queuedDL".equalsIgnoreCase(state) || "queuedUP".equalsIgnoreCase(state)) {
                            data.setStatus("Queued");
                        } else if ("error".equalsIgnoreCase(state)) {
                            data.setStatus("Error");
                        } else if ("missingFiles".equalsIgnoreCase(state)) {
                            data.setStatus("Missing Files");
                        } else if ("checkingDL".equalsIgnoreCase(state) || "checkingUP".equalsIgnoreCase(state) || "checkingResumeData".equalsIgnoreCase(state)) {
                            data.setStatus("Checking");
                        } else if ("metaDL".equalsIgnoreCase(state)) {
                            data.setStatus("Metadata");
                        } else if ("forcedDL".equalsIgnoreCase(state) || "forcedUP".equalsIgnoreCase(state)) {
                            data.setStatus("Forced");
                        } else {
                            data.setStatus(state);
                        }

                        long eta = parseLong(getJsonValue(torrentData, "eta"));
                        data.setEta(formatEta(eta));

                        double ratio = parseDouble(getJsonValue(torrentData, "ratio"));
                        data.setRatio(ratio);

                        result.add(data);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error fetching torrents: " + e.getMessage());
        }

        return result;
    }

    /**
     * Get transfer information (speeds, data transferred)
     *
     * @return TransferStats object
     */
    public TransferStats getTransferInfo() {
        if (!isLoggedIn) {
            return new TransferStats();
        }

        TransferStats stats = new TransferStats();

        try {
            URL url = new URL(baseUrl + API_PATH + TRANSFER_INFO_PATH);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String response = in.lines().collect(Collectors.joining());

                    Map<String, String> data = parseJson(response);

                    stats.setDownloadSpeed(parseLong(getJsonValue(data, "dl_info_speed")));
                    stats.setUploadSpeed(parseLong(getJsonValue(data, "up_info_speed")));
                    stats.setSessionDownloaded(parseLong(getJsonValue(data, "dl_info_data")));
                    stats.setSessionUploaded(parseLong(getJsonValue(data, "up_info_data")));
                    stats.setAllTimeDownloaded(parseLong(getJsonValue(data, "alltime_dl")));
                    stats.setAllTimeUploaded(parseLong(getJsonValue(data, "alltime_ul")));

                    // Calculate ratio
                    long totalUploaded = stats.getAllTimeUploaded();
                    long totalDownloaded = stats.getAllTimeDownloaded();
                    double ratio = totalDownloaded > 0 ? (double) totalUploaded / totalDownloaded : 0;
                    stats.setRatio(ratio);
                }
            }
        } catch (IOException e) {
            System.err.println("Error fetching transfer info: " + e.getMessage());
        }

        return stats;
    }

    /**
     * Get detailed information about a specific torrent
     *
     * @param hash Torrent hash
     * @return TorrentDetails object
     */
    public TorrentDetails getTorrentDetails(String hash) {
        if (!isLoggedIn || hash == null) {
            return new TorrentDetails();
        }

        TorrentDetails details = new TorrentDetails();
        details.setHash(hash);

        try {
            URL url = new URL(baseUrl + API_PATH + TORRENT_PROPERTIES_PATH + "?hash=" + hash);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String response = in.lines().collect(Collectors.joining());

                    Map<String, String> data = parseJson(response);

                    details.setSavePath(getJsonValue(data, "save_path"));

                    // Convert timestamps to readable dates
                    long creation = parseLong(getJsonValue(data, "creation_date"));
                    details.setCreationDate(formatTimestamp(creation));

                    long addition = parseLong(getJsonValue(data, "addition_date"));
                    details.setAddedOn(formatTimestamp(addition));

                    long lastSeen = parseLong(getJsonValue(data, "last_seen"));
                    details.setLastActivity(formatTimestamp(lastSeen));

                    long downloadLimit = parseLong(getJsonValue(data, "dl_limit"));
                    details.setDownloadLimit(downloadLimit);

                    long uploadLimit = parseLong(getJsonValue(data, "up_limit"));
                    details.setUploadLimit(uploadLimit);

                    // Time active in seconds
                    long timeActive = parseLong(getJsonValue(data, "seeding_time"));
                    details.setTimeActive(formatDuration(timeActive));

                    // Connection count
                    int connections = (int) parseLong(getJsonValue(data, "nb_connections"));
                    details.setConnections(connections);
                }
            }
        } catch (IOException e) {
            System.err.println("Error fetching torrent details: " + e.getMessage());
        }

        return details;
    }

    /**
     * Get list of files for a specific torrent
     *
     * @param hash Torrent hash
     * @return List of FileData objects
     */
    public List<FileData> getTorrentFiles(String hash) {
        if (!isLoggedIn || hash == null) {
            return new ArrayList<>();
        }

        List<FileData> result = new ArrayList<>();

        try {
            URL url = new URL(baseUrl + API_PATH + TORRENT_FILES_PATH + "?hash=" + hash);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String response = in.lines().collect(Collectors.joining());

                    String[] files = response.split("\\},\\{");
                    for (String file : files) {
                        file = file.replace("[{", "").replace("}]", "");

                        Map<String, String> fileData = parseJson(file);

                        FileData data = new FileData();
                        data.setName(getJsonValue(fileData, "name"));
                        data.setSize(formatSize(parseLong(getJsonValue(fileData, "size"))));
                        data.setProgress(parseDouble(getJsonValue(fileData, "progress")));

                        int priority = (int) parseLong(getJsonValue(fileData, "priority"));
                        switch (priority) {
                            case 0:
                                data.setPriority("Not Downloaded");
                                break;
                            case 1:
                                data.setPriority("Normal");
                                break;
                            case 6:
                                data.setPriority("High");
                                break;
                            case 7:
                                data.setPriority("Maximum");
                                break;
                            default:
                                data.setPriority("Normal");
                        }

                        result.add(data);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error fetching torrent files: " + e.getMessage());
        }

        return result;
    }

    /**
     * Get list of trackers for a specific torrent
     *
     * @param hash Torrent hash
     * @return List of TrackerData objects
     */
    public List<TrackerData> getTorrentTrackers(String hash) {
        if (!isLoggedIn || hash == null) {
            return new ArrayList<>();
        }

        List<TrackerData> result = new ArrayList<>();

        try {
            URL url = new URL(baseUrl + API_PATH + TORRENT_TRACKERS_PATH + "?hash=" + hash);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String response = in.lines().collect(Collectors.joining());

                    String[] trackers = response.split("\\},\\{");
                    for (String tracker : trackers) {
                        tracker = tracker.replace("[{", "").replace("}]", "");

                        Map<String, String> trackerData = parseJson(tracker);

                        TrackerData data = new TrackerData();
                        data.setUrl(getJsonValue(trackerData, "url"));

                        int status = (int) parseLong(getJsonValue(trackerData, "status"));
                        switch (status) {
                            case 0:
                                data.setStatus("Working");
                                break;
                            case 1:
                                data.setStatus("Updating");
                                break;
                            case 2:
                                data.setStatus("Not Working");
                                break;
                            case 3:
                                data.setStatus("Disabled");
                                break;
                            default:
                                data.setStatus("Unknown");
                        }

                        data.setTier((int) parseLong(getJsonValue(trackerData, "tier")));
                        data.setPeers((int) parseLong(getJsonValue(trackerData, "num_peers", "0")));
                        data.setSeeds((int) parseLong(getJsonValue(trackerData, "num_seeds", "0")));
                        data.setLeeches((int) parseLong(getJsonValue(trackerData, "num_leeches", "0")));

                        result.add(data);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error fetching torrent trackers: " + e.getMessage());
        }

        return result;
    }

    /**
     * Get list of peers for a specific torrent
     *
     * @param hash Torrent hash
     * @return List of PeerData objects
     */
    public List<PeerData> getTorrentPeers(String hash) {
        if (!isLoggedIn || hash == null) {
            return new ArrayList<>();
        }

        List<PeerData> result = new ArrayList<>();

        try {
            URL url = new URL(baseUrl + API_PATH + TORRENT_PEERS_PATH + "?hash=" + hash);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String response = in.lines().collect(Collectors.joining());

                    // The peers response is more complex, with a nested structure
                    if (response.contains("\"peers\":")) {
                        response = response.substring(response.indexOf("\"peers\":") + 8);
                        if (response.startsWith("{")) {
                            response = response.substring(1);
                        }
                        if (response.endsWith("}")) {
                            response = response.substring(0, response.length() - 1);
                        }

                        String[] peerEntries = response.split("\\},\"");

                        for (String peerEntry : peerEntries) {
                            String peerIp = peerEntry.split("\":\\{")[0].replace("\"", "");
                            String peerData = peerEntry.split("\":\\{")[1];

                            Map<String, String> data = parseJson(peerData);

                            PeerData peer = new PeerData();
                            peer.setAddress(peerIp);
                            peer.setClient(getJsonValue(data, "client", "Unknown"));
                            peer.setProgress(parseDouble(getJsonValue(data, "progress", "0")));
                            peer.setDownloadSpeed(formatSpeed(parseLong(getJsonValue(data, "dl_speed", "0"))));
                            peer.setUploadSpeed(formatSpeed(parseLong(getJsonValue(data, "up_speed", "0"))));

                            // Calculate relevance as a random number between 0 and 1
                            // In a real implementation, this would be based on piece availability
                            peer.setRelevance(random.nextDouble());

                            result.add(peer);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error fetching torrent peers: " + e.getMessage());
        }

        return result;
    }

    /**
     * Pause a torrent
     *
     * @param hash Torrent hash
     * @return true if successful
     */
    public boolean pauseTorrent(String hash) {
        if (!isLoggedIn || hash == null) {
            return false;
        }

        try {
            URL url = new URL(baseUrl + API_PATH + TORRENT_PAUSE_PATH);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            String params = "hashes=" + hash;

            try (OutputStream os = connection.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (IOException e) {
            System.err.println("Error pausing torrent: " + e.getMessage());
        }

        return false;
    }

    /**
     * Resume a torrent
     *
     * @param hash Torrent hash
     * @return true if successful
     */
    public boolean resumeTorrent(String hash) {
        if (!isLoggedIn || hash == null) {
            return false;
        }

        try {
            URL url = new URL(baseUrl + API_PATH + TORRENT_RESUME_PATH);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            String params = "hashes=" + hash;

            try (OutputStream os = connection.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (IOException e) {
            System.err.println("Error resuming torrent: " + e.getMessage());
        }

        return false;
    }

    /**
     * Delete a torrent
     *
     * @param hash        Torrent hash
     * @param deleteFiles Whether to delete files from disk
     * @return true if successful
     */
    public boolean deleteTorrent(String hash, boolean deleteFiles) {
        if (!isLoggedIn || hash == null) {
            return false;
        }

        try {
            URL url = new URL(baseUrl + API_PATH + TORRENT_DELETE_PATH);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            String params = "hashes=" + hash + "&deleteFiles=" + (deleteFiles ? "true" : "false");

            try (OutputStream os = connection.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (IOException e) {
            System.err.println("Error deleting torrent: " + e.getMessage());
        }

        return false;
    }

    /**
     * Add a new torrent
     *
     * @param url URL or magnet link of the torrent
     * @return true if successful
     */
    public boolean addTorrent(String url) {
        if (!isLoggedIn || url == null) {
            return false;
        }

        try {
            URL apiUrl = new URL(baseUrl + API_PATH + TORRENT_ADD_PATH);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            String params = "urls=" + URLEncoder.encode(url, StandardCharsets.UTF_8.toString());

            try (OutputStream os = connection.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (IOException e) {
            System.err.println("Error adding torrent: " + e.getMessage());
        }

        return false;
    }

    /**
     * Set download limit for a torrent
     *
     * @param hash  Torrent hash
     * @param limit Download speed limit in KB/s (0 for unlimited)
     * @return true if successful
     */
    public boolean setTorrentDownloadLimit(String hash, int limit) {
        if (!isLoggedIn || hash == null) {
            return false;
        }

        try {
            URL url = new URL(baseUrl + API_PATH + TORRENT_SET_DOWNLOAD_LIMIT_PATH);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            String params = "hashes=" + hash + "&limit=" + (limit * 1024); // Convert to bytes

            try (OutputStream os = connection.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (IOException e) {
            System.err.println("Error setting download limit: " + e.getMessage());
        }

        return false;
    }

    /**
     * Set upload limit for a torrent
     *
     * @param hash  Torrent hash
     * @param limit Upload speed limit in KB/s (0 for unlimited)
     * @return true if successful
     */
    public boolean setTorrentUploadLimit(String hash, int limit) {
        if (!isLoggedIn || hash == null) {
            return false;
        }

        try {
            URL url = new URL(baseUrl + API_PATH + TORRENT_SET_UPLOAD_LIMIT_PATH);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            String params = "hashes=" + hash + "&limit=" + (limit * 1024); // Convert to bytes

            try (OutputStream os = connection.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (IOException e) {
            System.err.println("Error setting upload limit: " + e.getMessage());
        }

        return false;
    }

    /**
     * Get the qBittorrent API version
     *
     * @return Version string
     */
    public String getApiVersion() {
        if (!isLoggedIn) {
            return "Unknown";
        }

        try {
            URL url = new URL(baseUrl + API_PATH + APP_VERSION_PATH);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    return in.lines().collect(Collectors.joining()).replace("\"", "");
                }
            }
        } catch (IOException e) {
            System.err.println("Error fetching API version: " + e.getMessage());
        }

        return "Unknown";
    }

    private Map<String, String> parseJson(String json) {
        Map<String, String> result = new HashMap<>();

        String[] entries = json.split(",\"");
        for (String entry : entries) {
            String processedEntry = entry.startsWith("\"") ? entry : "\"" + entry;
            String[] keyValue = processedEntry.split("\":\"?|\"?\\s*,\\s*\"?|\"?\\s*\\}");

            if (keyValue.length >= 2) {
                String key = keyValue[0].replaceAll("\"", "").trim();
                String value = keyValue[1].replaceAll("\"$", "").trim();
                result.put(key, value);
            }
        }

        return result;
    }

    private String getJsonValue(Map<String, String> data, String key) {
        return getJsonValue(data, key, "");
    }

    private String getJsonValue(Map<String, String> data, String key, String defaultValue) {
        return data.containsKey(key) ? data.get(key) : defaultValue;
    }

    private long parseLong(String value) {
        try {
            return value == null || value.isEmpty() ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDouble(String value) {
        try {
            return value == null || value.isEmpty() ? 0 : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond == 0) {
            return "0 B/s";
        } else if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B/s";
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.2f KB/s", bytesPerSecond / 1024.0);
        } else {
            return String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024));
        }
    }

    private String formatEta(long seconds) {
        if (seconds < 0) {
            return "∞";
        } else if (seconds == 0) {
            return "Done";
        }

        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(seconds));
        long remainingSeconds = seconds - TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(seconds));

        StringBuilder result = new StringBuilder();
        if (days > 0) {
            result.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            result.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            result.append(minutes).append("m ");
        }
        if (remainingSeconds > 0) {
            result.append(remainingSeconds).append("s");
        }

        return result.toString().trim();
    }

    private String formatDuration(long seconds) {
        if (seconds == 0) {
            return "0";
        }

        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) - TimeUnit.DAYS.toHours(days);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(seconds));
        long remainingSeconds = seconds - TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(seconds));

        StringBuilder result = new StringBuilder();
        if (days > 0) {
            result.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            result.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            result.append(minutes).append("m ");
        }
        if (remainingSeconds > 0) {
            result.append(remainingSeconds).append("s");
        }

        return result.toString().trim();
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "Never";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
        return dateTime.format(dateTimeFormatter);
    }
}

