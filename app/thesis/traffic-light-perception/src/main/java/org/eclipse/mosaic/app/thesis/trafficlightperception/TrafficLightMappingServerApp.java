/*
 * Copyright (c) 2022 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.app.thesis.trafficlightperception;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.eclipse.mosaic.app.thesis.trafficlightperception.config.CTrafficLightMappingServerApp;
import org.eclipse.mosaic.app.thesis.trafficlightperception.messages.TrafficLightMappingMessage;
import org.eclipse.mosaic.app.thesis.trafficlightperception.payloads.TrafficLightMapping;
import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class TrafficLightMappingServerApp
        extends ConfigurableApplication<CTrafficLightMappingServerApp, ServerOperatingSystem>
        implements CommunicationApplication {

    private CTrafficLightMappingServerApp config;

    private File mappedTrafficLightsFile;

    private Map<String, TrafficLightMapping> mappedTrafficLights;

    public TrafficLightMappingServerApp() {
        super(CTrafficLightMappingServerApp.class);
    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "[Startup] App={}, Unit={}.", this.getClass().getSimpleName(), getOs().getId());
        config = getConfiguration();
        if (config.persistentTrafficLightMapping) {
            mappedTrafficLightsFile = new File(getOs().getConfigurationPath(), getConfiguration().trafficLightMappingFileName);
            if (mappedTrafficLightsFile.exists()) {
                readMappedTrafficLights();
            }

        }
        getOs().getCellModule().enable();
    }

    @Override
    public void onShutdown() {
        if (config.persistentTrafficLightMapping) {
            storeMappedTrafficLights();
        }

    }

    private void readMappedTrafficLights() {
        try (Reader reader = new InputStreamReader(Files.newInputStream(mappedTrafficLightsFile.toPath()), StandardCharsets.UTF_8)) {
            final Type tlMappingType = new TypeToken<Map<String, TrafficLightMapping>>() {
            }.getType();
            mappedTrafficLights = new Gson().fromJson(reader, tlMappingType);
        } catch (IOException e) {
            getLog().warnSimTime(this, "Couldn't read mapped TLs.");
        }
        if (mappedTrafficLights == null) {
            mappedTrafficLights = new HashMap<>();
        }
        for (TrafficLightMapping trafficLightMapping : mappedTrafficLights.values()) {
            if (SimulationKernel.SimulationKernel.getCentralPerceptionComponentComponent().mapTrafficLightPosition(
                    trafficLightMapping.getTrafficLightName(),
                    trafficLightMapping.getTrafficLightPosition())
            ) {
                getLog().infoSimTime(this, "[Read TL Mapping] TL={}; position={}",
                        trafficLightMapping.getTrafficLightName(), trafficLightMapping.getTrafficLightPosition());
            }
        }

    }

    private void storeMappedTrafficLights() {
        try {
            mappedTrafficLightsFile.createNewFile();
            getLog().infoSimTime(this, "[File Creation] Created TL mapping file.");
        } catch (IOException e) {
            getLog().warnSimTime(this, "Couldn't create TL mapping file.");
        }
        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(mappedTrafficLightsFile.toPath()), StandardCharsets.UTF_8))) {
            new GsonBuilder().setPrettyPrinting().create().toJson(mappedTrafficLights, writer);
        } catch (IOException e) {
            getLog().warnSimTime(this, "Couldn't store TL Mappings.");
        }
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (receivedV2xMessage.getMessage() instanceof TrafficLightMappingMessage) {
            TrafficLightMappingMessage message = (TrafficLightMappingMessage) receivedV2xMessage.getMessage();
            TrafficLightMapping trafficLightMapping = message.getTrafficLightMapping();
            if (
                    SimulationKernel.SimulationKernel.getCentralPerceptionComponentComponent().mapTrafficLightPosition(
                            trafficLightMapping.getTrafficLightName(),
                            trafficLightMapping.getTrafficLightPosition())
            ) {
                getLog().infoSimTime(this, "[TL Mapping] TL={}; position={}",
                        trafficLightMapping.getTrafficLightName(), trafficLightMapping.getTrafficLightPosition());
                if (config.persistentTrafficLightMapping) {
                    mappedTrafficLights.putIfAbsent(trafficLightMapping.getTrafficLightName(), trafficLightMapping);
                }
            }
        }
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgement) {

    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {

    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {

    }
}
