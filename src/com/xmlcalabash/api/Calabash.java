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
package com.xmlcalabash.api;

import java.io.IOException;
import java.net.URISyntaxException;

import com.xmlcalabash.core.XProcConfiguration;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.util.ErrorMessageRegistry;
import com.xmlcalabash.util.Input;
import com.xmlcalabash.util.Output;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

/**
 * Presents a friendly API to Calabash that makes it easy to run a pipeline
 * over and over again.
 * 
 * <p>
 * This class is thread-safe and can be used by multiple threads concurrently.
 * </p>
 * 
 * @author Jonathan W. Cranford 
 */
public final class Calabash {

    private final XProcConfiguration config;
    private final ErrorMessageRegistry errorRegistry;
	
    /**
     * Creates a Calabash facade object that uses the given configuration.
     */
    public Calabash(XProcConfiguration config) {
        this.config = config;
        errorRegistry = new ErrorMessageRegistry(config.getProcessor().newDocumentBuilder());
    }


    /**
     * Runs the given pipeline with the given input.  This method assumes that
     * the pipeline takes a single input on the primary input port with
     * no parameters or required options.  All output documents on the primary 
     * output port are written to the given Output.
     * 
     * @param pipelineNode
     *            Parsed pipeline to run
     * @param input
     *            Input to be mapped to the primary non-parameter input port.
     * @param output
     *              Where to write the primary output documents
     * 
     * @throws XProcException
     *              on an XProc error running the pipeline
     * @throws IOException 
     *              on I/O error while reading the input or writing the output        
     * @throws SaxonApiException 
     *              on XML-related error while running the pipeline
     * @throws URISyntaxException 
     *              if the output URI has a syntax problem
     */
    public void run(XdmNode pipelineNode, Input input, Output output) 
    throws XProcException, IOException, SaxonApiException, URISyntaxException {
        final PipelineWrapper pipe = loadPipeline(pipelineNode);
        try {
            final String iport = pipe.findPrimaryInputPort();
            if (iport != null) {
                pipe.clearInputs(iport);
                pipe.writeInput(iport, input);
            }

            pipe.run();

            // write primary outputs
            final String oport = pipe.findPrimaryOutputPort();
            if (oport != null) {
               pipe.copyOutputs(oport, output);
            }
        } finally {
            pipe.close();
        }
    }
    
    
    /**
     * Loads the given pipeline.
     * 
     * @throws SaxonApiException on an XML-related error loading the pipeline
     */
    public PipelineWrapper loadPipeline(Input pipeline) throws SaxonApiException {
        XProcRuntime runtime = new XProcRuntime(config);
        return new PipelineWrapper(runtime, runtime.load(pipeline));
    }
    
    
    /**
     * Loads the given pipeline.
     */
    public PipelineWrapper loadPipeline(XdmNode pipeline) {
        XProcRuntime runtime = new XProcRuntime(config);
        return new PipelineWrapper(runtime, runtime.use(pipeline));
    }
    
    
    /** 
     * Returns the registered error message that matches the code from the
     * given exception. 
     * 
     * @param e XProcException to look up 
     * @return matching error message, or UNKNOWN_ERROR if nothing matches
     */
    public String lookupErrorMessage(final XProcException e) {
       return errorRegistry.lookup(e.getErrorCode());
    }
    
    
    /** 
     * Returns the error code and corresponding registered error message. 
     *
     * @param e XProcException to look up 
     * @return formatted string containing the error code and message
     */
    public String errorCodeAndMessage(final XProcException e) {
        final QName code = e.getErrorCode();
        final String msg = errorRegistry.lookup(code);
        if (code == null) {
            return msg;
        }
        final String localName = code.getLocalName();
        return localName + ": " + msg;
    }
    
    
    /**
     * Returns a formatted exception message containing the exception message,
     * the error code, and the registered error message corresponding to the 
     * error code.
     * 
     * @param e exception to lookup
     * @return formatted error message
     */
    public String formattedErrorMessage(final XProcException e) {
        final String msg = e.getMessage();
        final String errorCodeAndMessage = errorCodeAndMessage(e);
        if (isEmpty(msg)) {
            return errorCodeAndMessage;
        } 
        return String.format("%s (%s)", msg, errorCodeAndMessage);
    }

    
    // utility method
    private boolean isEmpty(final String s) {
        return s == null || s.isEmpty();
    }

}
