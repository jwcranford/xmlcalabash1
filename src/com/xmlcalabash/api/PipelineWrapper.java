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

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritableDocument;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.model.Serialization;
import com.xmlcalabash.runtime.XPipeline;
import com.xmlcalabash.util.Input;
import com.xmlcalabash.util.Output;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import static java.lang.String.format;

/**
 * Wrapper around a pipeline that exposes all the methods necessary to 
 * set the inputs, options, and parameters, run it, and gather the outputs.   
 * 
 * <p>
 * This class is NOT thread-safe and should only be used by one thread at a time.
 * </p>
 */
public final class PipelineWrapper implements Closeable {

    private final XProcRuntime runtime;
    private final XPipeline pipeline;
    
    
    /**
     * Constructor. Clients should use {@link Calabash#loadPipeline(Input)} or 
     * {@link Calabash#loadPipeline(XdmNode)} to get a PipelineWrapper
     * instead of calling this constructor directly.
     */
    public PipelineWrapper(XProcRuntime runtime, XPipeline pipeline) {
        this.runtime = runtime;
        this.pipeline = pipeline;
    }

    
    /**
     * Sets the given parameter on the primary parameter port.
     * 
     * @see com.xmlcalabash.runtime.XStep#setParameter(net.sf.saxon.s9api.QName, com.xmlcalabash.model.RuntimeValue)
     */
    public void setParameter(QName name, RuntimeValue value) {
        pipeline.setParameter(name, value);
    }


    /**
     * Sets the given parameter on the given port.
     * 
     * @see com.xmlcalabash.runtime.XStep#setParameter(java.lang.String, net.sf.saxon.s9api.QName, com.xmlcalabash.model.RuntimeValue)
     */
    public void setParameter(String port, QName name, RuntimeValue value) {
        pipeline.setParameter(port, name, value);
    }

    
    /**
     * Clears the inputs specified for the given port.  
     * 
     * @see com.xmlcalabash.runtime.XPipeline#clearInputs(java.lang.String)
     */
    public void clearInputs(String port) {
        pipeline.clearInputs(port);
    }


    /**
     * Writes the given input to the given port.
     * 
     * @throws IOException 
     *              on an error closing an input stream
     * @see XPipeline#writeTo(String, XdmNode)
     */
    public void writeInput(String port, Input input) 
            throws IOException {
        pipeline.writeTo(port, runtime.parse(input));
    }

    
    /**
     * Writes the given input to the given port.
     * 
     * @see XPipeline#writeTo(String, XdmNode)
     */
    public void writeInput(String port, XdmNode input) {
        pipeline.writeTo(port, input);
    }

    
    /**
     * Passes the given option into the underlying pipeline.
     * 
     * @see com.xmlcalabash.runtime.XPipeline#passOption(net.sf.saxon.s9api.QName, com.xmlcalabash.model.RuntimeValue)
     */
    public void passOption(QName name, RuntimeValue value) {
        pipeline.passOption(name, value);
    }


    /** 
     * Runs the pipeline.  This method should only be called once.
     * 
     * @return the outputs of the pipeline
     * @throws XProcException
     *              on an XProc error running the pipeline
     * @throws SaxonApiException 
     *              on XML-related error while running the pipeline
     */
    public void run() throws SaxonApiException, XProcException {
        pipeline.run();
    }
    
    
    /** 
     * Closes the underlying XProcRuntime.  This method MUST be called to 
     * prevent a resource leak.
     */
    @Override
    public void close() {
       runtime.close(); 
    }



