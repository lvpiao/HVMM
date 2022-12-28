package proposed.hvmm.compares;

import java.util.List;
import proposed.hvmm.compares.allocation.VmAllocationPolicyJSA2021;
import proposed.hvmm.core.TrafficPowerHost;


public class VmManagePolicyJointPTR extends VmAllocationPolicyJSA2021 {


    public VmManagePolicyJointPTR(List<TrafficPowerHost> hostList) {
        super(hostList);
    }
}
