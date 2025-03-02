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
package org.cloudsimplus.examples.migration;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationBestFitStaticThreshold;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.HostHistoryTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.DatacenterBrokerEventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.listeners.VmHostEventInfo;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.MipsShare;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyMinimumUtilization;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparingLong;

/**
 * An example showing how to create 1 Datacenter having: 5 hosts
 * with increasing number of PEs (starting at 4 PEs for the 1st host);
 * 3 VMs with 2 PEs each one;
 * and 1 cloudlet by VM, each one having the same number of PEs from its VM.
 *
 *
 * <p>The example then performs VM migration using
 * a {@link VmAllocationPolicyMigrationBestFitStaticThreshold}.
 * Such a policy migrates VMs based on
 * a static host CPU utilization threshold.
 * The VmAllocationPolicy used in this example ignores power consumption of Hosts.
 * This way, it isn't required to set a PowerModel for Hosts.</p>
 *
 * <p>According to the allocation policy, VM 0 will be allocated to Host 0.
 * Since Host 0 has just 4 PEs, allocating a second VM into it
 * would cause overload.
 * Each cloudlet will start using 80% of its VM CPU.
 * As the VM 0 will run one Cloudlet and requires just 2 PEs from Host 0 (which has 4 PEs),
 * the initial Host CPU usage will be just 40% (1 VM using 80% of 2 PEs from a total of 4 Host PEs = 0.8*2 / 4).
 *
 * Allocating a second VM into Host 0 would double the Host CPU utilization,
 * overreaching its upper utilization threshold (defined as 70%).
 * This way, VMs 1 and 2 are allocated to Host 1 which has 5 PEs.
 * </p>
 *
 * <p>The {@link VmAllocationPolicyMigrationBestFitStaticThreshold}
 * allows setting static under/over CPU utilization thresholds to
 * enable VM migration.
 * The example uses a {@link UtilizationModelDynamic} to define that CPU usage of cloudlets
 * increases along simulation time.
 * The first 2 Cloudlets all start with a usage of 80% of CPU,
 * which increases along the time (see {@link #CLOUDLET_CPU_INCREMENT_PER_SECOND}).
 * The third Cloudlet starts at a lower CPU usage and increases in the same way.
 * </p>
 *
 * <p>Some constants are used to create simulation objects such as
 * {@link  DatacenterSimple}, {@link  Host} and {@link  Vm}.
 * The values of these constants were careful and accordingly chosen to allow:
 * (i) migration of VMs due to either under and overloaded hosts; and (ii)
 * the researcher to know exactly how the simulation will run
 * and what will be the final results.
 * </p>
 *
 * <p>
 * Several values impact simulation results, such as
 * (i) hosts CPU capacity and number of PEs,
 * (ii) VMs and cloudlets requirements and
 * (iii) even VM bandwidth (which defines the VM migration time).
 *
 * This way, if you want to change these values, you must
 * define new appropriated ones to allow the simulation
 * to run correctly.</p>
 *
 * <p>Realize that Host State History is just collected
 * if you enable that by calling {@link Host#setStateHistoryEnabled(boolean)}.</p>
 *
 * @author Manoel Campos da Silva Filho
 */
public final class MigrationExample1 {
    /**
     * @see Datacenter#getSchedulingInterval()
     */
    private static final int  SCHEDULING_INTERVAL = 1;

    /**
     * The percentage of host CPU usage that trigger VM migration
     * due to under utilization (in scale from 0 to 1, where 1 is 100%).
     */
    private static final double HOST_UNDER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION = 0.1;

    /**
     * The percentage of host CPU usage that trigger VM migration
     * due to over utilization (in scale from 0 to 1, where 1 is 100%).
     */
    private static final double HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION = 0.7;

    /** @see Datacenter#setHostSearchRetryDelay(double) */
    private static final int HOST_SEARCH_RETRY_DELAY = 60;

    /**
     * The time spent during VM migration depend on the
     * bandwidth of the target Host.
     * By default, a {@link Datacenter}
     * uses only 50% of the BW to migrate VMs, while the
     * remaining capacity is used for VM communication.
     * This can be changed by calling
     * {@link DatacenterSimple#setBandwidthPercentForMigration(double)}.
     *
     * <p>The 16000 Mb/s is the same as 2000 MB/s. Since just half of this capacity
     * is used for VM migration, only 1000 MB/s will be available for this process.
     * The time that takes to migrate a Vm depend on the VM RAM capacity.
     * Since VMs in this example are created with 2000 MB of RAM, any migration
     * will take 2 seconds to finish, as can be seen in the logs.
     */
    private static final long   HOST_BW = 16_000L; //Mb/s

