package com.cs472;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Contains main function, and handles reading arguments, and starting the server.
 */
public class Main {

    public static void main(String[] args) {
        // Check for correct number of arguments
        if (args.length < 2) {
            System.out.println("Not enough arguments specified.");
            printUsage();
            return;
        }

        // Get the port
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch(NumberFormatException e) {
            System.out.println(args[0] + " is not a valid port number.");
            return;
        }

        // Get the port
        int sslport;
        try {
            sslport = Integer.parseInt(args[1]);
        } catch(NumberFormatException e) {
            System.out.println(args[1] + " is not a valid port number.");
            return;
        }


        String configPath = "ftpserverd.conf";
        Config config;
        try {
            config = new Config(configPath);
        }catch (FileNotFoundException e) {
            System.out.println("Could not find the file: " + configPath);
            return;
        } catch (IOException e) {
            System.out.println("Error reading config file: " + e.getMessage());
            return;
        }

        // Create and start the FTP server.
        FTPServer server = new FTPServer(port, sslport, config);
        server.start();
    }

    /**
     * Print the usage message for when used incorrectly.
     */
    private static void printUsage() {
        System.out.println("Usage: ./FTPClient [port] [ssl port]");
    }
}
