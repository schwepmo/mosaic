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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index;

import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.PerceptionModel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.SpatialIndex;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.interactions.mapping.TrafficLightRegistration;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroup;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroupInfo;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class is to be extended by all differing implementations of the {@link SpatialIndex},
 * it provides basic functionality for positional static entities, as backend requirements
 * are not as dynamic as for vehicles.
 */
public abstract class AbstractPerceptionIndex implements SpatialIndex {

    /**
     * TODO: probably best to put it in a KD-Tree
     */
    private final Map<String, TrafficLightObject> indexedTrafficLights = new HashMap<>();

    @Override
    public int getNumberOfTrafficLights() {
        return indexedTrafficLights.size();
    }

    @Override
    public List<TrafficLightObject> getTrafficLightsInRange(PerceptionModel perceptionModel) {
        return indexedTrafficLights.values().stream()
                .filter(perceptionModel::isInRange)
                .collect(Collectors.toList());
    }

    @Override
    public void addTrafficLight(TrafficLightRegistration trafficLightRegistration) {
        TrafficLightGroup trafficLightGroup = trafficLightRegistration.getTrafficLightGroup();
        String trafficLightGroupId = trafficLightGroup.getGroupId();
        trafficLightGroup.getTrafficLights().forEach(
                (trafficLight) -> {
                    String trafficLightId = calculateTrafficLightId(trafficLightGroupId, trafficLight.getId());
                    indexedTrafficLights.computeIfAbsent(trafficLightId, TrafficLightObject::new)
                            .setTrafficLightGroupId(trafficLightGroupId)
                            .setPosition(trafficLight.getPosition().toCartesian())
                            .setIncomingLane(trafficLight.getIncomingLane())
                            .setOutgoingLane(trafficLight.getOutgoingLane())
                            .setTrafficLightState(trafficLight.getCurrentState());
                }
        );
    }

    @Override
    public void updateTrafficLights(Map<String, TrafficLightGroupInfo> trafficLightGroupsToUpdate) {
        trafficLightGroupsToUpdate.forEach(
                (trafficLightGroupId, trafficLightGroupInfo) -> {
                    List<TrafficLightState> trafficLightStates = trafficLightGroupInfo.getCurrentState();
                    for (int i = 0; i < trafficLightStates.size(); i++) {
                        String trafficLightId = calculateTrafficLightId(trafficLightGroupId, i);
                        indexedTrafficLights.get(trafficLightId)
                                .setTrafficLightState(trafficLightStates.get(i));
                    }
                }
        );
    }

    /**
     * Maps the exact position of a TL as this cannot be directly extracted
     * from some traffic simulators.
     *
     * @param trafficLightId       id of the TL to be mapped
     * @param trafficLightPosition position of the TL to be mapped
     * @return {@code true} if TL position was overridden, otherwise {@code false}
     */
    @Override
    public boolean mapTrafficLightPosition(String trafficLightId, GeoPoint trafficLightPosition) {
        TrafficLightObject trafficLightObject = indexedTrafficLights.get(trafficLightId);
        if (trafficLightObject == null || trafficLightObject.isMapped()) {
            return false;
        } else {
            trafficLightObject
                    .setPosition(trafficLightPosition.toCartesian())
                    .setMapped();
            return true;
        }
    }

    private String calculateTrafficLightId(String trafficLightGroupId, int trafficLightIndex) {
        return trafficLightGroupId + "_" + trafficLightIndex;
    }
}
