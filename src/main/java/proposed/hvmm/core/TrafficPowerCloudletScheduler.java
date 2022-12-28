package proposed.hvmm.core;

import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerDynamicWorkload;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.core.CloudSim;


/**
 * 每个Vm只允许一个Cloudlet，并且这个Cloudlet占用该Vm的所有资源
 *
 * @author PiAo
 * @date 2021/12/5
 */
public class TrafficPowerCloudletScheduler extends CloudletSchedulerDynamicWorkload {

    /**
     * Instantiates a new VM scheduler
     *
     * @param mips The individual MIPS capacity of each PE allocated to the VM using the scheduler,
     *        considering that all PEs have the same capacity.
     * @param numberOfPes The number of PEs allocated to the VM using the scheduler.
     */
    public TrafficPowerCloudletScheduler(double mips, int numberOfPes) {
        super(mips, numberOfPes);
    }

    /**
     * 只考虑迁移造成的VM的性能降低
     */
    @Override
    public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
        setCurrentMipsShare(mipsShare);
        double timeFrame = currentTime - getPreviousTime();
        double nextEvent = currentTime + CloudSim.getMinTimeBetweenEvents();
        for (ResCloudlet rcl : getCloudletExecList()) {
            TrafficPowerVm vm = Main.datacenter.getVmMap().get(rcl.getCloudlet().getVmId());
            double migrationPerformanceDegradation = vm.isInMigration() ? 0.2 : 0;
            vm.increaseSlavRate(migrationPerformanceDegradation);
            double timeFrameFinishedCloudletLength = (1 - migrationPerformanceDegradation)
                    * (timeFrame * getTotalCurrentAllocatedMipsForCloudlet(rcl, getPreviousTime()) * Consts.MILLION);
            rcl.updateCloudletFinishedSoFar((long) timeFrameFinishedCloudletLength);
            double estimatedFinishTime = getEstimatedFinishTime(rcl, currentTime);
            estimatedFinishTime = Math.max(estimatedFinishTime, currentTime + CloudSim.getMinTimeBetweenEvents());
            nextEvent = Math.max(nextEvent, estimatedFinishTime);
        }
        setPreviousTime(currentTime);
        if (getCloudletExecList().isEmpty()) {
            return 0;
        }
        return nextEvent;
    }

    @Override
    public double getTotalUtilizationOfCpu(double time) {
        if (getCloudletExecList().isEmpty())
            return 0;
        return getCloudletExecList().get(0).getCloudlet().getUtilizationOfCpu(time);
    }

    @Override
    public double cloudletSubmit(Cloudlet cl, double fileTransferTime) {
        if (getCloudletExecList().size() > 0) {
            throw new RuntimeException("一个Vm中只允许一个Cloudlet");
        }
        ResCloudlet rcl = new ResCloudlet(cl);
        rcl.setCloudletStatus(Cloudlet.INEXEC);
        // log.debug("Cloudlet#{}已经提交到Vm#{}", cl.getCloudletId(), cl.getVmId());
        for (int i = 0; i < cl.getNumberOfPes(); i++) {
            rcl.setMachineAndPeId(0, i);
        }

        getCloudletExecList().add(rcl);
        return getEstimatedFinishTime(rcl, getPreviousTime());
    }
}
