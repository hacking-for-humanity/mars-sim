package org.mars.sim.console.chat.simcommand;

import java.util.List;

import org.mars.sim.console.chat.ChatCommand;
import org.mars.sim.console.chat.command.InteractiveChatCommand;
import org.mars_sim.msp.core.Unit;

/**
 * This class is used to handle the outcome of a Connect command. It repesents a connection with a Unit. 
 */
public abstract class ConnectedUnitCommand extends InteractiveChatCommand {

	private Unit unit;

	protected ConnectedUnitCommand(Unit unit, List<ChatCommand> commands, InteractiveChatCommand parent) {
		super(null, null, null, null, unit.getName(), commands);

		this.unit = unit;

		// Add the Command commands from the parent
		if (parent != null) {
			addSubCommands(parent.getSubCommands());
		}
		// Add in the standard commands to reconnect and leave Unit
		addSubCommand(ByeCommand.BYE);

		setIntroduction("Connected to " + unit.getName());
	}

	/**
	 * The Unit with teh connection.
	 * @return
	 */
	public Unit getUnit() {
		return unit;
	}

}
