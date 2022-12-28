package proposed.hvmm.core;

import org.cloudbus.cloudsim.power.models.PowerModelSpecPower;

import lombok.Getter;

/**
 * @author PiAo
 * @date 2021/12/15
 */

@Getter
public class TrafficPowerPowerModel extends PowerModelSpecPower {

    private double[] data;

    public TrafficPowerPowerModel(double[] data) {
        this.data = data;
    }

    @Override
    protected double getPowerData(int index) {
        return data[index];
    }
}
