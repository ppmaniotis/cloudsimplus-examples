/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2021 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudsimplus.examples;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.listeners.VmHostEventInfo;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Shows how to destroy an overloaded VM during simulation runtime,
 * after its CPU usage reaches a defined threshold.
 *
 * <p>The example uses the {@link UtilizationModelDynamic} to
 * increase VM CPU usage over time (in seconds).
 * This {@link UtilizationModelDynamic} relies on a {@link Function}
 * to increase resource usage. Such a Function is defined using a Lambda Expression.</p>
 *
 * <p>It shows Host CPU utilization every second, so that when the overloaded VM
 * is destroyed, we can check the Host CPU utilization reduces.</p>
 *
 * <p><b>Lambda Expressions and Functional Interfaces are Java 8+ features.</b>
 * If you don't know what these features are, I suggest checking out this
 * <a href="http://www.oracle.com/webfolder/technetwork/tutorials/obe/java/Lambda-QuickStart/index.html">tutorial</a></p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.3.0
 */
public class VmDestructionExample {
    /**
     * Defines, between other things, the time intervals
     * to update the processing of simulation events.
     * This way, simulation clock increases according to this time.
     * Since a {@link UtilizationModelDynamic} is being used to define CPU usage of VMs,
     * the utilization is updated at this time interval.
     *
     * Setting the Datacenter scheduling interval is required to allow
     * the CPU utilization to increase along the simulation execution.
     *
     * @see Datacenter#setSchedulingInterval(double)
     */
    private static final int SCHEDULING_INTERVAL = 1;

    private static final int HOSTS = 1;
    private static final int HOST_PES = 8;

    private static final int VMS = 2;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 4;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new VmDestructionExample();
    }

    private VmDestructionExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();

        /* Adds a Listener to track the execution update of VM 1.
         * If you want to track the update processing of multiple VMs, you can
         * add this event listener for each desired VM that it will work
         * transparently for any VM.
         */
        vmList.get(1).addOnUpdateProcessingListener(this::vmProcessingUpdateListener);

        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    /**
     * Tracks the update of execution for a VM.
     * If that VM is overloaded then destroys it.
     * In this example, this {@link EventListener} is just
     * attached for VM 1, therefore, just such a VM will be destroyed
     * if overloaded.
     *
     * @param info event information, including the VM that was updated
     */
    private void vmProcessingUpdateListener(VmHostEventInfo info) {
        final Vm vm = info.getVm();
        //Destroys VM 1 when its CPU usage reaches 90%
        if(vm.getCpuPercentUtilization() > 0.9 && vm.isCreated()){
            System.out.printf(
                "%n# %.2f: Intentionally destroying %s due to CPU overload. Current VM CPU usage is %.2f%%%n",
                info.getTime(), vm, vm.getCpuPercentUtilization()*100);
            vm.getHost().destroyVm(vm);
        }

        datacenter0.getHostList().forEach(h -> System.out.printf("# %.2f: %s CPU Utilization %.2f%%%n", info.getTime(), h, h.getCpuPercentUtilization()*100));
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        final var dc = new DatacenterSimple(simulation, hostList);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000));
        }

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes
        final var ramProvisioner = new ResourceProvisionerSimple();
        final var bwProvisioner = new ResourceProvisionerSimple();
        final var vmScheduler = new VmSchedulerTimeShared();
        final var host = new HostSimple(ram, bw, storage, peList);
        host
            .setRamProvisioner(ramProvisioner)
            .setBwProvisioner(bwProvisioner)
            .setVmScheduler(vmScheduler);
        return host;
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
        final var list = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            Vm vm =
                new VmSimple(i, 1000, VM_PES)
                    .setRam(512).setBw(1000).setSize(10000)
                    .setCloudletScheduler(new CloudletSchedulerTimeShared());

            list.add(vm);
        }

        return list;
    }

    /**
     * Creates a list of Cloudlets.
     */
    private List<Cloudlet> createCloudlets() {
        final var list = new ArrayList<Cloudlet>(CLOUDLETS);
        final var utilization = new UtilizationModelFull();

        /*Creates a utilization model that enables increasing resource usage over time.
        * It is used to define the CPU usage of VMs dynamically.*/
        final var dynamicUtilization = new UtilizationModelDynamic();

        /**
         * Sets a {@link Function} that enables the utilization model to increment resource usage by 10% over time.
         * This function is defined using a Lambda Expression.
         */
        dynamicUtilization.setUtilizationUpdateFunction(um -> um.getUtilization() + um.getTimeSpan()*0.1);

        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet =
                new CloudletSimple(i, CLOUDLET_LENGTH, CLOUDLET_PES)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModelRam(utilization)
                    .setUtilizationModelBw(utilization)
                    .setUtilizationModelCpu(dynamicUtilization);
            list.add(cloudlet);
        }

        return list;
    }
}
