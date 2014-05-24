/*
 * Adapted from Calabash source code.
 * 
 * Copyright 2014 The MITRE Corporation.
 * Portions Copyright 2008 Mark Logic Corporation.
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
package com.xmlcalabash.drivers;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import com.xmlcalabash.core.XProcConfiguration;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.runtime.XPipeline;
import com.xmlcalabash.util.Input;

/**
 * Presents a friendly API to Calabash that can be used to run multiple 
 * pipelines.
 * 
 * <p>This class is NOT thread-safe and should only be used by one thread at a 
 * time.</p>
 * 
 * @author Jonathan Cranford (jcranford@mitre.org)
 */
public final class Calabash {

    private final XProcConfiguration config;
	
    /** 
     * Base URI for all pipeline files. Most often, this will be a
     * URI to the base directory for all pipeline files.
     */
    private final URI pipelineBaseURI;
	
    /** 
     * Cache of parsed pipeline files. 
     */
    private Map<URI, XdmNode> pipelineCache = new HashMap<>();
	

    /** 
     * Creates a Calabash facade object that uses the given configuration
     * and given base URI for pipeline files. 
     */ 
    public Calabash(XProcConfiguration config, URI pipelineBaseURI) {
	this.config = config;
	this.pipelineBaseURI = pipelineBaseURI;
    }
	
	
    /** 
     * Runs the given pipeline with the given input.  This method assumes 
     * that the underlying pipeline has a single primary input port, 
     * does not have any parameter ports, and takes no options.  
     * 
     * @param pipelineURI URI of the pipeline to run
     * @param input primary input document
     * @return all the outputs from the pipeline
     */
    public PipelineOutputs run(URI pipelineURI, Input input) {
	final XProcRuntime runtime = new XProcRuntime(config);
	final XPipeline pipeline 
	    = runtime.use(getPipeline(pipelineURI, runtime));  
		
        return new PipelineOutputs();
    }
	
	
    /** 
     * Looks up the given pipelineURI in the cache and loads it on a cache
     * miss, using the given runtime.
     */
    private XdmNode getPipeline(URI pipelineURI, XProcRuntime runtime) {
	XdmNode doc = pipelineCache.get(pipelineURI);
	if (doc == null) {
	    doc = runtime.parse(pipelineURI.toASCIIString(), 
				pipelineBaseURI.toASCIIString());
	    pipelineCache.put(pipelineURI, doc);
	}
	return pipelineCache.get(pipelineURI);
    }
	
	
    /** Represents all the outputs from a pipeline. */
    public static class PipelineOutputs {
	// primary - nullable
	// secondary: List
    }
}
