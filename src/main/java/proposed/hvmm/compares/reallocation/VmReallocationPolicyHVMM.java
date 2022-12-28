package proposed.hvmm.compares.reallocation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.cloudbus.cloudsim.Vm;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import proposed.hvmm.compares.allocation.VmAllocationPolicyCommon;
import proposed.hvmm.compares.allocation.VmAllocationPolicyHVMM;
import proposed.hvmm.core.Manager;
import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;


/**
 * 通过谱聚类重新部署虚拟机
 *
 * @author PiAo
 * @date 2021/12/11
 */
@Slf4j
@Setter
@Getter
public class VmReallocationPolicyHVMM extends VmReallocationPolicyCommon {
    private final static String cachePath = Manager.baseDataPath + "wCache.txt";

    private final Map<Integer, Set<Integer>> clusterToHosts;
    private final Map<Integer, Integer> hostToCluster;

    private Map<Integer, Integer> clusteringVmId;
    private Map<Integer, Integer> realVmId;

    private Set<Integer> consolidationVmSet;
    private int lastMigrationCnt = -1;
    private int lastVMCnt = 0;

    public VmReallocationPolicyHVMM(VmAllocationPolicyCommon vmAllocator) {
        super(vmAllocator);
        clusterToHosts = new HashMap<>();
        hostToCluster = new HashMap<>();
        clusteringHost(Manager.FAT_TREE_POD_COUNT, clusterToHosts, hostToCluster);
    }

    public VmAllocationPolicyHVMM getVmAllocator() {
        return (VmAllocationPolicyHVMM) vmAllocator;
    }

    public double[][] baseHostWeight() {
        int hostCnt = vmAllocator.getHostList().size();
        double[][] W = new double[hostCnt][hostCnt];
        List<TrafficPowerHost> hostList = vmAllocator.getHostList();
        for (int i = 0; i < hostCnt; i++) {
            TrafficPowerHost host0 = hostList.get(i);
            for (int j = i + 1; j < hostCnt; j++) {
                TrafficPowerHost host1 = hostList.get(j);
                int dist = host0.distanceToHost(host1);
                W[host0.getId()][host1.getId()] += 1f / (1 + dist);
                W[host1.getId()][host0.getId()] += 1f / (1 + dist);
            }
        }
        return W;
    }

