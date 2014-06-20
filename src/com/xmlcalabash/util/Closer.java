package com.xmlcalabash.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility methods for closing objects that implement the Closeable interface.
 */
public final class Closer {

    // prevent utility class from being created
    private Closer() {
    }

    
    /** 
     * Closes the given Closeable, protecting against nulls.
     */
    public static void close(Closeable c) throws IOException {
        if (c != null) {
            c.close();
        }
    }
    
    /** 
     * Closes the given resource, protecting against nulls.  If an IOException
     * occurs while closing the resource, a warning is logged to the given 
     * logger. 
     */
    public static void closeOrWarn(Closeable c, Logger logger) {
        try {
            close(c);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error closing resource", e); 
        }
    }
}
