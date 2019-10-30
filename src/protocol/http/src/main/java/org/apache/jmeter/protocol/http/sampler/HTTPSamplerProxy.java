/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.jmeter.protocol.http.sampler;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.samplers.Interruptible;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdFlavor;
import io.micrometer.statsd.StatsdMeterRegistry;

/**
 * Proxy class that dispatches to the appropriate HTTP sampler.
 * <p>
 * This class is stored in the test plan, and holds all the configuration settings.
 * The actual implementation is created at run-time, and is passed a reference to this class
 * so it can get access to all the settings stored by HTTPSamplerProxy.
 */
public final class HTTPSamplerProxy extends HTTPSamplerBase implements Interruptible {
    private static final long    serialVersionUID = 1L;
    private static final String micrometerMetricTotalName = "jmeter.http.total";
    private static final String micrometerMetricConnectName = "jmeter.http.connect";
    private static final String micrometerMetricLatencyName = "jmeter.http.latency";
    private static final String micrometerMetricIdleName = "jmeter.http.idle";
    private static final String micrometerMetricBytesSentName = "jmeter.http.bytes_sent";
    private static final String micrometerMetricBytesRecvName = "jmeter.http.bytes_recv";
    private static final Logger  log = LoggerFactory.getLogger(HTTPSamplerBase.class);

    private static boolean       micrometerRegistryTriedSetup = false;
    private static MeterRegistry micrometerRegistry = null;

    private transient HTTPAbstractImpl impl;

    public HTTPSamplerProxy(){
        super();
    }

    /**
     * Convenience method used to initialise the implementation.
     *
     * @param impl the implementation to use.
     */
    public HTTPSamplerProxy(String impl){
        super();
        setImplementation(impl);
    }

    /** {@inheritDoc} */
    @Override
    protected HTTPSampleResult sample(URL u, String method, boolean areFollowingRedirect, int depth) {
        // When Retrieve Embedded resources + Concurrent Pool is used
        // as the instance of Proxy is cloned, we end up with impl being null
        // testIterationStart will not be executed but it's not a problem for 51380 as it's download of resources
        // so SSL context is to be reused
        if (impl == null) { // Not called from multiple threads, so this is OK
            try {
                impl = HTTPSamplerFactory.getImplementation(getImplementation(), this);
            } catch (Exception ex) {
                return errorResult(ex, new HTTPSampleResult());
            }
        }
        return registerSample(impl.sample(u, method, areFollowingRedirect, depth));
    }

    // N.B. It's not possible to forward threadStarted() to the implementation class.
    // This is because Config items are not processed until later, and HTTPDefaults may define the implementation

    @Override
    public void threadFinished(){
        if (impl != null){
            impl.threadFinished(); // Forward to sampler
        }
    }

    @Override
    public boolean interrupt() {
        if (impl != null) {
            return impl.interrupt(); // Forward to sampler
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase#testIterationStart(org.apache.jmeter.engine.event.LoopIterationEvent)
     */
    @Override
    public void testIterationStart(LoopIterationEvent event) {
        if (impl != null) {
            impl.notifyFirstSampleAfterLoopRestart();
        }
    }

    synchronized private static void registryInitialize() {
        // If we've already tried to initialize the registry, just return
        if (micrometerRegistryTriedSetup) {
            return;
        }

        try {
            final StatsdConfig config = new StatsdConfig() {
                @Override
                public String get(String k) {
                    return null;
                }

                @Override
                public StatsdFlavor flavor() {
                    return StatsdFlavor.DATADOG;
                }
            };

            micrometerRegistry = new StatsdMeterRegistry(config, Clock.SYSTEM);

            log.info("PayPay: Datadog initialized.");
        } catch (Exception ex) {
            log.info("PayPay: Unable to create MicroMeter/Datadog registry.", ex);

            micrometerRegistry = null;
        }

        // Always set to initialized so we don't keep on trying to initialize it
        micrometerRegistryTriedSetup = true;
    }

    private static HTTPSampleResult registerSample(final HTTPSampleResult sampleResult) {
        if (null == sampleResult) {
            return null;
        }

        try {
            // If the registry is not setup, try to set it up (synchronized static)
            if (!micrometerRegistryTriedSetup) {
                registryInitialize();
            }

            // If the registry is available, log the sample stats
            if (null != micrometerRegistry) {
                final String label = sampleResult.getSampleLabel();

                final List<Tag> tags = new ArrayList<>(8);

                tags.add(0, Tag.of("env", "perf"));
                tags.add(1, Tag.of("jmeter", "true"));
                tags.add(2, Tag.of("jmeter_http_label", sampleResult.getSampleLabel()));
                tags.add(3, Tag.of("jmeter_http_url_path", sampleResult.getURL().getPath()));
                tags.add(4, Tag.of("jmeter_http_url_host", sampleResult.getURL().getHost()));
                tags.add(5, Tag.of("jmeter_http_response_code", sampleResult.getResponseCode()));
                tags.add(6, Tag.of("jmeter_http_errors", sampleResult.getErrorCount() > 0 ? "true" : "false"));
                tags.add(7, Tag.of("jmeter_http_thread", sampleResult.getThreadName()));

                micrometerRegistry.timer(micrometerMetricTotalName, tags).record(Duration.ofMillis(sampleResult.getTime()));
                micrometerRegistry.timer(micrometerMetricConnectName, tags).record(Duration.ofMillis(sampleResult.getConnectTime()));
                micrometerRegistry.timer(micrometerMetricIdleName, tags).record(Duration.ofMillis(sampleResult.getIdleTime()));
                micrometerRegistry.timer(micrometerMetricLatencyName, tags).record(Duration.ofMillis(sampleResult.getLatency()));
                micrometerRegistry.counter(micrometerMetricBytesSentName, tags).increment(sampleResult.getSentBytes());
                micrometerRegistry.counter(micrometerMetricBytesRecvName, tags).increment(sampleResult.getBytesAsLong());
            }
        } catch (Exception ex) {
            log.info("PayPay: Unable to record sample with statsd/Datadog.", ex);
        }

        return sampleResult;
    }
}
