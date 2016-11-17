package com.cs472;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Config {
    public static final String LOG_DIRECTORY = "logdirectory";
    public static final String NUM_LOG_FILES = "numlogfiles";
    public static final String USERNAME_FILE = "usernamefile";
    public static final String PORT_MODE = "port_mode";
    public static final String PASV_MODE = "pasv_mode";


    private Map<String, String> configParts;
    private static final Map<String, String> defaults;
    static {
        defaults = new HashMap<String, String>();
        defaults.put(NUM_LOG_FILES,"5");
        defaults.put(LOG_DIRECTORY, "/var/spool/log");
        defaults.put(USERNAME_FILE, "ftp.users");
        defaults.put(PORT_MODE, "no");
        defaults.put(PASV_MODE, "yes");
    }


    public Config(String configFile) throws IOException {
        configParts = new HashMap<String, String>();

        BufferedReader br = new BufferedReader(new FileReader(configFile));
        String line;
        while ((line = br.readLine()) != null) {
            parseLine(line);
        }
    }

    private void parseLine(String line) {
        line = line.trim();
        if (line.startsWith("#")) {
            return;
        }
        String[] parts = line.split("=");
        if (parts.length != 2) {
            return;
        }
        configParts.put(parts[0].trim().toLowerCase(), parts[1].trim());
    }

    public String getString(String value) {
        String result = configParts.get(value);
        if (result == null) {
            result = defaults.get(value);
        }
        return result;
    }

    public Integer getInt(String value) {
        try {
            return Integer.parseInt(getString(value));
        }catch(NumberFormatException e) {
            System.out.println("'" + value + "' is not a number.");
            return null;
        }
    }

    public Boolean getBoolean(String value) {
        String result = getString(value);
        if (result==null) return false;
        return result.toLowerCase().equals("yes");
    }
}
