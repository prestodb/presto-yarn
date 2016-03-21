/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.teradata.presto.yarn.test.fulfillment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.teradata.presto.yarn.test.utils.NodeSshUtils;
import com.teradata.tempto.Requirement;
import com.teradata.tempto.context.State;
import com.teradata.tempto.fulfillment.RequirementFulfiller;
import com.teradata.tempto.fulfillment.TestStatus;
import com.teradata.tempto.ssh.SshClient;
import com.teradata.tempto.ssh.SshClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.teradata.presto.yarn.test.PrestoCluster.COORDINATOR_COMPONENT;
import static com.teradata.presto.yarn.test.PrestoCluster.WORKER_COMPONENT;
import static com.teradata.presto.yarn.test.slider.Slider.LOCAL_CONF_DIR;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@RequirementFulfiller.AutoSuiteLevelFulfiller(priority = 1)
public class PrerequisitesClusterFulfiller
        implements RequirementFulfiller
{
    private static final Logger log = LoggerFactory.getLogger(PrerequisitesClusterFulfiller.class);
    private static final String REMOTE_HADOOP_CONF_DIR = "/etc/hadoop/conf/";

    @Inject
    @Named("cluster.master")
    private String master;
    @Inject
    @Named("cluster.slaves")
    private List<String> slaves;
    @Inject
    @Named("cluster.prepared")
    private boolean prepared;
    @Inject
    @Named("ssh.roles.yarn.password")
    private String yarnPassword;

    private final SshClientFactory sshClientFactory;
    private final NodeSshUtils nodeSshUtils;

    @Inject
    public PrerequisitesClusterFulfiller(SshClientFactory sshClientFactory, @Named("yarn") SshClient yarnSshClient)
    {
        this.sshClientFactory = sshClientFactory;
        this.nodeSshUtils = new NodeSshUtils(sshClientFactory, yarnSshClient);
    }

    @Override
    public Set<State> fulfill(Set<Requirement> requirements)
    {
        if (prepared) {
            log.info("Skipping cluster prerequisites fulfillment");
            return ImmutableSet.of(nodeSshUtils);
        }

        runOnMaster(singletonList("echo \'" + yarnPassword + "\' | passwd --stdin yarn"));

        fixHdpMapReduce();

        setupCgroup();

        setupYarnResourceManager();

        restartResourceManager();

        Map<String, String> node_labels = getNodeLabels();

        nodeSshUtils.createLabels(node_labels);

        useLabelsForSchedulerQueues();

        restartResourceManager();

        runOnAll(asList("mkdir -p /var/lib/presto", "chown yarn:yarn /var/lib/presto", "/etc/init.d/hadoop-yarn-nodemanager restart"));

        nodeSshUtils.labelNodes(node_labels);

        return ImmutableSet.of(nodeSshUtils);
    }

    private void fixHdpMapReduce()
    {
        nodeSshUtils.withSshClient(master, sshClient -> {
            sshClient.upload(Paths.get("target/classes/fix_hdp_mapreduce.sh"), "/tmp");
            return sshClient.command("sh /tmp/fix_hdp_mapreduce.sh || true");
        });
    }

    private Map<String, String> getNodeLabels()
    {
        Map<String, String> nodeLabels = new HashMap<>();

        nodeLabels.put(master, COORDINATOR_COMPONENT.toLowerCase());
        slaves.forEach(slave -> nodeLabels.put(slave, WORKER_COMPONENT.toLowerCase()));
        return nodeLabels;
    }

    private void setupYarnResourceManager()
    {
        nodeSshUtils.withSshClient(getAllNodes(), sshClient -> {
            sshClient.upload(Paths.get(LOCAL_CONF_DIR, "yarn", "yarn-site.xml"), REMOTE_HADOOP_CONF_DIR);
            sshClient.upload(Paths.get(LOCAL_CONF_DIR, "yarn", "container-executor.cfg"), REMOTE_HADOOP_CONF_DIR);
            return null;
        });

        runOnMaster(asList(
                "su - hdfs -c 'hadoop fs -mkdir -p /user/yarn'",
                "su - hdfs -c 'hadoop fs -chown yarn:yarn /user/yarn'"));
    }

    private void useLabelsForSchedulerQueues()
    {
        nodeSshUtils.withSshClient(master, sshClient -> {
            sshClient.upload(Paths.get(LOCAL_CONF_DIR, "yarn", "capacity-scheduler.xml"), REMOTE_HADOOP_CONF_DIR);
            return null;
        });
    }

    private void restartResourceManager()
    {
        runOnMaster(singletonList("/etc/init.d/hadoop-yarn-resourcemanager restart"));
    }

    private void setupCgroup()
    {
        runOnAll(asList(
                "find / -name container-executor | xargs chown root:yarn",
                "find / -name container-executor | xargs chmod 6050"));

        nodeSshUtils.withSshClient(getAllNodes(), sshClient -> {
            sshClient.upload(Paths.get(LOCAL_CONF_DIR, "cgroup", "cgrules.conf"), "/etc/");
            sshClient.upload(Paths.get(LOCAL_CONF_DIR, "cgroup", "cgconfig.conf"), "/etc/");
            return null;
        });

        runOnAll(asList(
                "/etc/init.d/cgconfig restart",
                "chmod -R 777 /cgroup"));
    }

    private void runOnMaster(List<String> commands)
    {
        nodeSshUtils.runOnNode(master, commands);
    }

    private void runOnAll(final List<String> commands)
    {
        getAllNodes().forEach(node -> nodeSshUtils.runOnNode(node, commands));
    }

    private List<String> getAllNodes()
    {
        return ImmutableList.<String>builder().addAll(slaves).add(master).build();
    }

    @Override
    public void cleanup(TestStatus testStatus)
    {
    }
}
