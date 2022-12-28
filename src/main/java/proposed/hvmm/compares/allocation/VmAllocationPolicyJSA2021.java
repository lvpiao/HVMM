package proposed.hvmm.compares.allocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomUtils;
import org.cloudbus.cloudsim.Vm;

import proposed.hvmm.core.TrafficPowerHost;
import proposed.hvmm.core.TrafficPowerVm;
import proposed.hvmm.interfaces.BatchVmAllocation;

public class VmAllocationPolicyJSA2021 extends VmAllocationPolicyCommon implements BatchVmAllocation {
    public final List<Vm> newComingVmCache;
    public Map<Integer, List<TrafficPowerHost>> rackHosts;
    public Map<Integer, List<TrafficPowerHost>> podHosts;

    public VmAllocationPolicyJSA2021(List<TrafficPowerHost> hostList) {
        super(hostList);
        newComingVmCache = new ArrayList<>();
        rackHosts = new HashMap<>();
        podHosts = new HashMap<>();
        for (TrafficPowerHost host : hostList) {
            if (!rackHosts.containsKey(host.getRackId()))
                rackHosts.put(host.getRackId(), new ArrayList<>());
            rackHosts.get(host.getRackId()).add(host);

            if (!podHosts.containsKey(host.getPodId()))
                podHosts.put(host.getPodId(), new ArrayList<>());
            podHosts.get(host.getPodId()).add(host);
        }
    }

    public List<TrafficPowerHost> vmNearToFarHostQueue(TrafficPowerVm vm) {
        List<TrafficPowerHost> hostQueue = new ArrayList<>();
        TrafficPowerHost vmHost = vm.getHost();
        hostQueue.add(vmHost);
        // the same rack
        int rackId = vmHost.getRackId();
        List<TrafficPowerHost> rackHostList = rackHosts.get(rackId);
        // 按照powerEfficiency降序
        rackHostList.sort(Comparator.comparingDouble(TrafficPowerHost::powerEfficiency));
        hostQueue.addAll(rackHostList);
        // the same pod
        int podId = vmHost.getPodId();
        List<TrafficPowerHost> podHostList = podHosts.get(podId);
        podHostList.sort(Comparator.comparingDouble(TrafficPowerHost::powerEfficiency));
        hostQueue.addAll(podHostList);
        // the others pod
        var otherPods = podHosts.entrySet().stream().filter(x -> x.getKey() != podId).map(Map.Entry::getValue)
                .sorted((p1, p2) -> {
                    double pe1 = p1.stream().mapToDouble(TrafficPowerHost::powerEfficiency).sum();
                    double pe2 = p2.stream().mapToDouble(TrafficPowerHost::powerEfficiency).sum();
                    return Double.compare(pe1, pe2);
                }).collect(Collectors.toList());
        for (var pod : otherPods) {
            hostQueue.addAll(pod);
        }
        return hostQueue;
    }

    public TrafficPowerHost getSuitableHost(TrafficPowerVm vm, List<TrafficPowerHost> hostList) {
        for (var host : hostList) {
            if (host.isSuitableForVm(vm)) {
                return host;
            }
        }
        return null;
    }

    public void allocateVmToHostList(Vm vm, List<TrafficPowerHost> hostList) {
        var host = getSuitableHost((TrafficPowerVm) vm, hostList);
        allocateHostForVm(vm, host);
    }

    public void allocateVmList(List<Vm> vmList) {
        if (vmList.isEmpty())
            return;
        // 选择是采用jointPT还是jointPR
        if (RandomUtils.nextInt(0, 100) < 20) {
            jointPT(vmList);
        } else {
            jointPR(vmList);
        }
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        throw new RuntimeException("这个方法不可用");
    }