    private static String execPythonSpectralClustering(String k) {
        try {
            // 执行py文件
            String[] command = new String[]{"python", Manager.baseDataPath + "spectral_clustering.py", k};
            Process proc = Runtime.getRuntime().exec(command);
            String labelStr = IOUtils.toString(proc.getInputStream(), StandardCharsets.UTF_8);
            proc.destroy();
            return labelStr;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected static int[] spectralClustering(int k, double[][] w) {
        List<String> lines = new ArrayList<>();
        for (var line : w) {
            String strLine = StringUtils.join(Arrays.stream(line).boxed().collect(Collectors.toList()), ' ');
            lines.add(strLine);
        }
        try {
            FileUtils.writeLines(new File(cachePath), lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String labelStr = execPythonSpectralClustering(String.valueOf(k)).trim();
        return Arrays.stream(labelStr.split(",")).mapToInt(Integer::parseInt).toArray();
    }

    private void clusteringHost(int clusterCnt, Map<Integer, Set<Integer>> clusterToHosts,
                                Map<Integer, Integer> hostToCluster) {
        double[][] W = baseHostWeight();
        int[] labels = spectralClustering(clusterCnt, W);
        System.out.println("labels length:" + labels.length);
        for (int cid = 0; cid < clusterCnt; cid++) {
            clusterToHosts.put(cid, new HashSet<>());
        }
        for (int i = 0; i < labels.length; i++) {
            clusterToHosts.get(labels[i]).add(i);
            hostToCluster.put(i, labels[i]);
        }
        for (int cid = 0; cid < clusterCnt; cid++) {
            System.out.println(cid + ":" + new TreeSet<>(clusterToHosts.get(cid)));
        }
    }

    public Map<Integer, Set<Integer>> getCurVmCluster() {
        Map<Integer, Set<Integer>> curVMCluster = new HashMap<>();
        for (var entry : getClusterToHosts().entrySet()) {
            Integer key = entry.getKey();
            curVMCluster.put(key, new HashSet<>());
            for (var hostId : entry.getValue()) {
                TrafficPowerHost host = getVmAllocator().getHost(hostId);
                List<Integer> vmIds = vmRealIdsToClusteringIds(host.getVmList().stream().map(Vm::getId).collect(Collectors.toList()));
                curVMCluster.get(key).addAll(vmIds);
            }
            if (curVMCluster.get(key).isEmpty()) {
                curVMCluster.remove(key);
            }
        }
        return curVMCluster;
    }

    public TrafficPowerHost moveVmToOtherHostCluster(int vmId, int targetHostClusterId) {
        TrafficPowerVm vm = getVmAllocator().getVm(vmId);
        TrafficPowerHost oldHost = vm.getHost();
        int srcClusterId = getClusterId(oldHost.getId());
        if (srcClusterId == targetHostClusterId) {
            return null;
        }
        Set<Integer> targetHostIds = getClusterToHosts().get(targetHostClusterId);
        return getVmAllocator().getSuitableHost(vm, getVmAllocator().hostIdsToHosts(targetHostIds));
    }

    private int getClusterId(int id) {
        return getHostToCluster().get(id);
    }

    public void convertVmIds(List<Integer> vmIds) {
        int pid = 0;
        clusteringVmId = new HashMap<>();
        realVmId = new HashMap<>();
        for (var vid : vmIds) {
            clusteringVmId.put(vid, pid);
            realVmId.put(pid, vid);
            pid++;
        }
        consolidationVmSet = new HashSet<>(vmIds);
    }

    public List<Integer> vmRealIdsToClusteringIds(List<Integer> realIds) {
        return realIds.stream().filter(x -> consolidationVmSet.contains(x)).map(x -> clusteringVmId.get(x)).collect(Collectors.toList());
    }

    public List<Integer> vmClusteringIdsToRealIds(Set<Integer> clusteringIds) {
        return clusteringIds.stream().map(x -> realVmId.get(x)).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getReallocationList(List<? extends Vm> vmList) {
        // 没有新来的虚拟机，并且上次迁移虚拟机的个数为0
        if (lastMigrationCnt == 0 && lastVMCnt == vmList.size()) {
            return new ArrayList<>();
        }
        int vmCnt = vmList.size();
        List<Integer> vmIds = vmList.stream().map(Vm::getId).collect(Collectors.toList());
        //权重矩阵
        double[][] W = new double[vmCnt][vmCnt];
        //虚拟机id不是从零开始，且不是连续的，所以要转换一下
        convertVmIds(vmIds);
        for (var vm : vmList) {
            TrafficPowerVm vm1 = (TrafficPowerVm) vm;
            int v1Id = vm1.getId();
            int p1Id = clusteringVmId.get(v1Id);
            for (var node : vm1.getConnVms().entrySet()) {
                int v2Id = node.getKey();
                if (!consolidationVmSet.contains(v2Id)) continue;
                double weight = (double) node.getValue();
                int p2Id = clusteringVmId.get(v2Id);
                W[p1Id][p2Id] += weight;
                W[p2Id][p1Id] += weight;
            }
        }
        Map<Integer, Set<Integer>> curVmCluster = getCurVmCluster();
        int k = curVmCluster.size();
        if (k <= 1 || k >= W.length) return new ArrayList<>();
        log.debug("vm clusters:{}", k);
        int[] labels = spectralClustering(k, W);
        Map<Integer, Set<Integer>> nextVmCluster = new HashMap<>();
        for (int cid = 0; cid < k; cid++) {
            nextVmCluster.put(cid, new HashSet<>());
        }
        for (int i = 0; i < labels.length; i++) {
            nextVmCluster.get(labels[i]).add(i);
        }
        // the vms set that need be migrated to new host cluster
        Set<Integer> migrateVmClusteringIds = new HashSet<>();
        Set<Integer> matched = new HashSet<>();
        Map<Integer, Integer> destHostCluster = new HashMap<>();
        for (var entry1 : nextVmCluster.entrySet()) {
            var c1 = entry1.getValue();
            int aimClusterId = -1;
            int maxInterLen = 0;
            for (var entry2 : curVmCluster.entrySet()) {
                int c2Id = entry2.getKey();
                if (matched.contains(c2Id)) continue;
                Set<Integer> c2 = entry2.getValue();
                //取交集
                Set<Integer> set = new HashSet<>(c1);
                set.retainAll(c2);
                if (maxInterLen < set.size()) {
                    maxInterLen = set.size();
                    aimClusterId = c2Id;
                }
            }
            if (aimClusterId == -1) continue;
            var aimCluster = curVmCluster.get(aimClusterId);
            Set<Integer> candidateVmIds = new HashSet<>(c1);
            candidateVmIds.removeAll(aimCluster);
            matched.add(aimClusterId);
            if (candidateVmIds.size() * 2 >= c1.size())
                continue;
            for (var vmClusteringId : candidateVmIds) {
                migrateVmClusteringIds.add(vmClusteringId);
                int realId = realVmId.get(vmClusteringId);
                destHostCluster.put(realId, aimClusterId);
            }
        }
        //do migration
        List<Integer> migrateVmRealIds = vmClusteringIdsToRealIds(migrateVmClusteringIds);
        var migrateVmList = migrateVmRealIds.stream().map(x -> getVmAllocator().getVm(x))
                .collect(Collectors.toList());
        Set<Integer> migrateSuccessVmIdSet = new HashSet<>();
        List<Map<String, Object>> migrationList = new ArrayList<>();
        for (var vm : migrateVmList) {
            if (vm.isInMigration()) continue;
            int vmId = vm.getId();
            if (migrateSuccessVmIdSet.contains(vmId)) continue;
            int hostClusterId = destHostCluster.get(vmId);
            TrafficPowerHost targetHost = moveVmToOtherHostCluster(vmId, hostClusterId);
            if (targetHost != null) {
                addMigrationItem(migrationList, vm, targetHost);
                migrateSuccessVmIdSet.add(vmId);
            }
        }
        lastMigrationCnt = migrateSuccessVmIdSet.size();
        lastVMCnt = vmCnt;
        return migrationList;
    }
}
