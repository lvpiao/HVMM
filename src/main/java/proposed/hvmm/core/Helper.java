package proposed.hvmm.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;

import lombok.extern.slf4j.Slf4j;

/**
 * @author PiAo
 * @date 2021/12/2
 */
@Slf4j
public class Helper {

    public static UtilizationModel utilizationModelFull = new UtilizationModelFull();
    public static List<List<Number>> hostSpecList;
    public static List<double[]> hostPowerData;
    public static Map<Integer, List<File>> workloadLevel = new HashMap<>();
    public static List<File> workloadList = new ArrayList<>();

    static {
        try {
            init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void init() throws Exception {
        loadWorkload();
        // loadVmConf();
        loadHostConf();
        loadHostPowerData();
    }

    private static void loadHostConf() throws IOException {
        hostSpecList = new ArrayList<>(16);
        String path = Manager.baseDataPath + "conf/host-spec.txt";
        List<String> lines = FileUtils.readLines(new File(path), "utf8");
        System.out.println("Find " + lines.size() + " kinds of host specifications.");
        for (var line : lines) {
            line = line.trim();
            if (StringUtils.isEmpty(line) || line.charAt(0) == '#')
                continue;
            String[] spec = line.split(",");
            List<Number> specList = new ArrayList<>(spec.length);
            for (var val : spec) {
                specList.add(Double.parseDouble(val.trim()));
            }
            hostSpecList.add(specList);
        }
    }

    private static void loadHostPowerData() throws IOException {
        hostPowerData = new ArrayList<>(16);
        String path = Manager.baseDataPath + "conf/host-power-data.txt";
        List<String> lines = FileUtils.readLines(new File(path), "utf8");
        for (var line : lines) {
            line = line.trim();
            if (StringUtils.isEmpty(line) || line.charAt(0) == '#')
                continue;
            String[] spec = line.split(",");
            List<Number> powerModel = new ArrayList<>(spec.length);
            for (var val : spec) {
                powerModel.add(Double.parseDouble(val.trim()));
            }
            hostPowerData.add(powerModel.stream().mapToDouble(Number::doubleValue).toArray());
        }
    }

    private static double workloadCpuUtilization(File trace) throws IOException {
        List<String> lines = FileUtils.readLines(trace, StandardCharsets.UTF_8);
        return lines.stream().mapToDouble(Double::parseDouble).average().getAsDouble();
    }

    public static void loadWorkload() throws IOException {
        String path = Manager.baseDataPath + "planetlab";
        File traceList = new File(path);
        for (File trace : traceList.listFiles()) {
            for (File traceItem : trace.listFiles()) {
                double avgUtilization = workloadCpuUtilization(traceItem);
                int lvl = (int) Math.floor(avgUtilization) / 10 * 10;
                if (!workloadLevel.containsKey(lvl))
                    workloadLevel.put(lvl, new ArrayList<>());
                workloadLevel.get(lvl).add(traceItem);
                workloadList.add(traceItem);
            }
        }
    }

    public static File getWorkloadByRandom(int level) {
        return choice(workloadLevel.get(level));
    }

    public static File getWorkloadByRandom() {
        return choice(workloadList);
    }

    private static final int vmMaxBandwidth = 1024;

    public static double[] createComputeOptimizedVm() {
        var baseConf = new double[4];
        int coefficient = RandomUtils.nextInt(1, 17);
        // 放大cpu核心数和内存容量
        baseConf[0] = coefficient;
        baseConf[2] = coefficient;
        // 设置最大带宽
        baseConf[3] = vmMaxBandwidth;
        // 设置算力
        baseConf[1] = RandomUtils.nextInt(35, 41) * 100;
        return baseConf;
    }

    public static double[] createGeneralPurposeVm() {
        var baseConf = new double[4];
        int coefficient = RandomUtils.nextInt(1, 17);
        // 放大cpu核心数和内存容量
        baseConf[0] = coefficient;
        baseConf[2] = 2 * coefficient;
        // 设置最大带宽
        baseConf[3] = vmMaxBandwidth;
        // 设置算力
        baseConf[1] = RandomUtils.nextInt(25, 36) * 100;
        return baseConf;
    }

    public static double[] createMemoryOptimizedVm() {
        var baseConf = new double[4];
        int coefficient = RandomUtils.nextInt(1, 9);
        // 放大cpu核心数和内存容量
        baseConf[0] = coefficient;
        baseConf[2] = 4 * coefficient;
        // 设置最大带宽
        baseConf[3] = vmMaxBandwidth;
        // 设置算力
        baseConf[1] = RandomUtils.nextInt(20, 31) * 100;
        return baseConf;
    }

    public static double[] getVmConfByRandom() {
        // 随机VM类型
        int type = RandomUtils.nextInt(1, 101);
        if (type <= 60) {
            return createGeneralPurposeVm();
        } else if (type <= 85) {
            return createMemoryOptimizedVm();
        } else {
            return createComputeOptimizedVm();
        }
    }

    public static int getHostConfIdxByRandom() {
        int n = hostSpecList.size();
        return RandomUtils.nextInt(0, n);
    }

    public static List<Number> getHostConfByIdx(int idx) {
        return hostSpecList.get(idx);
    }

    public static double[] getHostPowerDataByIdx(int idx) {
        return hostPowerData.get(idx);
    }

    public static List<Pe> createPeList(int peCnt, double mips) {
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < peCnt; i++) {
            peList.add(new Pe(i, new PeProvisionerSimple(mips)));
        }
        return peList;
    }

    public static void saveBroker(TrafficPowerBroker broker) {
        Gson gson = new Gson();
        List<TrafficPowerVm> vmList = broker.getRequestVmList();
        List<TrafficPowerCloudlet> cloudletList = broker.getRequestCloudletList();
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("brokerId", broker.getId());
        jsonMap.put("delay", broker.getDelay());
        jsonMap.put("vmSpecList", vmList.stream().map(TrafficPowerVm::getResource).toArray());
        jsonMap.put("vmIdList", vmList.stream().map(TrafficPowerVm::getId).toArray());
        jsonMap.put("vmConnMap", vmList.stream().map(x -> {
            List<String> val = new ArrayList<>();
            x.getConnVms().forEach((k, v) -> {
                val.add(k + "-" + v);
            });
            return val.toArray();
        }).toArray());
        jsonMap.put("cloudletList", cloudletList.stream().map(TrafficPowerCloudlet::getWorkLoadPath)
                .collect(Collectors.toList()));
        String jsonStr = gson.toJson(jsonMap);
        try {
            FileUtils.write(new File(TrafficPowerBroker.savePath, broker.getId() + ".txt"), jsonStr,
                    "utf8");
            // log.debug("broker：{}", jsonStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<TrafficPowerBroker> loadBrokerList() {
        List<TrafficPowerBroker> brokerList = new ArrayList<>();
        File path = new File(TrafficPowerBroker.savePath);
        try {
            File[] files = path.listFiles();
            Arrays.sort(files, (f1, f2) -> {
                int t1 = Integer.parseInt(f1.getName().split("\\.")[0]);
                int t2 = Integer.parseInt(f2.getName().split("\\.")[0]);
                return t1 - t2;
            });
            for (File file : files) {
                String jsonStr = FileUtils.readFileToString(file, "utf8");
                // log.debug(jsonStr);
                Gson gson = new Gson();
                Map json = gson.fromJson(jsonStr, Map.class);
                int brokerId = (int) (double) json.get("brokerId");
                double delay = (Double) json.get("delay");
                // log.debug("{}-{}", brokerId, delay);
                var vmList = new ArrayList<TrafficPowerVm>();
                var cloudletList = new LinkedList<TrafficPowerCloudlet>();
                // 获取虚拟机id
                var vmIdList = (List) json.get("vmIdList");
                // 解析VmList
                var vmSpecList = (List) json.get("vmSpecList");
                int idx = 0;
                for (Object vmSpecObj : vmSpecList) {
                    var vmSpec = (List) vmSpecObj;
                    double[] resource = new double[vmSpec.size()];
                    for (int i = 0; i < vmSpec.size(); i++) {
                        resource[i] = (double) vmSpec.get(i);
                    }
                    // 调整资源单位
                    // 确保内存单位为MB
                    if (resource[2] < 1024) {
                        resource[2] *= 1024;
                    }
                    // log.debug(Arrays.toString(resource));
                    TrafficPowerVm vm = new TrafficPowerVm((int) (double) vmIdList.get(idx++),
                            brokerId, resource);
                    vmList.add(vm);
                }
                // 解析vmConnMap
                List<Object> vmConnPairs = (List<Object>) json.get("vmConnMap");
                for (int i = 0; i < vmConnPairs.size(); i++) {
                    TrafficPowerVm vm = vmList.get(i);
                    var connPairs = (List<Object>) vmConnPairs.get(i);
                    for (Object pairObj : connPairs) {
                        String pair = (String) pairObj;
                        String[] kv = pair.split("-");
                        vm.addCommunicateVm(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
                    }
                }
                // 解析Cloudlet
                List<Object> cloudletListObj = (List<Object>) json.get("cloudletList");
                for (int i = 0; i < vmList.size(); i++) {
                    String workloadPath = (String) cloudletListObj.get(i);
                    TrafficPowerVm vm = vmList.get(i);
                    TrafficPowerCloudlet cloudlet = new TrafficPowerCloudlet(vm.getId(),
                            workloadPath, Integer.MAX_VALUE, vm.getNumberOfPes());
                    cloudlet.setUserId(brokerId);
                    cloudlet.setVmId(vm.getId());
                    cloudletList.add(cloudlet);
                }
                TrafficPowerBroker broker = new TrafficPowerBroker(vmList, cloudletList, delay);
                brokerList.add(broker);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // if (brokerList.size() != Manager.BROKER_COUNT) {
        // throw new RuntimeException("BROKER数量不对");
        // }
        log.info("加载了{}个Broker", brokerList.size());
        return brokerList;
    }

    public static void generateBroker() throws Exception {
        FileUtils.cleanDirectory(new File(TrafficPowerBroker.savePath));
        for (int i = 1; i <= Manager.BROKER_COUNT; i++) {
            new TrafficPowerBroker();
        }
    }

    private static List<TrafficPowerHost> createHostList(List<String> hostSpecList,
            List<String> hostPositionList, List<String> hostPowerDataList) {
        // 解析配置
        List<TrafficPowerHost> list = new ArrayList<>();
        int hostId = 0;
        for (int i = 0; i < hostPositionList.size(); i++) {
            // 资源
            String[] hostSpec = hostSpecList.get(i).split(",");
            double[] hostResourceVal =
                    Arrays.stream(hostSpec).mapToDouble(Double::parseDouble).toArray();
            // 位置
            String[] position = hostPositionList.get(i).split(",");
            int[] positionVal = Arrays.stream(position).mapToInt(Integer::parseInt).toArray();
            // 能耗
            String[] hostPowerModel = hostPowerDataList.get(i).split(",");
            double[] hostPowerModelVal =
                    Arrays.stream(hostPowerModel).mapToDouble(Double::parseDouble).toArray();
            // 翻转不同利用率功率的值，因为功率是逆序存的
            int l = 0, r = hostPowerModelVal.length - 1;
            while (l < r) {
                double t = hostPowerModelVal[l];
                hostPowerModelVal[l] = hostPowerModelVal[r];
                hostPowerModelVal[r] = t;
                l++;
                r--;
            }
            // 调整资源单位
            // 确保内存单位为MB
            if (hostResourceVal[Manager.RAM] < 1024) {
                hostResourceVal[Manager.RAM] *= 1024;
            }
            List<Pe> peList =
                    createPeList((int) hostResourceVal[Manager.PE], hostResourceVal[Manager.MIPS]);
            TrafficPowerPowerModel powerModel = new TrafficPowerPowerModel(hostPowerModelVal);
            TrafficPowerHost host = new TrafficPowerHost(hostId++, hostResourceVal, positionVal,
                    peList, powerModel);
            list.add(host);
        }
        Collections.shuffle(list);
        System.out.println("创建了" + list.size() + "个主机");
        return list;
    }

    public static List<TrafficPowerHost> loadFatTree(int k) throws IOException {
        log.info("加载保存的Host配置");
        // 加载Host
        String savePath = Manager.baseDataPath + "hosts";
        // 资源
        File specFile = new File(savePath, "hostSpecList");
        List<String> hostSpecList = new ArrayList<>(FileUtils.readLines(specFile, "utf8"));
        // 位置
        File positionFile = new File(savePath, "hostPositionList");
        List<String> hostPositionList = new ArrayList<>(FileUtils.readLines(positionFile, "utf8"));
        // 能耗
        File powerModelFile = new File(savePath, "hostPowerModelList");
        List<String> hostPowerModelList =
                new ArrayList<>(FileUtils.readLines(powerModelFile, "utf8"));
        return createHostList(hostSpecList, hostPositionList, hostPowerModelList);
    }

    public static List<TrafficPowerHost> generateFatTree(int k) throws IOException {
        List<String> hostSpecList = new ArrayList<>();
        List<String> hostPositionList = new ArrayList<>();
        List<String> hostPowerDataList = new ArrayList<>();
        log.info("创建Hosts并保存");
        int idx = 0;
        // 创建Hosts并保存
        for (int pod = 0; pod < k; pod++) {
            for (int rack = 0; rack < k / 2; rack++) {
                List<Number> hostResource = getHostConfByIdx(idx);
                double[] powerModel = getHostPowerDataByIdx(idx);
                idx = (idx + 1) % hostPowerData.size();
                for (int port = 0; port < k / 2; port++) {
                    // 资源、位置、能耗
                    String resourceStr = StringUtils.join(hostResource, ',');
                    String positionStr = StringUtils.join(new int[] {pod, rack, port}, ',');
                    String powerModelStr = StringUtils.join(powerModel, ',');
                    hostPositionList.add(positionStr);
                    hostSpecList.add(resourceStr);
                    hostPowerDataList.add(powerModelStr);
                }
            }
        }
        // 保存生成的host
        String savePath = Manager.baseDataPath + "hosts";
        FileUtils.cleanDirectory(new File(savePath));
        // 资源
        File specFile = new File(savePath, "hostSpecList");
        FileUtils.writeLines(specFile, hostSpecList);
        // 位置
        File positionFile = new File(savePath, "hostPositionList");
        FileUtils.writeLines(positionFile, hostPositionList);
        // 能耗
        File powerModelFile = new File(savePath, "hostPowerModelList");
        FileUtils.writeLines(powerModelFile, hostPowerDataList);
        return createHostList(hostSpecList, hostPositionList, hostPowerDataList);
    }

    public static double getVmsRealUsedMips(Collection<TrafficPowerVm> vmList) {
        return vmList.stream().mapToDouble(TrafficPowerVm::getRealUsedMips).sum();
    }

    public static double getNowVmsUsedMips(Collection<TrafficPowerVm> vmList) {
        double usedMips = 0;
        for (var vm : vmList) {
            usedMips += vm.getRealUsedMips();
        }
        return usedMips;
    }

    public static <T> List<T> sample(List<T> list, int cnt) {
        // Collections.shuffle(list);
        List<T> tmpList = new ArrayList<>(list);
        Collections.shuffle(tmpList);
        return tmpList.subList(0, cnt);
    }

    public static <T> T choice(List<T> list) {
        return list.get(RandomUtils.nextInt(0, list.size()));
    }

    public static double cosSim(double[] x, double[] y) {
        double xy = 0;
        double xx = 0;
        double yy = 0;
        for (int i = 0; i < x.length; i++) {
            xy += x[i] * y[i];
            xx += x[i] * x[i];
            yy += y[i] * y[i];
        }
        if (xy == 0) {
            return 0;
        }
        return xy / (Math.sqrt(xx) * Math.sqrt(yy));
    }
}
