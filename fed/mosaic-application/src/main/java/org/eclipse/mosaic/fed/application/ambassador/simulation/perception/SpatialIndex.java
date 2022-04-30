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

package org.eclipse.mosaic.fed.application.ambassador.simulation.perception;

import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.interactions.mapping.TrafficLightRegistration;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroupInfo;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;

import java.util.List;
import java.util.Map;

/**
 * A {@link SpatialIndex} is a representation of space using a special data structure.
 * The goal is to allow for efficient querying of nearby entities.
 */
public interface SpatialIndex {

    /**
     * Queries the {@link SpatialIndex} and returns all vehicles inside the {@link PerceptionRange}.
     */
    List<VehicleObject> getVehiclesInRange(PerceptionRange searchRange);

    /**
     * Remove all vehicles from the {@link SpatialIndex} by a list of vehicle ids.
     *
     * @param vehiclesToRemove the list of vehicles to remove from the index
     */
    void removeVehicles(Iterable<String> vehiclesToRemove);

    /**
     * Updates the {@link SpatialIndex} with a list of {@link VehicleData} objects.
     *
     * @param vehiclesToUpdate the list of vehicles to add or update in the index
     */
    void updateVehicles(Iterable<VehicleData> vehiclesToUpdate);

    /**
     * Returns the amount of indexed vehicles.
     *
     * @return the number of vehicles
     */
    int getNumberOfVehicles();

    /**
     * Queries the {@link SpatialIndex} and returns all traffic lights inside the {@link PerceptionRange}.
     */
    List<TrafficLightObject> getTrafficLightsInRange(PerceptionRange searchRange);

    /**
     * Adds TODO
     * @param trafficLightRegistration
     */
    void addTrafficLight(TrafficLightRegistration trafficLightRegistration);

    /**
     * Updates the {@link SpatialIndex} in regard to traffic lights. The unit simulator has to be queried as
     * {@code TrafficLightUpdates} do not contain all necessary information.
     *
     * @param trafficLightsToUpdate a list of information packages transmitted by the traffic simulator
     */
    void updateTrafficLights(Map<String, TrafficLightGroupInfo> trafficLightsToUpdate);
}