    private static final int    HOST_MIPS = 1000; //for each PE
    private static final long   HOST_RAM[] = {15_000, 500_000, 25_000}; //host memory (MB)
    private static final long   HOST_STORAGE = 1_000_000; //host storage

    /**
     * An array where each item defines the number of PEs for each Host to be created.
     * The length of the array represents the number of Hosts.
     */
    private static final int    HOST_PES[] = {4, 5, 5};

    private static final int    VM_PES[]   = {2, 2, 2, 1};
    private static final int    VM_MIPS = 1000; //for each PE
    private static final long   VM_SIZE = 1000; //image size (MB)
    private static final int    VM_RAM = 10_000; //VM memory (MB)
    private static final double VM_BW = HOST_BW/(double)VM_PES.length;

    private static final long   CLOUDLET_LENGTH = 20_000;
    private static final long   CLOUDLET_FILESIZE = 300;
    private static final long   CLOUDLET_OUTPUTSIZE = 300;

    /**
     * The percentage of CPU that a cloudlet will use when
     * it starts executing (in scale from 0 to 1, where 1 is 100%).
     * For each cloudlet create, this value is used
     * as a base to define CPU usage.
     * @see #createAndSubmitCloudlets(DatacenterBroker)
     */
    private static final double CLOUDLET_INITIAL_CPU_PERCENTAGE = 0.8;

    /**
     * Defines the speed (in percentage) that CPU usage of a cloudlet
     * will increase during the simulation execution.
     * (in scale from 0 to 1, where 1 is 100%).
     * @see #createCpuUtilizationModel(double, double)
     */
    private static final double CLOUDLET_CPU_INCREMENT_PER_SECOND = 0.04;

    /**
     * List of all created VMs.
     */
    private final List<Vm> vmList = new ArrayList<>();
    private final DatacenterBrokerSimple broker;

    private final CloudSimPlus simulation;
    private VmAllocationPolicyMigrationStaticThreshold allocationPolicy;
    private List<Host> hostList;
    private int migrationsNumber = 0;

    public static void main(String[] args) {
        new MigrationExample1();
    }

    private MigrationExample1(){
        Log.setLevel(Level.INFO);

        if(HOST_PES.length != HOST_RAM.length){
            throw new IllegalStateException("The length of arrays HOST_PES and HOST_RAM must match.");
        }

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSimPlus();

        @SuppressWarnings("unused")
        final var datacenter0 = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);
        createAndSubmitVms(broker);
        createAndSubmitCloudlets(broker);

        broker.addOnVmsCreatedListener(this::onVmsCreatedListener);

        simulation.start();

        final var cloudletFinishedList = broker.getCloudletFinishedList();
        final Comparator<Cloudlet> cloudletComparator =
            comparingLong((Cloudlet c) -> c.getVm().getHost().getId())
                .thenComparingLong(c -> c.getVm().getId());
        cloudletFinishedList.sort(cloudletComparator);
        new CloudletsTableBuilder(cloudletFinishedList).build();
        System.out.printf("%nHosts CPU usage History (when the allocated MIPS is lower than the requested, it is due to VM migration overhead)%n");

        hostList.stream().filter(h -> h.getId() <= 2).forEach(this::printHostStateHistory);
        System.out.printf("Number of VM migrations: %d%n", migrationsNumber);
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    /**
     * A listener method that is called when a VM migration starts.
     * @param info information about the happened event
     *
     * @see #createAndSubmitVms(DatacenterBroker)
     * @see Vm#addOnMigrationFinishListener(EventListener)
     */
    private void startMigration(final VmHostEventInfo info) {
        final Vm vm = info.getVm();
        final Host targetHost = info.getHost();
        System.out.printf(
            "# %.2f: %s started migrating to %s (you can perform any operation you want here)%n",
            info.getTime(), vm, targetHost);
        showVmAllocatedMips(vm, targetHost, info.getTime());
        //VM current host (source)
        showHostAllocatedMips(info.getTime(), vm.getHost());
        //Migration host (target)
        showHostAllocatedMips(info.getTime(), targetHost);
        System.out.println();

        migrationsNumber++;
        if(migrationsNumber > 1){
            return;
        }

        //After the first VM starts being migrated, tracks some metrics along simulation time
        simulation.addOnClockTickListener(clock -> {
            if (clock.getTime() <= 2 || (clock.getTime() >= 11 && clock.getTime() <= 15))
                showVmAllocatedMips(vm, targetHost, clock.getTime());
        });
    }

