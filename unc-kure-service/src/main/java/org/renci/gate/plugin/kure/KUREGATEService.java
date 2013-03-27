package org.renci.gate.plugin.kure;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.renci.gate.AbstractGATEService;
import org.renci.gate.GlideinMetric;
import org.renci.jlrm.JLRMException;
import org.renci.jlrm.Queue;
import org.renci.jlrm.lsf.LSFJobStatusInfo;
import org.renci.jlrm.lsf.LSFJobStatusType;
import org.renci.jlrm.lsf.ssh.LSFSSHFactory;
import org.renci.jlrm.lsf.ssh.LSFSSHJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author jdr0887
 */
public class KUREGATEService extends AbstractGATEService {

    private final Logger logger = LoggerFactory.getLogger(KUREGATEService.class);

    private final List<LSFSSHJob> jobCache = new ArrayList<LSFSSHJob>();

    public KUREGATEService() {
        super();
    }

    @Override
    public Map<String, GlideinMetric> lookupMetrics() {
        logger.info("ENTERING lookupMetrics()");
        Map<String, GlideinMetric> metricsMap = new HashMap<String, GlideinMetric>();

        LSFSSHFactory lsfSSHFactory = LSFSSHFactory.getInstance(getSite());

        try {

            Set<LSFJobStatusInfo> jobStatusSet = lsfSSHFactory.lookupStatus(jobCache);

            logger.debug("jobStatusSet.size(): {}", jobStatusSet.size());

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

            if (jobStatusSet != null && jobStatusSet.size() > 0) {
                for (LSFJobStatusInfo info : jobStatusSet) {
                    if (metricsMap.containsKey(info.getQueue())) {
                        continue;
                    }
                    if (!"glidein".equals(info.getJobName())) {
                        continue;
                    }
                    metricsMap.put(info.getQueue(), new GlideinMetric(0, 0, info.getQueue()));
                    alreadyTalliedJobIdSet.add(info.getJobId());
                }

                for (LSFJobStatusInfo info : jobStatusSet) {

                    if (!"glidein".equals(info.getJobName())) {
                        continue;
                    }

                    GlideinMetric metric = metricsMap.get(info.getQueue());
                    switch (info.getType()) {
                        case PENDING:
                            metric.setPending(metric.getPending() + 1);
                            break;
                        case RUNNING:
                            metric.setRunning(metric.getRunning() + 1);
                            break;
                    }
                    logger.debug("metric: {}", metric.toString());
                }
            }

            Iterator<LSFSSHJob> jobCacheIter = jobCache.iterator();
            while (jobCacheIter.hasNext()) {
                LSFSSHJob nextJob = jobCacheIter.next();
                for (LSFJobStatusInfo info : jobStatusSet) {

                    if (!nextJob.getName().equals(info.getJobName())) {
                        continue;
                    }

                    if (!alreadyTalliedJobIdSet.contains(nextJob.getId()) && nextJob.getId().equals(info.getJobId())) {
                        GlideinMetric metric = metricsMap.get(info.getQueue());
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
                        logger.debug("metric: {}", metric.toString());
                    }
                }
            }

        } catch (JLRMException e) {
            e.printStackTrace();
            logger.error("Error:", e);
        }
        return metricsMap;
    }

    @Override
    public void createGlidein(Queue queue) {
        logger.info("ENTERING createGlidein(Queue)");

        if (StringUtils.isNotEmpty(getActiveQueues()) && !getActiveQueues().contains(queue.getName())) {
            logger.warn("queue name is not in active queue list...see etc/org.renci.gate.plugin.kure.cfg");
            return;
        }

        File submitDir = new File("/tmp", System.getProperty("user.name"));
        submitDir.mkdirs();
        LSFSSHJob job = null;

        try {
            logger.info("siteInfo: {}", getSite());
            logger.info("queueInfo: {}", queue);
            LSFSSHFactory lsfSSHFactory = LSFSSHFactory.getInstance(getSite());
            String hostAllow = "*.its.unc.edu";
            job = lsfSSHFactory
                    .submitGlidein(submitDir, getCollectorHost(), queue, 40, "glidein", hostAllow, hostAllow);
            if (job != null && StringUtils.isNotEmpty(job.getId())) {
                logger.info("job.getId(): {}", job.getId());
                jobCache.add(job);
            }
        } catch (JLRMException e) {
            logger.error("Problem submitting: ", e);
        }
    }

    @Override
    public void deleteGlidein(Queue queue) {
        logger.info("ENTERING deleteGlidein(QueueInfo)");
        if (jobCache.size() > 0) {
            try {
                logger.info("siteInfo: {}", getSite());
                logger.info("queueInfo: {}", queue);
                LSFSSHFactory lsfSSHFactory = LSFSSHFactory.getInstance(getSite());
                LSFSSHJob job = jobCache.get(0);
                logger.info("job: {}", job.toString());
                lsfSSHFactory.killGlidein(job.getId());
                jobCache.remove(0);
            } catch (JLRMException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void deletePendingGlideins() {
        logger.info("ENTERING deletePendingGlideins()");
        try {
            LSFSSHFactory lsfSSHFactory = LSFSSHFactory.getInstance(getSite());
            Set<LSFJobStatusInfo> jobStatusSet = lsfSSHFactory.lookupStatus(jobCache);
            for (LSFJobStatusInfo info : jobStatusSet) {
                if (info.getType().equals(LSFJobStatusType.PENDING)) {
                    lsfSSHFactory.killGlidein(info.getJobId());
                }
                try {
                    // throttle the deleteGlidein calls such that SSH doesn't complain
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (JLRMException e) {
            e.printStackTrace();
        }
    }

}
