package org.renci.gate.plugin.kure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.renci.gate.GlideinMetric;
import org.renci.jlrm.Site;
import org.renci.jlrm.lsf.LSFJobStatusInfo;
import org.renci.jlrm.lsf.ssh.LSFSSHLookupStatusCallable;

public class LookupStatusTask implements Runnable {

    @Override
    public void run() {

        Map<String, GlideinMetric> metricsMap = new HashMap<String, GlideinMetric>();

        try {

            Site site = new Site();
            site.setName("Kure");
            site.setProject("TCGA");
            site.setUsername("rc_renci.svc");
            site.setSubmitHost("biodev1.its.unc.edu");

            LSFSSHLookupStatusCallable callable = new LSFSSHLookupStatusCallable(site);
            Set<LSFJobStatusInfo> jobStatusSet = Executors.newSingleThreadExecutor().submit(callable).get();

            // get unique list of queues
            Set<String> queueSet = new HashSet<String>();
            if (jobStatusSet != null && jobStatusSet.size() > 0) {
                for (LSFJobStatusInfo info : jobStatusSet) {
                    if (!queueSet.contains(info.getQueue())) {
                        queueSet.add(info.getQueue());
                    }
                }

                for (LSFJobStatusInfo info : jobStatusSet) {
                    if (metricsMap.containsKey(info.getQueue())) {
                        continue;
                    }
                    if (!info.getJobName().contains("glidein")) {
                        continue;
                    }
                    metricsMap.put(info.getQueue(), new GlideinMetric(site.getName(), info.getQueue(), 0, 0));
                }

                for (LSFJobStatusInfo info : jobStatusSet) {

                    if (!info.getJobName().contains("glidein")) {
                        continue;
                    }

                    switch (info.getType()) {
                        case PENDING:
                            metricsMap.get(info.getQueue()).incrementPending();
                            break;
                        case RUNNING:
                            metricsMap.get(info.getQueue()).incrementRunning();
                            break;
                    }
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        for (String key : metricsMap.keySet()) {
            GlideinMetric metric = metricsMap.get(key);
            System.out.println(metric.toString());
        }

    }

    public static void main(String[] args) {
        try {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(new LookupStatusTask(), 5, 15, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
