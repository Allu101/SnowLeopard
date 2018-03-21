package org.encanta.mc.ac;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.craftbukkit.v1_12_R1.command.ColouredConsoleSender;

import net.md_5.bungee.api.ChatColor;

public class EncantaAC extends JavaPlugin implements CommandExecutor {
	private static EncantaAC instance;
	private AimDataManager dataManager;
	final String messagePrefix = ChatColor.translateAlternateColorCodes('&', "&9&l「&r&bUnrealPower&r&9&l」"); // â–ºâ–º
	private CombatAnalyser analyser;
	private long trainTimeLength = 6; // in seconds
	private long trainPhases = 6;
	private double outlierThreshold = 0.3;

	public void onEnable() {
		instance = this;
		Utils.initFolders();
		this.dataManager = new AimDataManager(this);
		this.analyser = new CombatAnalyser(this);
		try {
			this.analyser.rebuild();
		} catch (IOException | InvalidConfigurationException e) {
			this.getLogger().severe("Could not build neuron network, please check the stacktrace");
			e.printStackTrace();
		}
		this.getCommand("upw").setExecutor(this);
		this.getServer().getPluginManager().registerEvents(new PlayerDataLogger(this), this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("upw")) {
			if (args.length == 0) {
				sender.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&', "&fV: &910.2-1U&r &fNN: &91&r &fBV: &91 &fID: &9Unavailable"));
				sender.sendMessage(
						ChatColor.WHITE + "Server version: " + ChatColor.DARK_AQUA + this.getServer().getVersion());
				return true;
			}
			if (args.length == 1)
				switch (args[0]) {
				case "start":
					this.dataManager.addPlayer(sender.getName());
					sender.sendMessage(messagePrefix + ChatColor.YELLOW + "Started capturing");
					return true;
				case "stop":
					if (!this.dataManager.isRecording(sender.getName()))
						return true;
					String fileName = String.valueOf(System.currentTimeMillis());
					try {
						this.dataManager.getDataSeries(sender.getName()).save(fileName);
					} catch (IOException | InvalidConfigurationException e) {
						e.printStackTrace();
						sender.sendMessage(messagePrefix + ChatColor.YELLOW + "File saving failed, check consoles");
					}
					this.dataManager.removePlayer(sender.getName());
					sender.sendMessage(messagePrefix + ChatColor.YELLOW + "Stopped capturing");
					return true;
				case "cancel":
					if (!this.dataManager.isRecording(sender.getName()))
						return true;
					this.dataManager.clearData(sender.getName());
					sender.sendMessage(messagePrefix + ChatColor.YELLOW + "Cancelled capturing");
					return true;
				case "m":
					Player p = (Player) sender;
					Location toSpawn = p.getLocation().add(p.getEyeLocation().getDirection().multiply(5));
					toSpawn.setY(toSpawn.getY() + 2);
					p.getWorld().spawnEntity(toSpawn, EntityType.ZOMBIE);
					return true;
				case "info":
					analyser.sendInfoToPlayer((Player) sender);
					return true;
				case "rebuild":
					sender.sendMessage(messagePrefix + ChatColor.GREEN + "Attempt to rebuild neuron network...");
					try {
						analyser.rebuild((Player) sender);
					} catch (IOException | InvalidConfigurationException e) {
						e.printStackTrace();
					}
					return true;
				case "analyse":
					Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
						@Override
						public void run() {
							Player player = (Player) sender;
							if (player.getNearbyEntities(10, 10, 10).size() == 0)
								return;
							Entity entity = player.getNearbyEntities(10, 10, 10).get(0);
							Vector playerLookDir = player.getEyeLocation().getDirection();
							Vector playerEyeLoc = player.getEyeLocation().toVector();
							Vector entityLoc = entity.getLocation().toVector();
							Vector playerEntityVec = entityLoc.subtract(playerEyeLoc);
							float angle = playerLookDir.angle(playerEntityVec);
							player.sendMessage(String.valueOf(angle));
						}

					}, 0, 10);
					return true;
				}

			if (args.length == 2)
				switch (args[0]) {
				case "train":
					String category = args[1];
					sender.sendMessage(messagePrefix + ChatColor.YELLOW + "Start traning " + category);
					this.train((Player) sender, category);
					return true;
				}