    private void showVmAllocatedMips(final Vm vm, final Host targetHost, final double time) {
        final String msg = String.format("# %.2f: %s in %s: total allocated", time, vm, targetHost);
        final MipsShare allocatedMips = targetHost.getVmScheduler().getAllocatedMips(vm);
        final String msg2 = allocatedMips.totalMips() == VM_MIPS * 0.9 ? " - reduction due to migration overhead" : "";
        System.out.printf("%s %.0f MIPs (divided by %d PEs)%s\n", msg, allocatedMips.totalMips(), allocatedMips.pes(), msg2);
    }

    /**
     * A listener method that is called when a VM migration finishes.
     * @param info information about the happened event
     *
     * @see #createAndSubmitVms(DatacenterBroker)
     * @see Vm#addOnMigrationStartListener(EventListener)
     */
    private void finishMigration(final VmHostEventInfo info) {
        final var host = info.getHost();
        System.out.printf(
            "# %.2f: %s finished migrating to %s (you can perform any operation you want here)%n",
            info.getTime(), info.getVm(), host);
        System.out.print("\t\t");
        showHostAllocatedMips(info.getTime(), hostList.get(1));
        System.out.print("\t\t");
        showHostAllocatedMips(info.getTime(), host);
    }

    private void showHostAllocatedMips(final double time, final Host host) {
        System.out.printf(
            "%.2f: %s allocated %.2f MIPS from %.2f total capacity%n",
            time, host, host.getTotalAllocatedMips(), host.getTotalMipsCapacity());
    }

    private void printHostStateHistory(final Host host) {
        new HostHistoryTableBuilder(host).setTitle(host.toString()).build();
    }

    public void createAndSubmitCloudlets(DatacenterBroker broker) {
        final var newCloudletList = new ArrayList<Cloudlet>(VM_PES.length);
        var cloudlet = Cloudlet.NULL;
        UtilizationModelDynamic um = createCpuUtilizationModel(CLOUDLET_INITIAL_CPU_PERCENTAGE, 1);
        for(final var vm: vmList){
            cloudlet = createCloudlet(vm, broker, um);
            newCloudletList.add(cloudlet);
        }

        //Changes the CPU usage of the last cloudlet to start at a lower value and increase dynamically up to 100%
        cloudlet.setUtilizationModelCpu(createCpuUtilizationModel(0.2, 1));

        broker.submitCloudletList(newCloudletList);
    }

    /**
     * Creates a Cloudlet.
     *
     * @param vm the VM that will run the Cloudlets
     * @param broker the broker that the created Cloudlets belong to
     * @param cpuUtilizationModel the CPU UtilizationModel for the Cloudlet
     * @return the created Cloudlets
     */
    public Cloudlet createCloudlet(Vm vm, DatacenterBroker broker, UtilizationModel cpuUtilizationModel) {
        final var utilizationModelFull = new UtilizationModelFull();

        final var cloudlet =
            new CloudletSimple(CLOUDLET_LENGTH, (int)vm.getPesNumber())
                .setFileSize(CLOUDLET_FILESIZE)
                .setOutputSize(CLOUDLET_OUTPUTSIZE)
                .setUtilizationModelRam(utilizationModelFull)
                .setUtilizationModelBw(utilizationModelFull)
                .setUtilizationModelCpu(cpuUtilizationModel);
        broker.bindCloudletToVm(cloudlet, vm);

        return cloudlet;
    }

    public void createAndSubmitVms(DatacenterBroker broker) {
        final var newVmList = new ArrayList<Vm>(VM_PES.length);
        for (final int pes : VM_PES) {
            newVmList.add(createVm(pes));
        }

        vmList.addAll(newVmList);
        broker.submitVmList(newVmList);

        newVmList.forEach(vm -> vm.addOnMigrationStartListener(this::startMigration));
        newVmList.forEach(vm -> vm.addOnMigrationFinishListener(this::finishMigration));
    }

    public Vm createVm(final int pes) {
        final var vm = new VmSimple(VM_MIPS, pes);
        vm
            .setRam(VM_RAM).setBw((long)VM_BW).setSize(VM_SIZE)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
        return vm;
    }