    public List<Integer> mst(List<Vm> vmList) {
        Map<Integer, Map<Integer, Integer>> graph = new HashMap<>();
        Map<Integer, Integer> dist = new HashMap<>();
        int[] vmIdCluster = vmList.stream().mapToInt(Vm::getId).toArray();

        for (var x : vmIdCluster) {
            dist.put(x, 0);
            graph.put(x, new HashMap<>());
        }
        // create graph
        int start = vmIdCluster[0];
        int n = vmIdCluster.length;
        int maxW = -1;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int x = vmIdCluster[i];
                int y = vmIdCluster[j];
                int w = ((TrafficPowerVm) vmList.get(i)).getConnVms().getOrDefault(vmList.get(j).getId(), 0);
                Map<Integer, Integer> node1 = new HashMap<>();
                node1.put(y, -w);
                graph.put(x, node1);
                Map<Integer, Integer> node2 = new HashMap<>();
                node2.put(x, -w);
                graph.put(y, node2);
                if (w > maxW) {
                    maxW = w;
                    start = x;
                }
            }
        }
        // prime
        dist.putAll(graph.get(start));
        List<Integer> vmSequence = new ArrayList<>();
        Set<Integer> vis = new HashSet<>();
        vis.add(start);
        vmSequence.add(start);
        for (int i = 0; i < n - 1; i++) {
            int v = -1, maxDist = 1;
            for (var t : vmIdCluster) {
                if (vis.contains(t))
                    continue;
                if (maxDist > dist.get(t)) {
                    maxDist = dist.get(t);
                    v = t;
                }
            }
            int u = v;
            vis.add(u);
            vmSequence.add(u);
            for (var tw : graph.get(u).entrySet()) {
                int t = tw.getKey();
                int w = tw.getValue();
                if (vis.contains(t))
                    continue;
                dist.put(t, Math.min(dist.get(u) + w, dist.get(t)));
            }
        }
        return vmSequence;
    }

    public void jointPT(List<Vm> vmList) {
        Map<Integer, TrafficPowerVm> vmsMap = new HashMap<>();
        for (var vm : vmList) {
            vmsMap.put(vm.getId(), (TrafficPowerVm) vm);
        }
        List<Integer> vmIdsSequence = mst(vmList);
        List<TrafficPowerVm> vmSequence = vmIdsSequence.stream().map(vmsMap::get).collect(Collectors.toList());
        var lastVm = vmSequence.get(0);
        allocateVmToHostList(lastVm, getHostList());
        for (int i = 1; i < vmSequence.size(); i++) {
            var curVm = vmSequence.get(i);
            var hostQueue = vmNearToFarHostQueue(lastVm);
            allocateVmToHostList(curVm, hostQueue);
            lastVm = curVm;
        }
    }

    public void jointPR(List<Vm> vmList) {
        List<TrafficPowerHost> activeHosts = getHostList().stream()
                .map(x -> (TrafficPowerHost) x)
                .filter(TrafficPowerHost::isActive)
                .sorted(Comparator.comparingDouble(TrafficPowerHost::powerEfficiency))
                .collect(Collectors.toList());

        for (var vm : vmList) {
            double mini = Double.MAX_VALUE;
            TrafficPowerHost bestHost = null;
            for (var host : activeHosts) {
                if (host.isSuitableForVm(vm)) {
                    double resourceWastage = host.getResourceWastageAfterPlaceVm(vm);
                    if (resourceWastage < mini) {
                        mini = resourceWastage;
                        bestHost = host;
                    }
                }
            }
            if (bestHost != null) {
                allocateHostForVm(vm, bestHost);
            } else {
                TrafficPowerHost bestIdleHost = getHostList().stream()
                        .map(x -> (TrafficPowerHost) x)
                        .filter(TrafficPowerHost::isIdle)
                        .filter(host -> host.isSuitableForVm(vm))
                        .min(Comparator.comparingDouble(TrafficPowerHost::powerEfficiency))
                        .get();

                allocateHostForVm(vm, bestIdleHost);
                activeHosts.add(bestIdleHost);
            }
        }
    }
}
