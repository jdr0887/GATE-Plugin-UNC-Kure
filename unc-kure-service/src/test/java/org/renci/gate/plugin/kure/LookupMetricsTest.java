package org.renci.gate.plugin.kure;

import static org.junit.Assert.assertTrue;
import static org.renci.jlrm.lsf.LSFJobStatusType.PENDING;
import static org.renci.jlrm.lsf.LSFJobStatusType.RUNNING;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Test;
import org.renci.gate.GlideinMetric;
import org.renci.jlrm.lsf.LSFJobStatusInfo;
import org.renci.jlrm.lsf.ssh.LSFSSHJob;

public class LookupMetricsTest {

    @Test
    public void testLookupMetrics() {
        List<LSFSSHJob> jobCache = new ArrayList<LSFSSHJob>();

        Set<LSFJobStatusInfo> jobStatusSet = new HashSet<LSFJobStatusInfo>();
        Random r = new Random();
        for (int i = 0; i < 30; ++i) {
            jobStatusSet.add(new LSFJobStatusInfo(r.nextInt() + "", RUNNING, "pseq_prod", "asdf"));
        }

        for (int i = 0; i < 10; ++i) {
            jobStatusSet.add(new LSFJobStatusInfo(r.nextInt() + "", PENDING, "pseq_prod", "asdf"));
        }

        for (int i = 0; i < 20; ++i) {
            jobStatusSet.add(new LSFJobStatusInfo(r.nextInt() + "", RUNNING, "week", "asdf"));
        }

        for (int i = 0; i < 6; ++i) {
            jobStatusSet.add(new LSFJobStatusInfo(r.nextInt() + "", PENDING, "week", "asdf"));
        }

        // get unique list of queues
        Set<String> queueSet = new HashSet<String>();
        if (jobStatusSet != null && jobStatusSet.size() > 0) {
            for (LSFJobStatusInfo info : jobStatusSet) {
                queueSet.add(info.getQueue());
            }
            for (LSFSSHJob job : jobCache) {
                queueSet.add(job.getQueueName());
            }
        }

        Set<String> alreadyTalliedJobIdSet = new HashSet<String>();
        Map<String, GlideinMetric> jobTallyMap = new HashMap<String, GlideinMetric>();

        if (jobStatusSet != null && jobStatusSet.size() > 0) {
            for (LSFJobStatusInfo info : jobStatusSet) {
                if (!jobTallyMap.containsKey(info.getQueue())) {
                    jobTallyMap.put(info.getQueue(), new GlideinMetric(0, 0, info.getQueue()));
                }
                alreadyTalliedJobIdSet.add(info.getJobId());
            }

            for (LSFJobStatusInfo info : jobStatusSet) {
                GlideinMetric metric = jobTallyMap.get(info.getQueue());
                switch (info.getType()) {
                    case PENDING:
                        metric.setPending(metric.getPending() + 1);
                        break;
                    case RUNNING:
                        metric.setRunning(metric.getRunning() + 1);
                        break;
                }
            }
        }

        LSFSSHJob job = new LSFSSHJob("test", new File("/bin/hostname"));
        job.setId(r.nextInt() + "");
        job.setQueueName("pseq_prod");
        jobCache.add(job);

        Iterator<LSFSSHJob> jobCacheIter = jobCache.iterator();
        while (jobCacheIter.hasNext()) {
            LSFSSHJob nextJob = jobCacheIter.next();
            for (LSFJobStatusInfo info : jobStatusSet) {
                if (!alreadyTalliedJobIdSet.contains(nextJob.getId()) && nextJob.getId().equals(info.getJobId())) {
                    GlideinMetric metric = jobTallyMap.get(info.getQueue());
                    switch (info.getType()) {
                        case PENDING:
                            metric.setPending(metric.getPending() + 1);
                            break;
                        case RUNNING:
                            metric.setRunning(metric.getRunning() + 1);
                            break;
                        case EXIT:
                        case UNKNOWN:
                        case ZOMBIE:
                        case DONE:
                            jobCacheIter.remove();
                            break;
                        case SUSPENDED_BY_SYSTEM:
                        case SUSPENDED_BY_USER:
                        case SUSPENDED_FROM_PENDING:
                        default:
                            break;
                    }
                }
            }
        }

        int totalRunningGlideinJobs = 0;
        int totalPendingGlideinJobs = 0;

        for (String queue : queueSet) {
            GlideinMetric metrics = jobTallyMap.get(queue);
            totalRunningGlideinJobs += metrics.getRunning();
            totalPendingGlideinJobs += metrics.getPending();
        }

        int totalSiteJobs = totalRunningGlideinJobs + totalPendingGlideinJobs;
        assertTrue(totalSiteJobs == 66);

    }
}
