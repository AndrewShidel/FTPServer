package com.cs472;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Timer;

/**
 * Handles interactions with a single user.
 */
public class SessionHandler implements Runnable {
    private final static long TIMEOUT = 1000*60*10; // Timeout after 10 minutes
    private final Map<String, Integer> activeUsers;
    private final String remoteIP;
    private boolean running = false; // Is server currently responding to this user.
    private String username;

    // All global states that the user can be in.
    private enum State {NONE, USER, PASS, DATA}
    private State currentState;

    private final Logger logger;
    private final Config config;
    private final Socket socket;
    private ServerSocket dataChannel;
    private Socket dataChannelSocket;
    private BufferedReader in = null;
    private OutputStreamWriter out = null;

    // Current working directory.
    private File directory = new File(System.getProperty("user.dir"));
    private Timer timoutTimer;
    private Map<String, String> validUsers;

    public SessionHandler(Socket socket, Logger logger, Map<String, String> valisUsers, Config config, Map<String, Integer> activeUsers) {
        this.currentState = State.NONE;
        this.socket = socket;
        this.logger = logger;
        this.running = true;
        this.validUsers = valisUsers;
        this.config = config;
        this.remoteIP = socket.getRemoteSocketAddress().toString();
        this.activeUsers = activeUsers;

        try {
            out = new OutputStreamWriter(socket.getOutputStream());
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        timoutTimer = new Timer();
        resetTimer();
    }

    @Override
    public void run() {
        logger.log(remoteIP + " has connected.");
        activeUsers.put(remoteIP, activeUsers.get(remoteIP)+1);

        logger.log("A new user joined the server.");
        writeToControl("220 Welcome to Andrew Shidel's FTP Server!\n");

        String line;
        while (running && (line = readlnFromControl()) != null) {
            resetTimer();

            String lineParsed = line.toLowerCase().trim();
            if (lineParsed.startsWith("user ")) {
                handleUser(line.split(" ")[1]);
            } else if (lineParsed.startsWith("pass ")) {
                handlePass(line.split(" ")[1]);
            } else if (lineParsed.startsWith("cwd ")) {
                cwd(line.split(" ")[1]);
            } else if (lineParsed.startsWith("cdup")) {
                cdup();
            } else if (lineParsed.startsWith("pasv")) {
                pasv();
            } else if (lineParsed.startsWith("port ")) {
                port(lineParsed.split(" ")[1]);
            } else if (lineParsed.startsWith("eprt ")) {
                eprt(lineParsed.split(" ")[1]);
            } else if (lineParsed.startsWith("epsv")) {
                epsv();
            } else if (lineParsed.startsWith("retr ")) {
                retr(line.split(" ")[1]);
            } else if (lineParsed.startsWith("pwd")) {
                pwd();
            } else if (lineParsed.startsWith("list")) {
                list();
            } else if (lineParsed.startsWith("help")) {
                help();
            }else if (lineParsed.startsWith("quit")) {
                quit();
            }else if (lineParsed.startsWith("type")) {
                writelnToControl("200 Type set to I");
            }else {
                writelnToControl("502 Command not implemented.");
            }
        }
        logger.log(remoteIP + " has disconnected.");
        activeUsers.put(remoteIP, activeUsers.get(remoteIP)-1);
    }

    /**
     * Retrieves a file from working directory, and sends it to client.
     * @param filename The path to the file to get
     */
    private void retr(String filename) {
        if (checkDataAuth()) {
            File file = new File(directory, filename);
            if (file.exists() && file.isFile()) {
                writelnToControl("150 Opening BINARY mode data connection for " + filename + " (" + file.length() + ").");
            } else {
                writelnToControl("550 Failed to open file.");
            }
            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                BufferedOutputStream outputStream = new BufferedOutputStream(dataChannelSocket.getOutputStream());
                byte[] buffer = new byte[(int)file.length()];
                while(fileInputStream.read(buffer) > 0) {
                    outputStream.write(buffer);
                }
                fileInputStream.close();
                outputStream.flush();
                closeData();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                logger.log(e.getMessage(), true);
            } catch (IOException e) {
                e.printStackTrace();
                logger.log(e.getMessage(), true);
            }
            writelnToControl("226 Transfer complete.");
        }
    }

