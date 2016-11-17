package com.cs472;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class FTPServer {
    private Logger logger;
    private String ip;
    private int port;
    private int sslport;
    private Config config;

    // Maps a username to a password
    private Map<String, String> validUsers = new HashMap<String, String>();
    private Map<String, Integer> activeUsers = new HashMap<String, Integer>();

    /**
     * Creates anew ftp server at localhost:port, and logs things to outputFile
     */
    public FTPServer(int port, int sslport, Config config) {
        this.port = port;
        this.sslport = sslport;
        this.config = config;

        // Get the localhost ip address for logging.
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        try {
            this.logger = new Logger(config);
        } catch (IOException e) {
            this.logger = new Logger();
            logger.log("Could not open logfile for writing, defaulting to standard out.", true);
        }
        readUsers();
    }

    private void readUsers() {
        String userFilePath = config.getString(Config.USERNAME_FILE);
        File userFile = new File(userFilePath);
        if (!userFile.exists()) {
            logger.log(userFile + " does not exist.");
            System.exit(1);
        }else if (!userFile.canRead()) {
            logger.log("User does not have permissions to read " + userFilePath);
            System.exit(1);
        }

        try {
            BufferedReader fileReader = new BufferedReader(new FileReader(userFilePath));
            String line;
            while ((line = fileReader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    validUsers.put(parts[0].trim(), parts[1].trim());
                }
            }
            if (validUsers.size() == 0) {
                logger.log("At least one user must be specified in " + userFilePath);
                System.exit(1);
            }
        }catch (IOException e) {
            logger.log("Could not read " + userFilePath);
            System.exit(1);
        }
    }

    /**
     * Causes the server to start listening on port.
     */
    public void start() {
        logger.log("Opening connection on " + ip + ":" + port);
        new Thread(ConnectionHandler).start();
        new Thread(SSLConnectionHandler).start();
    }

    /**
     * Used by background thread to listen for new incoming connections
     */
    private Runnable ConnectionHandler = new Runnable() {
        @Override
        public void run() {
            try {
                ServerSocket incomingConnections = new ServerSocket(port);
                while (true) { // Run forever...
                    Socket socket = incomingConnections.accept();
                    String ip = socket.getRemoteSocketAddress().toString();
                    Integer usersWithIP = activeUsers.get(ip);
                    if (usersWithIP == null) {
                        activeUsers.put(ip, 0);
                        usersWithIP = 0;
                    }
                    if (usersWithIP > 10) {
                        return;
                    }

                    new Thread(new SessionHandler(socket, logger, validUsers, config, activeUsers)).start();
                }
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    /**
     * Used by background thread to listen for new incoming connections
     */
    private Runnable SSLConnectionHandler = new Runnable() {
        @Override
        public void run() {
            try {
                SSLServerSocketFactory factory=(SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                SSLServerSocket sslserversocket=(SSLServerSocket) factory.createServerSocket(sslport);
                while (true) { // Run forever...
                    Socket socket = sslserversocket.accept();
                    String ip = socket.getRemoteSocketAddress().toString();
                    Integer usersWithIP = activeUsers.get(ip);
                    if (usersWithIP == null) {
                        activeUsers.put(ip, 0);
                        usersWithIP = 0;
                    }
                    if (usersWithIP > 10) {
                        return;
                    }

                    new Thread(new SessionHandler(socket, logger, validUsers, config, activeUsers)).start();
                }
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
}
