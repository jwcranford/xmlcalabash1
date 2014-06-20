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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xmlcalabash.core.XProcConfiguration;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritableDocument;
import com.xmlcalabash.model.Serialization;
import com.xmlcalabash.runtime.XPipeline;
import com.xmlcalabash.util.ErrorMessageRegistry;
import com.xmlcalabash.util.Input;
import com.xmlcalabash.util.Output;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

/**
 * Presents a friendly API to Calabash that can be used to run a pipeline
 * over and over again.
 * 
 * <p>
 * This class is NOT thread-safe and should only be used by one thread at a
 * time.
 * </p>
 * 
 * @author Jonathan Cranford (jcranford@mitre.org)
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
        final XProcRuntime runtime = new XProcRuntime(config);
        try {
            final XPipeline pipeline = runtime.use(pipelineNode);
            XdmNode inputDoc = Main.parse(runtime, input);

            final PipelineOutputs outputs  = run(runtime, pipeline, inputDoc); 

            // write primary outputs
            final String oport = outputs.getPrimaryPort();
            if (oport != null) {
                Serialization serial = pipeline.getSerialization(oport);
                WritableDocument wd = Main.createWriteableDocument(runtime, serial, output);
                try {
                    for (XdmNode doc : outputs.getOutputs().get(oport)) {
                        wd.write(doc);
                    }
                } finally {
                    wd.close();
                }
            }
        } finally {
            runtime.close();
        }
    }
    
    
    /**
     * Runs the given pipeline with the given input.  This method assumes that
     * the pipeline takes a single input on the primary input port with
     * no parameters or required options.  All output documents are returned.
     * 
     * @param pipelineNode
     *            Parsed pipeline to run
     * @param inputNode
     *            Input to be mapped to the primary non-parameter input port.
     * @return The outputs of the pipeline
     * @throws SaxonApiException 
     *              on XML-related error while running the pipeline
     */
    public PipelineOutputs run(
            XdmNode pipelineNode,
            XdmNode inputNode) throws SaxonApiException {
        final XProcRuntime runtime = new XProcRuntime(config);
        try {
            final XPipeline pipeline = runtime.use(pipelineNode);
            return run(runtime, pipeline, inputNode);
        } finally {
            runtime.close();
        }
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
    
    
    /**
     * Primary workhorse of this class.  Runs the given pipeline with the 
     * given input, returning all the output documents mapped by port name.
     * This method assumes that
     * the pipeline takes a single input on the primary input port, and that
     * the pipeline takes no parameters or required options.
     * 
     * @throws SaxonApiException
     *              on XML-related error while running the pipeline
     */
    private static PipelineOutputs run(
            XProcRuntime runtime, 
            XPipeline pipeline, 
            XdmNode inputNode) 
    throws SaxonApiException {

        final String iport = findPrimaryInputPort(pipeline);
        if (iport != null) {
            pipeline.clearInputs(iport);
            pipeline.writeTo(iport, inputNode);
        }

        pipeline.run();
        
        // read all outputs
        final Map<String,List<XdmNode>> outputs = new HashMap<String,List<XdmNode>>();
        for (String port : pipeline.getOutputs()) {
            ReadablePipe rpipe = pipeline.readFrom(port);
            final List<XdmNode> nodes = new ArrayList<XdmNode>();
            while (rpipe.moreDocuments()) {
                nodes.add(rpipe.read());
            }
            outputs.put(port, nodes);
        }
        return new PipelineOutputs(
                findPrimaryOutputPort(pipeline),
                outputs);
    }
    
    
    /**
     * Finds the primary input port for the given pipeline.
     *
     * @return name of primary input port, or null if there is none 
     */
    private static String findPrimaryInputPort(XPipeline pipeline) {
        for (String port : pipeline.getInputs()) {
            final com.xmlcalabash.model.Input declareStepInput = pipeline.getDeclareStep().getInput(port);
            if (declareStepInput.getPrimary() 
                    && !declareStepInput.getParameterInput()) {
                return port;
            }
        }
        return null;
    }
    
    /**
     * Finds the primary output port for the given pipeline.
     * 
     * @return the name of the primary output port, or null if there is none
     */
    private static String findPrimaryOutputPort(XPipeline pipeline) {
        for (String port : pipeline.getOutputs()) {
            if (pipeline.getDeclareStep().getOutput(port).getPrimary()) {
                return port;
            }
        }
        return null;
    }
    
    

    /** 
     * Represents the outputs of a pipeline.  If the pipeline has no primary
     * output port, then primaryPort will be null.  If the pipeline has no
     * output ports at all, then the outputs map will be empty.
     */
    public static class PipelineOutputs {
        private final String primaryPort;
        private final Map<String,List<XdmNode>> outputs;
        public PipelineOutputs(
                String primaryPort,
                Map<String, List<XdmNode>> outputs) {
            this.primaryPort = primaryPort;
            this.outputs = outputs;
        }
        public String getPrimaryPort() {
            return primaryPort;
        }
        public Map<String, List<XdmNode>> getOutputs() {
            return outputs;
        }
    }

}
