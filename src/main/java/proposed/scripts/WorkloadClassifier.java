package proposed.scripts;

import org.apache.commons.io.FileUtils;
import proposed.hvmm.core.Manager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkloadClassifier {

    private static double workloadCpuUtilization(File trace) throws IOException {
        List<String> lines = FileUtils.readLines(trace, StandardCharsets.UTF_8);
        return lines.stream().mapToDouble(Double::parseDouble).average().getAsDouble();
    }

    public static void main(String[] args) throws IOException {
        String path = Manager.baseDataPath + "planetlab";
        File traceList = new File(path);
        Map<Integer, Integer> lvlCnt = new HashMap<>();
        int cnt = 0;
        int gap = 20;
        for (File trace : traceList.listFiles()) {
            for (File traceItem : trace.listFiles()) {
                double avgUtilization = workloadCpuUtilization(traceItem);
                int lvl = (int) Math.floor(avgUtilization) / gap * gap;
                lvlCnt.put(lvl, lvlCnt.getOrDefault(lvl, 0) + 1);
                cnt++;
            }

        }
        for (int i = 0; i <= 5; i++) {
            System.out.println(i * gap + "-" + (i + 1) * gap + "," +
                    lvlCnt.getOrDefault(i * gap, 0) + "," +
                    (double) lvlCnt.getOrDefault(i * gap, 0) / cnt);
        }
    }
}
