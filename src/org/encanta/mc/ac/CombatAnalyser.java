package org.encanta.mc.ac;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;

public class CombatAnalyser {
	private EncantaAC main;
	private LVQNeuronNetwork lvq;

	public CombatAnalyser(EncantaAC main) {
		this.main = main;

	}

	public void rebuild() throws FileNotFoundException, IOException, InvalidConfigurationException {
		this.lvq = new LVQNeuronNetwork(0.2, 0.95);
		for (Dataset dataset : Utils.getAllCategory())
			lvq.input(dataset);
		lvq.normalize();
		lvq.initialize();
		lvq.trainUntil(0.00000000001);
	}

	private Bukkit getServer() {
		// TODO Auto-generated method stub
		return null;
	}

	public void rebuild(Player callback) throws FileNotFoundException, IOException, InvalidConfigurationException {
		this.lvq = new LVQNeuronNetwork(0.2, 0.95);
		for (Dataset dataset : Utils.getAllCategory())
			lvq.input(dataset);
		lvq.normalize();
		lvq.initialize();
		callback.sendMessage(
				ChatColor.GREEN + "Rebuilded neuron network with epoch(es) " + lvq.trainUntil(0.00000000001));
	}

	public void sendAnalyse(ConsoleCommandSender sender, Double[] dump, Player p) {
		lvq.printPredictResult(dump);
		//getServer();
		//ConsoleCommandSender console = Bukkit.getConsoleSender();
		sender.sendMessage(ChatColor.GOLD + "** Analysis Report **");
		sender.sendMessage(ChatColor.GREEN + "  Best matched: " + ChatColor.YELLOW + lvq.predict(dump).bestMatched);
		sender.sendMessage(
				ChatColor.GREEN + "  Euclidean distance: " + ChatColor.YELLOW + lvq.predict(dump).distance);
		if(lvq.predict(dump).bestMatched.equals("combat.Impossible")){
			//p.kickPlayer(ChatColor.translateAlternateColorCodes('&', "&c&lUnrealPower&r &8-&r &7Machine Learning (combat.Impossible)"));
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + p.getPlayerListName() + " 7d &c&lUnrealPower&r &8-&r &7Machine Learning (" + lvq.predict(dump).bestMatched + ")");
		}
	}

	public void sendInfoToPlayer(ConsoleCommandSender sender) {
		sender.sendMessage(ChatColor.AQUA + "  Neuron network: ");
		sender.sendMessage("   Input layer: " + ChatColor.YELLOW + lvq.getInputLayerSize());
		sender.sendMessage("   Output layer: " + ChatColor.YELLOW + lvq.getOutputLayerSize());
		lvq.print_outputlayers();

		try {
			Utils.sendInfoToPlayer(sender);
		} catch (IOException | InvalidConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
