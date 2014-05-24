package com.xmlcalabash.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton strategy pattern for closing objects that implement the 
 * Closeable interface.
 */
public class Closer {
	
	public static final Closer PROPAGATE_ON_ERROR = new Closer();
	public static final WarningCloser WARN_ON_ERROR = new WarningCloser();
	
	private static Closer instance = PROPAGATE_ON_ERROR;
	
    // prevent utility class from being created outside of sub-classes
    protected Closer() {
    }

    public static final Closer getInstance() {
		return instance;
	}

	public static final void setInstance(Closer instance) {
		if (instance != null) {
			Closer.instance = instance;
		}
	}

	/** 
     * Closes the given Closeable, protecting against nulls.
     */
    public void close(Closeable c) throws IOException {
        if (c != null) {
            c.close();
        }
    }
    
}

final class WarningCloser extends Closer {
	
	private final Logger logger = Logger.getLogger(getClass().getName());
	
    /** 
     * Closes the given resource, protecting against nulls.  If an IOException
     * occurs while closing the resource, a warning is logged to the given 
     * logger. 
     */
	@Override
    public void close(Closeable c) {
        try {
            super.close(c);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error closing resource", e); 
        }
    }
}
