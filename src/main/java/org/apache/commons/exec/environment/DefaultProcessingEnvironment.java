/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.exec.environment;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.OS;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.util.MapUtils;

/**
 * Helper class to determine the environment variable
 * for the OS. Depending on the JDK the environment
 * variables can be either retrieved directly from the
 * JVM or requires starting a process to get them running
 * an OS command line. 
 */
public class DefaultProcessingEnvironment {

    /** the line seperator of the system */
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    /** the environment variables of the process */
    protected Map procEnvironment;

    /**
     * Find the list of environment variables for this process.
     *
     * @return a map containing the environment variables
     * @throws IOException obtaining the environment variables failed
     */
    public synchronized Map getProcEnvironment() throws IOException {

        if(procEnvironment == null) {
            procEnvironment = this.createProcEnvironment();
        }

        // create a copy of the map just in case that
        // anyone is going to modifiy it, e.g. removing
        // or setting an evironment variable

        return MapUtils.copy(procEnvironment);
    }

    /**
     * Find the list of environment variables for this process.
     *
     * @return a amp containing the environment variables
     * @throws IOException the operation failed 
     */
    protected Map createProcEnvironment() throws IOException {
        if (procEnvironment == null) {
            try {
                Method getenvs = System.class.getMethod( "getenv", (java.lang.Class[]) null );
                Map env = (Map) getenvs.invoke( null, (java.lang.Object[]) null );
                procEnvironment = new HashMap( env );
            } catch ( NoSuchMethodException e ) {
                // ok, just not on JDK 1.5
            } catch ( IllegalAccessException e ) {
                // Unexpected error obtaining environment - using JDK 1.4 method
            } catch ( InvocationTargetException e ) {
                // Unexpected error obtaining environment - using JDK 1.4 method
            }
        }

        if(procEnvironment == null) {
            procEnvironment = new HashMap();
            BufferedReader in = runProcEnvCommand();

            String var = null;
            String line;
            while ((line = in.readLine()) != null) {
                if (line.indexOf('=') == -1) {
                    // Chunk part of previous env var (UNIX env vars can
                    // contain embedded new lines).
                    if (var == null) {
                        var = LINE_SEPARATOR + line;
                    } else {
                        var += LINE_SEPARATOR + line;
                    }
                } else {
                    // New env var...append the previous one if we have it.
                    if (var != null) {
                    	EnvironmentUtil.addVariableToEnvironment(procEnvironment, var);
                    }
                    var = line;
                }
            }
            // Since we "look ahead" before adding, there's one last env var.
            if (var != null) {
            	EnvironmentUtil.addVariableToEnvironment(procEnvironment, var);
            }
        }
        return procEnvironment;
    }

    /**
     * Start a process to list the environment variables.
     *
     * @return a reader containing the output of the process 
     * @throws IOException starting the process failed
     */
    protected BufferedReader runProcEnvCommand() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Executor exe = new DefaultExecutor();
        exe.setStreamHandler(new PumpStreamHandler(out));
        // ignore the exit value - Just try to use what we got
        exe.execute(getProcEnvCommand(), new HashMap());
        return new BufferedReader(new StringReader(toString(out)));
    }

    /**
     * Determine the OS specific command line to get a list of environment
     * variables.
     *
     * @return the command line
     */
    protected CommandLine getProcEnvCommand() {
        String executable;
        String[] arguments = null;
        if (OS.isFamilyOS2()) {
            // OS/2 - use same mechanism as Windows 2000
            executable = "cmd";
            
            arguments = new String[] {"/c", "set"};
        } else if (OS.isFamilyWindows()) {
            // Determine if we're running under XP/2000/NT or 98/95
            if (OS.isFamilyWin9x()) {
                executable = "command.com";
                // Windows 98/95
            } else {
                executable = "cmd";
                // Windows XP/2000/NT/2003
            }
            arguments = new String[] {"/c", "set"};
        } else if (OS.isFamilyZOS() || OS.isFamilyUnix()) {
            // On most systems one could use: /bin/sh -c env

            // Some systems have /bin/env, others /usr/bin/env, just try
            if (new File("/bin/env").canRead()) {
                executable = "/bin/env";
            } else if (new File("/usr/bin/env").canRead()) {
                executable = "/usr/bin/env";
            } else {
                // rely on PATH
                executable = "env";
            }
        } else if (OS.isFamilyNetware() || OS.isFamilyOS400()) {
            // rely on PATH
            executable = "env";
        } else {
            // MAC OS 9 and previous
            // TODO: I have no idea how to get it, someone must fix it
            executable = null;
        }
        CommandLine commandLine = null;
        if(executable != null) {
            commandLine = new CommandLine(executable);
            commandLine.addArguments(arguments);
        }
        return commandLine;
    }

    /**
     * ByteArrayOutputStream#toString doesn't seem to work reliably on OS/390,
     * at least not the way we use it in the execution context.
     * 
     * @param bos
     *            the output stream that one wants to read
     * @return the output stream as a string, read with special encodings in the
     *         case of z/os and os/400
     */
    private String toString(final ByteArrayOutputStream bos) {
        if (OS.isFamilyZOS()) {
            try {
                return bos.toString("Cp1047");
            } catch (java.io.UnsupportedEncodingException e) {
                // noop default encoding used
            }
        } else if (OS.isFamilyOS400()) {
            try {
                return bos.toString("Cp500");
            } catch (java.io.UnsupportedEncodingException e) {
                // noop default encoding used
            }
        }
        return bos.toString();
    }
}
