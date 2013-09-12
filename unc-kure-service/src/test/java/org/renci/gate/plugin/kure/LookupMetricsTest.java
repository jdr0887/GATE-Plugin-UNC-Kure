package org.renci.gate.plugin.kure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.renci.gate.GlideinMetric;
import org.renci.jlrm.Site;
import org.renci.jlrm.lsf.LSFJobStatusInfo;
import org.renci.jlrm.lsf.ssh.LSFSSHLookupStatusCallable;

public class LookupMetricsTest {

    @Test
    public void testLookupMetrics() {
        Map<String, GlideinMetric> metricsMap = new HashMap<String, GlideinMetric>();

        try {

            Site site = new Site();
            site.setName("Kure");
            site.setProject("TCGA");
            site.setUsername("rc_renci.svc");
            site.setSubmitHost("biodev1.its.unc.edu");

            LSFSSHLookupStatusCallable callable = new LSFSSHLookupStatusCallable(site);
            Set<LSFJobStatusInfo> jobStatusSet = Executors.newSingleThreadExecutor().submit(callable).get();

            if (jobStatusSet != null && jobStatusSet.size() > 0) {

                for (LSFJobStatusInfo info : jobStatusSet) {
                    if (metricsMap.containsKey(info.getQueue())) {
                        continue;
                    }
                    if (!"glidein".equals(info.getJobName())) {
                        continue;
                    }
                    metricsMap.put(info.getQueue(), new GlideinMetric(site.getName(), info.getQueue(), 0, 0));
                }

                for (LSFJobStatusInfo info : jobStatusSet) {

                    if (!"glidein".equals(info.getJobName())) {
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

}
