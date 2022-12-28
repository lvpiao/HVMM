package proposed.hvmm.core;


import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import lombok.extern.slf4j.Slf4j;
import proposed.hvmm.compares.VmManagePolicyFFDMMT;
import proposed.hvmm.compares.VmManagePolicyHVMM;
import proposed.hvmm.compares.VmManagePolicyHVMMWithoutMigration;
import proposed.hvmm.compares.VmManagePolicyJointPTR;
import proposed.hvmm.compares.VmManagePolicyPABFDMMT;
import proposed.hvmm.compares.VmManagePolicyPEAS;


@Slf4j
public class Main {

    public static TrafficPowerDatacenter datacenter;

    public static List<String> getSimulationResult() {
        List<String> res = new ArrayList<>();
        res.add(datacenter.getVmAllocationPolicy().getClass().getSimpleName().substring(14));
        res.add(String.format("%d", datacenter.getVmList().size()));
        res.add(String.format("%d", datacenter.getActiveHostCnt()));
        res.add(String.format("%.2f", datacenter.getTotalEnergyConsumedInKWH()));
        res.add(String.format("%d", datacenter.getTotalTrafficCost()));
        res.add(String.format("%.2f", datacenter.getTotalResourceWastage()));
        res.add(String.format("%d", datacenter.getTotalMigrationTimes()));
        res.add(String.format("%f", datacenter.getAverageSlavRate()));
        return res;
    }

    public static void runAllCompareSimulationLaboratory() throws Exception {
        int[] userCnt = new int[] {100, 300, 500 };
        int[] workloadDownThr = new int[] { 0, 41, 71, 0 };
        int[] workloadUpThr = new int[] { 100, 70, 100, 40 };
        for (int j = 0; j < workloadDownThr.length; j++) {
            Manager.WORKLOAD_DOWN_THR = workloadDownThr[j];
            Manager.WORKLOAD_UP_THR = workloadUpThr[j];
            for (int cnt : userCnt) {
                Manager.BROKER_COUNT = cnt;
                Manager.LOAD_DATA_FROM_FILES = false;
                runCompareSimulationLaboratory();
            }
        }
    }

    public static void runCompareSimulationLaboratory() throws Exception {
        // 进行对比的所有的算法
        Class[] vmAllocationPolicies = new Class[] {
                VmManagePolicyJointPTR.class,
                VmManagePolicyFFDMMT.class,
                VmManagePolicyPABFDMMT.class,
                VmManagePolicyPEAS.class,
                VmManagePolicyHVMMWithoutMigration.class,
                VmManagePolicyHVMM.class,
        };
        List<List<String>> allRes = new ArrayList<>();
        for (Class<VmAllocationPolicy> vmAllocationClass : vmAllocationPolicies) {
            CloudSim.init(1, Calendar.getInstance(), false);
            CloudSim.terminateSimulation(Manager.ONE_DAY_SECOND * Manager.SIMULATE_DAYS);
            // 加载完全一样的Host列表
            List<TrafficPowerHost> hostList = Manager.LOAD_DATA_FROM_FILES
                    ? Helper.loadFatTree(Manager.FAT_TREE_POD_COUNT)
                    : Helper.generateFatTree(Manager.FAT_TREE_POD_COUNT);
            VmAllocationPolicy vmAllocationPolicy = (VmAllocationPolicy) vmAllocationClass
                    .getDeclaredConstructor(List.class).newInstance(hostList);
            if (Manager.LOAD_DATA_FROM_FILES) {
                Helper.loadBrokerList();
            } else {
                Helper.generateBroker();
            }
            datacenter = createDatacenter(hostList, vmAllocationPolicy);
            datacenter.setMigrationAble(Manager.USE_MIGRATION);
            log.debug("开始算法：{}", vmAllocationPolicy.getClass().getSimpleName());
            CloudSim.startSimulation();
            // if (Manager.LOAD_DATA_FROM_FILES)
            allRes.add(getSimulationResult());
            CloudSim.stopSimulation();
            // 第一个用于创建模拟数据，后面的算法复用第一个算法创建的数据,保证算法的输入相同
            Manager.LOAD_DATA_FROM_FILES = true;
        }
        File resultFile = new File(Manager.baseDataPath + "result" + File.separator + Manager.FAT_TREE_POD_COUNT
                + "-" + Manager.BROKER_COUNT + "-" + Manager.WORKLOAD_DOWN_THR + "-" + Manager.WORKLOAD_UP_THR
                + ".csv");
        System.out.println("服务器总数：" + datacenter.getHostList().size());
        List<String> lines = new ArrayList<>();
        String[] headers = new String[] { "Algorithm", "Vms", "ActiveHosts",
                "EnergyCost(KWH)", "TrafficCost", "ResourceWastage", "Migrations",
                "averageSlavRate" };
        lines.add(StringUtils.join(headers, ","));
        System.out.format("%20s %20s %20s %20s %20s %20s %20s %20s\n", (Object[]) headers);
        // 输出对比实验的结果
        for (int i = 0; i < allRes.size(); i++) {
            List<String> res = allRes.get(i);
            lines.add(StringUtils.join(res, ","));
            System.out.format("%20s %20s %20s %20s %20s %20s %20s %20s\n", res.toArray());
        }
        FileUtils.writeLines(resultFile, lines);
    }

    public static void main(String[] args) throws Exception {
        Log.setDisabled(Manager.DISABLE_CLOUDSIM_LOG);
        runAllCompareSimulationLaboratory();
    }

    private static TrafficPowerDatacenter createDatacenter(List<TrafficPowerHost> hostList,
            VmAllocationPolicy vmAllocationPolicy) throws Exception {
        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10; // time zone this resource located
        double cost = 300; // the cost of using processing in this resource
        double costPerMem = 5; // the cost of using memory in this resource
        double costPerStorage = 10; // the cost of using storage in this resource
        double costPerBw = 10; // the cost of using bw in this resource
        Collections.shuffle(hostList);
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(arch, os, vmm, hostList, time_zone,
                cost, costPerMem, costPerStorage, costPerBw);
        return new TrafficPowerDatacenter("Datacenter", characteristics, vmAllocationPolicy);

    }
}
