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

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.SimplePerceptionConfiguration;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.database.Database;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.math.Vector3d;
import org.eclipse.mosaic.lib.math.VectorUtils;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.routing.database.DatabaseRouting;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SimpleTrafficLightPerceptionApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication {

    private static final String EVENT_RESOURCE = "PERCEPTION";
    private static final long queryInterval = 1 * TIME.SECOND;

    private static final double VIEWING_ANGLE = 108d;
    private static final double VIEWING_RANGE = 100d;

    private static final float WAIT_DISTANCE_BEFORE_TRAFFIC_LIGHT = 1; //m

    private Database database;

    private GeoPoint previousPosition;

    private GeoPoint currentPosition;

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Started {} on {}.", this.getClass().getSimpleName(), getOs().getId());

        enablePerceptionModule();
        schedulePerception();
        database = ((DatabaseRouting) SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting()).getScenarioDatabase();
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
        if (previousVehicleData != null) {
            previousPosition = previousVehicleData.getPosition();
        }
        currentPosition = updatedVehicleData.getPosition();
    }

    private void schedulePerception() {
        getOs().getEventManager().newEvent(getOs().getSimulationTime() + queryInterval, this).withResource(EVENT_RESOURCE).schedule();
    }

    private void perceiveTrafficLights() {
        List<TrafficLightObject> perceivedTrafficLights = getOs().getPerceptionModule().getPerceivedTrafficLights();
        perceivedTrafficLights.forEach(trafficLightObject -> {
            if (getOs().getVehicleData() != null && getOs().getRoadPosition() != null) {
                if (isRelevantTrafficLight(trafficLightObject)) {
                    mapTrafficLightPosition(trafficLightObject);
                    double distanceToTrafficLight = getDistanceToTrafficLight(trafficLightObject);
                    getLog().infoSimTime(this, "[perceived] TL={}, distance={}, state={}", trafficLightObject.getId(),
                            distanceToTrafficLight, trafficLightObject.getTrafficLightState());
                }
            }
        });
//        getLog().infoSimTime(this, "Perceived traffic lights: {}", perceivedTrafficLights.stream().map(TrafficLightObject::getId).collect(Collectors.toList()));
    }

    private void mapTrafficLightPosition(TrafficLightObject trafficLightObject) {
        if (trafficLightObject.isMapped()) {
            return;
        }
        if (vehiclesInFrontOnSameEdge()) {
            return;
        }
        if (currentPosition == null || previousPosition == null || currentPosition.distanceTo(previousPosition) >= 0.05) {
            return;
        }
//        if (!MathUtils.isFuzzyEqual(getOs().getVehicleData().getSpeed(), 0d, 0.01d)) {
//            return;
//        }
        if (!trafficLightObject.getTrafficLightState().equals(TrafficLightState.RED)) {
            return;
        }
        // add min-gap of vehicle to position of the traffic light
        GeoPoint trafficLightPosition = getOs().getPosition().toVector3d()
                .add(VectorUtils.getDirectionVectorFromHeading(
                                getOs().getVehicleData().getHeading(), new Vector3d())
                        .multiply(WAIT_DISTANCE_BEFORE_TRAFFIC_LIGHT)
                ).toGeo();
        SimulationKernel.SimulationKernel.getCentralPerceptionComponentComponent().mapTrafficLightPosition(trafficLightObject.getId(), trafficLightPosition);
        getLog().infoSimTime(this, "[TL Position] original={}, perceived={}", trafficLightObject.toGeo(), trafficLightPosition);

    }

    private boolean vehiclesInFrontOnSameEdge() {
        List<VehicleObject> perceivedVehicles = getOs().getPerceptionModule().getPerceivedVehicles();
        if (perceivedVehicles.isEmpty()) {
            return false;
        }
        for (VehicleObject vehicleObject : perceivedVehicles) { // check if any vehicles are in front of ego-vehicle
            IRoadPosition otherVehiclePosition = getOs().getNavigationModule().getClosestRoadPosition(vehicleObject.toGeo());
            if (Objects.equals(otherVehiclePosition.getConnectionId(), getOs().getRoadPosition().getConnectionId())
                /*&& otherVehiclePosition.getLaneIndex() == getOs().getRoadPosition().getLaneIndex()*/) {
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
}
