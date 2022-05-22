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

import org.apache.commons.lang3.Validate;
import org.eclipse.mosaic.app.thesis.trafficlightperception.config.CTrafficLightMappingApp;
import org.eclipse.mosaic.app.thesis.trafficlightperception.messages.TrafficLightMappingMessage;
import org.eclipse.mosaic.app.thesis.trafficlightperception.payloads.TrafficLightMapping;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.SimplePerceptionConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.math.VectorUtils;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class TrafficLightMappingApp extends ConfigurableApplication<CTrafficLightMappingApp, VehicleOperatingSystem>
        implements VehicleApplication, CommunicationApplication {
    /**
     * Name to recognise event.
     */
    private static final String EVENT_RESOURCE = "PERCEPTION";
    /**
     * Store configuration here to avoid multiple {@link super#getConfiguration()} calls.
     */
    private CTrafficLightMappingApp config;
    /**
     * Position from previous update. (Stored to detect if vehicle is moving)
     */
    private GeoPoint previousPosition;
    /**
     * Position from current update. (Stored to detect if vehicle is moving)
     */
    private GeoPoint currentPosition;

    public TrafficLightMappingApp() {
        super(CTrafficLightMappingApp.class);
    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "[Startup] App={}, Vehicle={}.", this.getClass().getSimpleName(), getOs().getId());
        config = getConfiguration();
        enablePerceptionModule();
        getOs().getCellModule().enable();
        schedulePerception();
    }

    private void enablePerceptionModule() {
        SimplePerceptionConfiguration perceptionModuleConfiguration =
                new SimplePerceptionConfiguration(config.viewingAngle, config.viewingRange);
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
        if (previousVehicleData != null) {
            previousPosition = previousVehicleData.getPosition();
        }
        currentPosition = updatedVehicleData.getPosition();
    }

    private void schedulePerception() {
        getOs().getEventManager().newEvent(getOs().getSimulationTime() + config.perceptionInterval, this)
                .withResource(EVENT_RESOURCE).schedule();
    }

    private void perceiveTrafficLights() {
        List<TrafficLightObject> perceivedTrafficLights = getOs().getPerceptionModule().getPerceivedTrafficLights();
        perceivedTrafficLights.forEach(trafficLightObject -> {
            if (getOs().getVehicleData() != null && getOs().getRoadPosition() != null) {
                if (isRelevantTrafficLight(trafficLightObject)) {
                    mapTrafficLightPosition(trafficLightObject);
                    double distanceToTrafficLight = getDistanceToTrafficLight(trafficLightObject);
                    getLog().debugSimTime(
                            this, "[perceived] TL={}, distance={}, state={}, isMapped={}",
                            trafficLightObject.getId(), distanceToTrafficLight,
                            trafficLightObject.getTrafficLightState(), trafficLightObject.isMapped()
                    );
                }
            }
        });
    }

    private void mapTrafficLightPosition(TrafficLightObject trafficLightObject) {
        if (trafficLightObject.isMapped()) { // TLs can only be mapped once
            return;
        }
        if (vehiclesInFrontOnSameEdge()) { // this vehicle is the first in queue
            return;
        }
        if (currentPosition == null || previousPosition == null
                || currentPosition.distanceTo(previousPosition) >= 0.05) { // vehicle is not moving noticeably
            return;
        }
        if (!trafficLightObject.getTrafficLightState().equals(TrafficLightState.RED)) { // TL shows red
            return;
        }
        // add min-gap of vehicle to position of the traffic light
        GeoPoint trafficLightPosition = getOs().getPosition().toVector3d()
                .add(VectorUtils.getDirectionVectorFromHeading(
                                getOs().getVehicleData().getHeading(), new Vector3d())
                        .multiply(config.distanceToTrafficLight)
                ).toGeo();
        TrafficLightMapping trafficLightMapping = new TrafficLightMapping(trafficLightObject.getId(), trafficLightPosition);
        getOs().getCellModule().sendV2xMessage(new TrafficLightMappingMessage(getRoutingToServer(), trafficLightMapping));
        getLog().infoSimTime(this, "[TL Mapping Message] recipient={}, Content[tl={}, original={}, new={}]",
                config.trafficLightMappingServerName, trafficLightObject.getId(), trafficLightObject.toGeo(), trafficLightPosition);
    }

    private boolean vehiclesInFrontOnSameEdge() {
        List<VehicleObject> perceivedVehicles = getOs().getPerceptionModule().getPerceivedVehicles();
        if (perceivedVehicles.isEmpty()) {
            return false;
        }
        for (VehicleObject vehicleObject : perceivedVehicles) { // check if any vehicles are in front of ego-vehicle
            IRoadPosition otherVehiclePosition = getOs().getNavigationModule().getClosestRoadPosition(vehicleObject.toGeo());
            if (Objects.equals(otherVehiclePosition.getConnectionId(), getOs().getRoadPosition().getConnectionId())
                /*&& otherVehiclePosition.getLaneIndex() == getOs().getRoadPosition().getLaneIndex()*/) { // FIXME: Lane is not set in getClosestRoadPosition
                return true;
            }
        }
        return false;
    }

    private double getDistanceToTrafficLight(TrafficLightObject trafficLightObject) {
        return getOs().getPosition().distanceTo(trafficLightObject.getProjectedPosition().toGeo());
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

    /**
     * Gets a {@link MessageRouting} to the server.
     *
     * @return topocast MessageRouting
     */
    private MessageRouting getRoutingToServer() {
        return getOs().getCellModule().createMessageRouting().topoCast(config.trafficLightMappingServerName);
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {

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
