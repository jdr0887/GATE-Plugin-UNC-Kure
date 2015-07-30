package org.renci.gate.commands.kure;

import java.io.File;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.AbstractAction;
import org.renci.jlrm.JLRMException;
import org.renci.jlrm.Queue;
import org.renci.jlrm.Site;
import org.renci.jlrm.lsf.ssh.LSFSSHJob;
import org.renci.jlrm.lsf.ssh.LSFSSHSubmitCondorGlideinCallable;

@Command(scope = "unc-kure", name = "create-glidein", description = "Create Glidein")
public class CreateKureGlideinAction extends AbstractAction {

    @Option(name = "--username", required = true, multiValued = false)
    private String username;

    @Option(name = "--submitHost", required = true, multiValued = false)
    private String submitHost;

    @Option(name = "--queueName", required = true, multiValued = false)
    private String queueName;

    @Option(name = "--collectorHost", required = true, multiValued = false)
    private String collectorHost;

    @Option(name = "--runTime", required = false, multiValued = false)
    private Long runTime = 5760L;

    public CreateKureGlideinAction() {
        super();
    }

    @Override
    public Object doExecute() {

        Site site = new Site();
        site.setName("Kure");
        site.setSubmitHost(submitHost);
        site.setUsername(username);

        Queue queue = new Queue();
        queue.setName(queueName);
        queue.setRunTime(runTime);
        queue.setNumberOfProcessors(8);

        File submitDir = new File("/tmp");

        try {

            LSFSSHSubmitCondorGlideinCallable callable = new LSFSSHSubmitCondorGlideinCallable();
            callable.setCollectorHost(collectorHost);
            callable.setUsername(System.getProperty("user.name"));
            callable.setSite(site);
            callable.setJobName(String.format("glidein-%s", site.getName().toLowerCase()));
            callable.setQueue(queue);
            callable.setSubmitDir(submitDir);
            callable.setRequiredMemory(40);
            callable.setHostAllowRead("*.unc.edu");
            callable.setHostAllowWrite("*.unc.edu");

            LSFSSHJob job = callable.call();
            System.out.println(job.getId());

        } catch (JLRMException e) {
            e.printStackTrace();
        }

        return null;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSubmitHost() {
        return submitHost;
    }

    public void setSubmitHost(String submitHost) {
        this.submitHost = submitHost;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getCollectorHost() {
        return collectorHost;
    }

    public void setCollectorHost(String collectorHost) {
        this.collectorHost = collectorHost;
    }

    public Long getRunTime() {
        return runTime;
    }

    public void setRunTime(Long runTime) {
        this.runTime = runTime;
    }

}