    /**
     * Returns name of primary input port, or null if there is none. 
     */
    public String findPrimaryInputPort() {
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
     * Reads the outputs from the given port.
     * 
     * @see com.xmlcalabash.runtime.XPipeline#readFrom(java.lang.String)
     */
    public ReadablePipe readOutputs(String port) {
        return pipeline.readFrom(port);
    }
    
    
    /**
     * Returns the name of the primary output port, or null if there is none.
     */
    public String findPrimaryOutputPort() {
        for (String port : pipeline.getOutputs()) {
            if (pipeline.getDeclareStep().getOutput(port).getPrimary()) {
                return port;
            }
        }
        return null;
    }


    /**
     * @return the current runtime in use
     */
    public XProcRuntime getRuntime() {
        return runtime;
    }


    /**
     * @return the underlying pipeline
     */
    public XPipeline getUnderlyingPipeline() {
        return pipeline;
    }


    /**
     * Copies the outputs from a given port.
     * 
     * @throws URISyntaxException 
     *              if the output URI has a syntax problem
     * @throws FileNotFoundException 
     *              if a file can't be created from the output URI
     * @throws SaxonApiException 
     *              on an XML-related error reading the output
     */
    public void copyOutputs(String port, Output output) 
            throws FileNotFoundException, URISyntaxException, SaxonApiException {
        Serialization serial = pipeline.getSerialization(port);
    
        if (serial == null) {
            // Use the configuration options
            // FIXME: should each of these be considered separately?
            // FIXME: should there be command-line options to override these settings?
            serial = new Serialization(runtime, pipeline.getNode()); // The node's a hack
            for (String name : runtime.getConfiguration().serializationOptions.keySet()) {
                String value = runtime.getConfiguration().serializationOptions.get(name);
    
                if ("byte-order-mark".equals(name)) serial.setByteOrderMark("true".equals(value));
                if ("escape-uri-attributes".equals(name)) serial.setEscapeURIAttributes("true".equals(value));
                if ("include-content-type".equals(name)) serial.setIncludeContentType("true".equals(value));
                if ("indent".equals(name)) serial.setIndent("true".equals(value));
                if ("omit-xml-declaration".equals(name)) serial.setOmitXMLDeclaration("true".equals(value));
                if ("undeclare-prefixes".equals(name)) serial.setUndeclarePrefixes("true".equals(value));
                if ("method".equals(name)) serial.setMethod(new QName("", value));
    
                // FIXME: if ("cdata-section-elements".equals(name)) serial.setCdataSectionElements();
                if ("doctype-public".equals(name)) serial.setDoctypePublic(value);
                if ("doctype-system".equals(name)) serial.setDoctypeSystem(value);
                if ("encoding".equals(name)) serial.setEncoding(value);
                if ("media-type".equals(name)) serial.setMediaType(value);
                if ("normalization-form".equals(name)) serial.setNormalizationForm(value);
                if ("standalone".equals(name)) serial.setStandalone(value);
                if ("version".equals(name)) serial.setVersion(value);
            }
        }
    
        final WritableDocument wd = createWriteableDocument(serial, output);
    
        try {
            ReadablePipe rpipe = pipeline.readFrom(port);
            while (rpipe.moreDocuments()) {
                wd.write(rpipe.read());
            }
        } finally {
            if (output != null) {
                wd.close();
            }
        }
    }


    /**
     * Creates a WriteableDocument for the given output.
     * 
     * @throws URISyntaxException
     *              if the output URI has a syntax problem
     * @throws FileNotFoundException
     *              if a file can't be created from the output URI 
     */
    private WritableDocument createWriteableDocument(
            Serialization serial, 
            Output output)
            throws URISyntaxException, FileNotFoundException {
        // I wonder if there's a better way...
        WritableDocument wd = null;
        if (output == null) {
            wd = new WritableDocument(runtime, null, serial);
        } else {
            switch (output.getKind()) {
                case URI:
                    URI furi = new URI(output.getUri());
                    String filename = furi.getPath();
                    FileOutputStream outfile = new FileOutputStream(filename);
                    wd = new WritableDocument(runtime, filename, serial, outfile);
                    break;
        
                case OUTPUT_STREAM:
                    OutputStream outputStream = output.getOutputStream();
                    wd = new WritableDocument(runtime, null, serial, outputStream);
                    break;
        
                default:
                    throw new UnsupportedOperationException(format("Unsupported output kind '%s'", output.getKind()));
            }
        }
        return wd;
    }
    
}