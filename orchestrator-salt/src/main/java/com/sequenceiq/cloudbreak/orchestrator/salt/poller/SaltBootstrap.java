package com.sequenceiq.cloudbreak.orchestrator.salt.poller;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import com.sequenceiq.cloudbreak.orchestrator.OrchestratorBootstrap;
import com.sequenceiq.cloudbreak.orchestrator.exception.CloudbreakOrchestratorFailedException;
import com.sequenceiq.cloudbreak.orchestrator.model.BootstrapParams;
import com.sequenceiq.cloudbreak.orchestrator.model.GatewayConfig;
import com.sequenceiq.cloudbreak.orchestrator.model.GenericResponse;
import com.sequenceiq.cloudbreak.orchestrator.model.GenericResponses;
import com.sequenceiq.cloudbreak.orchestrator.model.Node;
import com.sequenceiq.cloudbreak.orchestrator.salt.client.SaltActionType;
import com.sequenceiq.cloudbreak.orchestrator.salt.client.SaltConnector;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.Cloud;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.Minion;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.MinionIpAddressesResponse;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.Os;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.SaltAction;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.SaltAuth;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.SaltMaster;
import com.sequenceiq.cloudbreak.orchestrator.salt.poller.join.AcceptAllFpMatcher;
import com.sequenceiq.cloudbreak.orchestrator.salt.poller.join.DummyFingerprintCollector;
import com.sequenceiq.cloudbreak.orchestrator.salt.poller.join.EqualMinionFpMatcher;
import com.sequenceiq.cloudbreak.orchestrator.salt.poller.join.FingerprintFromSbCollector;
import com.sequenceiq.cloudbreak.orchestrator.salt.poller.join.MinionAcceptor;
import com.sequenceiq.cloudbreak.orchestrator.salt.states.SaltStates;

