package proposed.hvmm.core;


/**
 * @author PiAo
 * @date 2021/12/2
 */
public class Manager {

    public final static String baseDataPath = "/home/lvpiao/code/hvmm-source-code/data/";
    // public final static String baseDataPath = "F:\\simulation-cloudsim\\data\\";
    public static final int PE = 0;
    public static final int MIPS = 1;
    public static final int RAM = 2;
    public static final int BW = 3;

    public static final boolean USE_MIGRATION = true;
    public static final int MIGRATION_INTERVAL = 5 * 60; // 秒

    public static final int FAT_TREE_POD_COUNT = 16;
    public static final int FAT_TREE_RACK_COUNT = (int) (Math.pow(FAT_TREE_POD_COUNT, 2)) / 2;
    public static final int MAX_HOST_COUNT = (int) ((Math.pow(FAT_TREE_POD_COUNT, 3))) / 4;
    public static final int MAX_VM_CNT = 300000;

    public static final double SCHEDULING_INTERVAL = 5 * 60;
    public static final int ONE_DAY_SECOND = 24 * 60 * 60;
    public static final int SIMULATE_DAYS = 1;

    public static boolean DISABLE_CLOUDSIM_LOG = true;
    public static boolean LOAD_DATA_FROM_FILES = false;
    public static double HOST_OVERLOAD_THR = 0.9;
    public static boolean USE_OVERLOAD_DEGRADATION = false;

    // 对比实验改动参数
    public static int BROKER_COUNT = 200;
    public static int WORKLOAD_UP_THR = 100;
    public static int WORKLOAD_DOWN_THR = 0;

}
