package proposed.hvmm.core;

import java.io.IOException;

import org.cloudbus.cloudsim.UtilizationModelPlanetLabInMemory;


/**
 * @author PiAo
 * @date 2021/12/8
 */
public class TrafficPowerUtilizationModel extends UtilizationModelPlanetLabInMemory {
    public TrafficPowerUtilizationModel(String inputPath, double schedulingInterval)
            throws NumberFormatException, IOException {
        super(inputPath, schedulingInterval);
    }

    @Override
    public double getUtilization(double time) {
        if (time % getSchedulingInterval() == 0) {
            return getCpuUtilization((int) time / (int) getSchedulingInterval());
        }
        int time1 = (int) Math.floor(time / getSchedulingInterval());
        int time2 = (int) Math.ceil(time / getSchedulingInterval());
        // log.debug(time1 + " to " + time2);
        double utilization1 = getCpuUtilization(time1);
        double utilization2 = getCpuUtilization(time2);
        double delta = (utilization2 - utilization1) / ((time2 - time1) * getSchedulingInterval());
        double baseUtilization = utilization1 + delta * (time - time1 * getSchedulingInterval());
        return baseUtilization;
    }

    public double getCpuUtilization(int idx) {
        double[] data = getData();
        return data[idx % data.length];
    }
}
