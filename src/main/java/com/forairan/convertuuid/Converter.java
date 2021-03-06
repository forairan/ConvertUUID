/*
 * Copyright (C) 2013 Devin Ryan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.forairan.convertuuid;

import com.mojang.api.profiles.HttpProfileRepository;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Converter handles spawning {@link ConversionJob}s and manages the results
 * of the conversion.
 *
 * @author Devin Ryan
 */
public class Converter implements Runnable {

    public static final ConcurrentHashMap<String, String> results = new ConcurrentHashMap<String, String>();
    private final List<ConversionJob> jobs = new ArrayList<ConversionJob>();
    private final List<String> usernames;
    private final int maxJobs;
    private final HttpProfileRepository repository = new HttpProfileRepository();
    private final PrintWriter output;
    private long lastUpdate = 0L;
    private int countComplete = 0;

    public Converter(List<String> usernames, PrintWriter output, int maxJobs) {
        this.usernames = usernames;
        this.output = output;
        this.maxJobs = maxJobs;
    }

    public void run() {
        long startTime = System.currentTimeMillis();
        lastUpdate = startTime;
        System.out.println("Converter spawned, " + usernames.size() + " usernames to process.");

        while (usernames.size() > 0) {
            if (jobs.size() < maxJobs) {
                // There's room for more jobs, spawn them accordingly
                for (int i = 0; i < maxJobs - jobs.size(); i++) {
                    if (usernames.size() > 0) {
                        String username = usernames.remove(0);
                        ConversionJob job = new ConversionJob(this, username);
                        jobs.add(job);
                        new Thread(job, "Conversion job: " + username).start();
                    }
                }
            }

            // Clean up any complete jobs
            Iterator<ConversionJob> i = jobs.iterator();
            while (i.hasNext()) {
                ConversionJob job = i.next();
                if (job.isComplete()) {
                    results.put(job.getUsername(), job.getUUID());
                    countComplete++;
                    i.remove();
                }
            }
            i = null;

            // Display progress
            if (System.currentTimeMillis() - lastUpdate >= 1000) {
                lastUpdate = System.currentTimeMillis();
                System.out.println("Progress: " + countComplete + " conversions completed.");
            }
            
            // Sleep a bit
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {

            }
        }

        System.out.println("Conversion complete, preparing results...");
        Properties props = new Properties();
        for (Entry<String, String> entry : results.entrySet()) {
            props.put(entry.getKey(), entry.getValue());
        }

        try {
            props.store(output, "Generated by ConvertUUID");
        } catch (IOException ex) {
            System.err.println("Error storing results!");
            ex.printStackTrace();
        }
        
        System.out.println("Done in " + (System.currentTimeMillis() - startTime) + "ms.");
    }

    public HttpProfileRepository getRepository() {
        return repository;
    }

}
