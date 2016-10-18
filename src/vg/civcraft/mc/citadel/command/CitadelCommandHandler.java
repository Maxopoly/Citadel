package vg.civcraft.mc.citadel.command;

import java.util.HashMap;
import java.util.Map;

import vg.civcraft.mc.citadel.command.commands.Acid;
import vg.civcraft.mc.citadel.command.commands.AreaReinforce;
import vg.civcraft.mc.citadel.command.commands.Bypass;
import vg.civcraft.mc.citadel.command.commands.Fortification;
import vg.civcraft.mc.citadel.command.commands.Information;
import vg.civcraft.mc.citadel.command.commands.Insecure;
import vg.civcraft.mc.citadel.command.commands.Materials;
import vg.civcraft.mc.citadel.command.commands.Off;
import vg.civcraft.mc.citadel.command.commands.Reinforce;
import vg.civcraft.mc.citadel.command.commands.ReinforcementsGUI;
import vg.civcraft.mc.citadel.command.commands.SetLogging;
import vg.civcraft.mc.citadel.command.commands.Stats;
import vg.civcraft.mc.citadel.command.commands.ToggleEasyMode;
import vg.civcraft.mc.citadel.command.commands.UpdateReinforcements;
import vg.civcraft.mc.civmodcore.command.Command;
import vg.civcraft.mc.civmodcore.command.CommandHandler;

public class CitadelCommandHandler extends CommandHandler {
	public Map<String, Command> commands = new HashMap<String, Command>();
	/**
	 * Registers the commands for the CommandHandler.
	 */
	public void registerCommands() {
		addCommands(new Acid("Acid"));
		addCommands(new Bypass("Bypass"));
		addCommands(new Fortification("Fortification"));
		addCommands(new Information("Information"));
		addCommands(new Insecure("Insecure"));
		addCommands(new Reinforce("Reinforce"));
		addCommands(new Materials("Materials"));
		addCommands(new Off("Off"));
		addCommands(new Stats("Stats"));
		addCommands(new UpdateReinforcements("UpdateReinforcements"));
		addCommands(new AreaReinforce("AreaReinforce"));
		addCommands(new SetLogging("SetLogging"));
		addCommands(new ToggleEasyMode("ToggleEasyMode"));
		addCommands(new ReinforcementsGUI("ReinforcementGUI"));
	}
}
