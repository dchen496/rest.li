/*
   Copyright (c) 2012 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.restli.server;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.linkedin.parseq.Engine;
import com.linkedin.parseq.EngineBuilder;
import com.linkedin.r2.filter.FilterChains;
import com.linkedin.r2.transport.common.bridge.server.TransportDispatcher;
import com.linkedin.r2.transport.http.server.HttpServer;
import com.linkedin.r2.transport.http.server.HttpServerFactory;
import com.linkedin.restli.docgen.DefaultDocumentationRequestHandler;
import com.linkedin.restli.server.resources.PrototypeResourceFactory;

/**
 * Configures and launches a rest.li application, served via a minimum standalone Jetty HttpServer
 * which is not intended for production use due lack of manageability features. This server will
 * <em>only</em> serve RestLi resources.
 *
 * @author dellamag
 */
public class StandaloneLauncher
{
  private final int _port;
  private final String[] _packages;
  private final HttpServer _server;

  /**
   * Construct a new standalone RestLi server that will listen on the given port and serve RestLi
   * resources from the given packages.
   *
   * @param port the port to listen on
   * @param packages package names to scan for RestLi resources
   */
  public StandaloneLauncher(final int port, final String... packages)
  {
    _port = port;
    _packages = packages;

    final RestLiConfig config = new RestLiConfig();
    config.setDocumentationRequestHandler(new DefaultDocumentationRequestHandler());
    config.setServerNodeUri(URI.create("/"));
    config.addResourcePackageNames(_packages);

    final int numCores = Runtime.getRuntime().availableProcessors();
    final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(numCores + 1);
    final Engine engine = new EngineBuilder()
        .setTaskExecutor(scheduler)
        .setTimerScheduler(scheduler)
        .build();

    final RestLiServer restServer = new RestLiServer(config, new PrototypeResourceFactory(), engine);
    final TransportDispatcher dispatcher = new DelegatingTransportDispatcher(restServer);
    _server = new HttpServerFactory(FilterChains.empty()).createServer(_port, dispatcher);
  }

  /**
   * @return the port that this server is listening on
   */
  public int getPort()
  {
    return _port;
  }

  /**
   * @return the packages that this server scanned for RestLi resources
   */
  public String[] getPackages()
  {
    return _packages;
  }

  /**
   * Start the server
   *
   * @throws IOException server startup fails
   */
  public void start() throws IOException
  {
    _server.start();
  }

  /**
   * Stop the server
   *
   * @throws IOException server shutdown fails
   */
  public void stop() throws IOException
  {
    _server.stop();
  }

  /**
   * Start a standalone launcher using configuration specified in args. The port defaults to 1338
   * and is optional, but the packages are required.
   *
   * @param args <code>[-port port] [-packages package1,package2,...]</code>
   * @throws IOException startup/shutdown fails
   */
  public static void main(final String[] args) throws IOException
  {
    final StandaloneLauncher launcher = configureLauncher(args);
    launcher.start();

    System.out.printf("HttpServer running on port %d. Press any key to stop server",
                      launcher.getPort());
    System.in.read();

    launcher.stop();
  }

  /**
   * Parse command line arguments
   */
  private static StandaloneLauncher configureLauncher(final String... args)
  {
    if (args.length < 2)
    {
      help();
    }

    int port = 1338;
    String[] packages = null;
    for (int i = 0; i < args.length; i++)
    {
      final boolean hasValueArg = i + 1 < args.length;
      if (args[i].equals("-port"))
      {
        if (hasValueArg)
        {
          try
          {
            port = Integer.parseInt(args[i + 1]);
          }
          catch (final NumberFormatException e)
          {
            System.out.println("Invalid port number: " + args[i + 1]);
            help();
          }
        }
        else
        {
          System.out.println("Missing port number");
          help();
        }
      }
      else if (args[i].equals("-packages"))
      {
        if (hasValueArg)
        {
          packages = args[i + 1].split(",");
        }
        else
        {
          System.out.println("Missing packages");
        }
      }
    }

    if (packages == null)
    {
      help();
    }

    return new StandaloneLauncher(port, packages);
  }

  /**
   * Print a usage message and quit
   */
  private static void help()
  {
    System.out.println("Usage: launcher [-port port] [-packages package1,package2,...]");
    System.exit(0);
  }
}
