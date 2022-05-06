/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
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

import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.SimplePerceptionConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SimpleTrafficLightPerceptionApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication {

    private static final String EVENT_RESOURCE = "PERCEPTION";
    private static final long queryInterval = 2 * TIME.SECOND;

    private static final double VIEWING_ANGLE = 108d;
    private static final double VIEWING_RANGE = 25d;

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Started {} on {}.", this.getClass().getSimpleName(), getOs().getId());

        enablePerceptionModule();
        schedulePerception();
    }

    private void enablePerceptionModule() {
        SimplePerceptionConfiguration perceptionModuleConfiguration = new SimplePerceptionConfiguration(VIEWING_ANGLE, VIEWING_RANGE);
        getOs().getPerceptionModule().enable(perceptionModuleConfiguration);
    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void processEvent(Event event) throws Exception {
        if (event.getResource() != null && event.getResource() instanceof String) {
            if (event.getResource().equals(EVENT_RESOURCE)) {
                perceiveTrafficLights();
                schedulePerception();
            }
        }
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {

    }

    private void schedulePerception() {
        getOs().getEventManager().newEvent(getOs().getSimulationTime() + queryInterval, this).withResource(EVENT_RESOURCE).schedule();
    }

    private void perceiveTrafficLights() {
        List<TrafficLightObject> perceivedTrafficLights = getOs().getPerceptionModule().getPerceivedTrafficLights();
        perceivedTrafficLights.forEach(trafficLightObject -> {
            if (getOs().getVehicleData() != null && getOs().getRoadPosition() != null) {
                // TODO: to get responsible traffic light we would need upcoming edge/lane
                if (isRelevantTrafficLight(trafficLightObject)) {
                    getLog().infoSimTime(this, "Traffic Light {} is on my route and shows {}", trafficLightObject.getId(), trafficLightObject.getTrafficLightState());
                }
            }
        });
//        getLog().infoSimTime(this, "Perceived traffic lights: {}", perceivedTrafficLights.stream().map(TrafficLightObject::getId).collect(Collectors.toList()));
    }

    private boolean isRelevantTrafficLight(TrafficLightObject trafficLightObject) {
        String currentLaneString = getOs().getRoadPosition().getConnectionId() + "_" + getOs().getRoadPosition().getLaneIndex();
        String nextConnectionOnRoute = getNextConnectionOnRoute();
        return currentLaneString.equals(trafficLightObject.getIncomingLane())
                && nextConnectionOnRoute != null && nextConnectionOnRoute.equals(trafficLightObject.getOutgoingLane().split("_")[0]);
    }

    private String getNextConnectionOnRoute() {
        String currentConnection = getOs().getRoadPosition().getConnectionId();
        List<String> currentRoute = Validate.notNull(getOs().getNavigationModule().getCurrentRoute(),
                "Currently no route available").getConnectionIds();
        for (int i = 0; i < currentRoute.size(); i++) {
            if (currentRoute.get(i).equals(currentConnection)) {
                if (i < currentRoute.size() - 1) {
                    return currentRoute.get(i + 1);
                }
            }
        }
        return null;
    }
}
