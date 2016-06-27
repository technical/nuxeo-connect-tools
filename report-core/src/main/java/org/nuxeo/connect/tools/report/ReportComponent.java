/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Stephane Lacoin at Nuxeo (aka matic)
 */
package org.nuxeo.connect.tools.report;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.nuxeo.connect.tools.report.ReportConfiguration.Contribution;
import org.nuxeo.runtime.RuntimeServiceEvent;
import org.nuxeo.runtime.RuntimeServiceListener;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.management.ResourcePublisher;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Reports aggregator, exposed as a service in the runtime.
 *
 * @since 8.3
 */
public class ReportComponent extends DefaultComponent {

    public interface Runner {

        void run(OutputStream out, Set<String> names) throws IOException;

        Set<String> list();
    }

    public static ReportComponent instance;

    public ReportComponent() {
        instance = this;
    }

    final ReportConfiguration configuration = new ReportConfiguration();

    final Service service = new Service();

    class Service implements ReportRunner {
        @Override
        public Set<String> list() {
            Set<String> names = new HashSet<>();
            for (Contribution contrib : configuration) {
                names.add(contrib.name);
            }
            return names;
        }

        @Override
        public void run(OutputStream out, Set<String> names) throws IOException {
            out.write('{');
            for (Contribution contrib : configuration.filter(names)) {
                out.write(contrib.name.getBytes());
                out.write(',');
                contrib.writer.write(out);
            }
            out.write('}');
        }
    }

    final Management management = new Management();

    public class Management implements ReportServer {

        @Override
        public void run(String host, int port, String... names) throws IOException {
            try (Socket sock = new Socket(host, port)) {
                service.run(sock.getOutputStream(), new HashSet<>(Arrays.asList(names)));
            }
        }

    }

    @Override
    public void applicationStarted(ComponentContext context) {
        instance = this;
        Framework.getService(ResourcePublisher.class).registerResource("connect-report", "connect-report", ReportServer.class, management);
        Framework.addListener(new RuntimeServiceListener() {

            @Override
            public void handleEvent(RuntimeServiceEvent event) {
                if (event.id != RuntimeServiceEvent.RUNTIME_ABOUT_TO_STOP) {
                    return;
                }
                Framework.removeListener(this);
                Framework.getService(ResourcePublisher.class).unregisterResource("connect-report", "connect-report");
            }
        });
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (contribution instanceof Contribution) {
            configuration.addContribution((Contribution) contribution);
        } else {
            throw new IllegalArgumentException(String.format("unknown contribution of type %s in %s", contribution.getClass(), contributor));
        }
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter.isAssignableFrom(Service.class)) {
            return adapter.cast(service);
        }
        return super.getAdapter(adapter);
    }

}