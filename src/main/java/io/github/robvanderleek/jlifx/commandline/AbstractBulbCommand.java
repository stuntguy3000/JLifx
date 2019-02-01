package io.github.robvanderleek.jlifx.commandline;

import io.github.robvanderleek.jlifx.bulb.Bulb;
import io.github.robvanderleek.jlifx.bulb.BulbDiscoveryService;
import io.github.robvanderleek.jlifx.bulb.GatewayBulb;
import io.github.robvanderleek.jlifx.common.MacAddress;
import org.apache.commons.lang3.ArrayUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;

public abstract class AbstractBulbCommand implements CommandLineCommand {
    private boolean interrupted = false;

    private class KeyListener implements Runnable {
        @Override
        public void run() {
            try {
                new BufferedReader(new InputStreamReader(System.in)).readLine();
            } catch (IOException e) {
                interrupted = true;
            }
            interrupted = true;
        }
    }

    private class Timer implements Runnable {
        private final int duration;

        Timer(int duration) {
            this.duration = duration;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(duration * 1000);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            interrupted = true;
        }

    }

    protected boolean isInterrupted() {
        return interrupted;
    }

    protected void startKeyListenerThread(PrintStream out) {
        out.println("Press [ENTER] to stop");
        new Thread(new KeyListener()).start();
    }

    protected void startTimer(int duration) {
        new Thread(new Timer(duration)).start();
    }

    @Override
    public boolean execute(String[] args, PrintStream out) throws Exception {
        if (args.length < 2) {
            return false;
        }
        GatewayBulb gatewayBulb;
        if (args[1].equals("-gw") && Utils.isValidIpv4Address(args[2]) && Utils.isValidMacAddress(args[3])) {
            gatewayBulb = new GatewayBulb(InetAddress.getByAddress(Utils.parseIpv4Address(args[2])),
                    Utils.parseMacAddress(args[3]));
            args = ArrayUtils.removeAll(args, 1, 2, 3);
        } else {
            gatewayBulb = BulbDiscoveryService.discoverGatewayBulb();
        }
        if (gatewayBulb == null) {
            out.println("Could not discover a gateway bulb!");
            System.exit(0);
        }
        String[] commandArgs = getCommandArgs(args);
        boolean result = dispatchExecute(gatewayBulb, commandArgs, out);
        gatewayBulb.disconnect();
        return result;
    }

    private boolean dispatchExecute(GatewayBulb gatewayBulb, String[] commandArgs, PrintStream out) throws Exception {
        if (commandArgs[0].equalsIgnoreCase("all")) {
            return executeForAllBulbs(gatewayBulb, commandArgs, out);
        } else if (commandArgs[0].equalsIgnoreCase("gateway")) {
            return executeForGatewayBulb(gatewayBulb, commandArgs, out);
        } else {
            return executeForSingleBulb(gatewayBulb, commandArgs, out);
        }
    }

    private boolean executeForAllBulbs(GatewayBulb gatewayBulb, String[] commandArgs,
                                       PrintStream out) throws Exception {
        return execute(BulbDiscoveryService.discoverAllBulbs(gatewayBulb), commandArgs, out);
    }

    private boolean executeForGatewayBulb(GatewayBulb gatewayBulb, String[] commandArgs,
                                          PrintStream out) throws Exception {
        return execute(Collections.singletonList(gatewayBulb), commandArgs, out);
    }

    private boolean executeForSingleBulb(GatewayBulb gatewayBulb, String[] commandArgs,
                                         PrintStream out) throws Exception {
        Bulb bulb;
        if (Utils.isValidMacAddress(commandArgs[0])) {
            MacAddress macAddress = Utils.parseMacAddress(commandArgs[0]);
            bulb = new Bulb(macAddress, gatewayBulb);
        } else {
            bulb = BulbDiscoveryService.discoverBulbByName(gatewayBulb, commandArgs[0])
                                       .orElseThrow(() -> new RuntimeException("Bulb not found: " + commandArgs[0]));
        }
        return execute(Collections.singletonList(bulb), commandArgs, out);
    }

    private String[] getCommandArgs(String[] args) {
        String[] commandArgs;
        if (args.length < 2) {
            commandArgs = new String[]{};
        } else {
            commandArgs = ArrayUtils.subarray(args, 1, args.length);
        }
        return commandArgs;
    }

    public abstract boolean execute(Collection<Bulb> bulbs, String[] commandArgs, PrintStream out) throws Exception;

}