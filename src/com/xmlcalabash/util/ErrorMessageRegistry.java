/*
 * ErrorMessageRegistry.java
 * 
 * Copyright (C) 2010, 2014 The MITRE Corporation
 * All rights reserved.
 * 
 * Refactored/extracted from Main.java, which has the following copyright:
 * Copyright 2008 Mark Logic Corporation.
 * Portions Copyright 2007 Sun Microsystems, Inc.
 * All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * https://xproc.dev.java.net/public/CDDL+GPL.html or
 * docs/CDDL+GPL.txt in the distribution. See the License for the
 * specific language governing permissions and limitations under the
 * License. When distributing the software, include this License Header
 * Notice in each file and include the License file at docs/CDDL+GPL.txt.
 */
package com.xmlcalabash.util;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.sax.SAXSource;

import com.xmlcalabash.core.XProcConstants;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.xml.sax.InputSource;

/**
 * Registry that ties error codes to error messages.
 * 
 * @author Jonathan Cranford
 */
public final class ErrorMessageRegistry {

	private static final String DEFAULT_UNKNOWN_ERROR = "Unknown error";

    private static final QName _code = new QName("code");
	
	private final Map<String, String> registry = new HashMap<String,String>();
	
	private static String unknownErrorMessage = DEFAULT_UNKNOWN_ERROR;
	
	public ErrorMessageRegistry(final DocumentBuilder builder) {
		InputStream instream = getClass().getResourceAsStream("/etc/error-list.xml");
        if (instream == null) {
        	throw new UnsupportedOperationException("Failed to load error-list.xml from JAR file. This \"can't happen\".");
        } 
        else {
            try {
				XdmNode doc = builder.build(new SAXSource(new InputSource(instream)));
				XdmSequenceIterator iter = doc.axisIterator(Axis.DESCENDANT, new QName(XProcConstants.NS_XPROC_ERROR,"error"));
				while (iter.hasNext()) {
				    XdmNode error = (XdmNode) iter.next();
				    registry.put(error.getAttributeValue(_code), error.getStringValue());
				}
			} catch (SaxonApiException e) {
	        	throw new UnsupportedOperationException("Error parsing error-list.xml from JAR file!", e);
			} catch (RuntimeException e) {
	        	throw new UnsupportedOperationException("Error parsing error-list.xml from JAR file!", e);
			} finally {
			    Closer.WARN_ON_ERROR.close(instream);
			}
        }
	}
	
	
	/**
	 * Returns the error message that matches the given code.
	 *  
	 * @return registered error message, or unknownErrorMessage if nothing matches
	 */
	public String lookup(QName code) {
		if (code != null) {
            final String msg = registry.get(code.getLocalName());
            if (msg != null) {
                return msg;
            }
        }
        return unknownErrorMessage;
	}


	public static String getUnknownErrorMessage() {
		return unknownErrorMessage;
	}


	public static void setUnknownErrorMessage(String unknownErrorMessage) {
		ErrorMessageRegistry.unknownErrorMessage = unknownErrorMessage;
	}

}
