package com.cs472;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.locks.Lock;

/**
 * A simple class for handling logging to a file.
 */
public class Logger {
    private boolean tee = false;
    private final Writer out;
    private Config config;
    private String filename;

    /**
     * Opens the log file for writing.
     * @param outputFile The path to the output file
     * @throws java.io.IOException If there is an error opening the output file
     */
    public Logger(Config config) throws IOException {
        // TODO: Make sure log file is specified.
        String logDirectory =  config.getString(Config.LOG_DIRECTORY);
        if (!new File(logDirectory).isDirectory()) {
            System.out.println("The specified log directory, " + logDirectory + ", does not exist.");
            System.exit(1);
        }
        logDirectory = logDirectory + (logDirectory.endsWith(File.separator)?"":File.separator);
        this.filename = logDirectory + "logfile";
        init(filename, config.getInt(Config.NUM_LOG_FILES));

        this.config = config;
        out = new FileWriter(this.filename, true);
        out.write(getTime() + " (SUCCESS) Opened log file\n");
    }

    private static void init(String filename, int maxDepth) {
        if (maxDepth == 0) {
            return;
        }

        String postFix = filename.contains(".")?filename.split("\\.")[1]:"";
        int postFixNum;
        try {
            postFixNum = postFix.isEmpty() ? -1 : Integer.parseInt(postFix);
        }catch(NumberFormatException e) {
            postFixNum = -1;
        }

        postFix = "." + postFix;

        if(new File(filename).exists()) {
            Path path = Paths.get(filename);
            String newFileName = filename.replace(postFix, "") + String.format(".%03d", (postFixNum+1));
            init(newFileName, maxDepth-1);
            try {
                Files.move(path, Paths.get(newFileName), StandardCopyOption.REPLACE_EXISTING);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            FileWriter writer = new FileWriter(filename);
            writer.write("Test");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Opens the standard output for writing.
     */
    public Logger() {
        out = new OutputStreamWriter(System.out);
    }

    /**
     * Should be called when the Logger is done being used. Closes the log file.
     * @throws java.io.IOException If there is an issue closing the log file.
     */
    public void close() throws IOException {
        out.close();
    }

    /**
     * Writes the msg to the log file with success as the status.
     */
    public void log(String msg) {
        log(msg, false);
    }

    /**
     * Writes the msg to the log file.
     */
    public void log(String msg, boolean error) {
        String time = getTime();
        String status = error?"(ERROR)":"(SUCCESS)";

        if (tee) {
            System.out.print(msg + "\n");
        }

        try {
            String message = time + " " + status + " " + msg + "\n";
            synchronized (out) {
                out.write(message);
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private String getTime() {
        return "9/25/16 20:00:00.0002";
    }
}