package com.sophon.client.cli;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "sophon",
        mixinStandardHelpOptions = true,
        version = "Sophon CLI 1.0.0",
        description = "Sophon local Agent — CLI client",
        subcommands = {
                HealthCommand.class,
                SessionCommand.class,
                ChatCommand.class,
                ReplCommand.class
        }
)
public class CliApp implements Callable<Integer> {

    public static void main(String[] args) {
        int code = new CommandLine(new CliApp()).execute(args);
        System.exit(code);
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }
}
