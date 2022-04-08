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

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.PerceptionGrid;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.PerceptionIndex;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.PerceptionTree;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.TrafficLightObject;
import org.eclipse.mosaic.fed.application.ambassador.simulation.perception.index.objects.VehicleObject;
import org.eclipse.mosaic.fed.application.ambassador.util.PerformanceMonitor;
import org.eclipse.mosaic.fed.application.config.CApplicationAmbassador;
import org.eclipse.mosaic.interactions.traffic.TrafficLightUpdates;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.lib.geo.CartesianRectangle;
import org.eclipse.mosaic.lib.objects.trafficlight.TrafficLightGroupInfo;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import ch.qos.logback.classic.LoggerContext;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * The {@link CentralPerceptionComponent} is responsible for keeping a spatial index of all vehicles,
 * which allows fast querying of nearby vehicles.
 */
public class CentralPerceptionComponent {

    private final static Logger LOG = LoggerFactory.getLogger(CentralPerceptionComponent.class);

    private final CApplicationAmbassador.CPerception configuration;
    private final PerformanceMonitor performanceMonitor = new PerformanceMonitor();

    /**
     * The spatial index used to store and find vehicles by their positions.
     */
    private SpatialIndex spatialIndex;

    /**
     * The last {@link VehicleUpdates} interaction which is used to update the {@link #spatialIndex}.
     */
    private VehicleUpdates latestVehicleUpdates;

    /**
     * The last {@link TrafficLightUpdates} interaction which is used to update the {@link #spatialIndex}.
     */
    private TrafficLightUpdates latestTrafficLightUpdates;


    /**
     * If set to true, the vehicleIndex will be updated when {@link #updateSpatialIndices} is called.
     */
    private boolean updateVehicleIndex = false;

    /**
     * If set to {@code true}, the vehicleIndex will be updated when {@link #updateSpatialIndices} is called.
     */
    private boolean updateTrafficLightIndex = false;

    public CentralPerceptionComponent(CApplicationAmbassador.CPerception perceptionConfiguration) {
        this.configuration = Validate.notNull(perceptionConfiguration, "perceptionConfiguration must not be null");
    }

    /**
     * Initializes the spatial index used for perception.
     */
    public void initialize() throws InternalFederateException {
        try {
            CartesianRectangle scenarioBounds =
                    SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().getScenarioBounds();
            if (scenarioBounds.getArea() > 0) {
                switch (configuration.perceptionBackend) {
                    case Grid:
                        spatialIndex = new PerceptionGrid(scenarioBounds, configuration.gridCellWidth, configuration.gridCellHeight);
                        break;
                    case QuadTree:
                        spatialIndex = new PerceptionTree(scenarioBounds, configuration.treeSplitSize, configuration.treeMaxDepth);
                        break;
                    case Trivial:
                    default:
                        spatialIndex = new PerceptionIndex();
                }
            } else {
                LOG.warn("The bounding area of the scenario could not be determined. A low performance spatial index will be used for perception.");
                spatialIndex = new PerceptionIndex();
            }

            if (configuration.measurePerformance) {
                spatialIndex = new MonitoringSpatialIndex(spatialIndex, performanceMonitor);
            }
        } catch (Exception e) {
            throw new InternalFederateException("Couldn't initialize CentralPerceptionComponent", e);
        }
    }

    /**
     * Returns the {@link SpatialIndex} storing all vehicles.
     */
    public SpatialIndex getSpatialIndex() {
        return spatialIndex;
    }

    /**
     * Updates the spatial indices (currently only vehicles).
     * If the positions of vehicles have not changed since last call of this method, nothing is done.
     */
    public void updateSpatialIndices() {
        if (updateVehicleIndex) {
            // do not update index until next VehicleUpdates interaction is received
            updateVehicleIndex = false;
            // using Iterables.concat allows iterating over both lists subsequently without creating a new list
            spatialIndex.updateVehicles(Iterables.concat(latestVehicleUpdates.getAdded(), latestVehicleUpdates.getUpdated()));
        }
        if (updateTrafficLightIndex) {
            updateTrafficLightIndex = false;

            spatialIndex.updateTrafficLights(latestTrafficLightUpdates.getUpdated());
        }
    }

    /**
     * Store new updates of all Vehicles to be used in the next update of the spatial index.
     *
     * @param vehicleUpdates the interaction holding all vehicle updates
     */
    public void updateVehicles(VehicleUpdates vehicleUpdates) {
        latestVehicleUpdates = vehicleUpdates;
        updateVehicleIndex = true;
        // we need to remove arrived vehicles in every simulation step, otherwise we could have dead vehicles in the index
        if (spatialIndex.getNumberOfVehicles() > 0) {
            spatialIndex.removeVehicles(vehicleUpdates.getRemovedNames());
        }
    }

    public void updateTrafficLights(TrafficLightUpdates trafficLightUpdates) {
        latestTrafficLightUpdates = trafficLightUpdates;
        updateTrafficLightIndex = true;
    }

    /**
     * Stores measurements done during update and search operations of the spatial index.
     */
    public void finish() {
        if (configuration.measurePerformance) {
            performanceMonitor.printSummary();
            String logDirectory = ((LoggerContext) LoggerFactory.getILoggerFactory()).getProperty("logDirectory");
            try (Writer perceptionPerformanceWriter = new OutputStreamWriter(
                    new FileOutputStream(new File(logDirectory, "PerceptionPerformance.csv")), Charsets.UTF_8)) {
                performanceMonitor.exportDetailedMeasurements(perceptionPerformanceWriter);
            } catch (IOException e) {
                LOG.warn("Could not write performance result for perception module.");
            }
        }
    }

    /**
     * Wrapper class to measure atomic calls of update, search and remove of the used spatial index
     */
    static class MonitoringSpatialIndex implements SpatialIndex {

        private final SpatialIndex parent;
        private final PerformanceMonitor monitor;

        MonitoringSpatialIndex(SpatialIndex parent, PerformanceMonitor monitor) {
            this.parent = parent;
            this.monitor = monitor;
        }

        @Override
        public List<VehicleObject> getVehiclesInRange(PerceptionRange searchRange) {
            try (PerformanceMonitor.Measurement m = monitor.start("search")) {
                m.setProperties(getNumberOfVehicles(), SimulationKernel.SimulationKernel.getCurrentSimulationTime())
                        .restart();
                return parent.getVehiclesInRange(searchRange);
            }
        }

        @Override
        public void removeVehicles(Iterable<String> vehiclesToRemove) {
            try (PerformanceMonitor.Measurement m = monitor.start("remove")) {
                m.setProperties(parent.getNumberOfVehicles(), SimulationKernel.SimulationKernel.getCurrentSimulationTime())
                        .restart();
                parent.removeVehicles(vehiclesToRemove);
            }
        }

        @Override
        public void updateVehicles(Iterable<VehicleData> vehiclesToUpdate) {
            try (PerformanceMonitor.Measurement m = monitor.start("update")) {
                m.setProperties(getNumberOfVehicles(), SimulationKernel.SimulationKernel.getCurrentSimulationTime())
                        .restart();
                parent.updateVehicles(vehiclesToUpdate);
            }
        }

        @Override
        public int getNumberOfVehicles() {
            return parent.getNumberOfVehicles();
        }

        @Override
        public List<TrafficLightObject> getTrafficLightsInRange(PerceptionRange searchRange) {
            // TODO: add
            return null;
        }

        @Override
        public void updateTrafficLights(Map<String, TrafficLightGroupInfo> trafficLightsToUpdate) {
            // TODO: add
        }
    }
}
