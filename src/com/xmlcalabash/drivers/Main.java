/*
 * Main.java
 *
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

package com.xmlcalabash.drivers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.xmlcalabash.api.Calabash;
import com.xmlcalabash.api.PipelineWrapper;
import com.xmlcalabash.core.XProcConfiguration;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.runtime.XPipeline;
import com.xmlcalabash.util.Input;
import com.xmlcalabash.util.Output;
import com.xmlcalabash.util.Output.Kind;
import com.xmlcalabash.util.ParseArgs;
import com.xmlcalabash.util.S9apiUtils;
import com.xmlcalabash.util.UserArgs;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;

import org.xml.sax.InputSource;

import static com.xmlcalabash.util.Output.Kind.OUTPUT_STREAM;
import static java.lang.String.format;

/**
 *
 * @author ndw
 */
public class Main {
    private static int exitStatus = 0;
    private XProcConfiguration config = null;
    private Logger logger = Logger.getLogger(this.getClass().getName());
    private boolean debug = false;
    private Calabash calabash = null;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        Main main = new Main();
        main.run(args);
        System.exit(exitStatus);
    }

    public void run(String[] args) throws IOException {
        UserArgs userArgs = null;
        try {
            userArgs = new ParseArgs().parse(args);
        } catch (XProcException xe) {
            System.err.println(xe.getMessage());
            usage();
        }

        try {
            XProcConfiguration config = userArgs.createConfiguration();

            if (run(userArgs, config)) {
                // It's just sooo much nicer if there's a newline at the end.
                System.out.println();
            }

        } catch (UnsupportedOperationException uoe) {
            usage();
        } catch (XProcException err) {
            exitStatus = 1;
            if (err.getErrorCode() != null) {
                error(logger, null, errorMessage(err), err.getErrorCode());
            } else {
                error(logger, null, err.toString(), null);
            }

            Throwable cause = err.getCause();
            while (cause != null && cause instanceof XProcException) {
                cause = cause.getCause();
            }

            if (cause != null) {
                error(logger, null, "Underlying exception: " + cause, null);
            }

            if (debug) {
                err.printStackTrace();
            }
        } catch (Exception err) {
            exitStatus = 1;
            error(logger, null, "Pipeline failed: " + err.toString(), null);
            if (err.getCause() != null) {
                Throwable cause = err.getCause();
                error(logger, null, "Underlying exception: " + cause, null);
            }
            if (debug) {
                err.printStackTrace();
            }
        } 
    }

    boolean run(UserArgs userArgs, XProcConfiguration config) throws SaxonApiException, IOException, URISyntaxException {
        this.config = config;
        calabash = new Calabash(config);
        debug = config.debug;

        if (userArgs.isShowVersion()) {
            XProcConfiguration.showVersion(config);
        }

        PipelineWrapper pipeline = null;

        if (userArgs.getPipeline() != null) {
            pipeline = calabash.loadPipeline(userArgs.getPipeline());
        } else if (userArgs.hasImplicitPipeline()) {
            final XProcRuntime runtime = new XProcRuntime(config);
            XdmNode implicitPipeline = userArgs.getImplicitPipeline(runtime);

            if (debug) {
                System.err.println("Implicit pipeline:");

                Serializer serializer = new Serializer();

                serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
                serializer.setOutputProperty(Serializer.Property.METHOD, "xml");

                serializer.setOutputStream(System.err);

                S9apiUtils.serialize(runtime, implicitPipeline, serializer);
            }

            pipeline = new PipelineWrapper(runtime, runtime.use(implicitPipeline));
        } else if (config.pipeline != null) {
            XdmNode doc = config.pipeline.read();
            pipeline = calabash.loadPipeline(doc);
        } else {
            throw new UnsupportedOperationException("Either a pipeline or libraries and / or steps must be given");
        }

        try {
            // Process parameters from the configuration...
            for (String port : config.params.keySet()) {
                Map<QName, String> parameters = config.params.get(port);
                setParametersOnPipeline(pipeline, port, parameters);
            }

            // Now process parameters from the command line...
            for (String port : userArgs.getParameterPorts()) {
                Map<QName, String> parameters = userArgs.getParameters(port);
                setParametersOnPipeline(pipeline, port, parameters);
            }

            final XPipeline xpipeline = pipeline.getUnderlyingPipeline();
            Set<String> ports = xpipeline.getInputs();
            Set<String> userArgsInputPorts = userArgs.getInputPorts();
            Set<String> cfgInputPorts = config.inputs.keySet();
            Set<String> allPorts = new HashSet<String>();
            allPorts.addAll(userArgsInputPorts);
            allPorts.addAll(cfgInputPorts);

            // map a given input without port specification to the primary non-parameter input implicitly
            for (String port : ports) {
                if (!allPorts.contains(port) && allPorts.contains(null)
                        && xpipeline.getDeclareStep().getInput(port).getPrimary()
                        && !xpipeline.getDeclareStep().getInput(port).getParameterInput()) {

                    if (userArgsInputPorts.contains(null)) {
                        userArgs.setDefaultInputPort(port);
                        allPorts.remove(null);
                        allPorts.add(port);
                    }
                    break;
                }
            }

            for (String port : allPorts) {
                if (!ports.contains(port)) {
                    throw new XProcException("There is a binding for the port '" + port + "' but the pipeline declares no such port.");
                }

                pipeline.clearInputs(port);

                if (userArgsInputPorts.contains(port)) {
                    for (Input input : userArgs.getInputs(port)) {
                        pipeline.writeInput(port, input);
                    }
                } else {
                    for (ReadablePipe pipe : config.inputs.get(port)) {
                        XdmNode doc = pipe.read();
                        pipeline.writeInput(port, doc);
                    }
                }
            }

            // Implicit binding for stdin?
            String implicitPort = null;
            for (String port : ports) {
                if (!allPorts.contains(port)) {
                    if (xpipeline.getDeclareStep().getInput(port).getPrimary()
                            && !xpipeline.getDeclareStep().getInput(port).getParameterInput()) {
                        implicitPort = port;
                    }
                }
            }

            if (implicitPort != null && !xpipeline.hasReadablePipes(implicitPort)) {
                XdmNode doc = pipeline.getRuntime().parse(new InputSource(System.in));
                pipeline.writeInput(implicitPort, doc);
            }

            Map<String, Output> portOutputs = new HashMap<String, Output>();

            Map<String, Output> userArgsOutputs = userArgs.getOutputs();
            for (String port : xpipeline.getOutputs()) {
                // Bind to "-" implicitly
                Output output = null;

                if (userArgsOutputs.containsKey(port)) {
                    output = userArgsOutputs.get(port);
                } else if (config.outputs.containsKey(port)) {
                    output = new Output(config.outputs.get(port));
                } else if (userArgsOutputs.containsKey(null)
                        && xpipeline.getDeclareStep().getOutput(port).getPrimary()) {
                    // Bind unnamed port to primary output port
                    output = userArgsOutputs.get(null);
                }

                // Look for explicit binding to "-"
                if ((output != null) && (output.getKind() == Kind.URI) && "-".equals(output.getUri())) {
                    output = null;
                }

                portOutputs.put(port, output);
            }

            for (QName optname : config.options.keySet()) {
                RuntimeValue value = new RuntimeValue(config.options.get(optname), null, null);
                pipeline.passOption(optname, value);
            }

            for (QName optname : userArgs.getOptionNames()) {
                RuntimeValue value = new RuntimeValue(userArgs.getOption(optname), null, null);
                pipeline.passOption(optname, value);
            }

            pipeline.run();

            for (String port : xpipeline.getOutputs()) {
                Output output;
                if (portOutputs.containsKey(port)) {
                    output = portOutputs.get(port);
                } else {
                    // You didn't bind it, and it isn't going to stdout, so it's going into the bit bucket.
                    continue;
                }

                if ((output == null) || ((output.getKind() == OUTPUT_STREAM) && System.out.equals(output.getOutputStream()))) {
                    finest(logger, null, "Copy output from " + port + " to stdout");
                } else {
                    switch (output.getKind()) {
                    case URI:
                        finest(logger, null, "Copy output from " + port + " to " + output.getUri());
                        break;

                    case OUTPUT_STREAM:
                        String outputStreamClassName = output.getOutputStream().getClass().getName();
                        finest(logger, null, "Copy output from " + port + " to " + outputStreamClassName + " stream");
                        break;

                    default:
                        throw new UnsupportedOperationException(format("Unsupported output kind '%s'", output.getKind()));
                    }
                }

                pipeline.copyOutputs(port, output);
            }

            return portOutputs.containsValue(null);
        }
        finally {
            // Here all memory should be freed by the next gc, right?
            pipeline.close();
        }
    }

    
    private void setParametersOnPipeline(PipelineWrapper pipeline, String port, Map<QName, String> parameters) {
        if ("*".equals(port)) {
            for (QName name : parameters.keySet()) {
                pipeline.setParameter(name, new RuntimeValue(parameters.get(name)));
            }
        } else {
            for (QName name : parameters.keySet()) {
                pipeline.setParameter(port, name, new RuntimeValue(parameters.get(name)));
            }
        }
    }

    private void usage() throws IOException {
        System.out.println();
        XProcConfiguration.showVersion(config);

        InputStream instream = getClass().getResourceAsStream("/etc/usage.txt");
        if (instream == null) {
            throw new UnsupportedOperationException("Failed to load usage text from JAR file. This \"can't happen\".");
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(instream));
        try {
            String line = null;
            while ((line = br.readLine()) != null) {
                System.err.println(line);
            }
        } finally {
            // BufferedReader.close also closes the underlying stream, so only 
            // one close() call is necessary.
            // instream.close();
            br.close();
        }
        System.exit(1);
    }

    private String errorMessage(XProcException e) {
        return calabash.lookupErrorMessage(e);
    }
    

    // ===========================================================
    // Logging methods repeated here so that they don't rely
    // on the XProcRuntime constructor succeeding.

    private String message(XdmNode node, String message) {
        String baseURI = "(unknown URI)";
        int lineNumber = -1;

        if (node != null) {
            baseURI = node.getBaseURI().toASCIIString();
            lineNumber = node.getLineNumber();
            return baseURI + ":" + lineNumber + ": " + message;
        } else {
            return message;
        }

    }

    public void error(Logger logger, XdmNode node, String message, QName code) {
        logger.severe(message(node, message));
    }

    public void warning(Logger logger, XdmNode node, String message) {
        logger.warning(message(node, message));
    }

    public void info(Logger logger, XdmNode node, String message) {
        logger.info(message(node, message));
    }

    public void fine(Logger logger, XdmNode node, String message) {
        logger.fine(message(node, message));
    }

    public void finer(Logger logger, XdmNode node, String message) {
        logger.finer(message(node, message));
    }

    public void finest(Logger logger, XdmNode node, String message) {
        logger.finest(message(node, message));
    }

    // ===========================================================

}
