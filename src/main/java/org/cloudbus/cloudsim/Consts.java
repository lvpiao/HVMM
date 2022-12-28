package org.cloudbus.cloudsim;

/**
 * Defines common constants, used throughout cloudsim.
 *
 * @author nikolay.grozev
 */
public final class Consts {
    
    /**
     * One million.
     */
    public static final int MILLION = 1000000;
    /**
     * One minute time in seconds.
     */
    public static final int MINUTE = 60;
    
    // ================== Time constants ==================
    /**
     * One hour time in seconds.
     */
    public static final int HOUR = 60 * MINUTE;
    /**
     * One day time in seconds.
     */
    public static final int DAY = 24 * HOUR;
    /**
     * One week time in seconds.
     */
    public static final int WEEK = 24 * HOUR;
    /**
     * Constant for *nix Operating Systems.
     */
    public static final String NIX_OS = "Linux/Unix";
    
    // ================== OS constants ==================
    /**
     * Constant for Windows Operating Systems.
     */
    public static final String WINDOWS = "Windows";
    
    /**
     * Suppreses intantiation.
     */
    private Consts() {
    }
}