    /**
     * Creates a CPU UtilizationModel for a Cloudlet.
     * If the initial usage is lower than the max usage, the usage will
     * be dynamically incremented along the time, according to the
     * {@link #getCpuUsageIncrement(UtilizationModelDynamic)}
     * function. Otherwise, the CPU usage will be static, according to the
     * defined initial usage.
     *
     * @param initialCpuUsagePercent the percentage of CPU utilization
     * that created Cloudlets will use when they start to execute.
     * If this value is greater than 1 (100%), it will be changed to 1.
     * @param maxCpuUsagePercentage the maximum percentage of
     * CPU utilization that created Cloudlets are allowed to use.
     * If this value is greater than 1 (100%), it will be changed to 1.
     * It must be equal or greater than the initial CPU usage.
     * @return
     */
    private UtilizationModelDynamic createCpuUtilizationModel(double initialCpuUsagePercent, double maxCpuUsagePercentage) {
        if(maxCpuUsagePercentage < initialCpuUsagePercent){
            throw new IllegalArgumentException("Max CPU usage must be equal or greater than the initial CPU usage.");
        }

        initialCpuUsagePercent = Math.min(initialCpuUsagePercent, 1);
        maxCpuUsagePercentage = Math.min(maxCpuUsagePercentage, 1);
        final UtilizationModelDynamic um;
        if (initialCpuUsagePercent < maxCpuUsagePercentage) {
            um = new UtilizationModelDynamic(initialCpuUsagePercent)
                .setUtilizationUpdateFunction(this::getCpuUsageIncrement);
        }
        else um = new UtilizationModelDynamic(initialCpuUsagePercent);

        um.setMaxResourceUtilization(maxCpuUsagePercentage);
        return um;
    }

    /**
     * Increments the CPU resource utilization, that is defined in percentage values.
     * @return the new resource utilization after the increment
     */
    private double getCpuUsageIncrement(final UtilizationModelDynamic um){
        return um.getUtilization() + um.getTimeSpan()*CLOUDLET_CPU_INCREMENT_PER_SECOND;
    }

    /**
     * Creates a Datacenter with number of Hosts defined by the length of {@link #HOST_PES},
     * but only some of these Hosts will be active (powered on) initially.
     *
     * @return
     */
    private Datacenter createDatacenter() {
        this.hostList = createHosts();
        System.out.println();

        /**
         * Sets an upper utilization threshold higher than the
         * {@link #HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION}
         * to enable placing VMs which will use more CPU than
         * defined by the value in the mentioned constant.
         * After VMs are all submitted to Hosts, the threshold is changed
         * to the value of the constant.
         * This is used to  place VMs into a Host which will
         * become overloaded in order to trigger the migration.
         */
        this.allocationPolicy =
            new VmAllocationPolicyMigrationBestFitStaticThreshold(
                new VmSelectionPolicyMinimumUtilization(),
                HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION + 0.2);
        this.allocationPolicy.setUnderUtilizationThreshold(HOST_UNDER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION);

        final var dc = new DatacenterSimple(simulation, hostList, allocationPolicy);
        for (Host host : hostList) {
            System.out.printf(
                "# Created %s with %.0f MIPS x %d PEs (%.0f total MIPS)%n",
                host, host.getMips(), host.getPesNumber(), host.getTotalMipsCapacity());
        }
        dc.setSchedulingInterval(SCHEDULING_INTERVAL)
          .setHostSearchRetryDelay(HOST_SEARCH_RETRY_DELAY);
        return dc;
    }

    private List<Host> createHosts() {
        final var hostList = new ArrayList<Host>(HOST_PES.length);
        for (int i = 0; i < HOST_PES.length; i++) {
            final int pes = HOST_PES[i];
            final long ram = HOST_RAM[i];
            hostList.add(createHost(pes, ram));
        }

        return hostList;
    }

    public Host createHost(final int pesNumber, final long ram) {
        final var peList = createPeList(pesNumber);
        final var host = new HostSimple(ram, HOST_BW, HOST_STORAGE, peList);
        host.setVmScheduler(new VmSchedulerTimeShared())
            .setStateHistoryEnabled(true);
        return host;
    }

    public List<Pe> createPeList(final int pesNumber) {
        final var peList = new ArrayList<Pe>(pesNumber);
        for(int i = 0; i < pesNumber; i++) {
            peList.add(new PeSimple(HOST_MIPS));
        }

        return peList;
    }

    /**
     * A listener that is called after all VMs from a broker are created,
     * setting the allocation policy to the default value
     * so that some Hosts will be overloaded with the placed VMs and migration will be fired.
     *
     * <p>The listener is removed after finishing, so that it's called just once,
     * even if new VMs are submitted and created latter on.</p>
     */
    private void onVmsCreatedListener(final DatacenterBrokerEventInfo info) {
        System.out.printf("# All %d VMs submitted to the broker have been created.%n", broker.getVmCreatedList().size());
        allocationPolicy.setOverUtilizationThreshold(HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION);
        broker.removeOnVmsCreatedListener(info.getListener());
        vmList.forEach(vm -> showVmAllocatedMips(vm, vm.getHost(), info.getTime()));

        System.out.println();
        hostList.forEach(host -> showHostAllocatedMips(info.getTime(), host));
        System.out.println();
    }
}
