/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.joshua.decoder;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

import org.apache.joshua.decoder.JoshuaConfiguration.SERVER_TYPE;
import org.apache.joshua.decoder.io.TranslationRequestStream;
import org.apache.joshua.server.TcpServer;
import org.apache.joshua.server.ServerThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements decoder initialization, including interaction with <code>JoshuaConfiguration</code>
 * and <code>DecoderThread</code>.
 * 
 * @author Zhifei Li, zhifei.work@gmail.com
 * @author wren ng thornton wren@users.sourceforge.net
 * @author Lane Schwartz dowobeha@users.sourceforge.net
 */
public class JoshuaDecoder {

  private static final Logger LOG = LoggerFactory.getLogger(JoshuaDecoder.class);

  // ===============================================================
  // Main
  // ===============================================================
  public static void main(String[] args) throws IOException {

    JoshuaConfiguration joshuaConfiguration = new JoshuaConfiguration();
    ArgsParser userArgs = new ArgsParser(args,joshuaConfiguration);


    long startTime = System.currentTimeMillis();

    /* Step-0: some sanity checking */
    joshuaConfiguration.sanityCheck();

    /* Step-1: initialize the decoder, test-set independent */
    Decoder decoder = new Decoder(joshuaConfiguration, userArgs.getConfigFile());

    LOG.info("Model loading took {} seconds", (System.currentTimeMillis() - startTime) / 1000);
    LOG.info("Memory used {} MB", ((Runtime.getRuntime().totalMemory()
        - Runtime.getRuntime().freeMemory()) / 1000000.0));

    /* Step-2: Decoding */
    // create a server if requested, which will create TranslationRequest objects
    if (joshuaConfiguration.server_port > 0) {
      int port = joshuaConfiguration.server_port;
      if (joshuaConfiguration.server_type == SERVER_TYPE.TCP) {
        new TcpServer(decoder, port, joshuaConfiguration).start();

      } else if (joshuaConfiguration.server_type == SERVER_TYPE.HTTP) {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        LOG.info("HTTP Server running and listening on port {}.", port);
        server.createContext("/", new ServerThread(null, decoder, joshuaConfiguration));
        server.setExecutor(null); // creates a default executor
        server.start();
      } else {
        LOG.error("Unknown server type");
        System.exit(1);
      }
      return;
    }
    
    // Create the n-best output stream
    FileWriter out = null;
    if (joshuaConfiguration.n_best_file != null)
      out = new FileWriter(joshuaConfiguration.n_best_file);
    
    // Create a TranslationRequest object, reading from a file if requested, or from STDIN
    InputStream input = (joshuaConfiguration.input_file != null) 
      ? new FileInputStream(joshuaConfiguration.input_file)
      : System.in;

    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
    TranslationRequestStream fileRequest = new TranslationRequestStream(reader, joshuaConfiguration);
    decoder.decodeAll(fileRequest, new PrintStream(System.out));
    
    if (joshuaConfiguration.n_best_file != null)
      out.close();

    LOG.info("Decoding completed.");
    LOG.info("Memory used {} MB", ((Runtime.getRuntime().totalMemory()
        - Runtime.getRuntime().freeMemory()) / 1000000.0));

    /* Step-3: clean up */
    decoder.cleanUp();
    LOG.info("Total running time: {} seconds",  (System.currentTimeMillis() - startTime) / 1000);
  }
}