public class SaltBootstrap implements OrchestratorBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaltBootstrap.class);

    private final SaltConnector sc;

    private final Collection<SaltConnector> saltConnectors;

    private final List<GatewayConfig> allGatewayConfigs;

    private final Set<Node> originalTargets;

    private final BootstrapParams params;

    private Set<Node> targets;

    public SaltBootstrap(SaltConnector sc, Collection<SaltConnector> saltConnectors, List<GatewayConfig> allGatewayConfigs, Set<Node> targets,
            BootstrapParams params) {
        this.sc = sc;
        this.saltConnectors = saltConnectors;
        this.allGatewayConfigs = allGatewayConfigs;
        originalTargets = Collections.unmodifiableSet(targets);
        this.targets = targets;
        this.params = params;
    }

    @Override
    public Boolean call() throws Exception {
        // ZZZ: Somehow on the 2nd call ... this goes to 100/100 <-- which implies that targets is empty.
        //   ZZZ: If targets is empty - this proceeds to go and check if the nodes are responding - skipping the createMinionAcceptor().acceptMinions() step.
        // ZZZ: Tries to go and check if the minions are responding via an IP check. If not, all targets are added back again.
        // ZZZ: Call 3 (already at the 30s mark) Bootstrapping 0/100
        //   ZZZ: Run 3 executes the minion acceptor again.
        //   ZZZ: This time around, it waits for all minions to come into a ready state of sorts.
        //   ZZZ: This itself finishes at 10:16. (First attempt 09:41, 2nd acceptor at 10:01) -> So likely all joined in 30 seconds?
        //   ZZZ: 10:31 Proceeds to run a network.ipaddress ... this itself takes 15 more seconds (So maybe a different step of the salt accept process?)
        //   ZZZ: Despite the 15 seconds - overall, "not-responding" count = 94?
        // ZZZ: Call 4 (At 10:41 - at the 50s mark now).
        //   ZZZ: this shows Bootstrapping 6/100 <- despite all not-responding and added to targets.
        //   ZZZ: Finally, everything looks good: Bootstrapping of nodes completed at 10:42 (60+ seconds after start).


        LOGGER.debug("Bootstrapping of nodes [{}/{}]", originalTargets.size() - targets.size(), originalTargets.size());
        if (!targets.isEmpty()) {
            // ZZZ TODO: This log line is a little excessive ... don't need to print all information about the missing nodes.
            LOGGER.debug("Missing targets for SaltBootstrap: targetCount={}, targets={}", targets.size(), targets);

            LOGGER.debug("ZZZ: bootstrapParams={}", params);
            SaltAction saltAction = createBootstrap(params.isRestartNeededFlagSupported(), params.isRestartNeeded());
            GenericResponses responses = sc.action(saltAction);

            Set<Node> failedTargets = new HashSet<>();

            LOGGER.debug("SaltBootstrap responses: {}", responses);
            for (GenericResponse genericResponse : responses.getResponses()) {
                if (genericResponse.getStatusCode() != HttpStatus.OK.value()) {
                    LOGGER.info("Failed to distributed salt run to: {}, error: {}", genericResponse.getAddress(), genericResponse.getErrorText());
                    String address = genericResponse.getAddress().split(":")[0];
                    failedTargets.addAll(originalTargets.stream().filter(a -> a.getPrivateIp().equals(address)).collect(Collectors.toList()));
                }
            }

            targets = failedTargets;
            // TODO ZZZ: This, in conjunction with the !targets.empty check in the outer loop, essentially
            //  means that this loop can end up flip-flopping between accept / network.ip_address calls on each attempt
            //  and ends up causing an unnecessary loop.
            //  This specifically happens when createMinionAcceptor().acceptMinions() specifically throws an exception,
            //  so collectMinionIpAddresses does not run, and targets ends up being empty as long as the saltboot.* call above responded correctly.

            //  The network.ipaddr lookup is useless until all the minions have been accepted (TODO is this true?), so tracking
            //  accepted minions separately may be worth it.
            //


            if (!targets.isEmpty()) {
                LOGGER.info("Missing nodes to run saltbootstrap: {}", targets);
                throw new CloudbreakOrchestratorFailedException("There are missing nodes from saltbootstrap: " + targets);
            }

            if (params.isRestartNeeded()) {
                params.setRestartNeeded(false);
            }

            if (!createMinionAcceptor().acceptMinions()) {
                // ZZZ: Temporary - to avoid the issue mentioned above. This isn't good enough to re-populate
                //  targets because multiple parts of the system can throw an exception. A better mechanism may be to
                //  catch and re-throw. Even better would be to somehow retain the minion list.
                targets = originalTargets;
            }
            // ZZZ This can throw a failed response. Essentially wait for all the salt minions to join?
        }

        // TODO ZZZ: In
        MinionIpAddressesResponse minionIpAddressesResponse = SaltStates.collectMinionIpAddresses(sc);
        if (minionIpAddressesResponse != null) {
            originalTargets.forEach(node -> {
                if (!minionIpAddressesResponse.getAllIpAddresses().contains(node.getPrivateIp())) {
                    LOGGER.info("Salt-minion is not responding on host: {}, yet", node);
                    targets.add(node);
                }
            });
            LOGGER.info("ZZZ: Salt-minion not responding hostCount(including previous targets)={}", targets.size());
        } else {
            throw new CloudbreakOrchestratorFailedException("Minions ip address collection returned null value");
        }
        if (!targets.isEmpty()) {
            LOGGER.info("ZZZ: Pending targets: {}", targets.size());
            throw new CloudbreakOrchestratorFailedException("There are missing nodes from salt network response: " + targets);
        }
        LOGGER.debug("Bootstrapping of nodes completed: {}", originalTargets.size());
        return true;
    }

    protected MinionAcceptor createMinionAcceptor() {
        List<Minion> minions = createMinionsFromOriginalTargets();
        return params.isSaltBootstrapFpSupported() ?
                new MinionAcceptor(saltConnectors, minions, new EqualMinionFpMatcher(), new FingerprintFromSbCollector())
                : new MinionAcceptor(saltConnectors, minions, new AcceptAllFpMatcher(), new DummyFingerprintCollector());
    }

    private SaltAction createBootstrap(boolean restartNeededFlagSupported, boolean restartNeeded) {
        SaltAction saltAction = new SaltAction(SaltActionType.RUN);
        if (params.getCloud() != null) {
            saltAction.setCloud(new Cloud(params.getCloud()));
        }
        if (params.getOs() != null) {
            saltAction.setOs(new Os(params.getOs()));
        }
        SaltAuth auth = new SaltAuth();
        auth.setPassword(sc.getSaltPassword());
        List<String> targetIps = targets.stream().map(Node::getPrivateIp).collect(Collectors.toList());
        for (GatewayConfig gatewayConfig : allGatewayConfigs) {
            String gatewayAddress = gatewayConfig.getPrivateAddress();
            if (targetIps.contains(gatewayAddress)) {
                Node saltMaster = targets.stream().filter(n -> n.getPrivateIp().equals(gatewayAddress)).findFirst().get();
                SaltMaster master = new SaltMaster();
                master.setAddress(gatewayAddress);
                master.setAuth(auth);
                master.setDomain(saltMaster.getDomain());
                master.setHostName(saltMaster.getHostname());
                // set due to compatibility reasons
                saltAction.setServer(gatewayAddress);
                saltAction.setMaster(master);
                saltAction.addMinion(createMinion(saltMaster, restartNeededFlagSupported, restartNeeded));
                saltAction.addMaster(master);
            }
        }
        for (Node minion : targets.stream().filter(node -> !getGatewayPrivateIps().contains(node.getPrivateIp())).collect(Collectors.toList())) {
            saltAction.addMinion(createMinion(minion, restartNeededFlagSupported, restartNeeded));
        }
        return saltAction;
    }

    private Minion createMinion(Node node, boolean restartNeededFlagSupported, boolean restartNeeded) {
        Minion minion = new Minion();
        minion.setAddress(node.getPrivateIp());
        minion.setHostGroup(node.getHostGroup());
        minion.setHostName(node.getHostname());
        minion.setDomain(node.getDomain());
        minion.setServers(calculateServerIps(restartNeededFlagSupported, restartNeeded));
        minion.setRestartNeeded(restartNeeded);
        // set due to compatibility reasons
        minion.setServer(getGatewayPrivateIps().get(0));
        return minion;
    }

    private List<Minion> createMinionsFromOriginalTargets() {
        return originalTargets.stream()
                .map(ot -> createMinion(ot, params.isRestartNeededFlagSupported(), params.isRestartNeeded()))
                .collect(Collectors.toList());
    }

    /***
     *  Restart needed flag was introduced for the following use case:
     *
     *     In a repair scenario where salt master has the same IP address as before, the minions on other instances are not restarted by salt-bootstrap.
     *     Salt minion does not try to communicate with master by itself for a long time (30mins or sthg like this).
     *     So when we are waiting for the minions' keys on salt-master then timeout can happen if all the minions haven't tried to communicate with
     *     master during this time frame.
     *
     *     Timeout error message in this scenario: There are missing nodes from salt network response.
     *     Solution was to introduce a restartNeeded flag in salt-bootstrap and we restart salt minions every time we do a salt-master replacement.
     *     (it used to be restarted only in case when salt-master IP address changed)
     *
     *  In case of older salt-bootsrap ( pre 0.13.4 ) the restartNeeded flag is not interpreted. So in an upgrade scenario (it has a repair flow in
     *  itself) where the upgrade starts from a 7.1.0 image, the minions are not restarted so the above mentioned timeout could happen.
     *
     *  It is a dirty fix but if the restartNeeded flag is set but salt-bootstrap does not support this flag, then we change the
     *  salt master ip address to loopback address (127.0.0.1), so salt-minion will be restarted by older salt-bootstrap also.
     *
     *  The original IP will be set in the next iteration because restartNeeded flag will be false.
     *
     * @param restartNeededFlagSupported is restartNeeded flag supported by salt-bootstrap
     * @param restartNeeded restart minions
     * @return salt master(s) ip adress(es)
     */
    private List<String> calculateServerIps(boolean restartNeededFlagSupported, boolean restartNeeded) {
        if (!restartNeededFlagSupported && restartNeeded) {
            return Collections.singletonList("127.0.0.1");
        } else {
            return getGatewayPrivateIps();
        }
    }

    private List<String> getGatewayPrivateIps() {
        return allGatewayConfigs.stream().map(GatewayConfig::getPrivateAddress).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SaltBootstrap{");
        sb.append("sc=").append(sc);
        sb.append(", allGatewayConfigs=").append(allGatewayConfigs);
        sb.append(", originalTargets=").append(originalTargets);
        sb.append(", targets=").append(targets);
        sb.append('}');
        return sb.toString();
    }
}