    /**
     * Sends a directory listing
     */
    private void list() {
        if (checkDataAuth()) {
            writelnToControl("150 Here comes the directory listing.");
            try {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(dataChannelSocket.getOutputStream()));
                writer.write(listDir());
                /*String[] list = directory.list();
                for (String file : list) {
                    //writer.write(file + "\n");
                }*/
                writer.flush();
                closeData();
            } catch (Exception e) {
                logger.log(e.getMessage(), true);
                writelnToControl("426 Directory send failed. " + e.getMessage());
                currentState = State.PASS;
                return;
            }
            writelnToControl("226 Directory send OK.");
        }
    }

    private String listDir() throws IOException, InterruptedException {
        Runtime r = Runtime.getRuntime();
        String command = System.getProperty("os.name").toLowerCase().contains("windows")?"dir":"ls -l";
        Process p = r.exec(command + " " + directory.getAbsolutePath());
        p.waitFor();
        BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = "";
        String result = "";
        while ((line = b.readLine()) != null) {
            result += line + "\n";
        }
        b.close();
        return result;
    }

    /**
     * Closes the data connections.
     */
    private void closeData() {
        try {
            if (dataChannelSocket != null) {
                dataChannelSocket.close();
            }
            if (dataChannel != null) {
                dataChannel.close();
            }
        }catch (IOException e) {
            logger.log("Unable to close data connections on quit. " + e.getMessage(), true);
        }
        currentState = State.PASS;
    }

    /**
     * Starts extended passive data transfer.
     */
    private void epsv() {
        pasv(true);
    }

    /**
     * Starts a passive data transfer
     */
    private void pasv() {
        pasv(false);
    }

    /**
     * Starts a passive data trasfer
     * @param useExtended True if epsv is being used.
     */
    private void pasv(boolean useExtended) {
        if (!config.getBoolean(Config.PASV_MODE)) {
            logger.log("User attempted to use disabled feature PASV/EPSV.", true);
            writelnToControl("502 Command not implemented.");
            return;
        }

        closeData();
        if (checkBasicAuth()) {
            try {
                dataChannel = new ServerSocket(0);
            } catch (IOException e) {
                writelnToControl("425 Error opening new port on server");
                logger.log("Could not open new port on server.", true);
                return;
            }
            int port = dataChannel.getLocalPort();

            String ipPortStr;
            if (!useExtended) {
                byte[] ip = dataChannel.getInetAddress().getAddress();
                ipPortStr = ip[0] + "," + ip[1] + "," + ip[2] + "," + ip[3] + "," +
                        (port >>> (3 * 8)) + "," + (port >>> (4 * 8));
            }else{
                ipPortStr = "|||" + port + "|";
            }
            writelnToControl((useExtended?229:227) + " Entering " + (useExtended?"extended":"") + " Passive Mode (" + ipPortStr + ").");

            // Run in new thread to avoid blocking connection port
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!dataChannel.isClosed()) {
                            dataChannelSocket = dataChannel.accept();
                        }
                    } catch (IOException e) {
                        return;
                    }
                }
            }).start();


            currentState = State.DATA;
        }
    }

    /**
     * Start an extended port data transfer.
     * @param clientPath The locator string specified by the client
     */
    private void eprt(String clientPath) {
        port(clientPath, true);
    }

    /**
     * Start a PORT data transfer.
     * @param clientPath The locator string specified by the client
     */
    private void port(String clientPath) {
        port(clientPath, false);
    }

    /**
     * Start a PORT data transfer
     * @param clientPath The locator string specified by the client
     * @param useExtended True if eprt is used.
     */
    private void port(String clientPath, boolean useExtended) {
        if (!config.getBoolean(Config.PORT_MODE)) {
            logger.log("User attempted to use disabled feature PORT/EPRT.", true);
            writelnToControl("502 Command not implemented.");
            return;
        }

        closeData();
        if (checkBasicAuth()) {
            String delim;
            if (useExtended) {
                delim = ""+clientPath.charAt(0);
                if (delim.equals("|")) delim = "\\|";
                clientPath = clientPath.substring(1);
            }else{
                delim = ",";
            }

            String[] parts = clientPath.split(delim);
            String ip;
            if (useExtended) {
                ip = parts[1];
            }else {
                ip = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
            }
            int port;
            if (useExtended) {
                port = Integer.parseInt(parts[2]);
            }else {
                port = Integer.parseInt(parts[4]) * 256 +
                        Integer.parseInt(parts[5]);
            }

            try {
                dataChannelSocket = new Socket(ip, port);
            } catch (IOException e) {
                logger.log("Could not open a socket at " + ip + ":" + port + ".", true);
                return;
            }

            if (useExtended) {
                writelnToControl("200 EPRT command successful. Consider using EPSV.");
            }else {
                writelnToControl("200 PORT command successful. Consider using PASV.");
            }

            currentState = State.DATA;
        }
    }

    /**
     * Checks if client has active data connection.
     * @return
     */
    private boolean checkDataAuth() {
        if (currentState == State.NONE || currentState == State.USER) {
            writelnToControl("331 Please specify the password.");
            return false;
        } else if (currentState == State.PASS) {
            writelnToControl("425 Use PORT or PASV first.");
            return false;
        } else {
            return true;
        }
    }

    /**
     * Checks if client is logged in.
     * @return
     */
    private boolean checkBasicAuth() {
        if (currentState == State.NONE || currentState == State.USER) {
            writelnToControl("530 Please login with USER and PASS.");
            return false;
        } else {
            return true;
        }
    }

    /**
     * Prints the working directory.
     */
    private void pwd() {
        if (checkBasicAuth()) {
            try {
                writelnToControl("257 \"" + directory.getCanonicalPath() + "\" is the current directory");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Go up one directory
     */
    private void cdup() {
        cwd("../");
    }

    /**
     * Go to directory
     * @param path Directory to change to.
     */
    private void cwd(String path) {
        if (checkBasicAuth()) {
            File tempDirectory = new File(path);
            if (tempDirectory.isDirectory() && tempDirectory.isAbsolute()) { // Check for abs path
                directory = tempDirectory;
                writelnToControl("250 Directory successfully changed.");
            }else if (tempDirectory.isAbsolute()) {
                writelnToControl("550 Failed to change directory.");
            } else{ // Handle relative
                tempDirectory = new File(directory, path);
                if (tempDirectory.exists()) {
                    directory = tempDirectory;
                    try {
                        directory = directory.getCanonicalFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    writelnToControl("250 Directory successfully changed.");
                }else{
                    writelnToControl("550 Failed to change directory.");
                }
            }
        }
    }

    /**
     * Sends a help message to the client.
     */
    private void help() {
        writelnToControl("Valid Commands: USER, PASS, CWD, CDUP, QUIT, PASV, EPSV, PORT, EPRT, RETR, PWD, LIST, HELP");
    }

    /**
     * Handles the USER command.
     * @param user The username.
     */
    private void handleUser(String user) {
        this.username = user;
        currentState = State.USER;

        writelnToControl("331 Please specify the password.");
    }

    /**
     * Handles the PASS command
     * @param pass The password.
     */
    private void handlePass(String pass) {
        if (currentState != State.USER) {
            writelnToControl("503 Login with USER first.");
            return;
        }

        if (validUsers.get(username).equals(pass)) {
            writelnToControl("230 Password Accepted.");
            currentState = State.PASS;
            logger.log("User " + username + " is logged in.");
        }else{
            logger.log("User " + username + " entered an incorrect password.");
            writelnToControl("530 Login incorrect.");
            currentState = State.USER;
        }
    }

    /**
     * Quits the current session.
     */
    private void quit() {
        running = false;
        closeData();
        writelnToControl("221 Goodbye.");
    }

    /**
     * Resets the timeout timer.
     */
    private void resetTimer() {
        timoutTimer.cancel();
        timoutTimer = new Timer();
        timoutTimer.schedule(new TimeoutTask(), TIMEOUT);
    }

    /**
     * Run when the timeout occurs.
     */
    private class TimeoutTask extends TimerTask {
        @Override
        public void run() {
            running = true;
        }
    }

    /**
     * Writes a line to the client.
     * @param msg
     */
    private void writelnToControl(String msg) {
        writeToControl(msg + "\n");
    }

    /**
     * Writes a string to the client.
     * @param msg
     */
    private void writeToControl(String msg) {
        try {
            logger.log("Sending \"" + msg.replace("\n", "") + "\" to " + username + ".");
            out.write(msg);
            out.flush();
        } catch (IOException e) {
            logger.log("Unable to write to: " + socket.toString() + "\n\t" + e.getMessage(), true);
        }
    }

    /**
     * Reads a line from the client.
     * @return
     */
    private String readlnFromControl() {
        try {
            String line = in.readLine();
            logger.log("Received \"" + line + "\" from " + username + ".");
            return line;
        } catch (IOException e) {
            logger.log("Error reading data from from: " + socket.toString(), true);
            return null;
        }
    }
}

