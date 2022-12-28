package proposed.hvmm.core;

import java.io.IOException;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.UtilizationModelNull;

import lombok.Getter;
import lombok.Setter;

/**
 * @author PiAo
 * @date 2021/12/5
 */
@Setter
@Getter
public class TrafficPowerCloudlet extends Cloudlet {

    public final static UtilizationModel utilizationModelNull = new UtilizationModelNull();
    public final static UtilizationModel utilizationModelFull = new UtilizationModelFull();
    private String workLoadPath;

    public TrafficPowerCloudlet(int cloudletId, String workLoadPath, long cloudletLengthForEachPe,
            int pesNumber) throws IOException {
        super(cloudletId, cloudletLengthForEachPe, pesNumber, -1, -1,
                new TrafficPowerUtilizationModel(workLoadPath, 300),
                // utilizationModelFull,
                utilizationModelNull, utilizationModelNull, false);
        this.workLoadPath = workLoadPath;
    }

    /**
     * Gets the utilization percentage of cpu.
     *
     * @param time the time
     * @return the utilization of cpu
     */
    public double getUtilizationOfCpu(final double time) {
        return getUtilizationModelCpu().getUtilization(Math.max(0, time - getSubmissionTime()));
    }
}