			if (args.length == 3) {
				switch (args[0]) {
				case "analyse":
					String playername = args[1];
					int timelength = Integer.valueOf(args[2]);
					sender.sendMessage(messagePrefix + ChatColor.YELLOW + "Attempt to classify " + playername + " for "
							+ timelength + " seconds");
					this.test((Player) sender, Bukkit.getPlayer(playername), timelength);
					return true;
				}
			}
			sender.sendMessage(messagePrefix + ChatColor.RED + "Unknown command");
			return true;
		}
		return false;
	}

	private BukkitTask task;

	private void train(Player p, String category) {
		if (this.dataManager.isRecording(p.getName())) {
			p.sendMessage(
					messagePrefix + ChatColor.RED + "Player already in a capturing process, please cancel it at first");
			return;
		}
		task = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
			int trained = 0;
			List<Dataset> samples = new ArrayList<Dataset>();

			@Override
			public void run() {
				if (trained != 0) {
					AimDataSeries datas = dataManager.getDataSeries(p.getName());
					// delete outliers
					if (samples.size() >= 1)	// if last sample != null
						for (int i = 0; i <= datas.getAllDump().length - 1; i++) {
							// simulate the delta between current sample and last sample
							double delta = Math.abs(datas.getAllDump()[i] - samples.get(samples.size() - 1).data[i]);
							if (delta >= outlierThreshold) {
								p.sendMessage(messagePrefix + ChatColor.YELLOW + "Outlier detected at sample " + trained + ", resampling...");
								dataManager.clearData(p.getName());
								return;
							}
						}
					p.sendMessage(messagePrefix + ChatColor.GREEN + "Finished sampling " + trained);
					samples.add(new Dataset(category, datas.getAllDump()));
					dataManager.clearData(p.getName());
				}
				if (trained >= trainPhases) {
					p.sendMessage(messagePrefix + ChatColor.GREEN + "Sample process finished");
					try {
						Utils.saveCategory(category, samples);
						p.sendMessage(messagePrefix + ChatColor.YELLOW + "Sample saved to " + category);
					} catch (IOException | InvalidConfigurationException e) {
						e.printStackTrace();
						p.sendMessage(
								messagePrefix + ChatColor.RED + "Failed to save model " + category + ", check console");
					}
					dataManager.removePlayer(p.getName());
					task.cancel();
					p.sendMessage(messagePrefix + ChatColor.GREEN + "Attempt to rebuild neuron network...");
					try {
						analyser.rebuild(p);
					} catch (IOException | InvalidConfigurationException e) {
						p.sendMessage(messagePrefix + ChatColor.RED + "Failed to build neuron network, check console");
						e.printStackTrace();
					}
					return;
				}
				trained++;
				p.sendMessage(messagePrefix + ChatColor.YELLOW + "Sampling features for " + category + ", sample " + trained + "/"
						+ trainPhases);
				// start train
				dataManager.addPlayer(p.getName());
			}
		}, 0, 20 * trainTimeLength);
	}

	private void test(Player callback, Player p, int timelength) {
		dataManager.addPlayer(p.getName());
		Bukkit.getScheduler().runTaskLater(this, new Runnable() {
			@Override
			public void run() {
				AimDataSeries data = getDataManager().getDataSeries(p.getName());
				dataManager.removePlayer(p.getName());
				analyser.sendAnalyse(callback, data.getAllDump(), p);
			}
		}, 20 * timelength);

	}

	public static EncantaAC getInstance() {
		return instance;
	}

	public AimDataManager getDataManager() {
		return this.dataManager;
	}

	public static void main(String[] args) {
		LVQNeuronNetwork lvq = new LVQNeuronNetwork(0.5, 0.95);

		Double[][] train_killaura = readDataset("E:/Killaura");
		for (Double[] line : train_killaura) {
			lvq.input("killaura", line);
		}
		Double[][] train_vanilla = readDataset("E:/Vanilla");
		for (Double[] line : train_vanilla) {
			lvq.input("vanilla", line);
		}
		lvq.print_inputlayers();

		System.out.println(">> Normalizing input layers");
		lvq.normalize();
		lvq.print_inputlayers();

		lvq.initialize();
		lvq.print_outputlayers();

		System.out.println(">> Training Neuron Network... Trained " + lvq.trainUntil(0.00000000001) + " times");
		lvq.print_outputlayers();

		System.out.println("Predicting the category of a new dataset according to the trained neuron network");
		System.out.println("  Killaura categorized: " + lvq.predict(
				new Double[] { 0.14148080214650174, 0.11398027697664276, 0.23061594367027283, 0.11088762416139893 }));
		System.out.println("  Vanilla categorized: " + lvq.predict(
				new Double[] { 0.20942507828179005, 0.13824867503629082, 0.37103211879730225, 0.1600528048017086 }));
	}

	public static Double[][] readDataset(String path) {
		try {
			int features = 0;
			String file = FileUtils.readFileToString(new File(path));
			List<Double[]> datasets = new ArrayList<Double[]>();

			for (String line : file.split("\n")) {
				List<Double> oneline = new ArrayList<Double>();
				for (String feature : line.split(" ")) {
					features = line.split(" ").length;
					oneline.add(Double.valueOf(feature));
				}
				Double[] buf = new Double[oneline.size()];
				datasets.add(oneline.toArray(buf));
				oneline.clear();
			}

			Double[][] buf = new Double[datasets.size()][features];
			datasets.toArray(buf);
			return buf;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
